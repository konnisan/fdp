package com.delivery.fdp.dto;

public class ArtifactDeliveryProjectRequest {
    private String projectCode;
    private String projectName;
    private String pipelineId;
    private String pipelineName;
    private String packageRepoId;
    private String packageRepoName;
    private String artifactName;
    private String previewPath;
    private Integer hostPort;
    private String containerName;
    private String envFile;

    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }
    public String getPipelineName() { return pipelineName; }
    public void setPipelineName(String pipelineName) { this.pipelineName = pipelineName; }
    public String getPackageRepoId() { return packageRepoId; }
    public void setPackageRepoId(String packageRepoId) { this.packageRepoId = packageRepoId; }
    public String getPackageRepoName() { return packageRepoName; }
    public void setPackageRepoName(String packageRepoName) { this.packageRepoName = packageRepoName; }
    public String getArtifactName() { return artifactName; }
    public void setArtifactName(String artifactName) { this.artifactName = artifactName; }
    public String getPreviewPath() { return previewPath; }
    public void setPreviewPath(String previewPath) { this.previewPath = previewPath; }
    public Integer getHostPort() { return hostPort; }
    public void setHostPort(Integer hostPort) { this.hostPort = hostPort; }
    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }
    public String getEnvFile() { return envFile; }
    public void setEnvFile(String envFile) { this.envFile = envFile; }
}
