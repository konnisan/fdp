package com.delivery.fdp.service;

import com.delivery.fdp.config.ManagedRuntimeProperties;
import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.dto.ManagedProjectRequest;
import com.delivery.fdp.repository.ManagedProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ManagedProjectService {
    private static final Pattern DB_NAME = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern SAFE_RELATIVE = Pattern.compile("^[A-Za-z0-9._/-]*$");

    private final ManagedProjectRepository repository;
    private final ManagedRuntimeProperties managed;
    private final RuntimeProperties runtime;
    private final YunxiaoOpenApiService yunxiao;
    private final CredentialCrypto crypto;
    private final CommandExecutor exec;
    private final DataSource dataSource;

    public ManagedProjectService(ManagedProjectRepository repository,
                                 ManagedRuntimeProperties managed,
                                 RuntimeProperties runtime,
                                 YunxiaoOpenApiService yunxiao,
                                 CredentialCrypto crypto,
                                 CommandExecutor exec,
                                 DataSource dataSource) {
        this.repository = repository;
        this.managed = managed;
        this.runtime = runtime;
        this.yunxiao = yunxiao;
        this.crypto = crypto;
        this.exec = exec;
        this.dataSource = dataSource;
    }

    public List<Map<String, Object>> projects() {
        return repository.findAll().stream().map(this::view).toList();
    }

    public Map<String, Object> project(Long id) {
        return view(required(id));
    }

    public Map<String, Object> create(ManagedProjectRequest request) {
        normalize(request);
        validate(request, null);
        ensureDatabaseAvailable(request.getDatabaseName());
        createDatabase(request.getDatabaseName());
        long id = repository.create(request, encryptedEnvironment(request.getEnvContent()), "fdp-project-pending-" + System.nanoTime());
        String containerName = containerName(id);
        repository.updateContainer(id, null, containerName, request.getRuntimeImage());
        repository.replaceArtifacts(id, request.getArtifacts());
        prepareProjectDirectories(id);
        writeRuntimeFiles(required(id));
        return project(id);
    }

    public Map<String, Object> update(Long id, ManagedProjectRequest request) {
        ManagedProjectRepository.Project current = required(id);
        normalize(request);
        if (!current.databaseName().equals(request.getDatabaseName())) {
            throw new IllegalArgumentException("项目 database 创建后不可切换");
        }
        validate(request, id);
        repository.update(id, request, encryptedEnvironment(request.getEnvContent()));
        repository.replaceArtifacts(id, request.getArtifacts());
        writeRuntimeFiles(required(id));
        return project(id);
    }

    public List<ManagedProjectRepository.ArtifactBinding> artifacts(Long projectId) {
        required(projectId);
        return repository.artifacts(projectId);
    }

    public List<Map<String, Object>> artifactVersions(Long projectId, Long artifactId) {
        ManagedProjectRepository.ArtifactBinding binding = repository.artifact(projectId, artifactId)
                .orElseThrow(() -> new IllegalArgumentException("制品绑定不存在: " + artifactId));
        List<Map<String, Object>> rows = yunxiao.artifacts(binding.repositoryId(), "GENERIC", binding.artifactName(), 1, 50);
        for (Map<String, Object> row : rows) {
            if (binding.artifactName().equals(String.valueOf(row.get("module")))) {
                Object versions = row.get("versions");
                if (versions instanceof List<?> list) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
                            map.forEach((k, v) -> value.put(String.valueOf(k), v));
                            result.add(value);
                        }
                    }
                    return result;
                }
            }
        }
        return List.of();
    }

    public Map<String, Object> deploy(Long projectId, DeployRequest request) {
        ManagedProjectRepository.Project project = required(projectId);
        ensureLinuxExecution();
        if (request == null || request.artifacts() == null || request.artifacts().isEmpty()) {
            throw new IllegalArgumentException("至少选择一个制品版本");
        }
        Map<Long, ManagedProjectRepository.ArtifactBinding> bindings = new LinkedHashMap<>();
        for (ManagedProjectRepository.ArtifactBinding binding : repository.artifacts(projectId)) bindings.put(binding.id(), binding);
        if (bindings.isEmpty()) throw new IllegalStateException("项目尚未绑定任何制品");

        Path root = projectRoot(projectId);
        Path current = root.resolve("current");
        Path config = root.resolve("config");
        Path staging = root.resolve(".staging-" + System.currentTimeMillis());
        Path stagedCurrent = staging.resolve("current");
        try {
            Files.createDirectories(stagedCurrent);
            Files.createDirectories(config);
            int index = 0;
            List<String> versionSummary = new ArrayList<>();
            for (ArtifactSelection selection : request.artifacts()) {
                ManagedProjectRepository.ArtifactBinding binding = bindings.get(selection.artifactId());
                if (binding == null) throw new IllegalArgumentException("制品绑定不属于当前项目: " + selection.artifactId());
                if (!StringUtils.hasText(selection.version())) throw new IllegalArgumentException(binding.artifactName() + " 未选择版本");
                if (!StringUtils.hasText(selection.downloadUrl())) {
                    throw new IllegalArgumentException(binding.artifactName() + " 缺少 downloadUrl。Packages 列表 OpenAPI 只返回版本元数据，部署调用方需要传入云效生成的临时下载地址。");
                }
                Path archive = staging.resolve("artifact-" + index + ".tgz");
                run("curl -fL --retry 2 --connect-timeout 15 -o " + ShellCommandSupport.quote(archive.toString()) + " " + ShellCommandSupport.quote(selection.downloadUrl()), staging);
                Path target = resolveTarget(stagedCurrent, binding.targetDirectory());
                Files.createDirectories(target);
                run("tar -xzf " + ShellCommandSupport.quote(archive.toString()) + " -C " + ShellCommandSupport.quote(target.toString()), staging);
                repository.markArtifactDeployed(binding.id(), selection.version());
                versionSummary.add(binding.artifactName() + "=" + selection.version());
                index++;
            }
            Files.createDirectories(current);
            run("rsync -a --delete " + ShellCommandSupport.quote(stagedCurrent + "/") + " " + ShellCommandSupport.quote(current + "/"), root);
            writeRuntimeFiles(project);
            prepareContainer(project);
            repository.markDeployed(projectId, String.join(", ", versionSummary), project.runtimeImage());
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("projectId", projectId);
            result.put("status", "DEPLOYED");
            result.put("deployedVersions", versionSummary);
            result.put("containerName", project.containerName());
            result.put("message", "制品已下载并解压到 current；Container 已准备，未自动启动");
            return result;
        } catch (Exception e) {
            repository.updateError(projectId, message(e));
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        } finally {
            try { deleteRecursively(staging); } catch (Exception ignored) {}
        }
    }

    public Map<String, Object> start(Long id) {
        ManagedProjectRepository.Project project = required(id);
        ensureLinuxExecution();
        prepareContainer(project);
        CommandExecutor.Result result = exec.execute("docker start " + ShellCommandSupport.quote(project.containerName()), projectRoot(id));
        check(result, "docker start");
        repository.markRunningVersions(id);
        return runtime(id);
    }

    public Map<String, Object> stop(Long id) {
        ManagedProjectRepository.Project project = required(id);
        ensureLinuxExecution();
        CommandExecutor.Result result = exec.execute("docker stop " + ShellCommandSupport.quote(project.containerName()), projectRoot(id));
        check(result, "docker stop");
        repository.updateStatus(id, "STOPPED");
        return runtime(id);
    }

    public Map<String, Object> restart(Long id) {
        ManagedProjectRepository.Project project = required(id);
        ensureLinuxExecution();
        writeRuntimeFiles(project);
        CommandExecutor.Result result = exec.execute("docker restart " + ShellCommandSupport.quote(project.containerName()), projectRoot(id));
        check(result, "docker restart");
        repository.markRunningVersions(id);
        return runtime(id);
    }

    public Map<String, Object> runtime(Long id) {
        ManagedProjectRepository.Project project = required(id);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(view(project));
        if (!runtime.isExecutionEnabled() || ShellCommandSupport.windows()) {
            result.put("containerStatus", "DRY_RUN");
            return result;
        }
        CommandExecutor.Result inspected = exec.execute("docker inspect -f '{{.State.Status}}|{{.Config.Image}}|{{.Id}}' " + ShellCommandSupport.quote(project.containerName()), projectRoot(id));
        if (!inspected.success()) {
            result.put("containerStatus", "NOT_FOUND");
            result.put("runtimeImage", project.deployedRuntimeImage());
            return result;
        }
        String[] parts = inspected.output().trim().split("\\|", 3);
        result.put("containerStatus", parts.length > 0 ? parts[0] : "UNKNOWN");
        result.put("runtimeImage", parts.length > 1 ? parts[1] : project.deployedRuntimeImage());
        result.put("containerId", parts.length > 2 ? parts[2] : project.containerId());
        return result;
    }

    public Map<String, Object> logs(Long id) {
        ManagedProjectRepository.Project project = required(id);
        ensureLinuxExecution();
        CommandExecutor.Result result = exec.execute("docker logs --tail 300 " + ShellCommandSupport.quote(project.containerName()), projectRoot(id));
        check(result, "docker logs");
        return Map.of("projectId", id, "containerName", project.containerName(), "content", result.output());
    }

    public Map<String, Object> executeSql(Long id, String sql) {
        ManagedProjectRepository.Project project = required(id);
        if (!StringUtils.hasText(sql)) throw new IllegalArgumentException("SQL 不能为空");
        int statements = 0;
        try (Connection connection = dataSource.getConnection()) {
            connection.setCatalog(project.databaseName());
            for (String fragment : splitSql(sql)) {
                if (!StringUtils.hasText(fragment)) continue;
                try (Statement statement = connection.createStatement()) {
                    statement.execute(fragment);
                    statements++;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("SQL 执行失败: " + message(e), e);
        }
        return Map.of("projectId", id, "database", project.databaseName(), "statements", statements, "success", true);
    }

    public Map<String, Object> switchPreview(Long id) {
        ManagedProjectRepository.Project project = required(id);
        ensureLinuxExecution();
        Map<String, Object> state = runtime(id);
        if (!"running".equalsIgnoreCase(String.valueOf(state.get("containerStatus")))) {
            throw new IllegalStateException("目标项目未运行，FDP 不会自动启动项目");
        }
        Path file = Path.of(managed.getPreviewNginxConfigFile()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(file.getParent());
            String config = """
                    events {}
                    http {
                      server {
                        listen 80;
                        server_name _;
                        location / {
                          proxy_pass http://%s:%d;
                          proxy_http_version 1.1;
                          proxy_set_header Host $host;
                          proxy_set_header X-Real-IP $remote_addr;
                          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                        }
                      }
                    }
                    """.formatted(project.containerName(), project.servicePort());
            Files.writeString(file, config, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("写入预览 Nginx 配置失败", e);
        }
        String container = ShellCommandSupport.quote(managed.getPreviewNginxContainer());
        check(exec.execute("docker exec " + container + " nginx -t", projectRoot(id)), "preview nginx -t");
        check(exec.execute("docker exec " + container + " nginx -s reload", projectRoot(id)), "preview nginx reload");
        repository.setPreviewProject(id);
        return Map.of("previewProjectId", id, "projectName", project.projectName(), "switched", true);
    }

    public void delete(Long id) {
        ManagedProjectRepository.Project project = required(id);
        if (runtime.isExecutionEnabled() && !ShellCommandSupport.windows()) {
            exec.execute("docker rm -f " + ShellCommandSupport.quote(project.containerName()) + " >/dev/null 2>&1 || true", projectRoot(id));
        }
        try { deleteRecursively(projectRoot(id)); } catch (Exception ignored) {}
        repository.delete(id);
    }

    public List<String> runtimeImages() { return managed.getRuntimeImages(); }

    private void prepareContainer(ManagedProjectRepository.Project project) {
        Path root = projectRoot(project.id());
        Path current = root.resolve("current");
        Path config = root.resolve("config");
        try {
            Files.createDirectories(current);
            Files.createDirectories(config);
        } catch (Exception e) {
            throw new IllegalStateException("无法创建项目运行目录", e);
        }
        String inspect = "docker inspect -f '{{.Config.Image}}' " + ShellCommandSupport.quote(project.containerName());
        CommandExecutor.Result existing = exec.execute(inspect, root);
        if (existing.success()) {
            String currentImage = existing.output().trim();
            if (project.runtimeImage().equals(currentImage)) return;
            CommandExecutor.Result state = exec.execute("docker inspect -f '{{.State.Status}}' " + ShellCommandSupport.quote(project.containerName()), root);
            if (state.success() && "running".equalsIgnoreCase(state.output().trim())) {
                throw new IllegalStateException("Runtime Image 已修改，但当前 Container 正在运行。请先人工停止项目，再重新部署。");
            }
            run("docker rm -f " + ShellCommandSupport.quote(project.containerName()), root);
        }
        run("docker network inspect " + ShellCommandSupport.quote(managed.getDockerNetwork()) + " >/dev/null 2>&1 || docker network create " + ShellCommandSupport.quote(managed.getDockerNetwork()), root);
        String workdir = managed.getWorkspaceMount() + (".".equals(project.workDirectory()) ? "" : "/" + project.workDirectory());
        StringBuilder command = new StringBuilder("docker create --name ")
                .append(ShellCommandSupport.quote(project.containerName()))
                .append(" --restart unless-stopped")
                .append(" --network ").append(ShellCommandSupport.quote(managed.getDockerNetwork()))
                .append(" -w ").append(ShellCommandSupport.quote(workdir))
                .append(" -v ").append(ShellCommandSupport.quote(current + ":" + managed.getWorkspaceMount()))
                .append(" -v ").append(ShellCommandSupport.quote(config + ":" + managed.getConfigMount()))
                .append(" ").append(ShellCommandSupport.quote(project.runtimeImage()))
                .append(" /bin/sh ").append(ShellCommandSupport.quote(managed.getConfigMount() + "/start.sh"));
        run(command.toString(), root);
        CommandExecutor.Result idResult = exec.execute("docker inspect -f '{{.Id}}' " + ShellCommandSupport.quote(project.containerName()), root);
        repository.updateContainer(project.id(), idResult.success() ? idResult.output().trim() : null, project.containerName(), project.runtimeImage());
    }

    private void writeRuntimeFiles(ManagedProjectRepository.Project project) {
        Path config = projectRoot(project.id()).resolve("config");
        try {
            Files.createDirectories(config);
            Files.writeString(config.resolve("env.sh"), environmentScript(project), StandardCharsets.UTF_8);
            Files.writeString(config.resolve("app-start.sh"), appStartScript(project), StandardCharsets.UTF_8);
            if ("NGINX".equals(project.serviceMode())) {
                Files.writeString(config.resolve("nginx.conf"), nginxConfig(project), StandardCharsets.UTF_8);
                Files.writeString(config.resolve("supervisord.conf"), supervisorConfig(), StandardCharsets.UTF_8);
            }
            Files.writeString(config.resolve("start.sh"), startScript(project), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("生成 FDP 运行配置失败", e);
        }
    }

    private String environmentScript(ManagedProjectRepository.Project project) {
        StringBuilder out = new StringBuilder("#!/bin/sh\n");
        String custom = repository.environmentCiphertext(project.id()).map(crypto::decrypt).orElse("");
        for (String line : custom.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#")) continue;
            int eq = value.indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException("环境变量格式应为 KEY=VALUE");
            String key = value.substring(0, eq).trim();
            String val = value.substring(eq + 1);
            out.append("export ").append(key).append("=").append(shQuote(val)).append("\n");
        }
        out.append("export DB_HOST=").append(shQuote(managed.getMysqlHost())).append("\n");
        out.append("export DB_PORT=").append(shQuote(String.valueOf(managed.getMysqlPort()))).append("\n");
        out.append("export DB_NAME=").append(shQuote(project.databaseName())).append("\n");
        out.append("export DB_USER=").append(shQuote(managed.getMysqlUser())).append("\n");
        out.append("export DB_PASSWORD=").append(shQuote(managed.getMysqlPassword())).append("\n");
        return out.toString();
    }

    private String appStartScript(ManagedProjectRepository.Project project) {
        return "#!/bin/sh\nset -e\n. " + managed.getConfigMount() + "/env.sh\ncd "
                + ShellCommandSupport.quote(managed.getWorkspaceMount() + (".".equals(project.workDirectory()) ? "" : "/" + project.workDirectory()))
                + "\nexec /bin/sh -c " + shQuote(project.startCommand()) + "\n";
    }

    private String startScript(ManagedProjectRepository.Project project) {
        if ("NGINX".equals(project.serviceMode())) {
            return "#!/bin/sh\nset -e\n. " + managed.getConfigMount() + "/env.sh\nexec supervisord -n -c " + managed.getConfigMount() + "/supervisord.conf\n";
        }
        return "#!/bin/sh\nset -e\nexec /bin/sh " + managed.getConfigMount() + "/app-start.sh\n";
    }

    private String nginxConfig(ManagedProjectRepository.Project project) {
        String apiPrefix = normalizeApiPrefix(project.nginxApiPrefix());
        String staticRoot = managed.getWorkspaceMount() + "/" + cleanRelative(project.nginxStaticDirectory());
        return """
                events {}
                http {
                  server {
                    listen %d;
                    server_name _;
                    root %s;
                    index index.html;
                    location %s {
                      proxy_pass http://127.0.0.1:%d%s;
                      proxy_http_version 1.1;
                      proxy_set_header Host $host;
                      proxy_set_header X-Real-IP $remote_addr;
                      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                    }
                    location / { try_files $uri $uri/ /index.html; }
                  }
                }
                """.formatted(project.servicePort(), staticRoot, apiPrefix, project.nginxBackendPort(), apiPrefix);
    }

    private String supervisorConfig() {
        return """
                [supervisord]
                nodaemon=true

                [program:app]
                command=/bin/sh /fdp/app-start.sh
                autorestart=true
                startsecs=2
                stdout_logfile=/dev/stdout
                stdout_logfile_maxbytes=0
                stderr_logfile=/dev/stderr
                stderr_logfile_maxbytes=0

                [program:nginx]
                command=nginx -g 'daemon off;' -c /fdp/nginx.conf
                autorestart=true
                startsecs=2
                stdout_logfile=/dev/stdout
                stdout_logfile_maxbytes=0
                stderr_logfile=/dev/stderr
                stderr_logfile_maxbytes=0
                """;
    }

    private void normalize(ManagedProjectRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        request.setProjectName(trim(request.getProjectName()));
        request.setDatabaseName(trim(request.getDatabaseName()));
        request.setRuntimeImage(trim(request.getRuntimeImage()));
        request.setWorkDirectory(StringUtils.hasText(request.getWorkDirectory()) ? cleanRelative(request.getWorkDirectory()) : ".");
        request.setStartCommand(request.getStartCommand() == null ? "" : request.getStartCommand().strip());
        request.setServiceMode(StringUtils.hasText(request.getServiceMode()) ? request.getServiceMode().trim().toUpperCase() : "DIRECT");
        request.setNginxApiPrefix(normalizeApiPrefix(request.getNginxApiPrefix()));
        int order = 0;
        for (ManagedProjectRequest.ArtifactBindingRequest artifact : request.getArtifacts()) {
            artifact.setRepositoryId(trim(artifact.getRepositoryId()));
            artifact.setRepositoryName(trim(artifact.getRepositoryName()));
            artifact.setArtifactName(trim(artifact.getArtifactName()));
            artifact.setTargetDirectory(StringUtils.hasText(artifact.getTargetDirectory()) ? cleanRelative(artifact.getTargetDirectory()) : ".");
            if (artifact.getSortOrder() == null) artifact.setSortOrder(order);
            order++;
        }
    }

    private void validate(ManagedProjectRequest request, Long excludeId) {
        if (!StringUtils.hasText(request.getProjectName())) throw new IllegalArgumentException("项目名称必填");
        if (repository.existsProjectName(request.getProjectName(), excludeId)) throw new IllegalArgumentException("项目名称已存在");
        if (!StringUtils.hasText(request.getDatabaseName()) || !DB_NAME.matcher(request.getDatabaseName()).matches()) throw new IllegalArgumentException("database 名称只允许字母、数字和下划线");
        if (repository.databaseBound(request.getDatabaseName(), excludeId)) throw new IllegalArgumentException("database 已绑定其他项目");
        if (!StringUtils.hasText(request.getRuntimeImage())) throw new IllegalArgumentException("Runtime Image 必填");
        if (!StringUtils.hasText(request.getStartCommand())) throw new IllegalArgumentException("启动命令必填");
        if (!List.of("DIRECT", "NGINX").contains(request.getServiceMode())) throw new IllegalArgumentException("serviceMode 仅支持 DIRECT / NGINX");
        if (request.getServicePort() == null || request.getServicePort() < 1 || request.getServicePort() > 65535) throw new IllegalArgumentException("项目服务端口非法");
        if ("NGINX".equals(request.getServiceMode())) {
            if (!StringUtils.hasText(request.getNginxStaticDirectory())) throw new IllegalArgumentException("NGINX 模式需要静态目录");
            if (request.getNginxBackendPort() == null) throw new IllegalArgumentException("NGINX 模式需要后端端口");
        }
        for (ManagedProjectRequest.ArtifactBindingRequest artifact : request.getArtifacts()) {
            if (!StringUtils.hasText(artifact.getRepositoryId()) || !StringUtils.hasText(artifact.getArtifactName())) throw new IllegalArgumentException("每个制品都需要 repositoryId 和 artifactName");
        }
    }

    private void ensureDatabaseAvailable(String databaseName) {
        try {
            Integer count = new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForObject(
                    "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?", Integer.class, databaseName);
            if (count != null && count > 0) throw new IllegalArgumentException("database 已存在，请填写一个新的 database 名称");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("检查 database 失败", e);
        }
    }

    private void createDatabase(String databaseName) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + databaseName + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (Exception e) {
            throw new IllegalStateException("创建 database 失败: " + message(e), e);
        }
    }

    private void prepareProjectDirectories(Long projectId) {
        try {
            Files.createDirectories(projectRoot(projectId).resolve("current"));
            Files.createDirectories(projectRoot(projectId).resolve("config"));
        } catch (Exception e) {
            throw new IllegalStateException("创建项目目录失败", e);
        }
    }

    private Map<String, Object> view(ManagedProjectRepository.Project project) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("id", project.id());
        result.put("projectName", project.projectName());
        result.put("databaseName", project.databaseName());
        result.put("runtimeImage", project.runtimeImage());
        result.put("workDirectory", project.workDirectory());
        result.put("startCommand", project.startCommand());
        result.put("serviceMode", project.serviceMode());
        result.put("servicePort", project.servicePort());
        result.put("nginxStaticDirectory", project.nginxStaticDirectory());
        result.put("nginxApiPrefix", project.nginxApiPrefix());
        result.put("nginxBackendPort", project.nginxBackendPort());
        result.put("containerId", project.containerId());
        result.put("containerName", project.containerName());
        result.put("deployedRuntimeImage", project.deployedRuntimeImage());
        result.put("deployedVersionSummary", project.deployedVersionSummary());
        result.put("runningVersionSummary", project.runningVersionSummary());
        result.put("pendingRestart", !safe(project.deployedVersionSummary()).equals(safe(project.runningVersionSummary())));
        result.put("deploymentStatus", project.deploymentStatus());
        result.put("lastError", project.lastError());
        result.put("lastDeployTime", project.lastDeployTime());
        result.put("artifacts", repository.artifacts(project.id()));
        result.put("envContent", repository.environmentCiphertext(project.id()).map(crypto::decrypt).orElse(""));
        result.put("projectRoot", projectRoot(project.id()).toString());
        return result;
    }

    private ManagedProjectRepository.Project required(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("项目不存在: " + id));
    }

    private Path projectRoot(Long id) {
        Path root = Path.of(managed.getProjectRoot()).toAbsolutePath().normalize();
        Path project = root.resolve(String.valueOf(id)).normalize();
        if (!project.startsWith(root)) throw new IllegalStateException("项目目录非法");
        return project;
    }

    private Path resolveTarget(Path stagedCurrent, String relative) {
        Path target = ".".equals(relative) ? stagedCurrent : stagedCurrent.resolve(relative).normalize();
        if (!target.startsWith(stagedCurrent)) throw new IllegalArgumentException("制品解压目录不能离开 current");
        return target;
    }

    private String containerName(Long id) { return "fdp-project-" + id; }
    private void ensureLinuxExecution() {
        if (!runtime.isExecutionEnabled()) throw new IllegalStateException("FDP_EXECUTION_ENABLED=false");
        if (ShellCommandSupport.windows()) throw new IllegalStateException("Docker 正式部署仅在 Linux FDP 服务器执行");
    }
    private void run(String command, Path cwd) { CommandExecutor.Result result = exec.execute(command, cwd); check(result, command); }
    private void check(CommandExecutor.Result result, String action) { if (!result.success()) throw new IllegalStateException(action + " failed: " + result.output()); }
    private void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        }
    }

    private List<String> splitSql(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean single = false, dbl = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !dbl) single = !single;
            if (c == '"' && !single) dbl = !dbl;
            if (c == ';' && !single && !dbl) { result.add(current.toString().trim()); current.setLength(0); }
            else current.append(c);
        }
        if (!current.toString().isBlank()) result.add(current.toString().trim());
        return result;
    }

    private String encryptedEnvironment(String content) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n').strip();
        return StringUtils.hasText(normalized) ? crypto.encrypt(normalized) : null;
    }
    private String normalizeApiPrefix(String value) {
        String prefix = StringUtils.hasText(value) ? value.trim() : "/api/";
        if (!prefix.startsWith("/")) prefix = "/" + prefix;
        if (!prefix.endsWith("/")) prefix += "/";
        return prefix;
    }
    private String cleanRelative(String value) {
        String path = value == null ? "." : value.trim().replace('\\', '/');
        if (path.isEmpty()) return ".";
        if (path.startsWith("/") || path.contains("..") || !SAFE_RELATIVE.matcher(path).matches()) throw new IllegalArgumentException("相对路径非法: " + value);
        return path;
    }
    private String shQuote(String value) { String v = value == null ? "" : value; return "'" + v.replace("'", "'\"'\"'") + "'"; }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String safe(String value) { return value == null ? "" : value; }
    private String message(Throwable error) { String value = error == null ? null : error.getMessage(); return StringUtils.hasText(value) ? value : String.valueOf(error); }

    public record ArtifactSelection(Long artifactId, String version, String downloadUrl) {}
    public record DeployRequest(List<ArtifactSelection> artifacts) {}
}
