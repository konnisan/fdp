package com.delivery.fdp.controller;

import com.delivery.fdp.dto.ArtifactDeliveryProjectRequest;
import com.delivery.fdp.repository.ArtifactDeliveryRepository;
import com.delivery.fdp.service.ArtifactDeliveryService;
import com.delivery.fdp.service.ManagedEnvironmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/artifact-delivery")
public class ArtifactDeliveryController {
    private final ArtifactDeliveryService service;
    private final ManagedEnvironmentService environment;

    public ArtifactDeliveryController(ArtifactDeliveryService service,
                                      ManagedEnvironmentService environment) {
        this.service = service;
        this.environment = environment;
    }

    @GetMapping("/projects")
    public List<ArtifactDeliveryRepository.Project> projects() {
        return service.projects();
    }

    @PostMapping("/projects")
    public ArtifactDeliveryRepository.Project create(@RequestBody ArtifactDeliveryProjectRequest request) {
        return service.create(request);
    }

    @PutMapping("/projects/{id}")
    public ArtifactDeliveryRepository.Project update(@PathVariable Long id,
                                                     @RequestBody ArtifactDeliveryProjectRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/projects/{id}/environment")
    public Map<String, Object> environment(@PathVariable Long id) {
        return environment.info(id);
    }

    @PutMapping("/projects/{id}/environment")
    public Map<String, Object> updateEnvironment(@PathVariable Long id,
                                                 @RequestBody EnvironmentRequest request) {
        environment.save(id, request == null ? "" : request.content());
        return environment.info(id);
    }

    @GetMapping("/projects/{id}/releases")
    public List<Map<String, Object>> releases(@PathVariable Long id) {
        return service.releases(id);
    }

    @GetMapping("/projects/{id}/history")
    public List<ArtifactDeliveryRepository.Deployment> history(@PathVariable Long id) {
        return service.history(id);
    }

    @PostMapping("/projects/{id}/deploy")
    public Map<String, Object> deploy(@PathVariable Long id,
                                      @RequestBody(required = false) DeployRequest request) {
        return service.deploy(id, request == null ? null : request.runId());
    }

    public record DeployRequest(String runId) {}
    public record EnvironmentRequest(String content) {}
}
