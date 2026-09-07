package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.repository.ArtifactDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArtifactRuntimeService {
    private final RuntimeProperties runtime;
    private final ArtifactDeliveryRepository projects;
    private final ManagedEnvironmentService environment;
    private final CommandExecutor exec;

    public ArtifactRuntimeService(RuntimeProperties runtime,
                                  ArtifactDeliveryRepository projects,
                                  ManagedEnvironmentService environment,
                                  CommandExecutor exec) {
        this.runtime = runtime;
        this.projects = projects;
        this.environment = environment;
        this.exec = exec;
    }

    public Map<String, Object> status(Long projectId) {
        ArtifactDeliveryRepository.Project project = project(projectId);
        LinkedHashMap<String, Object> result = base(project);
        if (dryRun()) {
            result.put("containerStatus", project.currentVersion() == null ? "NOT_DEPLOYED" : project.status());
            result.put("runtimeMode", "DRY_RUN");
            result.put("message", "Windows / execution-disabled mode: container commands are previewed but not executed.");
            return result;
        }
        String command = "docker inspect -f '{{.State.Status}}|{{.Config.Image}}' " + ShellCommandSupport.quote(project.containerName());
        CommandExecutor.Result inspected = exec.execute(command, cwd());
        if (!inspected.success()) {
            boolean neverDeployed = !StringUtils.hasText(project.currentVersion()) && !StringUtils.hasText(project.currentImage());
            result.put("containerStatus", neverDeployed ? "NOT_DEPLOYED" : "NOT_FOUND");
            result.put("runtimeMode", "LIVE");
            result.put("message", neverDeployed
                    ? "该配置尚未部署任何版本。请到“来源与版本”选择制品版本并部署。"
                    : "记录中存在已部署版本，但服务器上未找到对应 Container。可重新部署该版本恢复。\n" + inspected.output());
            return result;
        }
        String[] parts = inspected.output().trim().split("\\|", 2);
        result.put("containerStatus", parts.length > 0 ? parts[0] : "UNKNOWN");
        result.put("runtimeImage", parts.length > 1 ? parts[1] : project.currentImage());
        result.put("runtimeMode", "LIVE");
        return result;
    }

    public Map<String, Object> restart(Long projectId) { return action(projectId, "restart", "RUNNING"); }
    public Map<String, Object> stop(Long projectId) { return action(projectId, "stop", "STOPPED"); }
    public Map<String, Object> remove(Long projectId) { return action(projectId, "rm -f", "STOPPED"); }

    public Map<String, Object> logs(Long projectId) {
        ArtifactDeliveryRepository.Project project = project(projectId);
        String command = "docker logs --tail 300 " + ShellCommandSupport.quote(project.containerName());
        CommandExecutor.Result result = dryRun()
                ? new CommandExecutor.Result(0, "[DRY-RUN] " + command)
                : exec.execute(command, cwd());
        if (!result.success()) throw new IllegalStateException("docker logs failed: " + result.output());
        return Map.of(
                "projectId", projectId,
                "containerName", project.containerName(),
                "runtimeMode", dryRun() ? "DRY_RUN" : "LIVE",
                "content", result.output()
        );
    }

    public Map<String, Object> deploymentPlan(Long projectId) {
        ArtifactDeliveryRepository.Project project = project(projectId);
        LinkedHashMap<String, Object> result = base(project);
        result.put("runtimeMode", dryRun() ? "DRY_RUN" : "LIVE");
        result.put("steps", List.of(
                Map.of("code", "FETCH_ARTIFACT", "name", "从 Packages 下载交付制品"),
                Map.of("code", "VERIFY", "name", "校验制品与版本"),
                Map.of("code", "PUBLISH_FRONTEND", "name", "发布前端静态资源到 Nginx"),
                Map.of("code", "LOAD_IMAGE", "name", "加载项目专属后端 Docker Image"),
                Map.of("code", "MATERIALIZE_ENV", "name", "生成 FDP 托管的 Docker 环境变量文件"),
                Map.of("code", "REPLACE_CONTAINER", "name", "按 FDP Docker 配置替换 Container"),
                Map.of("code", "HEALTH_CHECK", "name", "执行配置的健康检查"),
                Map.of("code", "REFRESH_ROUTE", "name", "刷新 8090 客户预览路由")
        ));
        return result;
    }

    private Map<String, Object> action(Long projectId, String dockerAction, String targetStatus) {
        ArtifactDeliveryRepository.Project project = project(projectId);
        String command = "docker " + dockerAction + " " + ShellCommandSupport.quote(project.containerName());
        boolean dryRun = dryRun();
        CommandExecutor.Result result = dryRun
                ? new CommandExecutor.Result(0, "[DRY-RUN] " + command)
                : exec.execute(command, cwd());
        if (!result.success()) throw new IllegalStateException("docker " + dockerAction + " failed: " + result.output());
        if (!dryRun) projects.updateStatus(projectId, targetStatus);
        return Map.of(
                "projectId", projectId,
                "containerName", project.containerName(),
                "runtimeMode", dryRun ? "DRY_RUN" : "LIVE",
                "action", dockerAction,
                "status", dryRun ? project.status() : targetStatus,
                "output", result.output()
        );
    }

    private LinkedHashMap<String, Object> base(ArtifactDeliveryRepository.Project project) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", project.id());
        result.put("projectCode", project.projectCode());
        result.put("containerName", project.containerName());
        result.put("hostPort", project.hostPort());
        result.put("containerPort", project.containerPort());
        result.put("previewPath", project.previewPath());
        result.put("image", project.currentImage());
        result.put("version", project.currentVersion());
        result.put("status", project.status());
        result.put("environmentManaged", environment.configured(project.id()));
        result.put("environmentVariableCount", environment.variableCount(project.id()));
        result.put("managedEnvPath", Path.of(runtime.getEnvRoot()).toAbsolutePath().normalize()
                .resolve(project.projectCode().replaceAll("[^A-Za-z0-9._-]", "_") + ".env").toString());
        result.put("legacyEnvFile", project.envFile());
        result.put("legacyEnvFileReady", envFileReady(project.envFile()));
        result.put("cpuLimit", project.cpuLimit());
        result.put("memoryLimit", project.memoryLimit());
        result.put("hostDataPath", project.hostDataPath());
        result.put("containerDataPath", project.containerDataPath());
        result.put("healthCheckPath", project.healthCheckPath());
        return result;
    }

    private Boolean envFileReady(String envFile) {
        if (!StringUtils.hasText(envFile)) return null;
        try { return Files.isRegularFile(Path.of(envFile).toAbsolutePath().normalize()); }
        catch (Exception ignored) { return false; }
    }

    private boolean dryRun() { return !runtime.isExecutionEnabled() || ShellCommandSupport.windows(); }

    private Path cwd() {
        try {
            Path path = Path.of(runtime.getArtifactRoot()).toAbsolutePath().normalize();
            Files.createDirectories(path);
            return path;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot prepare artifact runtime directory", e);
        }
    }

    private ArtifactDeliveryRepository.Project project(Long id) {
        return projects.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("工程交付项目不存在: " + id));
    }
}
