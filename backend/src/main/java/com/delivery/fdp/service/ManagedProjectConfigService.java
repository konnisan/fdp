package com.delivery.fdp.service;

import com.delivery.fdp.config.ManagedRuntimeProperties;
import com.delivery.fdp.dto.ManagedProjectRequest;
import com.delivery.fdp.repository.ManagedProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ManagedProjectConfigService {
    private static final String UNCONFIGURED_START_COMMAND = "__FDP_START_COMMAND_NOT_CONFIGURED__";
    private static final Pattern SAFE_RELATIVE = Pattern.compile("^[A-Za-z0-9._/-]*$");

    private final ManagedProjectRepository repository;
    private final ManagedRuntimeProperties managed;
    private final CredentialCrypto crypto;

    public ManagedProjectConfigService(ManagedProjectRepository repository,
                                       ManagedRuntimeProperties managed,
                                       CredentialCrypto crypto) {
        this.repository = repository;
        this.managed = managed;
        this.crypto = crypto;
    }

    /**
     * Update project runtime configuration without touching artifact bindings.
     * This is intentionally separate from the historical full-project PUT path:
     * changing start command / ports / env must not delete artifact rows or lose
     * deployed_version / running_version state.
     */
    public void updateRuntimeConfig(Long id, ManagedProjectRequest request) {
        ManagedProjectRepository.Project current = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在: " + id));
        if (request == null) throw new IllegalArgumentException("request is required");

        String projectName = trim(request.getProjectName());
        String databaseName = trim(request.getDatabaseName());
        String runtimeImage = trim(request.getRuntimeImage());
        String workDirectory = cleanRelative(request.getWorkDirectory());
        String startCommand = normalizeStartCommand(request.getStartCommand());
        String serviceMode = StringUtils.hasText(request.getServiceMode())
                ? request.getServiceMode().trim().toUpperCase()
                : "DIRECT";

        if (!StringUtils.hasText(projectName)) throw new IllegalArgumentException("项目名称必填");
        if (repository.existsProjectName(projectName, id)) throw new IllegalArgumentException("项目名称已存在");
        if (!current.databaseName().equals(databaseName)) throw new IllegalArgumentException("项目 database 创建后不可切换");
        if (!StringUtils.hasText(runtimeImage)) throw new IllegalArgumentException("Runtime Image 必填");
        if (!List.of("DIRECT", "NGINX").contains(serviceMode)) throw new IllegalArgumentException("serviceMode 仅支持 DIRECT / NGINX");
        if (request.getServicePort() == null || request.getServicePort() < 1 || request.getServicePort() > 65535) {
            throw new IllegalArgumentException("项目服务端口非法");
        }

        request.setProjectName(projectName);
        request.setDatabaseName(databaseName);
        request.setRuntimeImage(runtimeImage);
        request.setWorkDirectory(workDirectory);
        request.setStartCommand(startCommand);
        request.setServiceMode(serviceMode);
        request.setNginxApiPrefix(normalizeApiPrefix(request.getNginxApiPrefix()));

        if ("NGINX".equals(serviceMode)) {
            request.setNginxStaticDirectory(cleanRelative(request.getNginxStaticDirectory()));
            if (!StringUtils.hasText(request.getNginxStaticDirectory())) throw new IllegalArgumentException("NGINX 模式需要静态目录");
            if (request.getNginxBackendPort() == null || request.getNginxBackendPort() < 1 || request.getNginxBackendPort() > 65535) {
                throw new IllegalArgumentException("NGINX 模式需要合法后端端口");
            }
        } else {
            request.setNginxStaticDirectory(null);
            request.setNginxBackendPort(null);
        }

        repository.update(id, request, encryptedEnvironment(request.getEnvContent()));
        writeRuntimeFiles(repository.findById(id).orElseThrow());
    }

    private void writeRuntimeFiles(ManagedProjectRepository.Project project) {
        Path config = projectRoot(project.id()).resolve("config");
        try {
            Files.createDirectories(config);
            Files.writeString(config.resolve("env.sh"), environmentScript(project), StandardCharsets.UTF_8);
            Files.writeString(config.resolve("app-start.sh"), appStartScript(project), StandardCharsets.UTF_8);
            Files.writeString(config.resolve("start.sh"), startScript(project), StandardCharsets.UTF_8);
            if ("NGINX".equals(project.serviceMode())) {
                Files.writeString(config.resolve("nginx.conf"), nginxConfig(project), StandardCharsets.UTF_8);
                Files.writeString(config.resolve("supervisord.conf"), supervisorConfig(), StandardCharsets.UTF_8);
            } else {
                Files.deleteIfExists(config.resolve("nginx.conf"));
                Files.deleteIfExists(config.resolve("supervisord.conf"));
            }
        } catch (Exception e) {
            throw new IllegalStateException("写入项目运行配置失败: " + message(e), e);
        }
    }

    private String environmentScript(ManagedProjectRepository.Project project) {
        StringBuilder out = new StringBuilder("#!/bin/sh\n");
        repository.environmentCiphertext(project.id()).map(crypto::decrypt).ifPresent(content -> {
            for (String line : content.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
                String value = line.strip();
                if (value.isEmpty() || value.startsWith("#")) continue;
                int eq = value.indexOf('=');
                if (eq <= 0) continue;
                String key = value.substring(0, eq).trim();
                String val = value.substring(eq + 1);
                if (key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    out.append("export ").append(key).append("=").append(shQuote(val)).append("\n");
                }
            }
        });
        out.append("export DB_HOST=").append(shQuote(managed.getMysqlHost())).append("\n");
        out.append("export DB_PORT=").append(shQuote(String.valueOf(managed.getMysqlPort()))).append("\n");
        out.append("export DB_NAME=").append(shQuote(project.databaseName())).append("\n");
        out.append("export DB_USER=").append(shQuote(managed.getMysqlUser())).append("\n");
        out.append("export DB_PASSWORD=").append(shQuote(managed.getMysqlPassword())).append("\n");
        return out.toString();
    }

    private String appStartScript(ManagedProjectRepository.Project project) {
        String command = project.startCommand();
        if (!StringUtils.hasText(command) || UNCONFIGURED_START_COMMAND.equals(command)) {
            return "#!/bin/sh\necho 'FDP: 启动命令尚未配置，请先在项目编辑中设置启动命令。' >&2\nexit 1\n";
        }
        String workdir = managed.getWorkspaceMount() + (".".equals(project.workDirectory()) ? "" : "/" + project.workDirectory());
        return "#!/bin/sh\nset -e\n. " + managed.getConfigMount() + "/env.sh\ncd "
                + ShellCommandSupport.quote(workdir)
                + "\nexec /bin/sh -c " + shQuote(command) + "\n";
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

    private Path projectRoot(Long id) {
        Path root = Path.of(managed.getProjectRoot()).toAbsolutePath().normalize();
        Path project = root.resolve(String.valueOf(id)).normalize();
        if (!project.startsWith(root)) throw new IllegalStateException("项目目录非法");
        return project;
    }

    private String normalizeStartCommand(String value) {
        if (!StringUtils.hasText(value) || UNCONFIGURED_START_COMMAND.equals(value)) return "";
        return value.strip();
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
        if (path.startsWith("/") || path.contains("..") || !SAFE_RELATIVE.matcher(path).matches()) {
            throw new IllegalArgumentException("相对路径非法: " + value);
        }
        return path;
    }

    private String encryptedEnvironment(String content) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n').strip();
        return StringUtils.hasText(normalized) ? crypto.encrypt(normalized) : null;
    }

    private String trim(String value) { return value == null ? null : value.trim(); }
    private String shQuote(String value) { String v = value == null ? "" : value; return "'" + v.replace("'", "'\"'\"'") + "'"; }
    private String message(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
}
