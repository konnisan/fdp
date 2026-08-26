package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.model.PocProject;
import com.delivery.fdp.repository.PocProjectRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class ContainerRuntimeService {
    private final RuntimeProperties props;
    private final PocProjectRepository projects;
    private final CommandExecutor exec;

    public ContainerRuntimeService(RuntimeProperties props,PocProjectRepository projects,CommandExecutor exec){
        this.props=props;this.projects=projects;this.exec=exec;
    }

    public String logs(Long projectId){
        PocProject project=projects.findById(projectId).orElseThrow(()->new IllegalArgumentException("Delivery project not found: "+projectId));
        if(!"CONTAINER".equals(project.getProjectType()))throw new IllegalArgumentException("Runtime logs are only available for CONTAINER projects");
        Path root=Path.of(props.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path workspace=root.resolve(project.getProjectCode()).normalize();
        Path cwd=workspace.resolve(project.getProjectDirectory()).normalize();
        if(!workspace.startsWith(root)||!cwd.startsWith(workspace))throw new IllegalArgumentException("Invalid project workspace");
        CommandExecutor.Result result=exec.execute("docker logs --tail 300 "+q(project.getContainerName()),cwd);
        if(!result.success())throw new IllegalStateException("docker logs failed ("+result.exitCode()+"): "+result.output());
        return result.output();
    }

    private String q(Object value){
        String text=String.valueOf(value);
        if(!text.matches("^[A-Za-z0-9_./:@\\\\ -]+$"))throw new IllegalArgumentException("Unsafe command argument");
        return "'"+text.replace("'","'\\''")+"'";
    }
}
