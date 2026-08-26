package com.delivery.fdp.model;

import java.time.LocalDateTime;

public class PocProject {
    private Long id;
    private String projectCode;
    private String projectName;
    private String gitUrl;
    private String gitBranch;
    private String projectType;
    private String projectDirectory;
    private String buildCommand;
    private String buildOutput;
    private String dockerfilePath;
    private String dockerBuildContext;
    private String imageName;
    private String containerName;
    private Integer hostPort;
    private Integer containerPort;
    private String cpuLimit;
    private String memoryLimit;
    private String hostDataPath;
    private String containerDataPath;
    private String healthCheckPath;
    private String previewPath;
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
    public String getProjectDirectory(){return projectDirectory;} public void setProjectDirectory(String v){projectDirectory=v;}
    public String getBuildCommand(){return buildCommand;} public void setBuildCommand(String v){buildCommand=v;}
    public String getBuildOutput(){return buildOutput;} public void setBuildOutput(String v){buildOutput=v;}
    public String getDockerfilePath(){return dockerfilePath;} public void setDockerfilePath(String v){dockerfilePath=v;}
    public String getDockerBuildContext(){return dockerBuildContext;} public void setDockerBuildContext(String v){dockerBuildContext=v;}
    public String getImageName(){return imageName;} public void setImageName(String v){imageName=v;}
    public String getContainerName(){return containerName;} public void setContainerName(String v){containerName=v;}
    public Integer getHostPort(){return hostPort;} public void setHostPort(Integer v){hostPort=v;}
    public Integer getContainerPort(){return containerPort;} public void setContainerPort(Integer v){containerPort=v;}
    public String getCpuLimit(){return cpuLimit;} public void setCpuLimit(String v){cpuLimit=v;}
    public String getMemoryLimit(){return memoryLimit;} public void setMemoryLimit(String v){memoryLimit=v;}
    public String getHostDataPath(){return hostDataPath;} public void setHostDataPath(String v){hostDataPath=v;}
    public String getContainerDataPath(){return containerDataPath;} public void setContainerDataPath(String v){containerDataPath=v;}
    public String getHealthCheckPath(){return healthCheckPath;} public void setHealthCheckPath(String v){healthCheckPath=v;}
    public String getPreviewPath(){return previewPath;} public void setPreviewPath(String v){previewPath=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getDeployedCommit(){return deployedCommit;} public void setDeployedCommit(String v){deployedCommit=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
    public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
}
