package com.delivery.fdp.dto;

public class SourceCredentialRequest {
    private String name;
    private String provider = "CODEUP";
    private String cloneUsername;
    private String token;

    public String getName(){return name;}
    public void setName(String value){name=value;}
    public String getProvider(){return provider;}
    public void setProvider(String value){provider=value;}
    public String getCloneUsername(){return cloneUsername;}
    public void setCloneUsername(String value){cloneUsername=value;}
    public String getToken(){return token;}
    public void setToken(String value){token=value;}
}
