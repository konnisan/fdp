package com.delivery.fdp.controller;

import com.delivery.fdp.dto.ManagedProjectRequest;
import com.delivery.fdp.service.ManagedProjectConfigService;
import com.delivery.fdp.service.ManagedProjectService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/managed-projects")
public class ManagedProjectConfigController {
    private final ManagedProjectConfigService configService;
    private final ManagedProjectService projectService;

    public ManagedProjectConfigController(ManagedProjectConfigService configService,
                                          ManagedProjectService projectService) {
        this.configService = configService;
        this.projectService = projectService;
    }

    @PutMapping("/{id}/configuration")
    public Map<String, Object> updateConfiguration(@PathVariable Long id,
                                                   @RequestBody ManagedProjectRequest request) {
        configService.updateRuntimeConfig(id, request);
        return projectService.project(id);
    }
}
