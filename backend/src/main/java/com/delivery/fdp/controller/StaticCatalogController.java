package com.delivery.fdp.controller;

import com.delivery.fdp.service.StaticCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/static-catalog")
public class StaticCatalogController {
    private final StaticCatalogService catalog;

    public StaticCatalogController(StaticCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public Map<String, Object> list() {
        return catalog.list();
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        return catalog.refresh();
    }

    @PostMapping("/activate")
    public Map<String, Object> activate(@RequestBody ActivateRequest request) {
        return catalog.activate(request.projectName());
    }

    public record ActivateRequest(String projectName) {
    }
}
