package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.model.PocProject;
import com.delivery.fdp.repository.PocProjectRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StaticSourceService {
    private final RuntimeProperties props;
    private final PocProjectRepository projects;
    private final CommandExecutor exec;
    private final GitAuthenticationService gitAuth;
    private final Set<Long> syncingProjects = ConcurrentHashMap.newKeySet();

    public StaticSourceService(RuntimeProperties props,
                               PocProjectRepository projects,
                               CommandExecutor exec,
                               GitAuthenticationService gitAuth) {
        this.props = props;
        this.projects = projects;
        this.exec = exec;
        this.gitAuth = gitAuth;
    }

    public Map<String, Object> sync(Long projectId) {
        PocProject project = staticProject(projectId);
        if (!syncingProjects.add(projectId)) {
            throw new IllegalStateException("This static project already has a source sync running.");
        }
        try {
            Path workspace = workspace(project);
            Files.createDirectories(workspace);

            String command = Files.isDirectory(workspace.resolve(".git"))
                    ? "git fetch --prune origin && git checkout " + q(project.getGitBranch())
                    + " && git reset --hard origin/" + q(project.getGitBranch())
                    : "git clone --branch " + q(project.getGitBranch())
                    + " --single-branch " + q(project.getGitUrl()) + " .";

            CommandExecutor.Result syncResult = gitAuth.execute(
                    project.getCredentialId(),
                    command,
                    workspace
            );
            check(syncResult);

            String commit = "DRY-RUN";
            if (props.isExecutionEnabled()) {
                CommandExecutor.Result commitResult = exec.execute(
                        "git rev-parse HEAD",
                        workspace
                );
                check(commitResult);
                commit = commitResult.output().trim();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", props.isExecutionEnabled() ? "SYNCED" : "DRY_RUN");
            result.put("projectId", project.getId());
            result.put("projectCode", project.getProjectCode());
            result.put("branch", project.getGitBranch());
            result.put("commit", commit);
            result.put("workspace", workspace.toString());
            result.put("entries", entries(projectId));
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Static source sync failed: " + message(e), e);
        } finally {
            syncingProjects.remove(projectId);
        }
    }

    public List<Map<String, Object>> entries(Long projectId) {
        PocProject project = staticProject(projectId);
        Path workspace = workspace(project);
        Path projectRoot = resolveRelative(workspace, project.getProjectDirectory(), "projectDirectory");
        Path source = resolveRelative(projectRoot, project.getBuildOutput(), "buildOutput");
        if (!props.isExecutionEnabled() || !Files.isDirectory(source)) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        try {
            if (Files.isRegularFile(source.resolve("index.html"))) {
                result.add(entry(project, source, source, "项目根页面"));
            }
            try (var children = Files.list(source)) {
                children
                        .filter(Files::isDirectory)
                        .filter(path -> Files.isRegularFile(path.resolve("index.html")))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .forEach(path -> result.add(
                                entry(project, source, path, path.getFileName().toString())
                        ));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect static source: " + message(e), e);
        }
    }

    private Map<String, Object> entry(PocProject project,
                                      Path sourceRoot,
                                      Path entryDirectory,
                                      String name) {
        String relative = sourceRoot.relativize(entryDirectory)
                .toString()
                .replace('\\', '/');
        String base = project.getPreviewPath();
        String previewPath = relative.isBlank()
                ? base + "/"
                : base + "/" + relative + "/";

        Path publishedRoot = Path.of(props.getStaticRoot())
                .toAbsolutePath()
                .normalize()
                .resolve(base.replaceFirst("^/", ""))
                .normalize();
        Path publishedIndex = relative.isBlank()
                ? publishedRoot.resolve("index.html")
                : publishedRoot.resolve(relative).resolve("index.html");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("relativePath", relative);
        item.put("previewPath", previewPath);
        item.put("published", Files.isRegularFile(publishedIndex));
        return item;
    }

    private PocProject staticProject(Long id) {
        PocProject project = projects.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Delivery project not found: " + id));
        if (!"STATIC".equals(project.getProjectType())) {
            throw new IllegalArgumentException("Only STATIC projects support direct source sync.");
        }
        return project;
    }

    private Path workspace(PocProject project) {
        Path root = Path.of(props.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path workspace = root.resolve(project.getProjectCode()).normalize();
        if (!workspace.startsWith(root)) {
            throw new IllegalArgumentException("Invalid workspace");
        }
        return workspace;
    }

    private Path resolveRelative(Path root, String relative, String field) {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException(field + " escapes its allowed directory");
        }
        return path;
    }

    private void check(CommandExecutor.Result result) {
        if (!result.success()) {
            throw new IllegalStateException(
                    "Command failed (" + result.exitCode() + "): " + result.output()
            );
        }
    }

    private String q(Object value) {
        return ShellCommandSupport.quote(String.valueOf(value));
    }

    private String message(Throwable error) {
        return error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }
}
