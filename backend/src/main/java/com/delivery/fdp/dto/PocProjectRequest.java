package com.delivery.fdp.dto;

public class PocProjectRequest {
    private String projectCode, projectName, gitUrl, gitBranch="develop", projectType;
    private String buildCommand, startCommand, buildOutput="dist", previewPath, pm2Name, sqlitePath="app.db";
    private Integer internalPort;
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
}
