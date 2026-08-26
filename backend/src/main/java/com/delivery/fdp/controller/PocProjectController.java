package com.delivery.fdp.controller;

import com.delivery.fdp.dto.PocProjectRequest;
import com.delivery.fdp.model.PocProject;
import com.delivery.fdp.service.DeploymentService;
import com.delivery.fdp.service.ProjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/poc-projects")
public class PocProjectController {
    private final ProjectService projects; private final DeploymentService deploy;
    public PocProjectController(ProjectService p,DeploymentService d){projects=p;deploy=d;}
    @GetMapping public List<PocProject> list(){return projects.list();}
    @PostMapping public PocProject create(@RequestBody PocProjectRequest r){return projects.create(r);}
    @PutMapping("/{id}") public PocProject update(@PathVariable Long id,@RequestBody PocProjectRequest r){return projects.update(id,r);}
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id){projects.delete(id);}
    @PostMapping("/{id}/deploy") public Map<String,Object> deploy(@PathVariable Long id){return Map.of("taskId",deploy.deploy(id));}
    @PostMapping("/{id}/restart") public Map<String,String> restart(@PathVariable Long id){deploy.restart(id);return Map.of("status","RUNNING");}
    @PostMapping("/{id}/stop") public Map<String,String> stop(@PathVariable Long id){deploy.stop(id);return Map.of("status","STOPPED");}
}
