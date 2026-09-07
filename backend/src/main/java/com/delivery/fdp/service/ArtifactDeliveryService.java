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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ArtifactDeliveryService {
    private static final String MANIFEST_FILE = "fdp-manifest.yml";

    private final RuntimeProperties runtime;
    private final ArtifactDeliveryRepository repository;
    private final YunxiaoOpenApiService yunxiao;
    private final CommandExecutor exec;
    private final DeploymentService deploymentService;
    private final ManagedEnvironmentService environment;
    private final TaskExecutor deploymentTaskExecutor;
    private final Set<Long> activeProjects = ConcurrentHashMap.newKeySet();

    public ArtifactDeliveryService(RuntimeProperties runtime,
                                   ArtifactDeliveryRepository repository,
                                   YunxiaoOpenApiService yunxiao,
                                   CommandExecutor exec,
                                   DeploymentService deploymentService,
                                   ManagedEnvironmentService environment,
                                   @Qualifier("deploymentTaskExecutor") TaskExecutor deploymentTaskExecutor) {
        this.runtime = runtime;
        this.repository = repository;
        this.yunxiao = yunxiao;
        this.exec = exec;
        this.deploymentService = deploymentService;
        this.environment = environment;
        this.deploymentTaskExecutor = deploymentTaskExecutor;
    }

    public List<ArtifactDeliveryRepository.Project> projects() {
        return repository.findAll();
    }

    public ArtifactDeliveryRepository.Project create(ArtifactDeliveryProjectRequest request) {
        normalize(request);
        if (!StringUtils.hasText(request.getProjectCode())) {
            request.setProjectCode(generateProjectCode(request));
        }
        validate(request);
        environment.assertSavable(request.getEnvContent());
        long id = repository.create(request);
        environment.save(id, request.getEnvContent());
        return project(id);
    }

    public ArtifactDeliveryRepository.Project update(Long id, ArtifactDeliveryProjectRequest request) {
        ArtifactDeliveryRepository.Project current = project(id);
        if ("DEPLOYING".equals(current.status()) || "QUEUED".equals(current.status())) {
            throw new IllegalStateException("部署任务执行中，暂时不能修改 Docker 配置");
        }
        request.setProjectCode(current.projectCode());
        normalize(request);
        validate(request);
        environment.assertSavable(request.getEnvContent());
        repository.update(id, request);
        if (request.getEnvContent() != null) environment.save(id, request.getEnvContent());
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

        Path managedEnv = environment.materialize(project);
        Path legacyEnv = null;
        if (managedEnv == null && StringUtils.hasText(project.envFile())) {
            legacyEnv = Path.of(project.envFile()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(legacyEnv)) {
                throw new IllegalStateException("旧版服务器 Env 文件不存在: " + legacyEnv + "。请在 FDP 编辑页把环境变量迁移到平台托管配置");
            }
        }
        Path dockerEnv = managedEnv != null ? managedEnv : legacyEnv;

        if (StringUtils.hasText(project.hostDataPath())) {
            Files.createDirectories(Path.of(project.hostDataPath()).toAbsolutePath().normalize());
        }

        run("docker rm -f " + ShellCommandSupport.quote(project.containerName()) + " >/dev/null 2>&1 || true", cwd);
        StringBuilder command = new StringBuilder("docker run -d --name ")
                .append(ShellCommandSupport.quote(project.containerName()))
                .append(" --restart unless-stopped");
        if (StringUtils.hasText(project.cpuLimit())) {
            command.append(" --cpus ").append(ShellCommandSupport.quote(project.cpuLimit()));
        }
        if (StringUtils.hasText(project.memoryLimit())) {
            command.append(" --memory ").append(ShellCommandSupport.quote(project.memoryLimit()));
        }
        command.append(" -p ")
                .append(ShellCommandSupport.quote("127.0.0.1:" + project.hostPort() + ":" + project.containerPort()));
        if (dockerEnv != null) {
            command.append(" --env-file ").append(ShellCommandSupport.quote(dockerEnv.toString()));
        }
        if (StringUtils.hasText(project.hostDataPath())) {
            command.append(" -v ").append(ShellCommandSupport.quote(project.hostDataPath() + ":" + project.containerDataPath()));
        }
        command.append(" ").append(ShellCommandSupport.quote(manifest.backendImage()));
        run(command.toString(), cwd);

        if (StringUtils.hasText(project.healthCheckPath())) {
            String path = project.healthCheckPath().startsWith("/")
                    ? project.healthCheckPath()
                    : "/" + project.healthCheckPath();
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
        return new Manifest(frontendArchive, frontendRoot, imageArchive, image);
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
        request.setCpuLimit(trim(request.getCpuLimit()));
        request.setMemoryLimit(trim(request.getMemoryLimit()));
        request.setHostDataPath(trim(request.getHostDataPath()));
        request.setContainerDataPath(trim(request.getContainerDataPath()));
        request.setHealthCheckPath(trim(request.getHealthCheckPath()));
        if (StringUtils.hasText(request.getPreviewPath()) && request.getPreviewPath().length() > 1) {
            request.setPreviewPath(request.getPreviewPath().replaceAll("/+$", ""));
        }
        if (StringUtils.hasText(request.getHealthCheckPath()) && !request.getHealthCheckPath().startsWith("/")) {
            request.setHealthCheckPath("/" + request.getHealthCheckPath());
        }
    }

    private void validate(ArtifactDeliveryProjectRequest request) {
        if (!StringUtils.hasText(request.getProjectCode()) || !request.getProjectCode().matches("^[A-Za-z0-9._-]+$")) {
            throw new IllegalArgumentException("内部项目标识格式非法");
        }
        if (!StringUtils.hasText(request.getProjectName())) throw new IllegalArgumentException("项目名称不能为空");
        if (!StringUtils.hasText(request.getPipelineId())) throw new IllegalArgumentException("必须选择 Flow 流水线");
        if (!StringUtils.hasText(request.getPackageRepoId())) throw new IllegalArgumentException("必须选择 Packages 仓库");
        if (!StringUtils.hasText(request.getArtifactName())) throw new IllegalArgumentException("必须选择交付制品");
        if (!StringUtils.hasText(request.getPreviewPath()) || "/".equals(request.getPreviewPath())
                || !request.getPreviewPath().matches("^/[A-Za-z0-9._/-]+$") || request.getPreviewPath().contains("..")) {
            throw new IllegalArgumentException("访问 Path 必须是非根路径，例如 /financial-system");
        }
        if (request.getHostPort() == null || request.getHostPort() < 1024 || request.getHostPort() > 65535) {
            throw new IllegalArgumentException("宿主机端口必须在 1024-65535 之间");
        }
        if (request.getContainerPort() == null || request.getContainerPort() < 1 || request.getContainerPort() > 65535) {
            throw new IllegalArgumentException("容器端口必须在 1-65535 之间");
        }
        if (!StringUtils.hasText(request.getContainerName())
                || !request.getContainerName().matches("^[A-Za-z0-9][A-Za-z0-9_.-]+$")) {
            throw new IllegalArgumentException("Container Name 格式非法");
        }
        if (StringUtils.hasText(request.getEnvFile()) && !request.getEnvFile().startsWith("/")) {
            throw new IllegalArgumentException("旧版 Env 文件必须是 FDP Linux 服务器绝对路径");
        }
        if (StringUtils.hasText(request.getCpuLimit()) && !request.getCpuLimit().matches("^[0-9]+(?:\\.[0-9]+)?$")) {
            throw new IllegalArgumentException("CPU Limit 格式非法，例如 1 或 0.5");
        }
        if (StringUtils.hasText(request.getMemoryLimit()) && !request.getMemoryLimit().matches("^[0-9]+[kKmMgG]?$")) {
            throw new IllegalArgumentException("Memory Limit 格式非法，例如 512m 或 1g");
        }
        boolean hostVolume = StringUtils.hasText(request.getHostDataPath());
        boolean containerVolume = StringUtils.hasText(request.getContainerDataPath());
        if (hostVolume != containerVolume) {
            throw new IllegalArgumentException("Volume 必须同时填写 Host Path 和 Container Path");
        }
        if (hostVolume && !request.getHostDataPath().startsWith("/")) {
            throw new IllegalArgumentException("Host Volume 必须是 FDP Linux 服务器绝对路径");
        }
        if (containerVolume && !request.getContainerDataPath().startsWith("/")) {
            throw new IllegalArgumentException("Container Volume 必须是容器内绝对路径");
        }
        if (StringUtils.hasText(request.getHealthCheckPath()) && !request.getHealthCheckPath().matches("^/[A-Za-z0-9_./-]*$")) {
            throw new IllegalArgumentException("Health Check Path 格式非法");
        }
    }

    private String generateProjectCode(ArtifactDeliveryProjectRequest request) {
        String base = firstNonBlank(request.getArtifactName(), request.getContainerName(), request.getProjectName(), "artifact");
        base = base.toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if (!StringUtils.hasText(base)) base = "artifact";
        if (base.length() > 50) base = base.substring(0, 50);
        String candidate;
        do {
            candidate = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        } while (repository.existsProjectCode(candidate));
        return candidate;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        return "artifact";
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
            String backendImage
    ) {}
}
