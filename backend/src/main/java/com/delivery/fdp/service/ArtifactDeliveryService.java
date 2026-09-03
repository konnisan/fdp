package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.dto.ArtifactDeliveryProjectRequest;
import com.delivery.fdp.repository.ArtifactDeliveryRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ArtifactDeliveryService {
    private static final String MANIFEST_FILE = "fdp-manifest.yml";

    private final RuntimeProperties runtime;
    private final ArtifactDeliveryRepository repository;
    private final YunxiaoOpenApiService yunxiao;
    private final CommandExecutor exec;
    private final DeploymentService deploymentService;
    private final TaskExecutor deploymentTaskExecutor;
    private final Set<Long> activeProjects = ConcurrentHashMap.newKeySet();

    public ArtifactDeliveryService(RuntimeProperties runtime,
                                   ArtifactDeliveryRepository repository,
                                   YunxiaoOpenApiService yunxiao,
                                   CommandExecutor exec,
                                   DeploymentService deploymentService,
                                   @Qualifier("deploymentTaskExecutor") TaskExecutor deploymentTaskExecutor) {
        this.runtime = runtime;
        this.repository = repository;
        this.yunxiao = yunxiao;
        this.exec = exec;
        this.deploymentService = deploymentService;
        this.deploymentTaskExecutor = deploymentTaskExecutor;
    }

    public List<ArtifactDeliveryRepository.Project> projects() {
        return repository.findAll();
    }

    public ArtifactDeliveryRepository.Project create(ArtifactDeliveryProjectRequest request) {
        normalize(request);
        validate(request);
        long id = repository.create(request);
        return project(id);
    }

    public List<Map<String, Object>> releases(Long projectId) {
        ArtifactDeliveryRepository.Project project = project(projectId);
        return yunxiao.releaseCandidates(project.pipelineId(), project.packageRepoId(), project.artifactName());
    }

    public List<ArtifactDeliveryRepository.Deployment> history(Long projectId) {
        project(projectId);
        return repository.deployments(projectId);
    }

    public Map<String, Object> deploy(Long projectId, String requestedRunId) {
        ArtifactDeliveryRepository.Project project = project(projectId);
        if (!activeProjects.add(projectId)) {
            throw new IllegalStateException("该工程已有制品部署任务正在执行");
        }

        Map<String, Object> release;
        try {
            if (StringUtils.hasText(requestedRunId)) {
                release = yunxiao.releaseCandidate(
                        project.pipelineId(), requestedRunId.trim(), project.packageRepoId(), project.artifactName());
            } else {
                List<Map<String, Object>> candidates = releases(projectId);
                if (candidates.isEmpty()) {
                    throw new IllegalStateException("没有找到可部署的成功 Flow 制品版本");
                }
                release = candidates.get(0);
            }
            String runId = text(release.get("runId"));
            String version = text(release.get("version"));
            long deploymentId = repository.createDeployment(projectId, runId, version);
            repository.updateStatus(projectId, "QUEUED");
            deploymentTaskExecutor.execute(() -> execute(deploymentId, projectId, release));
            return Map.of(
                    "deploymentId", deploymentId,
                    "projectId", projectId,
                    "runId", runId,
                    "version", version,
                    "status", "QUEUED"
            );
        } catch (RuntimeException e) {
            activeProjects.remove(projectId);
            throw e;
        }
    }

    private void execute(long deploymentId, Long projectId, Map<String, Object> release) {
        String image = null;
        try {
            repository.markDeploymentRunning(deploymentId);
            repository.updateStatus(projectId, "DEPLOYING");
            ArtifactDeliveryRepository.Project project = project(projectId);
            ensureLinuxRuntime();

            String version = requiredText(release, "version");
            String runId = requiredText(release, "runId");
            String downloadUrl = requiredText(release, "downloadUrl");
            String md5 = text(release.get("md5"));
            validateDownloadUrl(downloadUrl);

            Path releaseRoot = releaseRoot(project, version);
            deleteRecursively(releaseRoot);
            Files.createDirectories(releaseRoot);
            Path bundle = releaseRoot.resolve("delivery-bundle.tgz");

            run("curl -fL --retry 2 --connect-timeout 15 -o "
                    + ShellCommandSupport.quote(bundle.toString()) + " "
                    + ShellCommandSupport.quote(downloadUrl), releaseRoot);
            if (StringUtils.hasText(md5)) verifyMd5(bundle, md5);

            Path unpacked = releaseRoot.resolve("bundle");
            Files.createDirectories(unpacked);
            run("tar -xzf " + ShellCommandSupport.quote(bundle.toString())
                    + " -C " + ShellCommandSupport.quote(unpacked.toString()), releaseRoot);

            Manifest manifest = readManifest(unpacked.resolve(MANIFEST_FILE));
            publishFrontend(project, manifest, unpacked, releaseRoot);
            image = deployBackend(project, manifest, unpacked, releaseRoot);

            repository.updateDeployment(projectId, version, runId, image);
            deploymentService.refreshRoutes();
            repository.finishDeployment(deploymentId, "SUCCESS", image,
                    "Flow run #" + runId + " / version " + version + " deployed successfully");
        } catch (Exception e) {
            repository.updateStatus(projectId, "FAILED");
            repository.finishDeployment(deploymentId, "FAILED", image, message(e));
            try {
                deploymentService.refreshRoutes();
            } catch (Exception ignored) {
            }
        } finally {
            activeProjects.remove(projectId);
        }
    }

    private void publishFrontend(ArtifactDeliveryRepository.Project project,
                                 Manifest manifest,
                                 Path unpacked,
                                 Path cwd) throws Exception {
        Path frontendArchive = resolveRelative(unpacked, manifest.frontendArchive(), "frontend.archive");
        if (!Files.isRegularFile(frontendArchive)) {
            throw new IllegalStateException("前端制品不存在: " + manifest.frontendArchive());
        }

        Path extracted = unpacked.resolve("_frontend");
        deleteRecursively(extracted);
        Files.createDirectories(extracted);
        extractTarGz(frontendArchive, extracted, cwd);

        Path frontendRoot = resolveRelative(extracted, manifest.frontendRoot(), "frontend.root");
        if (!Files.isRegularFile(frontendRoot.resolve("index.html"))) {
            throw new IllegalStateException("前端制品缺少 index.html: " + frontendRoot);
        }

        Path staticRoot = Path.of(runtime.getStaticRoot()).toAbsolutePath().normalize();
        Path destination = staticRoot.resolve(project.previewPath().replaceFirst("^/", "")).normalize();
        if (!destination.startsWith(staticRoot)) throw new IllegalStateException("静态发布目录非法");
        Files.createDirectories(destination);
        run("rsync -a --delete " + ShellCommandSupport.quote(frontendRoot + "/") + " "
                + ShellCommandSupport.quote(destination + "/"), cwd);
    }

    private String deployBackend(ArtifactDeliveryRepository.Project project,
                                 Manifest manifest,
                                 Path unpacked,
                                 Path cwd) throws Exception {
        Path imageArchive = resolveRelative(unpacked, manifest.backendImageArchive(), "backend.imageArchive");
        if (!Files.isRegularFile(imageArchive)) {
            throw new IllegalStateException("后端镜像制品不存在: " + manifest.backendImageArchive());
        }
        run("docker load -i " + ShellCommandSupport.quote(imageArchive.toString()), cwd);
        run("docker image inspect " + ShellCommandSupport.quote(manifest.backendImage()) + " >/dev/null", cwd);

        if (StringUtils.hasText(project.envFile())) {
            Path env = Path.of(project.envFile()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(env)) {
                throw new IllegalStateException("FDP 环境变量文件不存在: " + env);
            }
        }

        run("docker rm -f " + ShellCommandSupport.quote(project.containerName()) + " >/dev/null 2>&1 || true", cwd);
        StringBuilder command = new StringBuilder("docker run -d --name ")
                .append(ShellCommandSupport.quote(project.containerName()))
                .append(" --restart unless-stopped")
                .append(" -p ")
                .append(ShellCommandSupport.quote("127.0.0.1:" + project.hostPort() + ":" + manifest.backendContainerPort()));
        if (StringUtils.hasText(project.envFile())) {
            command.append(" --env-file ").append(ShellCommandSupport.quote(Path.of(project.envFile()).toAbsolutePath().normalize().toString()));
        }
        command.append(" ").append(ShellCommandSupport.quote(manifest.backendImage()));
        run(command.toString(), cwd);

        if (StringUtils.hasText(manifest.backendHealthCheck())) {
            String path = manifest.backendHealthCheck().startsWith("/")
                    ? manifest.backendHealthCheck()
                    : "/" + manifest.backendHealthCheck();
            String url = "http://127.0.0.1:" + project.hostPort() + path;
            run("for i in $(seq 1 30); do curl -fsS --max-time 5 " + ShellCommandSupport.quote(url)
                    + " >/dev/null && exit 0; sleep 2; done; exit 1", cwd);
        }
        return manifest.backendImage();
    }

    @SuppressWarnings("unchecked")
    private Manifest readManifest(Path file) throws Exception {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("制品包根目录缺少 " + MANIFEST_FILE);
        }
        Object loaded;
        try (InputStream in = Files.newInputStream(file)) {
            loaded = new Yaml().load(in);
        }
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalStateException(MANIFEST_FILE + " 格式非法");
        }
        Map<String, Object> frontend = child(root, "frontend");
        Map<String, Object> backend = child(root, "backend");

        String frontendArchive = required(frontend, "archive");
        String frontendRoot = optional(frontend, "root", ".");
        String imageArchive = required(backend, "imageArchive");
        String image = required(backend, "image");
        int containerPort;
        try {
            containerPort = Integer.parseInt(required(backend, "containerPort"));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("backend.containerPort 必须是整数");
        }
        if (containerPort < 1 || containerPort > 65535) {
            throw new IllegalStateException("backend.containerPort 非法");
        }
        String healthCheck = optional(backend, "healthCheck", "");
        return new Manifest(frontendArchive, frontendRoot, imageArchive, image, containerPort, healthCheck);
    }

    private Map<String, Object> child(Map<?, ?> root, String key) {
        Object value = root.get(key);
        if (!(value instanceof Map<?, ?> map)) throw new IllegalStateException("缺少 manifest 节点: " + key);
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private String required(Map<String, Object> map, String key) {
        String value = text(map.get(key));
        if (!StringUtils.hasText(value)) throw new IllegalStateException("manifest 缺少字段: " + key);
        return value;
    }

    private String optional(Map<String, Object> map, String key, String fallback) {
        String value = text(map.get(key));
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void extractTarGz(Path archive, Path destination, Path cwd) {
        String name = archive.getFileName().toString().toLowerCase();
        if (!(name.endsWith(".tar.gz") || name.endsWith(".tgz"))) {
            throw new IllegalStateException("V1 前端制品只支持 .tar.gz / .tgz: " + archive.getFileName());
        }
        run("tar -xzf " + ShellCommandSupport.quote(archive.toString())
                + " -C " + ShellCommandSupport.quote(destination.toString()), cwd);
    }

    private Path releaseRoot(ArtifactDeliveryRepository.Project project, String version) {
        Path root = Path.of(runtime.getArtifactRoot()).toAbsolutePath().normalize();
        Path result = root.resolve(safeSegment(project.projectCode())).resolve(safeSegment(version)).normalize();
        if (!result.startsWith(root)) throw new IllegalStateException("制品缓存目录非法");
        return result;
    }

    private Path resolveRelative(Path root, String relative, String field) {
        Path result = root.resolve(relative).normalize();
        if (!result.startsWith(root)) throw new IllegalStateException(field + " 不能离开制品目录");
        return result;
    }

    private void ensureLinuxRuntime() {
        if (!runtime.isExecutionEnabled()) {
            throw new IllegalStateException("FDP_EXECUTION_ENABLED=false，正式制品部署不会在 DRY-RUN 模式执行");
        }
        if (ShellCommandSupport.windows()) {
            throw new IllegalStateException("Pipeline Artifact Deployment 仅支持 FDP Linux 部署服务器");
        }
    }

    private void validateDownloadUrl(String value) {
        URI uri = URI.create(value);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("云效制品 downloadUrl 不是 HTTP(S) 地址");
        }
    }

    private void verifyMd5(Path file, String expected) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expected.trim())) {
            throw new IllegalStateException("制品 MD5 校验失败，expected=" + expected + ", actual=" + actual);
        }
    }

    private void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        }
    }

    private void run(String command, Path cwd) {
        CommandExecutor.Result result = exec.execute(command, cwd);
        if (!result.success()) {
            throw new IllegalStateException("Command failed (" + result.exitCode() + "): " + result.output());
        }
    }

    private ArtifactDeliveryRepository.Project project(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("工程交付项目不存在: " + id));
    }

    private void normalize(ArtifactDeliveryProjectRequest request) {
        request.setProjectCode(trim(request.getProjectCode()));
        request.setProjectName(trim(request.getProjectName()));
        request.setPipelineId(trim(request.getPipelineId()));
        request.setPipelineName(trim(request.getPipelineName()));
        request.setPackageRepoId(trim(request.getPackageRepoId()));
        request.setPackageRepoName(trim(request.getPackageRepoName()));
        request.setArtifactName(trim(request.getArtifactName()));
        request.setPreviewPath(trim(request.getPreviewPath()));
        request.setContainerName(trim(request.getContainerName()));
        request.setEnvFile(trim(request.getEnvFile()));
        if (StringUtils.hasText(request.getPreviewPath()) && request.getPreviewPath().length() > 1) {
            request.setPreviewPath(request.getPreviewPath().replaceAll("/+$", ""));
        }
    }

    private void validate(ArtifactDeliveryProjectRequest request) {
        if (!StringUtils.hasText(request.getProjectCode()) || !request.getProjectCode().matches("^[A-Za-z0-9._-]+$")) {
            throw new IllegalArgumentException("projectCode 只能包含字母、数字、点、下划线和短横线");
        }
        if (!StringUtils.hasText(request.getProjectName())) throw new IllegalArgumentException("projectName is required");
        if (!StringUtils.hasText(request.getPipelineId())) throw new IllegalArgumentException("pipelineId is required");
        if (!StringUtils.hasText(request.getPackageRepoId())) throw new IllegalArgumentException("packageRepoId is required");
        if (!StringUtils.hasText(request.getArtifactName())) throw new IllegalArgumentException("artifactName is required");
        if (!StringUtils.hasText(request.getPreviewPath()) || "/".equals(request.getPreviewPath())
                || !request.getPreviewPath().matches("^/[A-Za-z0-9._/-]+$") || request.getPreviewPath().contains("..")) {
            throw new IllegalArgumentException("previewPath 必须是非根路径，例如 /financial-system");
        }
        if (request.getHostPort() == null || request.getHostPort() < 1024 || request.getHostPort() > 65535) {
            throw new IllegalArgumentException("hostPort 必须在 1024-65535 之间");
        }
        if (!StringUtils.hasText(request.getContainerName())
                || !request.getContainerName().matches("^[A-Za-z0-9][A-Za-z0-9_.-]+$")) {
            throw new IllegalArgumentException("containerName 格式非法");
        }
    }

    private String safeSegment(String value) {
        String result = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!StringUtils.hasText(result) || result.equals(".") || result.equals("..")) {
            throw new IllegalStateException("非法制品目录标识: " + value);
        }
        return result;
    }

    private String requiredText(Map<String, Object> map, String key) {
        String value = text(map.get(key));
        if (!StringUtils.hasText(value)) throw new IllegalStateException("云效制品缺少字段: " + key);
        return value;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String message(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }

    private record Manifest(
            String frontendArchive,
            String frontendRoot,
            String backendImageArchive,
            String backendImage,
            int backendContainerPort,
            String backendHealthCheck
    ) {}
}
