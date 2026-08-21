package com.delivery.fdp.controller;

import org.springframework.web.bind.annotation.*;
import com.delivery.fdp.service.*;

@RestController
@RequestMapping("/deploy")
public class DeployController {

 private final GitService git;
 private final BuildService build;
 private final DeployService deploy;

 public DeployController(GitService git,BuildService build,DeployService deploy){
  this.git=git;
  this.build=build;
  this.deploy=deploy;
 }

 @PostMapping("/run")
 public String run(){
  return git.pull(".")+
  build.build("mvn package")+
  deploy.deploy("echo deploy");
 }
}