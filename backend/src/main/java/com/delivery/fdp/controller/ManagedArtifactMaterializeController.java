package com.delivery.fdp.controller;

import com.delivery.fdp.service.AtomicManagedArtifactMaterializeService;
import com.delivery.fdp.service.ManagedArtifactMaterializeService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/managed-projects")
public class ManagedArtifactMaterializeController {
    private final AtomicManagedArtifactMaterializeService service;

    public ManagedArtifactMaterializeController(AtomicManagedArtifactMaterializeService service) {
        this.service = service;
    }

    @PostMapping("/{id}/materialize")
    public Map<String, Object> materialize(@PathVariable Long id,
                                           @RequestBody ManagedArtifactMaterializeService.MaterializeRequest request) {
        return service.materialize(id, request);
    }
}
