package com.delivery.fdp.service;

import com.delivery.fdp.config.ManagedRuntimeProperties;
import com.delivery.fdp.config.YunxiaoProperties;
import com.delivery.fdp.repository.ManagedProjectRepository;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ManagedArtifactMaterializeService {
    private final ManagedProjectRepository repository;
    private final ManagedRuntimeProperties managed;
    private final YunxiaoProperties yunxiao;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final ConcurrentHashMap<Long, ReentrantLock> projectLocks = new ConcurrentHashMap<>();

    public ManagedArtifactMaterializeService(ManagedProjectRepository repository,
                                             ManagedRuntimeProperties managed,
                                             YunxiaoProperties yunxiao) {
        this.repository = repository;
        this.managed = managed;
        this.yunxiao = yunxiao;
    }

    public Map<String, Object> materialize(Long projectId, MaterializeRequest request) {
        repository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));
        if (request == null || request.artifacts() == null || request.artifacts().isEmpty()) {
            throw new IllegalArgumentException("至少选择一个制品版本");
        }
        if (!yunxiao.isEnabled() || !StringUtils.hasText(yunxiao.getOrganizationId()) || !StringUtils.hasText(yunxiao.getToken())) {
            throw new IllegalStateException("云效 Packages 未配置，请检查 FDP_YUNXIAO_ENABLED / ORGANIZATION_ID / TOKEN");
        }

        ReentrantLock lock = projectLocks.computeIfAbsent(projectId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) throw new IllegalStateException("当前项目已有下载/解压任务正在执行，请稍后重试");
        try {
            return doMaterialize(projectId, request);
        } finally {
            lock.unlock();
        }
    }

    private Map<String, Object> doMaterialize(Long projectId, MaterializeRequest request) {
        Map<Long, ManagedProjectRepository.ArtifactBinding> bindings = new LinkedHashMap<>();
        for (ManagedProjectRepository.ArtifactBinding binding : repository.artifacts(projectId)) {
            bindings.put(binding.id(), binding);
        }
        if (bindings.isEmpty()) throw new IllegalStateException("项目尚未绑定制品");

        Path root = projectRoot(projectId);
        Path current = root.resolve("current");
        Path staging = root.resolve(".materialize-" + System.currentTimeMillis());
        Path stagedCurrent = staging.resolve("current");
        List<ResolvedSelection> resolved = new ArrayList<>();
        try {
            Files.createDirectories(stagedCurrent);
            int index = 0;
            for (MaterializeSelection selection : request.artifacts()) {
                ManagedProjectRepository.ArtifactBinding binding = bindings.get(selection.artifactId());
                if (binding == null) throw new IllegalArgumentException("制品绑定不属于当前项目: " + selection.artifactId());
                if (!StringUtils.hasText(selection.version())) {
                    throw new IllegalArgumentException(binding.artifactName() + " 未选择版本");
                }

                URI uri = resolveDownloadUri(binding, selection);
                Path archive = staging.resolve("artifact-" + index + ".package");
                download(uri, archive);
                Path target = resolveTarget(stagedCurrent, binding.targetDirectory());
                Files.createDirectories(target);
                extract(archive, target);
                resolved.add(new ResolvedSelection(binding, selection.version().trim(), uri));
                index++;
            }

            swapCurrent(root, current, stagedCurrent);
            List<String> versionSummary = new ArrayList<>();
            for (ResolvedSelection item : resolved) {
                repository.markArtifactDeployed(item.binding().id(), item.version());
                versionSummary.add(item.binding().artifactName() + "=" + item.version());
            }
            ManagedProjectRepository.Project project = repository.findById(projectId).orElseThrow();
            repository.markDeployed(projectId, String.join(", ", versionSummary), project.runtimeImage());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("projectId", projectId);
            result.put("status", "DEPLOYED");
            result.put("current", current.toString());
            result.put("versions", versionSummary);
            result.put("windows", ShellCommandSupport.windows());
            result.put("containerPrepared", false);
            result.put("message", ShellCommandSupport.windows()
                    ? "制品已下载并解压到 current；Windows 本地不会创建 Docker Container"
                    : "制品已下载并解压到 current；Container 将在启动时按当前配置准备");
            return result;
        } catch (RuntimeException e) {
            repository.updateError(projectId, message(e));
            throw e;
        } catch (Exception e) {
            repository.updateError(projectId, message(e));
            throw new IllegalStateException("制品下载/解压失败: " + message(e), e);
        } finally {
            try { deleteRecursively(staging); } catch (Exception ignored) {}
        }
    }

    private URI resolveDownloadUri(ManagedProjectRepository.ArtifactBinding binding,
                                   MaterializeSelection selection) {
        if (StringUtils.hasText(selection.downloadUrl())) {
            URI supplied = URI.create(selection.downloadUrl().trim());
            if (!"https".equalsIgnoreCase(supplied.getScheme())) {
                throw new IllegalArgumentException("制品 downloadUrl 只允许 HTTPS");
            }
            return supplied;
        }
        String base = StringUtils.hasText(yunxiao.getPackagesDownloadBaseUrl())
                ? yunxiao.getPackagesDownloadBaseUrl().trim()
                : "https://packages.aliyun.com";
        return UriComponentsBuilder.fromHttpUrl(base)
                .pathSegment("api", "protocol", yunxiao.getOrganizationId(), "generic",
                        binding.repositoryId(), "files", binding.artifactName())
                .queryParam("version", selection.version().trim())
                .build()
                .encode()
                .toUri();
    }

    private void download(URI uri, Path destination) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .GET();
        if (uri.getHost() != null && uri.getHost().endsWith("packages.aliyun.com")) {
            builder.header("x-yunxiao-token", yunxiao.getToken());
        }
        HttpResponse<Path> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(destination);
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new IllegalStateException("Packages 下载鉴权失败（HTTP " + response.statusCode()
                        + "）。当前 PAT 可读取 OpenAPI 元数据，但该仓库下载协议可能要求云效生成的签名 downloadUrl/Packages 服务连接凭证。");
            }
            throw new IllegalStateException("Packages 下载失败（HTTP " + response.statusCode() + "）：" + uri);
        }
        if (!Files.isRegularFile(destination) || Files.size(destination) == 0) {
            throw new IllegalStateException("Packages 返回了空制品文件");
        }
    }

    private void extract(Path archive, Path target) throws Exception {
        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(archive))) {
            input.mark(8);
            byte[] magic = input.readNBytes(4);
            input.reset();
            if (magic.length >= 2 && (magic[0] & 0xff) == 0x1f && (magic[1] & 0xff) == 0x8b) {
                try (GzipCompressorInputStream gzip = new GzipCompressorInputStream(input);
                     TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
                    extractTar(tar, target);
                }
                return;
            }
            if (magic.length >= 4 && magic[0] == 'P' && magic[1] == 'K') {
                try (ZipArchiveInputStream zip = new ZipArchiveInputStream(input)) {
                    extractZip(zip, target);
                }
                return;
            }
            try (TarArchiveInputStream tar = new TarArchiveInputStream(input)) {
                extractTar(tar, target);
            }
        }
    }

    private void extractTar(TarArchiveInputStream tar, Path target) throws Exception {
        TarArchiveEntry entry;
        boolean found = false;
        while ((entry = tar.getNextTarEntry()) != null) {
            found = true;
            if (entry.isSymbolicLink() || entry.isLink()) continue;
            Path output = safeEntry(target, entry.getName());
            if (entry.isDirectory()) {
                Files.createDirectories(output);
            } else {
                Files.createDirectories(output.getParent());
                Files.copy(tar, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (!found) throw new IllegalStateException("制品不是有效的 tar/tgz 压缩包");
    }

    private void extractZip(ZipArchiveInputStream zip, Path target) throws Exception {
        ZipArchiveEntry entry;
        boolean found = false;
        while ((entry = zip.getNextZipEntry()) != null) {
            found = true;
            Path output = safeEntry(target, entry.getName());
            if (entry.isDirectory()) {
                Files.createDirectories(output);
            } else {
                Files.createDirectories(output.getParent());
                Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (!found) throw new IllegalStateException("制品不是有效的 zip 压缩包");
    }

    private Path safeEntry(Path target, String name) {
        Path output = target.resolve(name == null ? "" : name).normalize();
        if (!output.startsWith(target)) throw new IllegalArgumentException("制品包含非法越界路径: " + name);
        return output;
    }

    private Path resolveTarget(Path stagedCurrent, String relative) {
        String value = relative == null || relative.isBlank() ? "." : relative.replace('\\', '/');
        Path target = ".".equals(value) ? stagedCurrent : stagedCurrent.resolve(value).normalize();
        if (!target.startsWith(stagedCurrent)) throw new IllegalArgumentException("制品解压目录不能离开 current");
        return target;
    }

    private void swapCurrent(Path root, Path current, Path stagedCurrent) throws Exception {
        Path backup = root.resolve(".current-backup-" + System.currentTimeMillis());
        boolean backedUp = false;
        try {
            Files.createDirectories(root);
            if (Files.exists(current)) {
                Files.move(current, backup, StandardCopyOption.REPLACE_EXISTING);
                backedUp = true;
            }
            Files.move(stagedCurrent, current, StandardCopyOption.REPLACE_EXISTING);
            if (backedUp) deleteRecursively(backup);
        } catch (Exception e) {
            try { if (Files.exists(current)) deleteRecursively(current); } catch (Exception ignored) {}
            if (backedUp && Files.exists(backup)) Files.move(backup, current, StandardCopyOption.REPLACE_EXISTING);
            throw e;
        }
    }

    private Path projectRoot(Long id) {
        Path root = Path.of(managed.getProjectRoot()).toAbsolutePath().normalize();
        Path project = root.resolve(String.valueOf(id)).normalize();
        if (!project.startsWith(root)) throw new IllegalStateException("项目目录非法");
        return project;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        }
    }

    private String message(Throwable error) {
        return error == null || error.getMessage() == null ? String.valueOf(error) : error.getMessage();
    }

    public record MaterializeSelection(Long artifactId, String version, String downloadUrl) {}
    public record MaterializeRequest(List<MaterializeSelection> artifacts) {}
    private record ResolvedSelection(ManagedProjectRepository.ArtifactBinding binding, String version, URI uri) {}
}
