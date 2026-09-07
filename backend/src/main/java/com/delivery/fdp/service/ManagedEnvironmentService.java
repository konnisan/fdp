package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.repository.ArtifactDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ManagedEnvironmentService {
    private static final Pattern KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final int MAX_SIZE = 128 * 1024;

    private final ArtifactDeliveryRepository projects;
    private final CredentialCrypto crypto;
    private final RuntimeProperties runtime;

    public ManagedEnvironmentService(ArtifactDeliveryRepository projects,
                                     CredentialCrypto crypto,
                                     RuntimeProperties runtime) {
        this.projects = projects;
        this.crypto = crypto;
        this.runtime = runtime;
    }

    public void save(Long projectId, String content) {
        project(projectId);
        if (content == null) return;
        String normalized = normalize(content);
        validate(normalized);
        if (!StringUtils.hasText(normalized)) {
            projects.updateEnvironmentCiphertext(projectId, null);
            return;
        }
        if (!crypto.configured()) {
            throw new IllegalStateException("Docker 环境变量需要加密保存。请先为 FDP 配置一次 FDP_CREDENTIAL_KEY，然后即可直接在页面维护项目环境变量");
        }
        projects.updateEnvironmentCiphertext(projectId, crypto.encrypt(normalized));
    }

    public String load(Long projectId) {
        project(projectId);
        return projects.environmentCiphertext(projectId)
                .map(value -> {
                    if (!crypto.configured()) {
                        throw new IllegalStateException("FDP_CREDENTIAL_KEY 未配置，无法读取已加密的 Docker 环境变量");
                    }
                    return crypto.decrypt(value);
                })
                .orElse("");
    }

    public Map<String, Object> info(Long projectId) {
        String content = load(projectId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("configured", StringUtils.hasText(content));
        result.put("variableCount", variableCount(content));
        result.put("content", content);
        result.put("managed", true);
        return result;
    }

    /**
     * Materialize the encrypted environment into an FDP-owned env file immediately before docker run.
     * The user never needs to create this file manually.
     */
    public Path materialize(ArtifactDeliveryRepository.Project project) throws Exception {
        String content = load(project.id());
        if (!StringUtils.hasText(content)) return null;

        Path root = Path.of(runtime.getEnvRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path file = root.resolve(safe(project.projectCode()) + ".env").normalize();
        if (!file.startsWith(root)) throw new IllegalStateException("FDP 环境变量文件路径非法");
        Files.writeString(file, content.endsWith("\n") ? content : content + "\n", StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows development filesystem has no POSIX permissions; deployment itself remains DRY-RUN there.
        }
        return file;
    }

    public boolean configured(Long projectId) {
        project(projectId);
        return projects.hasManagedEnvironment(projectId);
    }

    public int variableCount(Long projectId) {
        return variableCount(load(projectId));
    }

    private int variableCount(String content) {
        if (!StringUtils.hasText(content)) return 0;
        int count = 0;
        for (String line : content.split("\\n", -1)) {
            String value = line.trim();
            if (!value.isEmpty() && !value.startsWith("#")) count++;
        }
        return count;
    }

    private String normalize(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private void validate(String content) {
        if (content.indexOf('\0') >= 0) throw new IllegalArgumentException("环境变量中不能包含 NUL 字符");
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_SIZE) {
            throw new IllegalArgumentException("Docker 环境变量内容不能超过 128KB");
        }
        int lineNo = 0;
        for (String raw : content.split("\\n", -1)) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int equals = line.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("环境变量第 " + lineNo + " 行格式错误，应为 KEY=VALUE");
            }
            String key = line.substring(0, equals).trim();
            if (!KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("环境变量第 " + lineNo + " 行变量名非法: " + key);
            }
        }
    }

    private ArtifactDeliveryRepository.Project project(Long id) {
        return projects.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("容器部署不存在: " + id));
    }

    private String safe(String value) {
        String result = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!StringUtils.hasText(result) || ".".equals(result) || "..".equals(result)) {
            throw new IllegalStateException("非法环境变量文件标识");
        }
        return result;
    }
}
