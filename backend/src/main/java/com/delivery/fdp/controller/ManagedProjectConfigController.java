package com.delivery.fdp.controller;

import com.delivery.fdp.dto.ManagedProjectRequest;
import com.delivery.fdp.service.ManagedProjectEditService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/managed-projects")
public class ManagedProjectConfigController {
    private final ManagedProjectEditService editService;

    public ManagedProjectConfigController(ManagedProjectEditService editService) {
        this.editService = editService;
    }

    @PutMapping("/{id}/configuration")
    public Map<String, Object> updateConfiguration(@PathVariable Long id,
                                                   @RequestBody ManagedProjectRequest request) {
        return editService.update(id, request);
    }
}
