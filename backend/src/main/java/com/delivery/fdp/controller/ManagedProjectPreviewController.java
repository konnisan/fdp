package com.delivery.fdp.controller;

import com.delivery.fdp.config.ManagedRuntimeProperties;
import com.delivery.fdp.repository.ManagedProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/managed-projects")
public class ManagedProjectPreviewController {
    private static final int TREE_LIMIT = 2000;

    private final ManagedProjectRepository repository;
    private final ManagedRuntimeProperties managed;

    public ManagedProjectPreviewController(ManagedProjectRepository repository,
                                           ManagedRuntimeProperties managed) {
        this.repository = repository;
        this.managed = managed;
    }

    @GetMapping("/{id}/directory")
    public Map<String, Object> directory(@PathVariable Long id) {
        ManagedProjectRepository.Project project = required(id);
        Path current = currentRoot(id);
        List<Map<String, Object>> entries = new ArrayList<>();
        if (Files.isDirectory(current)) {
            try (var paths = Files.walk(current)) {
                paths.filter(path -> !path.equals(current))
                        .sorted(Comparator.comparing(path -> current.relativize(path).toString(), String.CASE_INSENSITIVE_ORDER))
                        .limit(TREE_LIMIT)
                        .forEach(path -> entries.add(entry(current, path)));
            } catch (Exception e) {
                throw new IllegalStateException("读取项目 current 目录失败: " + message(e), e);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", id);
        result.put("projectName", project.projectName());
        result.put("root", current.toString());
        result.put("exists", Files.isDirectory(current));
        result.put("entries", entries);
        result.put("truncated", entries.size() >= TREE_LIMIT);
        return result;
    }

    @GetMapping("/{id}/local-preview/**")
    public ResponseEntity<?> localPreview(@PathVariable Long id, HttpServletRequest request) {
        required(id);
        Path current = currentRoot(id);
        if (!Files.isDirectory(current)) {
            throw new ResponseStatusException(NOT_FOUND, "项目尚未产生 current 目录");
        }

        String prefix = "/api/managed-projects/" + id + "/local-preview/";
        String uri = request.getRequestURI();
        int prefixIndex = uri.indexOf(prefix);
        if (prefixIndex < 0) throw new ResponseStatusException(NOT_FOUND);
        String encoded = uri.substring(prefixIndex + prefix.length());
        String relative = URLDecoder.decode(encoded, StandardCharsets.UTF_8).replace('\\', '/');
        if (relative.isBlank()) throw new ResponseStatusException(NOT_FOUND, "请选择 current 目录中的 HTML 文件进行本地预览");

        Path candidate = current.resolve(relative).normalize();
        if (!candidate.startsWith(current)) throw new ResponseStatusException(BAD_REQUEST, "非法项目预览路径");
        if (Files.isDirectory(candidate)) candidate = candidate.resolve("index.html");
        if (!Files.isRegularFile(candidate)) throw new ResponseStatusException(NOT_FOUND, "项目预览文件不存在");

        try {
            String fileName = candidate.getFileName().toString();
            MediaType mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
            Path relativeFile = current.relativize(candidate);
            Path parent = relativeFile.getParent();
            String base = prefix;
            if (parent != null) {
                for (Path part : parent) {
                    base += UriUtils.encodePathSegment(part.toString(), StandardCharsets.UTF_8) + "/";
                }
            }

            if (fileName.toLowerCase().endsWith(".html")) {
                String html = Files.readString(candidate, StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .contentType(MediaType.TEXT_HTML)
                        .body(adaptHtml(html, base));
            }
            if (fileName.toLowerCase().endsWith(".css")) {
                String css = Files.readString(candidate, StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .contentType(MediaType.valueOf("text/css"))
                        .body(adaptCss(css, base));
            }
            Resource resource = new UrlResource(candidate.toUri());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(mediaType)
                    .body(resource);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(NOT_FOUND, "项目预览文件读取失败");
        }
    }

    private Map<String, Object> entry(Path root, Path path) {
        Path relative = root.relativize(path);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", path.getFileName() == null ? "" : path.getFileName().toString());
        entry.put("path", relative.toString().replace('\\', '/'));
        entry.put("depth", Math.max(0, relative.getNameCount() - 1));
        entry.put("directory", Files.isDirectory(path));
        entry.put("html", Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".html"));
        try {
            entry.put("size", Files.isRegularFile(path) ? Files.size(path) : null);
            Instant updated = Files.getLastModifiedTime(path).toInstant();
            entry.put("updatedAt", updated.toString());
        } catch (Exception ignored) {
            entry.put("size", null);
            entry.put("updatedAt", null);
        }
        return entry;
    }

    private ManagedProjectRepository.Project required(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("项目不存在: " + id));
    }

    private Path currentRoot(Long id) {
        Path root = Path.of(managed.getProjectRoot()).toAbsolutePath().normalize();
        Path current = root.resolve(String.valueOf(id)).resolve("current").normalize();
        if (!current.startsWith(root)) throw new IllegalStateException("项目目录非法");
        return current;
    }

    private String adaptHtml(String html, String previewBase) {
        String result = html;
        if (!result.toLowerCase().contains("<base ")) {
            String base = "<base href=\"" + previewBase + "\">";
            int head = result.toLowerCase().indexOf("<head>");
            result = head >= 0
                    ? result.substring(0, head + 6) + base + result.substring(head + 6)
                    : base + result;
        }
        String[] attributes = {"src", "href", "action", "poster", "data"};
        for (String attribute : attributes) {
            result = result.replace(attribute + "=\"/", attribute + "=\"" + previewBase)
                    .replace(attribute + "='/", attribute + "='" + previewBase);
        }
        return adaptCss(result, previewBase);
    }

    private String adaptCss(String value, String previewBase) {
        return value.replace("url(/", "url(" + previewBase)
                .replace("url('/", "url('" + previewBase)
                .replace("url(\"/", "url(\"" + previewBase);
    }

    private String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
