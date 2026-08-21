package com.delivery.fdp.service;

import org.springframework.stereotype.Service;

@Service
public class BuildService {

 private final CommandExecutor executor;

 public BuildService(CommandExecutor executor){
  this.executor=executor;
 }

 public String build(String command){
  return executor.execute(command);
 }
}