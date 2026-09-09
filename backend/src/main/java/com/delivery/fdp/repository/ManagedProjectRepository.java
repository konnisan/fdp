package com.delivery.fdp.repository;

import com.delivery.fdp.dto.ManagedProjectRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ManagedProjectRepository {
    private final JdbcTemplate jdbc;

    public ManagedProjectRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Project> findAll() {
        return jdbc.query("SELECT * FROM managed_project ORDER BY id DESC", (rs, row) -> mapProject(rs));
    }

    public Optional<Project> findById(Long id) {
        return jdbc.query("SELECT * FROM managed_project WHERE id=?", (rs, row) -> mapProject(rs), id).stream().findFirst();
    }

    public boolean existsProjectName(String name, Long excludeId) {
        Integer count = excludeId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM managed_project WHERE project_name=?", Integer.class, name)
                : jdbc.queryForObject("SELECT COUNT(*) FROM managed_project WHERE project_name=? AND id<>?", Integer.class, name, excludeId);
        return count != null && count > 0;
    }

    public boolean databaseBound(String databaseName, Long excludeId) {
        Integer count = excludeId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM managed_project WHERE database_name=?", Integer.class, databaseName)
                : jdbc.queryForObject("SELECT COUNT(*) FROM managed_project WHERE database_name=? AND id<>?", Integer.class, databaseName, excludeId);
        return count != null && count > 0;
    }

    public long create(ManagedProjectRequest request, String envCiphertext, String containerName) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO managed_project
                    (project_name,database_name,runtime_image,work_directory,start_command,service_mode,service_port,
                     nginx_static_directory,nginx_api_prefix,nginx_backend_port,env_content_ciphertext,container_name,deployment_status)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?, 'DRAFT')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.getProjectName());
            ps.setString(2, request.getDatabaseName());
            ps.setString(3, request.getRuntimeImage());
            ps.setString(4, request.getWorkDirectory());
            ps.setString(5, request.getStartCommand());
            ps.setString(6, request.getServiceMode());
            ps.setObject(7, request.getServicePort());
            ps.setString(8, blank(request.getNginxStaticDirectory()));
            ps.setString(9, request.getNginxApiPrefix());
            ps.setObject(10, request.getNginxBackendPort());
            ps.setString(11, envCiphertext);
            ps.setString(12, containerName);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("create managed project failed");
        return key.getKey().longValue();
    }

    public void update(Long id, ManagedProjectRequest request, String envCiphertext) {
        jdbc.update("""
                UPDATE managed_project
                   SET project_name=?,database_name=?,runtime_image=?,work_directory=?,start_command=?,service_mode=?,
                       service_port=?,nginx_static_directory=?,nginx_api_prefix=?,nginx_backend_port=?,
                       env_content_ciphertext=?,update_time=NOW()
                 WHERE id=?
                """,
                request.getProjectName(), request.getDatabaseName(), request.getRuntimeImage(), request.getWorkDirectory(),
                request.getStartCommand(), request.getServiceMode(), request.getServicePort(), blank(request.getNginxStaticDirectory()),
                request.getNginxApiPrefix(), request.getNginxBackendPort(), envCiphertext, id);
    }

    public void replaceArtifacts(Long projectId, List<ManagedProjectRequest.ArtifactBindingRequest> artifacts) {
        jdbc.update("DELETE FROM managed_project_artifact WHERE project_id=?", projectId);
        int order = 0;
        for (ManagedProjectRequest.ArtifactBindingRequest artifact : artifacts) {
            int sortOrder = artifact.getSortOrder() == null ? order : artifact.getSortOrder();
            jdbc.update("""
                    INSERT INTO managed_project_artifact
                    (project_id,repository_id,repository_name,artifact_name,target_directory,sort_order)
                    VALUES(?,?,?,?,?,?)
                    """, projectId, artifact.getRepositoryId(), blank(artifact.getRepositoryName()), artifact.getArtifactName(), artifact.getTargetDirectory(), sortOrder);
            order++;
        }
    }

    public List<ArtifactBinding> artifacts(Long projectId) {
        return jdbc.query("SELECT * FROM managed_project_artifact WHERE project_id=? ORDER BY sort_order,id",
                (rs, row) -> mapArtifact(rs), projectId);
    }

    public Optional<ArtifactBinding> artifact(Long projectId, Long artifactId) {
        return jdbc.query("SELECT * FROM managed_project_artifact WHERE project_id=? AND id=?",
                (rs, row) -> mapArtifact(rs), projectId, artifactId).stream().findFirst();
    }

    public void markArtifactDeployed(Long artifactId, String version) {
        jdbc.update("UPDATE managed_project_artifact SET deployed_version=? WHERE id=?", version, artifactId);
    }

    public void markRunningVersions(Long projectId) {
        jdbc.update("UPDATE managed_project_artifact SET running_version=deployed_version WHERE project_id=?", projectId);
        jdbc.update("""
                UPDATE managed_project
                   SET running_version_summary=deployed_version_summary,deployment_status='RUNNING',last_error=NULL,update_time=NOW()
                 WHERE id=?
                """, projectId);
    }

    public void markDeployed(Long projectId, String summary, String image) {
        jdbc.update("""
                UPDATE managed_project
                   SET deployed_version_summary=?,deployed_runtime_image=?,deployment_status='DEPLOYED',
                       last_deploy_time=NOW(),last_error=NULL,update_time=NOW()
                 WHERE id=?
                """, summary, image, projectId);
    }

    public void updateStatus(Long projectId, String status) {
        jdbc.update("UPDATE managed_project SET deployment_status=?,update_time=NOW() WHERE id=?", status, projectId);
    }

    public void updateContainer(Long projectId, String containerId, String containerName, String image) {
        jdbc.update("""
                UPDATE managed_project SET container_id=?,container_name=?,deployed_runtime_image=?,update_time=NOW() WHERE id=?
                """, blank(containerId), containerName, image, projectId);
    }

    public void updateError(Long projectId, String message) {
        jdbc.update("""
                UPDATE managed_project
                   SET deployment_status='FAILED',last_error=?,last_deploy_time=NOW(),update_time=NOW()
                 WHERE id=?
                """, truncate(message, 4000), projectId);
    }

    public Optional<String> environmentCiphertext(Long projectId) {
        return jdbc.query("SELECT env_content_ciphertext FROM managed_project WHERE id=?", (rs, row) -> rs.getString(1), projectId)
                .stream()
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    public void setPreviewProject(Long projectId) {
        jdbc.update("""
                INSERT INTO fdp_platform_setting(setting_key,setting_value)
                VALUES('preview_project_id',?)
                ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),update_time=NOW()
                """, String.valueOf(projectId));
    }

    public Optional<Long> previewProjectId() {
        return jdbc.query("SELECT setting_value FROM fdp_platform_setting WHERE setting_key='preview_project_id'", (rs, row) -> rs.getString(1))
                .stream()
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> {
                    try {
                        Long.parseLong(value);
                        return true;
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                })
                .map(Long::valueOf)
                .findFirst();
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM managed_project_artifact WHERE project_id=?", id);
        jdbc.update("DELETE FROM managed_project WHERE id=?", id);
    }

    private Project mapProject(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Project(rs.getLong("id"), rs.getString("project_name"), rs.getString("database_name"),
                rs.getString("runtime_image"), rs.getString("work_directory"), rs.getString("start_command"),
                rs.getString("service_mode"), rs.getObject("service_port", Integer.class), rs.getString("nginx_static_directory"),
                rs.getString("nginx_api_prefix"), rs.getObject("nginx_backend_port", Integer.class), rs.getString("container_id"),
                rs.getString("container_name"), rs.getString("deployed_runtime_image"), rs.getString("deployed_version_summary"),
                rs.getString("running_version_summary"), rs.getString("deployment_status"), rs.getString("last_error"),
                time(rs.getTimestamp("last_deploy_time")), time(rs.getTimestamp("create_time")), time(rs.getTimestamp("update_time")));
    }

    private ArtifactBinding mapArtifact(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ArtifactBinding(rs.getLong("id"), rs.getLong("project_id"), rs.getString("repository_id"),
                rs.getString("repository_name"), rs.getString("artifact_name"), rs.getString("target_directory"),
                rs.getInt("sort_order"), rs.getString("deployed_version"), rs.getString("running_version"));
    }

    private LocalDateTime time(java.sql.Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String truncate(String value, int max) { return value == null ? null : (value.length() <= max ? value : value.substring(0, max)); }

    public record Project(Long id,String projectName,String databaseName,String runtimeImage,String workDirectory,String startCommand,
                          String serviceMode,Integer servicePort,String nginxStaticDirectory,String nginxApiPrefix,Integer nginxBackendPort,
                          String containerId,String containerName,String deployedRuntimeImage,String deployedVersionSummary,String runningVersionSummary,
                          String deploymentStatus,String lastError,LocalDateTime lastDeployTime,LocalDateTime createTime,LocalDateTime updateTime) {}

    public record ArtifactBinding(Long id,Long projectId,String repositoryId,String repositoryName,String artifactName,
                                  String targetDirectory,Integer sortOrder,String deployedVersion,String runningVersion) {}
}
