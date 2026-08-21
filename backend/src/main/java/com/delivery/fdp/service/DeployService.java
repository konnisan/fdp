package com.delivery.fdp.service;

import org.springframework.stereotype.Service;

@Service
public class DeployService {

 private final CommandExecutor executor;

 public DeployService(CommandExecutor executor){
  this.executor=executor;
 }

 public String deploy(String command){
  return executor.execute(command);
 }
}