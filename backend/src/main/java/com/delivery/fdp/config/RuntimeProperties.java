package com.delivery.fdp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fdp.runtime")
public class RuntimeProperties {
    private boolean executionEnabled;
    private String workspaceRoot;
    private String staticRoot;
    private String dataRoot;
    private String artifactRoot;
    private String nginxConfigFile;
    private int nginxPublicPort = 8090;
    private String nginxReloadCommand;

    public boolean isExecutionEnabled() { return executionEnabled; }
    public void setExecutionEnabled(boolean executionEnabled) { this.executionEnabled = executionEnabled; }
    public String getWorkspaceRoot() { return workspaceRoot; }
    public void setWorkspaceRoot(String workspaceRoot) { this.workspaceRoot = workspaceRoot; }
    public String getStaticRoot() { return staticRoot; }
    public void setStaticRoot(String staticRoot) { this.staticRoot = staticRoot; }
    public String getDataRoot() { return dataRoot; }
    public void setDataRoot(String dataRoot) { this.dataRoot = dataRoot; }
    public String getArtifactRoot() { return artifactRoot; }
    public void setArtifactRoot(String artifactRoot) { this.artifactRoot = artifactRoot; }
    public String getNginxConfigFile() { return nginxConfigFile; }
    public void setNginxConfigFile(String nginxConfigFile) { this.nginxConfigFile = nginxConfigFile; }
    public int getNginxPublicPort() { return nginxPublicPort; }
    public void setNginxPublicPort(int nginxPublicPort) { this.nginxPublicPort = nginxPublicPort; }
    public String getNginxReloadCommand() { return nginxReloadCommand; }
    public void setNginxReloadCommand(String nginxReloadCommand) { this.nginxReloadCommand = nginxReloadCommand; }
}
