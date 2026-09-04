package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PlatformRuntimeService {
    private final RuntimeProperties props;

    public PlatformRuntimeService(RuntimeProperties props) {
        this.props = props;
    }

    public Map<String, Object> status() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("Git", "git", "--version", true));
        tools.add(tool("Docker", "docker", "--version", true));
        tools.add(tool("Docker daemon", "docker", "info", true));
        tools.add(tool("Nginx", "nginx", "-v", true));
        tools.add(tool("rsync", "rsync", "--version", true));
        tools.add(tool("curl", "curl", "--version", true));
        tools.add(tool("Java", "java", "-version", true));

        boolean liveReady = !windows && props.isExecutionEnabled()
                && tools.stream().filter(t -> Boolean.TRUE.equals(t.get("required")))
                .allMatch(t -> Boolean.TRUE.equals(t.get("available")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("os", System.getProperty("os.name"));
        result.put("osVersion", System.getProperty("os.version"));
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("executionEnabled", props.isExecutionEnabled());
        result.put("executionMode", props.isExecutionEnabled() ? "LIVE" : "DRY_RUN");
        result.put("linux", !windows);
        result.put("liveReady", liveReady);
        result.put("publicPort", props.getNginxPublicPort());
        result.put("workspaceRoot", props.getWorkspaceRoot());
        result.put("staticRoot", props.getStaticRoot());
        result.put("dataRoot", props.getDataRoot());
        result.put("artifactRoot", props.getArtifactRoot());
        result.put("nginxConfigFile", props.getNginxConfigFile());
        result.put("resolvedWorkspaceRoot", absolute(props.getWorkspaceRoot()));
        result.put("resolvedStaticRoot", absolute(props.getStaticRoot()));
        result.put("resolvedDataRoot", absolute(props.getDataRoot()));
        result.put("resolvedArtifactRoot", absolute(props.getArtifactRoot()));
        result.put("resolvedNginxConfigFile", absolute(props.getNginxConfigFile()));
        result.put("tools", tools);
        return result;
    }

    private String absolute(String value) {
        if (value == null || value.isBlank()) return "";
        return Path.of(value).toAbsolutePath().normalize().toString();
    }

    private Map<String, Object> tool(String name, String command, String argument, boolean required) {
        Probe probe = probe(command, argument);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("available", probe.available());
        item.put("detail", probe.detail());
        item.put("required", required);
        return item;
    }

    private Probe probe(String command, String argument) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command, argument);
            builder.redirectErrorStream(true);
            process = builder.start();
            boolean finished = process.waitFor(4, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Probe(false, "timeout after 4s");
            }
            String detail = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace('\r', ' ').replace('\n', ' ').trim().replaceAll("\\s+", " ");
            int code = process.exitValue();
            if (detail.length() > 220) detail = detail.substring(0, 220) + "...";
            return new Probe(code == 0, detail.isBlank() ? "exit=" + code : detail);
        } catch (Exception e) {
            if (process != null && process.isAlive()) process.destroyForcibly();
            return new Probe(false, e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "unavailable" : e.getMessage()));
        }
    }

    private record Probe(boolean available, String detail) {}
}
