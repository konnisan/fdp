package com.delivery.fdp.controller;

import com.delivery.fdp.repository.DeploymentRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {
    private final DeploymentRepository repo;
    public DeploymentController(DeploymentRepository repo){this.repo=repo;}
    @GetMapping public List<Map<String,Object>> tasks(@RequestParam(required=false) Long projectId){return repo.tasks(projectId);}
    @GetMapping("/{taskId}/logs") public List<String> logs(@PathVariable Long taskId){return repo.logs(taskId);}
}
