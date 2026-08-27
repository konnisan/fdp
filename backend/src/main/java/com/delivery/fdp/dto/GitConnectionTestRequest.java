package com.delivery.fdp.dto;

public class GitConnectionTestRequest {
    private String gitUrl;
    private String gitBranch;

    public String getGitUrl(){return gitUrl;}
    public void setGitUrl(String value){gitUrl=value;}
    public String getGitBranch(){return gitBranch;}
    public void setGitBranch(String value){gitBranch=value;}
}
