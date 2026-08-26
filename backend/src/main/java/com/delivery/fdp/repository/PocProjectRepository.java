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
    private final RowMapper<PocProject> mapper=(rs,n)->{
        PocProject p=new PocProject();
        p.setId(rs.getLong("id")); p.setProjectCode(rs.getString("project_code")); p.setProjectName(rs.getString("project_name"));
        p.setGitUrl(rs.getString("git_url")); p.setGitBranch(rs.getString("git_branch")); p.setProjectType(rs.getString("project_type"));
        p.setBuildCommand(rs.getString("build_command")); p.setStartCommand(rs.getString("start_command")); p.setBuildOutput(rs.getString("build_output"));
        int port=rs.getInt("internal_port"); p.setInternalPort(rs.wasNull()?null:port); p.setPreviewPath(rs.getString("preview_path"));
        p.setPm2Name(rs.getString("pm2_name")); p.setSqlitePath(rs.getString("sqlite_path")); p.setStatus(rs.getString("status"));
        p.setDeployedCommit(rs.getString("deployed_commit")); p.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
        if(rs.getTimestamp("update_time")!=null)p.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime()); return p;};
    public PocProjectRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<PocProject> findAll(){return jdbc.query("SELECT * FROM delivery_project ORDER BY id DESC",mapper);}
    public List<PocProject> findPublished(){return jdbc.query("SELECT * FROM delivery_project WHERE status IN ('RUNNING','PUBLISHED') ORDER BY id",mapper);}
    public Optional<PocProject> findById(Long id){return jdbc.query("SELECT * FROM delivery_project WHERE id=?",mapper,id).stream().findFirst();}
    public long create(PocProjectRequest r){KeyHolder k=new GeneratedKeyHolder(); jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO delivery_project(project_code,project_name,git_url,git_branch,project_type,build_command,start_command,build_output,internal_port,preview_path,pm2_name,sqlite_path,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?, 'DRAFT')",Statement.RETURN_GENERATED_KEYS); bind(ps,r); return ps;},k); if(k.getKey()==null)throw new IllegalStateException("create failed"); return k.getKey().longValue();}
    public void update(Long id,PocProjectRequest r){jdbc.update("UPDATE delivery_project SET project_code=?,project_name=?,git_url=?,git_branch=?,project_type=?,build_command=?,start_command=?,build_output=?,internal_port=?,preview_path=?,pm2_name=?,sqlite_path=?,update_time=NOW() WHERE id=?",ps->{bind(ps,r);ps.setLong(13,id);});}
    private void bind(PreparedStatement ps,PocProjectRequest r)throws java.sql.SQLException{ps.setString(1,r.getProjectCode());ps.setString(2,r.getProjectName());ps.setString(3,r.getGitUrl());ps.setString(4,r.getGitBranch());ps.setString(5,r.getProjectType());ps.setString(6,r.getBuildCommand());ps.setString(7,r.getStartCommand());ps.setString(8,r.getBuildOutput());if(r.getInternalPort()==null)ps.setNull(9,java.sql.Types.INTEGER);else ps.setInt(9,r.getInternalPort());ps.setString(10,r.getPreviewPath());ps.setString(11,r.getPm2Name());ps.setString(12,r.getSqlitePath());}
    public void delete(Long id){jdbc.update("DELETE FROM delivery_project WHERE id=?",id);}
    public void updateStatus(Long id,String s){jdbc.update("UPDATE delivery_project SET status=?,update_time=NOW() WHERE id=?",s,id);}
    public void updateDeployment(Long id,String s,String commit){jdbc.update("UPDATE delivery_project SET status=?,deployed_commit=?,update_time=NOW() WHERE id=?",s,commit,id);}
    public int count(String col,Object value,Long exclude){String sql=exclude==null?"SELECT COUNT(*) FROM delivery_project WHERE "+col+"=?":"SELECT COUNT(*) FROM delivery_project WHERE "+col+"=? AND id<>?";Integer n=exclude==null?jdbc.queryForObject(sql,Integer.class,value):jdbc.queryForObject(sql,Integer.class,value,exclude);return n==null?0:n;}
}
