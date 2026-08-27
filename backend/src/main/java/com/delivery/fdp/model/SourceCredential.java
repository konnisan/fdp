package com.delivery.fdp.model;

import java.time.LocalDateTime;

public class SourceCredential {
    private Long id;
    private String name;
    private String provider;
    private String cloneUsername;
    private String status;
    private String lastTestMessage;
    private LocalDateTime lastTestTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId(){return id;}
    public void setId(Long value){id=value;}
    public String getName(){return name;}
    public void setName(String value){name=value;}
    public String getProvider(){return provider;}
    public void setProvider(String value){provider=value;}
    public String getCloneUsername(){return cloneUsername;}
    public void setCloneUsername(String value){cloneUsername=value;}
    public String getStatus(){return status;}
    public void setStatus(String value){status=value;}
    public String getLastTestMessage(){return lastTestMessage;}
    public void setLastTestMessage(String value){lastTestMessage=value;}
    public LocalDateTime getLastTestTime(){return lastTestTime;}
    public void setLastTestTime(LocalDateTime value){lastTestTime=value;}
    public LocalDateTime getCreateTime(){return createTime;}
    public void setCreateTime(LocalDateTime value){createTime=value;}
    public LocalDateTime getUpdateTime(){return updateTime;}
    public void setUpdateTime(LocalDateTime value){updateTime=value;}
}
