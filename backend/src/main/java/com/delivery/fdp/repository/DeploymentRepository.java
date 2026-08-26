package com.delivery.fdp.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Repository
public class DeploymentRepository {
    private final JdbcTemplate jdbc;

    public DeploymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long createTask(Long projectId) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO deployment_task(project_id,status,current_step,start_time) VALUES(?,'QUEUED','QUEUED',NULL)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, projectId);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("task create failed");
        return key.getKey().longValue();
    }

    public void markRunning(Long id) {
        jdbc.update("UPDATE deployment_task SET status='RUNNING',current_step='PREPARE',start_time=NOW() WHERE id=?", id);
    }

    public void step(Long id, String step) {
        jdbc.update("UPDATE deployment_task SET current_step=? WHERE id=?", step, id);
    }

    public void commit(Long id, String commit) {
        jdbc.update("UPDATE deployment_task SET commit_id=? WHERE id=?", commit, id);
    }

    public void finish(Long id, String status) {
        jdbc.update("UPDATE deployment_task SET status=?,current_step=?,end_time=NOW() WHERE id=?", status, status, id);
    }

    public long startStep(Long taskId, String code, String name, int sortOrder) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO deployment_step(task_id,step_code,step_name,sort_order,status,start_time) VALUES(?,?,?,?, 'RUNNING',NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, taskId);
            ps.setString(2, code);
            ps.setString(3, name);
            ps.setInt(4, sortOrder);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("step create failed");
        return key.getKey().longValue();
    }

    public void finishStep(Long stepId, String status, String errorMessage) {
        jdbc.update("UPDATE deployment_step SET status=?,error_message=?,end_time=NOW() WHERE id=?", status, errorMessage, stepId);
    }

    public void skipStep(Long taskId, String code, String name, int sortOrder) {
        jdbc.update("INSERT INTO deployment_step(task_id,step_code,step_name,sort_order,status,start_time,end_time) VALUES(?,?,?,?, 'SKIPPED',NOW(),NOW())",
                taskId, code, name, sortOrder);
    }

    public void log(Long id, String text) {
        jdbc.update("INSERT INTO deployment_log(task_id,content) VALUES(?,?)", id, text == null ? "" : text);
    }

    public List<Map<String, Object>> tasks(Long projectId) {
        return projectId == null
                ? jdbc.queryForList("SELECT * FROM deployment_task ORDER BY id DESC LIMIT 100")
                : jdbc.queryForList("SELECT * FROM deployment_task WHERE project_id=? ORDER BY id DESC LIMIT 100", projectId);
    }

    public List<Map<String, Object>> steps(Long taskId) {
        return jdbc.queryForList("SELECT * FROM deployment_step WHERE task_id=? ORDER BY sort_order,id", taskId);
    }

    public List<String> logs(Long taskId) {
        return jdbc.query("SELECT content FROM deployment_log WHERE task_id=? ORDER BY id", (rs, n) -> rs.getString(1), taskId);
    }
}
