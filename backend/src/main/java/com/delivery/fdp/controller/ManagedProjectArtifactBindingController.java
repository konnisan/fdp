package com.delivery.fdp.controller;

import com.delivery.fdp.dto.ManagedProjectRequest;
import com.delivery.fdp.service.ManagedProjectArtifactBindingService;
import com.delivery.fdp.service.ManagedProjectService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/managed-projects")
public class ManagedProjectArtifactBindingController {
    private final ManagedProjectArtifactBindingService artifacts;
    private final ManagedProjectService projects;

    public ManagedProjectArtifactBindingController(ManagedProjectArtifactBindingService artifacts,
                                                   ManagedProjectService projects) {
        this.artifacts = artifacts;
        this.projects = projects;
    }

    @PutMapping("/{id}/artifact-bindings")
    public Map<String, Object> updateArtifacts(@PathVariable Long id,
                                               @RequestBody ArtifactBindingRequest request) {
        artifacts.replacePreservingVersions(id, request == null ? List.of() : request.artifacts());
        return projects.project(id);
    }

    public record ArtifactBindingRequest(List<ManagedProjectRequest.ArtifactBindingRequest> artifacts) {}
}
