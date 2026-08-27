package com.delivery.fdp.controller;

import com.delivery.fdp.service.PlatformRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeController {
    private final PlatformRuntimeService runtime;

    public RuntimeController(PlatformRuntimeService runtime) {
        this.runtime = runtime;
    }

    @GetMapping
    public Map<String, Object> status() {
        return runtime.status();
    }
}
