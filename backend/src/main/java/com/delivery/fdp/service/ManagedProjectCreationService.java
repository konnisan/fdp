package com.delivery.fdp.service;

import com.delivery.fdp.config.ManagedRuntimeProperties;
import com.delivery.fdp.dto.ManagedProjectRequest;
import com.delivery.fdp.repository.ManagedProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ManagedProjectCreationService {
    private static final String UNCONFIGURED_START_COMMAND = "__FDP_START_COMMAND_NOT_CONFIGURED__";
    private static final Pattern DB_NAME = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern SAFE_RELATIVE = Pattern.compile("^[A-Za-z0-9._/-]*$");

    private final ManagedProjectRepository repository;
    private final ManagedRuntimeProperties managed;
    private final CredentialCrypto crypto;
    private final DataSource dataSource;
    private final TransactionTemplate transactions;
    private final ManagedProjectService projects;

    public ManagedProjectCreationService(ManagedProjectRepository repository,
                                         ManagedRuntimeProperties managed,
                                         CredentialCrypto crypto,
                                         DataSource dataSource,
                                         TransactionTemplate transactions,
                                         ManagedProjectService projects) {
        this.repository = repository;
        this.managed = managed;
        this.crypto = crypto;
        this.dataSource = dataSource;
        this.transactions = transactions;
        this.projects = projects;
    }

    public Map<String, Object> create(ManagedProjectRequest request) {
        normalize(request);
        validate(request);
        ensureDatabaseAvailable(request.getDatabaseName());
        createDatabase(request.getDatabaseName());

        Long projectId = null;
        try {
            projectId = transactions.execute(status -> {
                long id = repository.create(
                        request,
                        encryptedEnvironment(request.getEnvContent()),
                        "fdp-project-pending-" + System.nanoTime());
                repository.updateContainer(id, null, containerName(id), request.getRuntimeImage());
                repository.replaceArtifacts(id, request.getArtifacts());
                return id;
            });
            if (projectId == null) throw new IllegalStateException("项目创建事务未返回 projectId");
            createProjectDirectories(projectId);
            return projects.project(projectId);
        } catch (RuntimeException e) {
            if (projectId != null) {
                try {
                    Long id = projectId;
                    transactions.executeWithoutResult(status -> repository.delete(id));
                } catch (Exception ignored) {}
                try { deleteRecursively(projectRoot(projectId)); } catch (Exception ignored) {}
            }
            try { dropDatabase(request.getDatabaseName()); } catch (Exception ignored) {}
            throw e;
        }
    }

    private void normalize(ManagedProjectRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        request.setProjectName(trim(request.getProjectName()));
        request.setDatabaseName(trim(request.getDatabaseName()));
        request.setRuntimeImage(trim(request.getRuntimeImage()));
        request.setWorkDirectory(cleanRelative(request.getWorkDirectory()));
        if (!StringUtils.hasText(request.getStartCommand())) request.setStartCommand(UNCONFIGURED_START_COMMAND);
        request.setServiceMode(StringUtils.hasText(request.getServiceMode()) ? request.getServiceMode().trim().toUpperCase() : "DIRECT");
        request.setNginxApiPrefix(normalizeApiPrefix(request.getNginxApiPrefix()));
        int order = 0;
        for (ManagedProjectRequest.ArtifactBindingRequest artifact : request.getArtifacts()) {
            artifact.setRepositoryId(trim(artifact.getRepositoryId()));
            artifact.setRepositoryName(trim(artifact.getRepositoryName()));
            artifact.setArtifactName(trim(artifact.getArtifactName()));
            artifact.setTargetDirectory(cleanRelative(artifact.getTargetDirectory()));
            artifact.setSortOrder(order++);
        }
    }

    private void validate(ManagedProjectRequest request) {
        if (!StringUtils.hasText(request.getProjectName())) throw new IllegalArgumentException("项目名称必填");
        if (repository.existsProjectName(request.getProjectName(), null)) throw new IllegalArgumentException("项目名称已存在");
        if (!StringUtils.hasText(request.getDatabaseName()) || !DB_NAME.matcher(request.getDatabaseName()).matches()) {
            throw new IllegalArgumentException("database 名称只允许字母、数字和下划线");
        }
        if (repository.databaseBound(request.getDatabaseName(), null)) throw new IllegalArgumentException("database 已绑定其他项目");
        if (!StringUtils.hasText(request.getRuntimeImage())) throw new IllegalArgumentException("Runtime Image 必填");
        if (!List.of("DIRECT", "NGINX").contains(request.getServiceMode())) throw new IllegalArgumentException("serviceMode 仅支持 DIRECT / NGINX");
        if (request.getServicePort() == null || request.getServicePort() < 1 || request.getServicePort() > 65535) {
            throw new IllegalArgumentException("项目服务端口非法");
        }
        if (request.getArtifacts() == null || request.getArtifacts().isEmpty()) throw new IllegalArgumentException("至少绑定一个制品");
        if ("NGINX".equals(request.getServiceMode())) {
            if (!StringUtils.hasText(request.getNginxStaticDirectory())) throw new IllegalArgumentException("NGINX 模式需要静态目录");
            request.setNginxStaticDirectory(cleanRelative(request.getNginxStaticDirectory()));
            if (request.getNginxBackendPort() == null || request.getNginxBackendPort() < 1 || request.getNginxBackendPort() > 65535) {
                throw new IllegalArgumentException("NGINX 模式需要合法后端端口");
            }
        }
        for (ManagedProjectRequest.ArtifactBindingRequest artifact : request.getArtifacts()) {
            if (!StringUtils.hasText(artifact.getRepositoryId()) || !StringUtils.hasText(artifact.getArtifactName())) {
                throw new IllegalArgumentException("每个制品都需要 repositoryId 和 artifactName");
            }
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

    private void dropDatabase(String databaseName) {
        if (!DB_NAME.matcher(databaseName).matches()) return;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
        } catch (Exception e) {
            throw new IllegalStateException("清理创建失败的 database 失败: " + message(e), e);
        }
    }

    private void createProjectDirectories(Long id) {
        try {
            Files.createDirectories(projectRoot(id).resolve("current"));
            Files.createDirectories(projectRoot(id).resolve("config"));
        } catch (Exception e) {
            throw new IllegalStateException("创建项目目录失败: " + message(e), e);
        }
    }

    private Path projectRoot(Long id) {
        Path root = Path.of(managed.getProjectRoot()).toAbsolutePath().normalize();
        Path project = root.resolve(String.valueOf(id)).normalize();
        if (!project.startsWith(root)) throw new IllegalStateException("项目目录非法");
        return project;
    }

    private void deleteRecursively(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private String cleanRelative(String value) {
        String path = value == null ? "." : value.trim().replace('\\', '/');
        if (path.isEmpty()) return ".";
        if (path.startsWith("/") || path.contains("..") || !SAFE_RELATIVE.matcher(path).matches()) {
            throw new IllegalArgumentException("相对路径非法: " + value);
        }
        return path;
    }

    private String normalizeApiPrefix(String value) {
        String prefix = StringUtils.hasText(value) ? value.trim() : "/api/";
        if (!prefix.startsWith("/")) prefix = "/" + prefix;
        if (!prefix.endsWith("/")) prefix += "/";
        return prefix;
    }

    private String encryptedEnvironment(String content) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n').strip();
        return StringUtils.hasText(normalized) ? crypto.encrypt(normalized) : null;
    }

    private String containerName(Long id) { return "fdp-project-" + id; }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String message(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
}
