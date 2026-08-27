package com.delivery.fdp.controller;

import com.delivery.fdp.dto.GitConnectionTestRequest;
import com.delivery.fdp.dto.SourceCredentialRequest;
import com.delivery.fdp.model.SourceCredential;
import com.delivery.fdp.service.SourceCredentialService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/source-credentials")
public class SourceCredentialController {
    private final SourceCredentialService service;

    public SourceCredentialController(SourceCredentialService service) {
        this.service = service;
    }

    @GetMapping
    public List<SourceCredential> list() {
        return service.list();
    }

    @PostMapping
    public SourceCredential create(@RequestBody SourceCredentialRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SourceCredential update(@PathVariable Long id, @RequestBody SourceCredentialRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable Long id, @RequestBody GitConnectionTestRequest request) {
        return service.test(id, request);
    }
}
