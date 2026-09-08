package com.delivery.fdp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "fdp.managed")
public class ManagedRuntimeProperties {
    private String projectRoot = "/data/fdp/projects";
    private String dockerNetwork = "fdp-network";
    private String workspaceMount = "/workspace";
    private String configMount = "/fdp";
    private String mysqlHost = "mysql";
    private int mysqlPort = 3306;
    private String mysqlUser = "root";
    private String mysqlPassword = "";
    private String previewNginxContainer = "fdp-preview-nginx";
    private String previewNginxConfigFile = "/data/fdp/preview/nginx.conf";
    private List<String> runtimeImages = new ArrayList<>(List.of(
            "eclipse-temurin:17-jre",
            "eclipse-temurin:21-jre",
            "node:20-bookworm-slim",
            "node:22-bookworm-slim"
    ));

    public String getProjectRoot() { return projectRoot; }
    public void setProjectRoot(String projectRoot) { this.projectRoot = projectRoot; }
    public String getDockerNetwork() { return dockerNetwork; }
    public void setDockerNetwork(String dockerNetwork) { this.dockerNetwork = dockerNetwork; }
    public String getWorkspaceMount() { return workspaceMount; }
    public void setWorkspaceMount(String workspaceMount) { this.workspaceMount = workspaceMount; }
    public String getConfigMount() { return configMount; }
    public void setConfigMount(String configMount) { this.configMount = configMount; }
    public String getMysqlHost() { return mysqlHost; }
    public void setMysqlHost(String mysqlHost) { this.mysqlHost = mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public void setMysqlPort(int mysqlPort) { this.mysqlPort = mysqlPort; }
    public String getMysqlUser() { return mysqlUser; }
    public void setMysqlUser(String mysqlUser) { this.mysqlUser = mysqlUser; }
    public String getMysqlPassword() { return mysqlPassword; }
    public void setMysqlPassword(String mysqlPassword) { this.mysqlPassword = mysqlPassword; }
    public String getPreviewNginxContainer() { return previewNginxContainer; }
    public void setPreviewNginxContainer(String previewNginxContainer) { this.previewNginxContainer = previewNginxContainer; }
    public String getPreviewNginxConfigFile() { return previewNginxConfigFile; }
    public void setPreviewNginxConfigFile(String previewNginxConfigFile) { this.previewNginxConfigFile = previewNginxConfigFile; }
    public List<String> getRuntimeImages() { return runtimeImages; }
    public void setRuntimeImages(List<String> runtimeImages) { this.runtimeImages = runtimeImages; }
}
