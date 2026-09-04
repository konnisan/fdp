package com.delivery.fdp.repository;

import com.delivery.fdp.dto.PocProjectRequest;
import com.delivery.fdp.model.PocProject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class PocProjectRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<PocProject> mapper = (rs, n) -> {
        PocProject p = new PocProject();
        p.setId(rs.getLong("id"));
        p.setProjectCode(rs.getString("project_code"));
        p.setProjectName(rs.getString("project_name"));
        p.setGitUrl(rs.getString("git_url"));
        p.setGitBranch(rs.getString("git_branch"));
        long credentialId = rs.getLong("credential_id");
        p.setCredentialId(rs.wasNull() ? null : credentialId);
        p.setCredentialName(rs.getString("credential_name"));
        p.setProjectType(rs.getString("project_type"));
        p.setDeploymentProfile(rs.getString("deployment_profile"));
        p.setProjectDirectory(rs.getString("project_directory"));
        p.setBuildCommand(rs.getString("build_command"));
        p.setBuildOutput(rs.getString("build_output"));
        p.setDockerfilePath(rs.getString("dockerfile_path"));
        p.setDockerBuildContext(rs.getString("docker_build_context"));
        p.setImageName(rs.getString("image_name"));
        p.setContainerName(rs.getString("container_name"));
        int hostPort = rs.getInt("host_port"); p.setHostPort(rs.wasNull() ? null : hostPort);
        int containerPort = rs.getInt("container_port"); p.setContainerPort(rs.wasNull() ? null : containerPort);
        p.setCpuLimit(rs.getString("cpu_limit"));
        p.setMemoryLimit(rs.getString("memory_limit"));
        p.setHostDataPath(rs.getString("host_data_path"));
        p.setContainerDataPath(rs.getString("container_data_path"));
        p.setHealthCheckPath(rs.getString("health_check_path"));
        p.setPreviewPath(rs.getString("preview_path"));
        p.setStatus(rs.getString("status"));
        p.setDeployedCommit(rs.getString("deployed_commit"));
        if (rs.getTimestamp("create_time") != null) p.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
        if (rs.getTimestamp("update_time") != null) p.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
        return p;
    };

    private static final String SELECT = """
            SELECT p.*, c.name AS credential_name
            FROM delivery_project p
            LEFT JOIN source_credential c ON c.id=p.credential_id
            """;

    public PocProjectRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<PocProject> findAll(){return jdbc.query(SELECT + " ORDER BY p.id DESC", mapper);}
    public List<PocProject> findPublished(){return jdbc.query(SELECT + " WHERE p.status IN ('RUNNING','PUBLISHED') ORDER BY p.id", mapper);}
    public Optional<PocProject> findById(Long id){return jdbc.query(SELECT + " WHERE p.id=?", mapper, id).stream().findFirst();}

    public long create(PocProjectRequest r){
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO delivery_project(project_code,project_name,git_url,git_branch,credential_id,project_type,deployment_profile,project_directory,build_command,build_output,dockerfile_path,docker_build_context,image_name,container_name,host_port,container_port,cpu_limit,memory_limit,host_data_path,container_data_path,health_check_path,preview_path,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT')",
                    Statement.RETURN_GENERATED_KEYS);
            bind(ps, r);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("create failed");
        return key.getKey().longValue();
    }

    public void update(Long id, PocProjectRequest r){
        jdbc.update("UPDATE delivery_project SET project_code=?,project_name=?,git_url=?,git_branch=?,credential_id=?,project_type=?,deployment_profile=?,project_directory=?,build_command=?,build_output=?,dockerfile_path=?,docker_build_context=?,image_name=?,container_name=?,host_port=?,container_port=?,cpu_limit=?,memory_limit=?,host_data_path=?,container_data_path=?,health_check_path=?,preview_path=?,update_time=NOW() WHERE id=?",
                ps -> { bind(ps, r); ps.setLong(23, id); });
    }

    private void bind(PreparedStatement ps, PocProjectRequest r) throws java.sql.SQLException {
        ps.setString(1, r.getProjectCode());
        ps.setString(2, r.getProjectName());
        ps.setString(3, r.getGitUrl());
        ps.setString(4, r.getGitBranch());
        if (r.getCredentialId() == null) ps.setNull(5, java.sql.Types.BIGINT); else ps.setLong(5, r.getCredentialId());
        ps.setString(6, r.getProjectType());
        ps.setString(7, r.getDeploymentProfile());
        ps.setString(8, r.getProjectDirectory());
        ps.setString(9, r.getBuildCommand());
        ps.setString(10, r.getBuildOutput());
        ps.setString(11, r.getDockerfilePath());
        ps.setString(12, r.getDockerBuildContext());
        ps.setString(13, r.getImageName());
        ps.setString(14, r.getContainerName());
        if (r.getHostPort() == null) ps.setNull(15, java.sql.Types.INTEGER); else ps.setInt(15, r.getHostPort());
        if (r.getContainerPort() == null) ps.setNull(16, java.sql.Types.INTEGER); else ps.setInt(16, r.getContainerPort());
        ps.setString(17, r.getCpuLimit());
        ps.setString(18, r.getMemoryLimit());
        ps.setString(19, r.getHostDataPath());
        ps.setString(20, r.getContainerDataPath());
        ps.setString(21, r.getHealthCheckPath());
        ps.setString(22, r.getPreviewPath());
    }

    public void delete(Long id){jdbc.update("DELETE FROM delivery_project WHERE id=?", id);}
    public void updateStatus(Long id, String status){jdbc.update("UPDATE delivery_project SET status=?,update_time=NOW() WHERE id=?", status, id);}
    public void updateDeployment(Long id, String status, String commit){jdbc.update("UPDATE delivery_project SET status=?,deployed_commit=?,update_time=NOW() WHERE id=?", status, commit, id);}
    public int count(String column, Object value, Long exclude){
        String sql = exclude == null ? "SELECT COUNT(*) FROM delivery_project WHERE " + column + "=?" : "SELECT COUNT(*) FROM delivery_project WHERE " + column + "=? AND id<>?";
        Integer n = exclude == null ? jdbc.queryForObject(sql, Integer.class, value) : jdbc.queryForObject(sql, Integer.class, value, exclude);
        return n == null ? 0 : n;
    }
}
