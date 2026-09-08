package com.delivery.fdp.dto;

import java.util.ArrayList;
import java.util.List;

public class ManagedProjectRequest {
    private String projectName;
    private String databaseName;
    private String runtimeImage;
    private String workDirectory = ".";
    private String startCommand;
    private String serviceMode = "DIRECT";
    private Integer servicePort;
    private String nginxStaticDirectory;
    private String nginxApiPrefix = "/api/";
    private Integer nginxBackendPort;
    private String envContent;
    private List<ArtifactBindingRequest> artifacts = new ArrayList<>();

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    public String getRuntimeImage() { return runtimeImage; }
    public void setRuntimeImage(String runtimeImage) { this.runtimeImage = runtimeImage; }
    public String getWorkDirectory() { return workDirectory; }
    public void setWorkDirectory(String workDirectory) { this.workDirectory = workDirectory; }
    public String getStartCommand() { return startCommand; }
    public void setStartCommand(String startCommand) { this.startCommand = startCommand; }
    public String getServiceMode() { return serviceMode; }
    public void setServiceMode(String serviceMode) { this.serviceMode = serviceMode; }
    public Integer getServicePort() { return servicePort; }
    public void setServicePort(Integer servicePort) { this.servicePort = servicePort; }
    public String getNginxStaticDirectory() { return nginxStaticDirectory; }
    public void setNginxStaticDirectory(String nginxStaticDirectory) { this.nginxStaticDirectory = nginxStaticDirectory; }
    public String getNginxApiPrefix() { return nginxApiPrefix; }
    public void setNginxApiPrefix(String nginxApiPrefix) { this.nginxApiPrefix = nginxApiPrefix; }
    public Integer getNginxBackendPort() { return nginxBackendPort; }
    public void setNginxBackendPort(Integer nginxBackendPort) { this.nginxBackendPort = nginxBackendPort; }
    public String getEnvContent() { return envContent; }
    public void setEnvContent(String envContent) { this.envContent = envContent; }
    public List<ArtifactBindingRequest> getArtifacts() { return artifacts; }
    public void setArtifacts(List<ArtifactBindingRequest> artifacts) { this.artifacts = artifacts == null ? new ArrayList<>() : artifacts; }

    public static class ArtifactBindingRequest {
        private String repositoryId;
        private String repositoryName;
        private String artifactName;
        private String targetDirectory = ".";
        private Integer sortOrder = 0;

        public String getRepositoryId() { return repositoryId; }
        public void setRepositoryId(String repositoryId) { this.repositoryId = repositoryId; }
        public String getRepositoryName() { return repositoryName; }
        public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }
        public String getArtifactName() { return artifactName; }
        public void setArtifactName(String artifactName) { this.artifactName = artifactName; }
        public String getTargetDirectory() { return targetDirectory; }
        public void setTargetDirectory(String targetDirectory) { this.targetDirectory = targetDirectory; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
