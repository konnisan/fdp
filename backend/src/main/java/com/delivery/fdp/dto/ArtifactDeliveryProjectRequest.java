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
    private Integer containerPort;
    private String containerName;
    /**
     * Legacy server-side env file path. Kept only for existing records/migration compatibility.
     * New UI should use envContent so FDP owns the per-project container environment.
     */
    private String envFile;
    /** Raw KEY=VALUE lines entered in FDP. Encrypted before persistence. */
    private String envContent;
    private String cpuLimit;
    private String memoryLimit;
    private String hostDataPath;
    private String containerDataPath;
    private String healthCheckPath;

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
    public Integer getContainerPort() { return containerPort; }
    public void setContainerPort(Integer containerPort) { this.containerPort = containerPort; }
    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }
    public String getEnvFile() { return envFile; }
    public void setEnvFile(String envFile) { this.envFile = envFile; }
    public String getEnvContent() { return envContent; }
    public void setEnvContent(String envContent) { this.envContent = envContent; }
    public String getCpuLimit() { return cpuLimit; }
    public void setCpuLimit(String cpuLimit) { this.cpuLimit = cpuLimit; }
    public String getMemoryLimit() { return memoryLimit; }
    public void setMemoryLimit(String memoryLimit) { this.memoryLimit = memoryLimit; }
    public String getHostDataPath() { return hostDataPath; }
    public void setHostDataPath(String hostDataPath) { this.hostDataPath = hostDataPath; }
    public String getContainerDataPath() { return containerDataPath; }
    public void setContainerDataPath(String containerDataPath) { this.containerDataPath = containerDataPath; }
    public String getHealthCheckPath() { return healthCheckPath; }
    public void setHealthCheckPath(String healthCheckPath) { this.healthCheckPath = healthCheckPath; }
}
