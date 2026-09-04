package com.delivery.fdp.controller;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.config.StaticSourceProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class StaticPreviewController {
    private static final String PREFIX = "/api/poc-preview/";

    private final RuntimeProperties runtime;
    private final StaticSourceProperties source;

    public StaticPreviewController(RuntimeProperties runtime, StaticSourceProperties source) {
        this.runtime = runtime;
        this.source = source;
    }

    @GetMapping("/api/poc-preview/**")
    public ResponseEntity<Resource> preview(HttpServletRequest request) {
        Path root = sourceRoot();
        String uri = request.getRequestURI();
        int prefixIndex = uri.indexOf(PREFIX);
        if (prefixIndex < 0) throw new ResponseStatusException(NOT_FOUND);

        String encoded = uri.substring(prefixIndex + PREFIX.length());
        String relative = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        if (relative.isBlank()) throw new ResponseStatusException(NOT_FOUND);

        relative = relative.replace('\\', '/');
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) {
            throw new ResponseStatusException(BAD_REQUEST, "非法静态预览路径");
        }

        Path rel = root.relativize(candidate);
        if (rel.getNameCount() < 1) throw new ResponseStatusException(NOT_FOUND);
        Path projectRoot = root.resolve(rel.getName(0)).normalize();
        if (!Files.isRegularFile(projectRoot.resolve("index.html"))) {
            throw new ResponseStatusException(NOT_FOUND, "静态 POC 不存在或缺少 index.html");
        }
        if (!candidate.startsWith(projectRoot)) {
            throw new ResponseStatusException(BAD_REQUEST, "非法静态预览路径");
        }

        if (Files.isDirectory(candidate)) candidate = candidate.resolve("index.html");
        if (!Files.isRegularFile(candidate)) {
            // 允许单页应用的 history 路由回退到项目自己的 index.html。
            candidate = projectRoot.resolve("index.html");
        }

        try {
            Resource resource = new UrlResource(candidate.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(candidate.getFileName().toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(mediaType)
                    .body(resource);
        } catch (Exception e) {
            throw new ResponseStatusException(NOT_FOUND, "静态预览文件读取失败");
        }
    }

    private Path sourceRoot() {
        Path workspaceRoot = Path.of(runtime.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path workspace = workspaceRoot.resolve(source.getWorkspace()).normalize();
        if (!workspace.startsWith(workspaceRoot)) {
            throw new ResponseStatusException(BAD_REQUEST, "静态 Codeup 工作目录非法");
        }
        String relative = source.getRootDirectory() == null || source.getRootDirectory().isBlank()
                ? "."
                : source.getRootDirectory();
        Path root = workspace.resolve(relative).normalize();
        if (!root.startsWith(workspace)) {
            throw new ResponseStatusException(BAD_REQUEST, "静态 Codeup 根目录非法");
        }
        return root;
    }
}
