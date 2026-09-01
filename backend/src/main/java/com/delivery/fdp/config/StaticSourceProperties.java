package com.delivery.fdp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fdp.static-source")
public class StaticSourceProperties {
    private boolean enabled;
    private String gitUrl;
    private String branch = "main";
    private String username;
    private String token;
    private String workspace = "static-codeup";
    private String rootDirectory = ".";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getGitUrl() { return gitUrl; }
    public void setGitUrl(String gitUrl) { this.gitUrl = gitUrl; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }
    public String getRootDirectory() { return rootDirectory; }
    public void setRootDirectory(String rootDirectory) { this.rootDirectory = rootDirectory; }
}
