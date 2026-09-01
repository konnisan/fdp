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
    private final GitAuthenticationService gitAuth;
    private final TaskExecutor deploymentTaskExecutor;
    private final Set<Long> activeProjects = ConcurrentHashMap.newKeySet();

    public DeploymentService(RuntimeProperties props,
                             PocProjectRepository projects,
                             DeploymentRepository deployments,
                             CommandExecutor exec,
                             GitAuthenticationService gitAuth,
                             @Qualifier("deploymentTaskExecutor") TaskExecutor deploymentTaskExecutor) {
        this.props = props;
        this.projects = projects;
        this.deployments = deployments;
        this.exec = exec;
        this.gitAuth = gitAuth;
        this.deploymentTaskExecutor = deploymentTaskExecutor;
    }

    public long deploy(Long projectId) {
        project(projectId);
        if (!activeProjects.add(projectId)) throw new IllegalStateException("This project already has a deployment task running.");
        long taskId = deployments.createTask(projectId);
        try {
            deploymentTaskExecutor.execute(() -> executeDeployment(taskId, projectId));
            return taskId;
        } catch (RuntimeException e) {
            activeProjects.remove(projectId);
            deployments.finish(taskId, "FAILED");
            deployments.log(taskId, "Deployment task could not be queued: " + message(e));
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
                        ? "git fetch --prune origin && git checkout " + q(project.getGitBranch()) + " && git reset --hard origin/" + q(project.getGitBranch())
                        : "git clone --branch " + q(project.getGitBranch()) + " --single-branch " + q(project.getGitUrl()) + " .";
                runGit(taskId, project, gitCommand, workspace);
                String sha = props.isExecutionEnabled() ? run(taskId, "git rev-parse HEAD", workspace).trim() : "DRY-RUN";
                deployments.commit(taskId, sha);
                return sha;
            });

            Path projectRoot = projectRoot(project, workspace);
            if (props.isExecutionEnabled() && !Files.isDirectory(projectRoot)) {
                throw new IllegalStateException("Project directory does not exist: " + project.getProjectDirectory());
            }

            if ("STATIC".equals(project.getProjectType())) {
                deployStatic(taskId, project, projectRoot);
            } else if ("CONTAINER".equals(project.getProjectType())) {
                deployContainer(taskId, project, projectRoot, commit);
            } else {
                throw new IllegalStateException("Unsupported project type: " + project.getProjectType());
            }

            stage(taskId, "ROUTE", "更新 Nginx 路由", 50, () -> {
                regenerateNginx(taskId, project);
                return null;
            });

            stage(taskId, "VERIFY", "运行校验", 60, () -> {
                verifyRuntime(taskId, project, projectRoot);
                return null;
            });

            projects.updateDeployment(projectId, "STATIC".equals(project.getProjectType()) ? "PUBLISHED" : "RUNNING", commit);
            deployments.finish(taskId, "SUCCESS");
        } catch (Exception e) {
            deployments.finish(taskId, "FAILED");
            deployments.log(taskId, "Deployment failed: " + message(e));
        } finally {
            activeProjects.remove(projectId);
        }
    }

    private void deployStatic(long taskId, PocProject project, Path projectRoot) throws Exception {
        if (StringUtils.hasText(project.getBuildCommand())) {
            stage(taskId, "STATIC_BUILD", "构建静态资源", 30, () -> {
                run(taskId, project.getBuildCommand(), projectRoot);
                return null;
            });
        } else {
            deployments.skipStep(taskId, "STATIC_BUILD", "构建静态资源", 30);
            deployments.log(taskId, "[STATIC_BUILD] SKIPPED - no build command configured");
        }
        stage(taskId, "STATIC_PUBLISH", "发布静态资源", 40, () -> {
            publishStatic(taskId, project, projectRoot);
            return null;
        });
    }

    private void deployContainer(long taskId, PocProject project, Path projectRoot, String commit) throws Exception {
        String tag = imageTag(project, commit);
        stage(taskId, "DOCKER_BUILD", "构建 Docker 镜像", 30, () -> {
            Path dockerfile = resolveRelative(projectRoot, project.getDockerfilePath(), "dockerfilePath");
            Path context = resolveRelative(projectRoot, project.getDockerBuildContext(), "dockerBuildContext");
            if (props.isExecutionEnabled() && !Files.isRegularFile(dockerfile)) throw new IllegalStateException("Dockerfile does not exist: " + dockerfile);
            if (props.isExecutionEnabled() && !Files.isDirectory(context)) throw new IllegalStateException("Docker build context does not exist: " + context);
            run(taskId, "docker build -t " + q(tag) + " -f " + q(dockerfile) + " " + q(context), projectRoot);
            deployments.image(taskId, tag);
            return null;
        });

        stage(taskId, "CONTAINER_REPLACE", "替换 Docker 容器", 40, () -> {
            if (StringUtils.hasText(project.getHostDataPath()) && props.isExecutionEnabled()) {
                Files.createDirectories(Path.of(project.getHostDataPath()));
            }
            run(taskId, "docker rm -f " + q(project.getContainerName()) + " >/dev/null 2>&1 || true", projectRoot);
            StringBuilder command = new StringBuilder("docker run -d --name ").append(q(project.getContainerName()))
                    .append(" --restart unless-stopped")
                    .append(" --cpus ").append(q(project.getCpuLimit()))
                    .append(" --memory ").append(q(project.getMemoryLimit()))
                    .append(" -p ").append(q("127.0.0.1:" + project.getHostPort() + ":" + project.getContainerPort()));
            if (StringUtils.hasText(project.getHostDataPath())) {
                command.append(" -v ").append(q(project.getHostDataPath() + ":" + project.getContainerDataPath()));
            }
            command.append(" ").append(q(tag));
            run(taskId, command.toString(), projectRoot);
            return null;
        });
    }

    public void restart(Long id) {
        PocProject project = container(id);
        check(exec.execute("docker restart " + q(project.getContainerName()), projectRoot(project, workspace(project))));
        projects.updateStatus(id, "RUNNING");
        regenerateNginx(null, null);
    }

    public void stop(Long id) {
        PocProject project = container(id);
        check(exec.execute("docker stop " + q(project.getContainerName()), projectRoot(project, workspace(project))));
        projects.updateStatus(id, "STOPPED");
        regenerateNginx(null, null);
    }

    public void refreshRoutes() {
        regenerateNginx(null, null);
    }

    private void publishStatic(long taskId, PocProject project, Path projectRoot) throws Exception {
        Path source = resolveRelative(projectRoot, project.getBuildOutput(), "buildOutput");
        if (props.isExecutionEnabled() && !Files.isDirectory(source)) throw new IllegalStateException("Static build output does not exist: " + source);
        Path root = Path.of(props.getStaticRoot()).toAbsolutePath().normalize();
        Path destination = root.resolve(project.getPreviewPath().replaceFirst("^/", "")).normalize();
        if (!destination.startsWith(root)) throw new IllegalArgumentException("Invalid previewPath");
        Files.createDirectories(destination);
        run(taskId, "rsync -a --delete --exclude=.git --exclude=node_modules " + q(source + "/") + " " + q(destination + "/"), projectRoot);
    }

    private void verifyRuntime(long taskId, PocProject project, Path projectRoot) throws Exception {
        if (!props.isExecutionEnabled()) {
            deployments.log(taskId, "[VERIFY] DRY-RUN - runtime verification skipped");
            return;
        }
        if ("CONTAINER".equals(project.getProjectType())) {
            String running = run(taskId, "docker inspect -f '{{.State.Running}}' " + q(project.getContainerName()), projectRoot).trim();
            if (!"true".equalsIgnoreCase(running)) throw new IllegalStateException("Container is not running: " + project.getContainerName());
            if (StringUtils.hasText(project.getHealthCheckPath())) {
                String url = "http://127.0.0.1:" + project.getHostPort() + project.getHealthCheckPath();
                run(taskId, "curl -fsS --max-time 10 " + q(url), projectRoot);
            }
            return;
        }
        Path root = Path.of(props.getStaticRoot()).toAbsolutePath().normalize();
        Path destination = root.resolve(project.getPreviewPath().replaceFirst("^/", "")).normalize();
        if (!Files.isDirectory(destination)) throw new IllegalStateException("Static publish directory does not exist: " + destination);
        try (var files = Files.list(destination)) {
            if (files.findAny().isEmpty()) throw new IllegalStateException("Static publish directory is empty: " + destination);
        }
    }

    private synchronized void regenerateNginx(Long taskId, PocProject currentProject) {
        try {
            List<PocProject> routes = new ArrayList<>(projects.findPublished());
            if (currentProject != null && routes.stream().noneMatch(p -> p.getId().equals(currentProject.getId()))) routes.add(currentProject);
            StringBuilder config = new StringBuilder("# Generated by FDP. Do not edit manually.\nserver {\n    listen " + props.getNginxPublicPort() + ";\n    server_name _;\n\n");
            Path staticRoot = Path.of(props.getStaticRoot()).toAbsolutePath().normalize();
            Path activeStaticRoot = staticRoot.resolve("_active").normalize();

            if (Files.isRegularFile(activeStaticRoot.resolve("index.html"))) {
                config.append("    # Active STATIC catalog project\n")
                        .append("    location / {\n")
                        .append("        root ").append(activeStaticRoot.toString().replace("\\", "/")).append(";\n")
                        .append("        index index.html;\n")
                        .append("        try_files $uri $uri/ /index.html;\n")
                        .append("    }\n\n");
            }

            for (PocProject project : routes) {
                if ("STATIC".equals(project.getProjectType())) {
                    config.append("    location ^~ ").append(project.getPreviewPath()).append("/ {\n")
                            .append("        root ").append(staticRoot.toString().replace("\\", "/")).append(";\n")
                            .append("        try_files $uri $uri/ ").append(project.getPreviewPath()).append("/index.html;\n")
                            .append("    }\n\n");
                } else if ("CONTAINER".equals(project.getProjectType())) {
                    config.append("    location ^~ ").append(project.getPreviewPath()).append("/ {\n")
                            .append("        proxy_pass http://127.0.0.1:").append(project.getHostPort()).append("/;\n")
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

    private <T> T stage(long taskId,String code,String name,int sortOrder,StageAction<T> action) throws Exception {
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

    private Path projectRoot(PocProject project, Path workspace) {
        return resolveRelative(workspace, project.getProjectDirectory(), "projectDirectory");
    }

    private Path resolveRelative(Path root, String relative, String field) {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException(field + " escapes its allowed directory");
        return path;
    }

    private String imageTag(PocProject project, String commit) {
        String version = "DRY-RUN".equals(commit) ? "dry-run" : commit.substring(0, Math.min(12, commit.length()));
        return project.getImageName() + ":" + version;
    }

    private PocProject project(Long id) {return projects.findById(id).orElseThrow(() -> new IllegalArgumentException("Delivery project not found: " + id));}
    private PocProject container(Long id) {PocProject p=project(id);if(!"CONTAINER".equals(p.getProjectType()))throw new IllegalArgumentException("Only CONTAINER projects have a runtime process");return p;}
    private String run(long taskId,String command,Path cwd){CommandExecutor.Result result=exec.execute(command,cwd,Map.of());deployments.log(taskId,"$ "+command+"\n"+result.output());check(result);return result.output();}
    private String runGit(long taskId,PocProject project,String command,Path cwd){CommandExecutor.Result result=gitAuth.execute(project.getCredentialId(),command,cwd);deployments.log(taskId,"$ "+command+"\n"+result.output());check(result);return result.output();}
    private void check(CommandExecutor.Result result){if(!result.success())throw new IllegalStateException("Command failed ("+result.exitCode()+"): "+result.output());}
    private String q(Object value){String text=String.valueOf(value);if(!text.matches("^[A-Za-z0-9_./:@\\\\ -]+$"))throw new IllegalArgumentException("Unsafe command argument: "+text);return "'"+text.replace("'","'\\''")+"'";}
    private String message(Throwable error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}

    @FunctionalInterface
    private interface StageAction<T>{T run() throws Exception;}
}
