package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.config.StaticSourceProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StaticCatalogService {
    private static final String ACTIVE_DIRECTORY = "_active";
    private static final String ACTIVE_MARKER = "static-active-project.txt";

    private final RuntimeProperties runtime;
    private final StaticSourceProperties source;
    private final GitAuthenticationService gitAuth;
    private final CommandExecutor exec;
    private final DeploymentService deploymentService;

    public StaticCatalogService(RuntimeProperties runtime,
                                StaticSourceProperties source,
                                GitAuthenticationService gitAuth,
                                CommandExecutor exec,
                                DeploymentService deploymentService) {
        this.runtime = runtime;
        this.source = source;
        this.gitAuth = gitAuth;
        this.exec = exec;
        this.deploymentService = deploymentService;
    }

    public Map<String, Object> list() {
        return snapshot(false, null);
    }

    public synchronized Map<String, Object> refresh() {
        validateConfiguration();
        if (!runtime.isExecutionEnabled()) {
            return snapshot(false, "FDP_EXECUTION_ENABLED=false，当前仅返回本地缓存，未执行 Codeup 同步");
        }
        Path workspace = workspace();
        try {
            Files.createDirectories(workspace);
            String command = Files.isDirectory(workspace.resolve(".git"))
                    ? "git remote set-url origin " + q(source.getGitUrl())
                    + " && git fetch --prune origin"
                    + " && git checkout -B " + q(source.getBranch()) + " origin/" + q(source.getBranch())
                    + " && git reset --hard origin/" + q(source.getBranch())
                    : "git clone --branch " + q(source.getBranch())
                    + " --single-branch " + q(source.getGitUrl()) + " .";

            CommandExecutor.Result result = gitAuth.execute(
                    source.getUsername(),
                    source.getToken(),
                    command,
                    workspace
            );
            check(result, "Codeup 静态仓库同步失败");
            return snapshot(true, null);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Codeup 静态仓库同步失败：" + message(e), e);
        }
    }

    public synchronized Map<String, Object> activate(String projectName) {
        validateConfiguration();
        if (projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("请选择需要对外展示的静态项目");
        }
        if (!runtime.isExecutionEnabled()) {
            throw new IllegalStateException("FDP_EXECUTION_ENABLED=false，无法切换对外静态项目");
        }

        Path root = sourceRoot();
        Path selected = root.resolve(projectName).normalize();
        if (!selected.startsWith(root) || !selected.getParent().equals(root)) {
            throw new IllegalArgumentException("非法静态项目目录");
        }
        if (!Files.isRegularFile(selected.resolve("index.html"))) {
            throw new IllegalArgumentException("静态项目不存在或缺少 index.html：" + projectName);
        }

        try {
            Path staticRoot = Path.of(runtime.getStaticRoot()).toAbsolutePath().normalize();
            Path destination = staticRoot.resolve(ACTIVE_DIRECTORY).normalize();
            if (!destination.startsWith(staticRoot)) {
                throw new IllegalStateException("静态发布目录非法");
            }
            Files.createDirectories(destination);

            CommandExecutor.Result result = exec.execute(
                    "rsync -a --delete --exclude=.git --exclude=node_modules "
                            + q(selected + "/") + " " + q(destination + "/"),
                    selected
            );
            check(result, "静态项目发布失败");

            Path marker = activeMarker();
            Files.createDirectories(marker.getParent());
            Files.writeString(
                    marker,
                    projectName,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            deploymentService.refreshRoutes();
            Map<String, Object> response = snapshot(false, null);
            response.put("status", "ACTIVE");
            response.put("activeProject", projectName);
            response.put("publicPath", "/");
            response.put("publicPort", runtime.getNginxPublicPort());
            return response;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("切换静态项目失败：" + message(e), e);
        }
    }

    private Map<String, Object> snapshot(boolean refreshed, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", source.isEnabled());
        result.put("configured", configured());
        result.put("gitUrl", source.getGitUrl());
        result.put("branch", source.getBranch());
        result.put("executionEnabled", runtime.isExecutionEnabled());
        result.put("publicPort", runtime.getNginxPublicPort());
        result.put("activeProject", activeProject());
        result.put("refreshed", refreshed);
        result.put("refreshedAt", refreshed ? Instant.now().toString() : null);
        result.put("message", message);
        result.put("projects", scanProjects());
        return result;
    }

    private List<Map<String, Object>> scanProjects() {
        Path root = sourceRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        String active = activeProject();
        List<Map<String, Object>> projects = new ArrayList<>();
        try (var children = Files.list(root)) {
            children
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> Files.isRegularFile(path.resolve("index.html")))
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER
                    ))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", name);
                        item.put("directory", name);
                        item.put("indexPath", name + "/index.html");
                        item.put("active", name.equals(active));
                        try {
                            item.put("updatedAt", Files.getLastModifiedTime(path.resolve("index.html")).toInstant().toString());
                        } catch (Exception ignored) {
                            item.put("updatedAt", null);
                        }
                        projects.add(item);
                    });
            return projects;
        } catch (Exception e) {
            throw new IllegalStateException("扫描静态项目失败：" + message(e), e);
        }
    }

    private Path workspace() {
        Path root = Path.of(runtime.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path workspace = root.resolve(source.getWorkspace()).normalize();
        if (!workspace.startsWith(root)) {
            throw new IllegalArgumentException("FDP_STATIC_CODEUP_WORKSPACE 非法");
        }
        return workspace;
    }

    private Path sourceRoot() {
        Path workspace = workspace();
        String relative = source.getRootDirectory() == null || source.getRootDirectory().isBlank()
                ? "."
                : source.getRootDirectory();
        Path root = workspace.resolve(relative).normalize();
        if (!root.startsWith(workspace)) {
            throw new IllegalArgumentException("FDP_STATIC_CODEUP_ROOT_DIR 不能离开静态仓库目录");
        }
        return root;
    }

    private Path activeMarker() {
        Path dataRoot = Path.of(runtime.getDataRoot()).toAbsolutePath().normalize();
        return dataRoot.resolve(ACTIVE_MARKER).normalize();
    }

    private String activeProject() {
        try {
            Path marker = activeMarker();
            return Files.isRegularFile(marker)
                    ? Files.readString(marker, StandardCharsets.UTF_8).trim()
                    : "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean configured() {
        return source.isEnabled()
                && source.getGitUrl() != null && !source.getGitUrl().isBlank()
                && source.getUsername() != null && !source.getUsername().isBlank()
                && source.getToken() != null && !source.getToken().isBlank();
    }

    private void validateConfiguration() {
        if (!source.isEnabled()) {
            throw new IllegalStateException("静态 Codeup 自动检索未启用，请设置 FDP_STATIC_CODEUP_ENABLED=true");
        }
        if (!configured()) {
            throw new IllegalStateException("请配置 FDP_STATIC_CODEUP_GIT_URL / USERNAME / TOKEN");
        }
        if (!source.getGitUrl().startsWith("https://")) {
            throw new IllegalArgumentException("静态 Codeup 仓库目前只接受 HTTPS Git URL");
        }
        try {
            URI uri = URI.create(source.getGitUrl());
            if (uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Git URL 中不要嵌入用户名或 Token");
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("不要嵌入")) {
                throw e;
            }
            throw new IllegalArgumentException("FDP_STATIC_CODEUP_GIT_URL 格式非法");
        }
        if (source.getBranch() == null
                || !source.getBranch().matches("[A-Za-z0-9._/-]+")
                || source.getBranch().contains("..")
                || source.getBranch().startsWith("-")) {
            throw new IllegalArgumentException("FDP_STATIC_CODEUP_BRANCH 格式非法");
        }
    }

    private String q(Object value) {
        return ShellCommandSupport.quote(String.valueOf(value));
    }

    private void check(CommandExecutor.Result result, String message) {
        if (!result.success()) {
            throw new IllegalStateException(message + "（exit=" + result.exitCode() + "）：" + result.output());
        }
    }

    private String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
