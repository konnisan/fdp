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
import org.springframework.web.util.UriUtils;

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

    /**
     * Preview a POC directly from the synchronized Codeup workspace.
     *
     * The whole project directory is preserved. index.html is only the entry file;
     * sibling assets/css/js/images are served from the same project directory.
     */
    @GetMapping("/api/poc-preview/**")
    public ResponseEntity<?> preview(HttpServletRequest request) {
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
        String projectName = rel.getName(0).toString();
        Path projectRoot = root.resolve(projectName).normalize();
        if (!Files.isRegularFile(projectRoot.resolve("index.html"))) {
            throw new ResponseStatusException(NOT_FOUND, "静态 POC 不存在或缺少 index.html");
        }
        if (!candidate.startsWith(projectRoot)) {
            throw new ResponseStatusException(BAD_REQUEST, "非法静态预览路径");
        }

        if (Files.isDirectory(candidate)) candidate = candidate.resolve("index.html");
        if (!Files.isRegularFile(candidate)) {
            // 只有无扩展名的前端 history 路由才回退 index.html。
            // js/css/image 请求缺失时必须返回 404，不能把 HTML 当成 JS/CSS 返回。
            String requestedName = candidate.getFileName() == null ? "" : candidate.getFileName().toString();
            if (requestedName.contains(".")) {
                throw new ResponseStatusException(NOT_FOUND, "静态 POC 资源不存在");
            }
            candidate = projectRoot.resolve("index.html");
        }

        try {
            String fileName = candidate.getFileName().toString();
            MediaType mediaType = MediaTypeFactory.getMediaType(fileName)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            String previewBase = PREFIX + UriUtils.encodePathSegment(projectName, StandardCharsets.UTF_8) + "/";

            if (fileName.toLowerCase().endsWith(".html")) {
                String html = Files.readString(candidate, StandardCharsets.UTF_8);
                html = adaptHtml(html, previewBase);
                return ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .contentType(MediaType.TEXT_HTML)
                        .body(html);
            }

            if (fileName.toLowerCase().endsWith(".css")) {
                String css = Files.readString(candidate, StandardCharsets.UTF_8);
                css = css.replace("url(/", "url(" + previewBase)
                        .replace("url('/", "url('" + previewBase)
                        .replace("url(\"/", "url(\"" + previewBase);
                return ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .contentType(MediaType.valueOf("text/css"))
                        .body(css);
            }

            Resource resource = new UrlResource(candidate.toUri());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(mediaType)
                    .body(resource);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(NOT_FOUND, "静态预览文件读取失败");
        }
    }

    private String adaptHtml(String html, String previewBase) {
        String result = html;

        // 相对资源统一以当前 POC 目录为基准。
        if (!result.toLowerCase().contains("<base ")) {
            String base = "<base href=\"" + previewBase + "\">";
            int head = result.toLowerCase().indexOf("<head>");
            if (head >= 0) {
                result = result.substring(0, head + 6) + base + result.substring(head + 6);
            } else {
                result = base + result;
            }
        }

        // Vite/webpack 默认 base=/ 时会产生 /assets/...；在 FDP 子路径预览时改到项目目录。
        String[] attributes = {"src", "href", "action", "poster", "data"};
        for (String attribute : attributes) {
            result = result.replace(attribute + "=\"/", attribute + "=\"" + previewBase)
                    .replace(attribute + "='/", attribute + "='" + previewBase);
        }
        result = result.replace("url(/", "url(" + previewBase)
                .replace("url('/", "url('" + previewBase)
                .replace("url(\"/", "url(\"" + previewBase);
        return result;
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
