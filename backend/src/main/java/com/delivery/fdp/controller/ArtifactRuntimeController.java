package com.delivery.fdp.controller;

import com.delivery.fdp.service.ArtifactRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/artifact-delivery/projects/{id}")
public class ArtifactRuntimeController {
    private final ArtifactRuntimeService runtime;

    public ArtifactRuntimeController(ArtifactRuntimeService runtime) {
        this.runtime = runtime;
    }

    @GetMapping("/runtime")
    public Map<String, Object> status(@PathVariable Long id) {
        return runtime.status(id);
    }

    @GetMapping("/deployment-plan")
    public Map<String, Object> deploymentPlan(@PathVariable Long id) {
        return runtime.deploymentPlan(id);
    }

    @GetMapping("/runtime-logs")
    public Map<String, Object> logs(@PathVariable Long id) {
        return runtime.logs(id);
    }

    @PostMapping("/restart")
    public Map<String, Object> restart(@PathVariable Long id) {
        return runtime.restart(id);
    }

    @PostMapping("/stop")
    public Map<String, Object> stop(@PathVariable Long id) {
        return runtime.stop(id);
    }

    @PostMapping("/remove-container")
    public Map<String, Object> remove(@PathVariable Long id) {
        return runtime.remove(id);
    }
}
