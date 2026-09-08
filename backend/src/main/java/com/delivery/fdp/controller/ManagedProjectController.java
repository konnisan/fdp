package com.delivery.fdp.controller;

import com.delivery.fdp.dto.ManagedProjectRequest;
import com.delivery.fdp.repository.ManagedProjectRepository;
import com.delivery.fdp.service.ManagedProjectService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/managed-projects")
public class ManagedProjectController {
    private final ManagedProjectService service;

    public ManagedProjectController(ManagedProjectService service) { this.service = service; }

    @GetMapping
    public List<Map<String, Object>> projects() { return service.projects(); }

    @GetMapping("/{id}")
    public Map<String, Object> project(@PathVariable Long id) { return service.project(id); }

    @PostMapping
    public Map<String, Object> create(@RequestBody ManagedProjectRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody ManagedProjectRequest request) { return service.update(id, request); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }

    @GetMapping("/{id}/artifacts")
    public List<ManagedProjectRepository.ArtifactBinding> artifacts(@PathVariable Long id) { return service.artifacts(id); }

    @GetMapping("/{id}/artifacts/{artifactId}/versions")
    public List<Map<String, Object>> versions(@PathVariable Long id, @PathVariable Long artifactId) { return service.artifactVersions(id, artifactId); }

    @PostMapping("/{id}/deploy")
    public Map<String, Object> deploy(@PathVariable Long id, @RequestBody ManagedProjectService.DeployRequest request) { return service.deploy(id, request); }

    @PostMapping("/{id}/start")
    public Map<String, Object> start(@PathVariable Long id) { return service.start(id); }

    @PostMapping("/{id}/stop")
    public Map<String, Object> stop(@PathVariable Long id) { return service.stop(id); }

    @PostMapping("/{id}/restart")
    public Map<String, Object> restart(@PathVariable Long id) { return service.restart(id); }

    @GetMapping("/{id}/runtime")
    public Map<String, Object> runtime(@PathVariable Long id) { return service.runtime(id); }

    @GetMapping("/{id}/logs")
    public Map<String, Object> logs(@PathVariable Long id) { return service.logs(id); }

    @PostMapping("/{id}/sql")
    public Map<String, Object> sql(@PathVariable Long id, @RequestBody SqlRequest request) { return service.executeSql(id, request == null ? null : request.sql()); }

    @PostMapping(value = "/{id}/sql-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> sqlFile(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("SQL 文件不能为空");
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".sql")) throw new IllegalArgumentException("只支持 .sql 文件");
        return service.executeSql(id, new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    @PostMapping("/{id}/preview")
    public Map<String, Object> preview(@PathVariable Long id) { return service.switchPreview(id); }

    @GetMapping("/runtime-images")
    public List<String> runtimeImages() { return service.runtimeImages(); }

    public record SqlRequest(String sql) {}
}
