package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.dto.GitConnectionTestRequest;
import com.delivery.fdp.dto.SourceCredentialRequest;
import com.delivery.fdp.model.SourceCredential;
import com.delivery.fdp.repository.SourceCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SourceCredentialService {
    private final SourceCredentialRepository repository;
    private final CredentialCrypto crypto;
    private final GitAuthenticationService git;
    private final RuntimeProperties runtime;

    public SourceCredentialService(SourceCredentialRepository repository,
                                   CredentialCrypto crypto,
                                   GitAuthenticationService git,
                                   RuntimeProperties runtime) {
        this.repository = repository;
        this.crypto = crypto;
        this.git = git;
        this.runtime = runtime;
    }

    public List<SourceCredential> list() {
        return repository.findAll();
    }

    public SourceCredential create(SourceCredentialRequest request) {
        normalize(request);
        validate(request, null, true);
        String encrypted = crypto.encrypt(request.getToken());
        return get(repository.create(request.getName(), request.getProvider(), request.getCloneUsername(), encrypted));
    }

    public SourceCredential update(Long id, SourceCredentialRequest request) {
        get(id);
        normalize(request);
        validate(request, id, false);
        String encrypted = StringUtils.hasText(request.getToken()) ? crypto.encrypt(request.getToken()) : null;
        repository.update(id, request.getName(), request.getProvider(), request.getCloneUsername(), encrypted);
        return get(id);
    }

    public void delete(Long id) {
        get(id);
        if (repository.countProjectsUsing(id) > 0) {
            throw new IllegalStateException("Credential is still used by one or more delivery projects");
        }
        repository.delete(id);
    }

    public Map<String, Object> test(Long id, GitConnectionTestRequest request) {
        get(id);
        if (!StringUtils.hasText(request.getGitUrl())) throw new IllegalArgumentException("gitUrl is required");
        String branch = StringUtils.hasText(request.getGitBranch()) ? request.getGitBranch().trim() : "develop";
        validateGitUrl(request.getGitUrl());

        String command = "git ls-remote --heads " + sh(request.getGitUrl().trim()) + " " + sh("refs/heads/" + branch);
        CommandExecutor.Result result = git.execute(id, command, Path.of(".").toAbsolutePath().normalize());

        if (!runtime.isExecutionEnabled()) {
            return Map.of(
                    "status", "DRY_RUN",
                    "message", "FDP_EXECUTION_ENABLED=false，已完成命令安全校验，但未实际访问 Codeup。",
                    "head", ""
            );
        }

        if (!result.success()) {
            String message = cleanResult(result.output(), "Codeup Git authentication failed");
            repository.updateTestResult(id, "FAILED", message);
            return Map.of("status", "FAILED", "message", message, "head", "");
        }

        String output = result.output() == null ? "" : result.output().trim();
        if (output.isEmpty()) {
            String message = "Codeup repository is reachable, but branch '" + branch + "' was not found";
            repository.updateTestResult(id, "FAILED", message);
            return Map.of("status", "FAILED", "message", message, "head", "");
        }

        String head = output.split("\\s+")[0];
        String message = "Codeup repository access succeeded";
        repository.updateTestResult(id, "AVAILABLE", message);
        return Map.of("status", "AVAILABLE", "message", message, "head", head);
    }

    public SourceCredential get(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Source credential not found: " + id));
    }

    private void normalize(SourceCredentialRequest request) {
        if (request.getName() != null) request.setName(request.getName().trim());
        if (!StringUtils.hasText(request.getProvider())) request.setProvider("CODEUP");
        request.setProvider(request.getProvider().trim().toUpperCase(Locale.ROOT));
        if (request.getCloneUsername() != null) request.setCloneUsername(request.getCloneUsername().trim());
    }

    private void validate(SourceCredentialRequest request, Long id, boolean tokenRequired) {
        if (!StringUtils.hasText(request.getName())) throw new IllegalArgumentException("Credential name is required");
        if (!"CODEUP".equals(request.getProvider())) throw new IllegalArgumentException("Only CODEUP credentials are supported in V1");
        if (!StringUtils.hasText(request.getCloneUsername())) throw new IllegalArgumentException("Codeup HTTPS clone username is required");
        if (tokenRequired && !StringUtils.hasText(request.getToken())) throw new IllegalArgumentException("Codeup token is required");
        if (repository.countByName(request.getName(), id) > 0) throw new IllegalArgumentException("Credential name already exists");
        if (!crypto.configured()) {
            throw new IllegalStateException("FDP_CREDENTIAL_KEY is not configured. Generate one with: openssl rand -base64 32");
        }
    }

    private void validateGitUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (!url.matches("^https?://.+")) throw new IllegalArgumentException("Credential test currently requires an HTTPS Git URL");
        if (url.contains("\n") || url.contains("\r") || url.contains("\0")) throw new IllegalArgumentException("Invalid gitUrl");
        int scheme = url.indexOf("://");
        int at = url.indexOf('@', scheme + 3);
        int slash = url.indexOf('/', scheme + 3);
        if (at >= 0 && (slash < 0 || at < slash)) {
            throw new IllegalArgumentException("Do not embed username or token in gitUrl; use Source Credential instead");
        }
    }

    private String sh(String value) {
        return ShellCommandSupport.quote(value);
    }

    private String cleanResult(String value, String fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        String result = value.trim();
        return result.length() <= 500 ? result : result.substring(0, 500);
    }
}
