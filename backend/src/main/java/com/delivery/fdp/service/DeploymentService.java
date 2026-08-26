package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.model.PocProject;
import com.delivery.fdp.repository.DeploymentRepository;
import com.delivery.fdp.repository.PocProjectRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeploymentService {
    private final RuntimeProperties props;
    private final PocProjectRepository projects;
    private final DeploymentRepository deployments;
    private final CommandExecutor exec;
    private final TaskExecutor deploymentTaskExecutor;
    private final Set<Long> activeProjects = ConcurrentHashMap.newKeySet();

    public DeploymentService(RuntimeProperties props,
                             PocProjectRepository projects,
                             DeploymentRepository deployments,
                             CommandExecutor exec,
                             @Qualifier("deploymentTaskExecutor") TaskExecutor deploymentTaskExecutor) {
        this.props = props;
        this.projects = projects;
        this.deployments = deployments;
        this.exec = exec;
        this.deploymentTaskExecutor = deploymentTaskExecutor;
    }

    public long deploy(Long projectId) {
        project(projectId);
        if (!activeProjects.add(projectId)) {
            throw new IllegalStateException("This POC already has a deployment task running.");
        }
        long taskId = deployments.createTask(projectId);
        try {
            deploymentTaskExecutor.execute(() -> executeDeployment(taskId, projectId));
            return taskId;
        } catch (RuntimeException e) {
            activeProjects.remove(projectId);
            deployments.finish(taskId, "FAILED");
            deployments.log(taskId, "Deployment task could not be queued: " + e.getMessage());
            throw e;
        }
    }

    private void executeDeployment(long taskId, Long projectId) {
        try {
            deployments.markRunning(taskId);
            PocProject project = project(projectId);

            Path workspace = stage(taskId, "PREPARE", "准备工作目录", 10, () -> {
                Path ws = workspace(project);
                Files.createDirectories(ws);
                return ws;
            });

            String commit = stage(taskId, "GIT_SYNC", "同步 Codeup 源码", 20, () -> {
                String gitCommand = Files.exists(workspace.resolve(".git"))
                        ? "git fetch --prune origin && git checkout " + q(project.getGitBranch())
                          + " && git reset --hard origin/" + q(project.getGitBranch())
                        : "git clone --branch " + q(project.getGitBranch())
                          + " --single-branch " + q(project.getGitUrl()) + " .";
                run(taskId, gitCommand, workspace, Map.of());
                String sha = props.isExecutionEnabled()
                        ? run(taskId, "git rev-parse HEAD", workspace, Map.of()).trim()
                        : "DRY-RUN";
                deployments.commit(taskId, sha);
                return sha;
            });

            if (StringUtils.hasText(project.getBuildCommand())) {
                stage(taskId, "BUILD", "执行构建", 30, () -> {
                    run(taskId, project.getBuildCommand(), workspace, Map.of());
                    return null;
                });
            } else {
                deployments.skipStep(taskId, "BUILD", "执行构建", 30);
                deployments.log(taskId, "[BUILD] SKIPPED - no build command configured");
            }

            if ("STATIC".equals(project.getProjectType())) {
                stage(taskId, "PUBLISH_STATIC", "发布静态资源", 40, () -> {
                    publishStatic(taskId, project, workspace);
                    return null;
                });
            } else {
                stage(taskId, "START_NODE", "启动 Node.js", 40, () -> {
                    startNode(taskId, project, workspace);
                    return null;
                });
            }

            stage(taskId, "ROUTE", "更新 Nginx 路由", 50, () -> {
                regenerateNginx(taskId, project);
                return null;
            });

            stage(taskId, "VERIFY", "运行校验", 60, () -> {
                verifyRuntime(taskId, project);
                return null;
            });

            projects.updateDeployment(projectId,
                    "STATIC".equals(project.getProjectType()) ? "PUBLISHED" : "RUNNING",
                    commit);
            deployments.finish(taskId, "SUCCESS");
        } catch (Exception e) {
            deployments.finish(taskId, "FAILED");
            deployments.log(taskId, "Deployment failed: " + message(e));
        } finally {
            activeProjects.remove(projectId);
        }
    }

    public void restart(Long id) {
        PocProject project = node(id);
        check(runRaw("pm2 restart " + q(project.getPm2Name()), workspace(project)));
        projects.updateStatus(id, "RUNNING");
        regenerateNginx(null, null);
    }

    public void stop(Long id) {
        PocProject project = node(id);
        check(runRaw("pm2 stop " + q(project.getPm2Name()), workspace(project)));
        projects.updateStatus(id, "STOPPED");
        regenerateNginx(null, null);
    }

    private void publishStatic(long taskId, PocProject project, Path workspace) throws Exception {
        Path source = workspace.resolve(project.getBuildOutput()).normalize();
        if (!source.startsWith(workspace)) throw new IllegalArgumentException("Invalid buildOutput");
        Path root = Path.of(props.getStaticRoot()).toAbsolutePath().normalize();
        Path destination = root.resolve(project.getPreviewPath().replaceFirst("^/", "")).normalize();
        if (!destination.startsWith(root)) throw new IllegalArgumentException("Invalid previewPath");
        Files.createDirectories(destination);
        run(taskId,
                "rsync -a --delete --exclude=.git --exclude=node_modules " + q(source + "/") + " " + q(destination + "/"),
                workspace,
                Map.of());
    }

    private void startNode(long taskId, PocProject project, Path workspace) throws Exception {
        Path root = Path.of(props.getDataRoot()).toAbsolutePath().normalize();
        Path dataDir = root.resolve(project.getProjectCode()).normalize();
        if (!dataDir.startsWith(root)) throw new IllegalArgumentException("Invalid data path");
        Files.createDirectories(dataDir);
        Path database = dataDir.resolve(project.getSqlitePath()).normalize();
        if (!database.startsWith(dataDir)) throw new IllegalArgumentException("Invalid sqlitePath");

        Map<String, String> env = new HashMap<>();
        env.put("PORT", String.valueOf(project.getInternalPort()));
        env.put("SQLITE_PATH", database.toString());

        run(taskId,
                "pm2 delete " + q(project.getPm2Name()) + " >/dev/null 2>&1 || true; "
                        + "pm2 start bash --name " + q(project.getPm2Name()) + " -- -lc " + q(project.getStartCommand()),
                workspace,
                env);
        run(taskId, "pm2 save", workspace, env);
    }

    private void verifyRuntime(long taskId, PocProject project) throws Exception {
        if (!props.isExecutionEnabled()) {
            deployments.log(taskId, "[VERIFY] DRY-RUN - runtime verification skipped");
            return;
        }
        if ("NODE_SQLITE".equals(project.getProjectType())) {
            run(taskId, "pm2 describe " + q(project.getPm2Name()), workspace(project), Map.of());
            return;
        }
        Path root = Path.of(props.getStaticRoot()).toAbsolutePath().normalize();
        Path destination = root.resolve(project.getPreviewPath().replaceFirst("^/", "")).normalize();
        if (!Files.isDirectory(destination)) {
            throw new IllegalStateException("Static publish directory does not exist: " + destination);
        }
        try (var files = Files.list(destination)) {
            if (files.findAny().isEmpty()) {
                throw new IllegalStateException("Static publish directory is empty: " + destination);
            }
        }
    }

    private synchronized void regenerateNginx(Long taskId, PocProject currentProject) {
        try {
            List<PocProject> routes = new ArrayList<>(projects.findPublished());
            if (currentProject != null && routes.stream().noneMatch(p -> p.getId().equals(currentProject.getId()))) {
                routes.add(currentProject);
            }

            StringBuilder config = new StringBuilder(
                    "# Generated by FDP. Do not edit manually.\nserver {\n    listen "
                            + props.getNginxPublicPort() + ";\n    server_name _;\n\n");
            Path staticRoot = Path.of(props.getStaticRoot()).toAbsolutePath().normalize();
            for (PocProject project : routes) {
                if ("STATIC".equals(project.getProjectType())) {
                    config.append("    location ^~ ").append(project.getPreviewPath()).append("/ {\n")
                            .append("        root ").append(staticRoot.toString().replace("\\", "/")).append(";\n")
                            .append("        try_files $uri $uri/ ").append(project.getPreviewPath()).append("/index.html;\n")
                            .append("    }\n\n");
                } else {
                    config.append("    location ^~ ").append(project.getPreviewPath()).append("/ {\n")
                            .append("        proxy_pass http://127.0.0.1:").append(project.getInternalPort()).append("/;\n")
                            .append("        proxy_http_version 1.1;\n")
                            .append("        proxy_set_header Host $host;\n")
                            .append("        proxy_set_header X-Real-IP $remote_addr;\n")
                            .append("        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n")
                            .append("    }\n\n");
                }
            }
            config.append("}\n");

            Path file = Path.of(props.getNginxConfigFile()).toAbsolutePath().normalize();
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, config.toString());
            CommandExecutor.Result result = exec.execute(props.getNginxReloadCommand(), file.getParent());
            if (taskId != null) deployments.log(taskId, result.output());
            check(result);
        } catch (Exception e) {
            throw new IllegalStateException("Nginx configuration failed: " + message(e), e);
        }
    }

    private <T> T stage(long taskId,
                        String code,
                        String name,
                        int sortOrder,
                        StageAction<T> action) throws Exception {
        deployments.step(taskId, code);
        long stepId = deployments.startStep(taskId, code, name, sortOrder);
        deployments.log(taskId, "[" + code + "] " + name + " - RUNNING");
        try {
            T result = action.run();
            deployments.finishStep(stepId, "SUCCESS", null);
            deployments.log(taskId, "[" + code + "] " + name + " - SUCCESS");
            return result;
        } catch (Exception e) {
            deployments.finishStep(stepId, "FAILED", message(e));
            deployments.log(taskId, "[" + code + "] " + name + " - FAILED: " + message(e));
            throw e;
        }
    }

    private Path workspace(PocProject project) {
        Path root = Path.of(props.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path workspace = root.resolve(project.getProjectCode()).normalize();
        if (!workspace.startsWith(root)) throw new IllegalArgumentException("Invalid workspace");
        return workspace;
    }

    private PocProject project(Long id) {
        return projects.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("POC project not found: " + id));
    }

    private PocProject node(Long id) {
        PocProject project = project(id);
        if (!"NODE_SQLITE".equals(project.getProjectType())) {
            throw new IllegalArgumentException("Only NODE_SQLITE has a process");
        }
        return project;
    }

    private String run(long taskId, String command, Path cwd, Map<String, String> env) {
        CommandExecutor.Result result = exec.execute(command, cwd, env);
        deployments.log(taskId, "$ " + command + "\n" + result.output());
        check(result);
        return result.output();
    }

    private CommandExecutor.Result runRaw(String command, Path cwd) {
        return exec.execute(command, cwd);
    }

    private void check(CommandExecutor.Result result) {
        if (!result.success()) {
            throw new IllegalStateException("Command failed (" + result.exitCode() + "): " + result.output());
        }
    }

    private String q(Object value) {
        String text = String.valueOf(value);
        if (!text.matches("^[A-Za-z0-9_./:@\\\\ -]+$")) {
            throw new IllegalArgumentException("Unsafe command argument");
        }
        return "'" + text.replace("'", "'\\''") + "'";
    }

    private String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    @FunctionalInterface
    private interface StageAction<T> {
        T run() throws Exception;
    }
}
