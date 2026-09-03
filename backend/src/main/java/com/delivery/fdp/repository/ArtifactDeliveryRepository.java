package com.delivery.fdp.repository;

import com.delivery.fdp.dto.ArtifactDeliveryProjectRequest;
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
public class ArtifactDeliveryRepository {
    private final JdbcTemplate jdbc;

    public ArtifactDeliveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Project> findAll() {
        return jdbc.query("SELECT * FROM artifact_delivery_project ORDER BY id DESC", (rs, row) -> mapProject(rs));
    }

    public Optional<Project> findById(Long id) {
        List<Project> rows = jdbc.query("SELECT * FROM artifact_delivery_project WHERE id=?", (rs, row) -> mapProject(rs), id);
        return rows.stream().findFirst();
    }

    public List<Project> findRouted() {
        return jdbc.query("SELECT * FROM artifact_delivery_project WHERE status='RUNNING' ORDER BY id", (rs, row) -> mapProject(rs));
    }

    public long create(ArtifactDeliveryProjectRequest request) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO artifact_delivery_project
                    (project_code, project_name, pipeline_id, pipeline_name, package_repo_id, package_repo_name,
                     artifact_name, preview_path, host_port, container_name, env_file, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.getProjectCode());
            ps.setString(2, request.getProjectName());
            ps.setString(3, request.getPipelineId());
            ps.setString(4, request.getPipelineName());
            ps.setString(5, request.getPackageRepoId());
            ps.setString(6, request.getPackageRepoName());
            ps.setString(7, request.getArtifactName());
            ps.setString(8, request.getPreviewPath());
            ps.setInt(9, request.getHostPort());
            ps.setString(10, request.getContainerName());
            ps.setString(11, blankToNull(request.getEnvFile()));
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    public void updateStatus(Long id, String status) {
        jdbc.update("UPDATE artifact_delivery_project SET status=? WHERE id=?", status, id);
    }

    public void updateDeployment(Long id, String version, String runId, String image) {
        jdbc.update("""
                UPDATE artifact_delivery_project
                   SET status='RUNNING', current_version=?, current_run_id=?, current_image=?
                 WHERE id=?
                """, version, runId, image, id);
    }

    public long createDeployment(Long projectId, String runId, String version) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO artifact_deployment_history
                    (project_id, pipeline_run_id, artifact_version, status, start_time)
                    VALUES (?, ?, ?, 'QUEUED', NOW())
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, projectId);
            ps.setString(2, runId);
            ps.setString(3, version);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    public void markDeploymentRunning(Long id) {
        jdbc.update("UPDATE artifact_deployment_history SET status='RUNNING' WHERE id=?", id);
    }

    public void finishDeployment(Long id, String status, String imageTag, String message) {
        jdbc.update("""
                UPDATE artifact_deployment_history
                   SET status=?, image_tag=?, message=?, end_time=NOW()
                 WHERE id=?
                """, status, blankToNull(imageTag), truncate(message, 4000), id);
    }

    public List<Deployment> deployments(Long projectId) {
        return jdbc.query("""
                SELECT id, project_id, pipeline_run_id, artifact_version, status, image_tag, message, start_time, end_time
                  FROM artifact_deployment_history
                 WHERE project_id=?
                 ORDER BY id DESC
                 LIMIT 30
                """, (rs, row) -> new Deployment(
                rs.getLong("id"),
                rs.getLong("project_id"),
                rs.getString("pipeline_run_id"),
                rs.getString("artifact_version"),
                rs.getString("status"),
                rs.getString("image_tag"),
                rs.getString("message"),
                time(rs.getTimestamp("start_time")),
                time(rs.getTimestamp("end_time"))
        ), projectId);
    }

    private Project mapProject(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Project(
                rs.getLong("id"),
                rs.getString("project_code"),
                rs.getString("project_name"),
                rs.getString("pipeline_id"),
                rs.getString("pipeline_name"),
                rs.getString("package_repo_id"),
                rs.getString("package_repo_name"),
                rs.getString("artifact_name"),
                rs.getString("preview_path"),
                rs.getInt("host_port"),
                rs.getString("container_name"),
                rs.getString("env_file"),
                rs.getString("status"),
                rs.getString("current_version"),
                rs.getString("current_run_id"),
                rs.getString("current_image"),
                time(rs.getTimestamp("create_time")),
                time(rs.getTimestamp("update_time"))
        );
    }

    private LocalDateTime time(java.sql.Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record Project(
            Long id,
            String projectCode,
            String projectName,
            String pipelineId,
            String pipelineName,
            String packageRepoId,
            String packageRepoName,
            String artifactName,
            String previewPath,
            Integer hostPort,
            String containerName,
            String envFile,
            String status,
            String currentVersion,
            String currentRunId,
            String currentImage,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {}

    public record Deployment(
            Long id,
            Long projectId,
            String pipelineRunId,
            String artifactVersion,
            String status,
            String imageTag,
            String message,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {}
}
