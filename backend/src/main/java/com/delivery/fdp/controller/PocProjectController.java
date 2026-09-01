package com.delivery.fdp.controller;

import com.delivery.fdp.dto.PocProjectRequest;
import com.delivery.fdp.model.PocProject;
import com.delivery.fdp.service.ContainerRuntimeService;
import com.delivery.fdp.service.DeploymentPlanService;
import com.delivery.fdp.service.DeploymentService;
import com.delivery.fdp.service.ProjectService;
import com.delivery.fdp.service.StaticSourceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/poc-projects")
public class PocProjectController {
    private final ProjectService projects;
    private final DeploymentService deploy;
    private final DeploymentPlanService plan;
    private final ContainerRuntimeService runtime;
    private final StaticSourceService staticSource;

    public PocProjectController(ProjectService projects,
                                DeploymentService deploy,
                                DeploymentPlanService plan,
                                ContainerRuntimeService runtime,
                                StaticSourceService staticSource) {
        this.projects = projects;
        this.deploy = deploy;
        this.plan = plan;
        this.runtime = runtime;
        this.staticSource = staticSource;
    }

    @GetMapping public List<PocProject> list(){return projects.list();}
    @PostMapping public PocProject create(@RequestBody PocProjectRequest request){return projects.create(request);}
    @PutMapping("/{id}") public PocProject update(@PathVariable Long id,@RequestBody PocProjectRequest request){return projects.update(id,request);}
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id){projects.delete(id);}
    @GetMapping("/{id}/deployment-plan") public Map<String,Object> deploymentPlan(@PathVariable Long id){return plan.plan(id);}
    @PostMapping("/{id}/deploy") public Map<String,Object> deploy(@PathVariable Long id){return Map.of("taskId",deploy.deploy(id));}
    @PostMapping("/{id}/sync-source") public Map<String,Object> syncSource(@PathVariable Long id){return staticSource.sync(id);}
    @GetMapping("/{id}/static-entries") public List<Map<String,Object>> staticEntries(@PathVariable Long id){return staticSource.entries(id);}
    @PostMapping("/{id}/restart") public Map<String,String> restart(@PathVariable Long id){deploy.restart(id);return Map.of("status","RUNNING");}
    @PostMapping("/{id}/stop") public Map<String,String> stop(@PathVariable Long id){deploy.stop(id);return Map.of("status","STOPPED");}
    @GetMapping("/{id}/runtime-logs") public Map<String,String> runtimeLogs(@PathVariable Long id){return Map.of("content",runtime.logs(id));}
}
