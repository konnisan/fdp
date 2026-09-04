package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.repository.ArtifactDeliveryRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArtifactRuntimeService {
    private final RuntimeProperties runtime;
    private final ArtifactDeliveryRepository projects;
    private final CommandExecutor exec;

    public ArtifactRuntimeService(RuntimeProperties runtime,
                                  ArtifactDeliveryRepository projects,
                                  CommandExecutor exec) {
        this.runtime = runtime;
        this.projects = projects;
        this.exec = exec;
    }

    public Map<String, Object> status(Long projectId) {
        ArtifactDeliveryRepository.Project project = project(projectId);
        LinkedHashMap<String, Object> result = base(project);
        if (dryRun()) {
            result.put("containerStatus", project.status());
            result.put("runtimeMode", "DRY_RUN");
            result.put("message", "Windows / execution-disabled mode: container commands are previewed but not executed.");
            return result;
        }
        String command = "docker inspect -f '{{.State.Status}}|{{.Config.Image}}' " + ShellCommandSupport.quote(project.containerName());
        CommandExecutor.Result inspected = exec.execute(command, cwd());
        if (!inspected.success()) {
            result.put("containerStatus", "NOT_FOUND");
            result.put("runtimeMode", "LIVE");
            result.put("message", inspected.output());
            return result;
        }
        String[] parts = inspected.output().trim().split("\\|", 2);
        result.put("containerStatus", parts.length > 0 ? parts[0] : "UNKNOWN");
        result.put("runtimeImage", parts.length > 1 ? parts[1] : project.currentImage());
        result.put("runtimeMode", "LIVE");
        return result;
    }

    public Map<String, Object> restart(Long projectId) {
        return action(projectId, "restart", "RUNNING");
    }

    public Map<String, Object> stop(Long projectId) {
        return action(projectId, "stop", "STOPPED");
    }

    public Map<String, Object> remove(Long projectId) {
        return action(projectId, "rm -f", "STOPPED");
    }

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
                Map.of("code", "REPLACE_CONTAINER", "name", "替换项目后端 Container"),
                Map.of("code", "HEALTH_CHECK", "name", "执行健康检查"),
                Map.of("code", "REFRESH_ROUTE", "name", "刷新客户预览路由")
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
        result.put("previewPath", project.previewPath());
        result.put("image", project.currentImage());
        result.put("version", project.currentVersion());
        result.put("status", project.status());
        result.put("envFile", project.envFile());
        return result;
    }

    private boolean dryRun() {
        return !runtime.isExecutionEnabled() || ShellCommandSupport.windows();
    }

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
