package com.delivery.fdp.model;

import java.time.LocalDateTime;

public class PocProject {
    private Long id;
    private String projectCode;
    private String projectName;
    private String gitUrl;
    private String gitBranch;
    private String projectType;
    private String buildCommand;
    private String startCommand;
    private String buildOutput;
    private Integer internalPort;
    private String previewPath;
    private String pm2Name;
    private String sqlitePath;
    private String status;
    private String deployedCommit;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getProjectCode(){return projectCode;} public void setProjectCode(String v){projectCode=v;}
    public String getProjectName(){return projectName;} public void setProjectName(String v){projectName=v;}
    public String getGitUrl(){return gitUrl;} public void setGitUrl(String v){gitUrl=v;}
    public String getGitBranch(){return gitBranch;} public void setGitBranch(String v){gitBranch=v;}
    public String getProjectType(){return projectType;} public void setProjectType(String v){projectType=v;}
    public String getBuildCommand(){return buildCommand;} public void setBuildCommand(String v){buildCommand=v;}
    public String getStartCommand(){return startCommand;} public void setStartCommand(String v){startCommand=v;}
    public String getBuildOutput(){return buildOutput;} public void setBuildOutput(String v){buildOutput=v;}
    public Integer getInternalPort(){return internalPort;} public void setInternalPort(Integer v){internalPort=v;}
    public String getPreviewPath(){return previewPath;} public void setPreviewPath(String v){previewPath=v;}
    public String getPm2Name(){return pm2Name;} public void setPm2Name(String v){pm2Name=v;}
    public String getSqlitePath(){return sqlitePath;} public void setSqlitePath(String v){sqlitePath=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getDeployedCommit(){return deployedCommit;} public void setDeployedCommit(String v){deployedCommit=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
    public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
}
