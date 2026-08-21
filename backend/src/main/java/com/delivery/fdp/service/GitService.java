package com.delivery.fdp.service;

import org.springframework.stereotype.Service;

@Service
public class GitService {

 private final CommandExecutor executor;

 public GitService(CommandExecutor executor){
  this.executor=executor;
 }

 public String pull(String path){
  return executor.execute(
   "cmd /c cd "+path+" && git pull");
 }
}