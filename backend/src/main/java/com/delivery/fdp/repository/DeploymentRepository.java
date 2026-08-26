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
    public DeploymentRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public long createTask(Long projectId){KeyHolder k=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO deployment_task(project_id,status,current_step,start_time) VALUES(?,'RUNNING','PREPARE',NOW())",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,projectId);return ps;},k);if(k.getKey()==null)throw new IllegalStateException("task create failed");return k.getKey().longValue();}
    public void step(Long id,String step){jdbc.update("UPDATE deployment_task SET current_step=? WHERE id=?",step,id);}
    public void commit(Long id,String commit){jdbc.update("UPDATE deployment_task SET commit_id=? WHERE id=?",commit,id);}
    public void finish(Long id,String status){jdbc.update("UPDATE deployment_task SET status=?,current_step=?,end_time=NOW() WHERE id=?",status,status,id);}
    public void log(Long id,String text){jdbc.update("INSERT INTO deployment_log(task_id,content) VALUES(?,?)",id,text==null?"":text);}
    public List<Map<String,Object>> tasks(Long projectId){return projectId==null?jdbc.queryForList("SELECT * FROM deployment_task ORDER BY id DESC LIMIT 100"):jdbc.queryForList("SELECT * FROM deployment_task WHERE project_id=? ORDER BY id DESC LIMIT 100",projectId);}
    public List<String> logs(Long taskId){return jdbc.query("SELECT content FROM deployment_log WHERE task_id=? ORDER BY id",(rs,n)->rs.getString(1),taskId);}
}
