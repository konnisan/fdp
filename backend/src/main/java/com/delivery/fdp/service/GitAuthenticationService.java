package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.repository.SourceCredentialRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Service
public class GitAuthenticationService {
    private final RuntimeProperties runtime;
    private final CommandExecutor executor;
    private final SourceCredentialRepository credentials;
    private final CredentialCrypto crypto;

    public GitAuthenticationService(RuntimeProperties runtime,
                                    CommandExecutor executor,
                                    SourceCredentialRepository credentials,
                                    CredentialCrypto crypto) {
        this.runtime = runtime;
        this.executor = executor;
        this.credentials = credentials;
        this.crypto = crypto;
    }

    public CommandExecutor.Result execute(Long credentialId, String command, Path cwd) {
        if (credentialId == null || !runtime.isExecutionEnabled()) {
            return executor.execute(command, cwd);
        }

        SourceCredentialRepository.CredentialSecret stored = credentials.findSecretById(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Source credential not found: " + credentialId));
        String token = crypto.decrypt(stored.encryptedSecret());
        return execute(stored.cloneUsername(), token, command, cwd);
    }

    public CommandExecutor.Result execute(String username, String token, String command, Path cwd) {
        if (!runtime.isExecutionEnabled()) {
            return executor.execute(command, cwd);
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Codeup HTTPS clone username is required");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Codeup personal access token is required");
        }

        Path askPass = null;
        try {
            askPass = Files.createTempFile("fdp-git-askpass-", ".sh");
            Files.writeString(askPass, """
                    #!/bin/sh
                    case "$1" in
                      *Username*) printf '%s\\n' "$FDP_GIT_USERNAME" ;;
                      *Password*) printf '%s\\n' "$FDP_GIT_TOKEN" ;;
                      *) printf '\\n' ;;
                    esac
                    """);
            if (!askPass.toFile().setExecutable(true, true)) {
                throw new IllegalStateException("Unable to make temporary Git credential helper executable");
            }
            Map<String, String> env = new HashMap<>();
            env.put("GIT_ASKPASS", askPass.toAbsolutePath().toString());
            env.put("GIT_TERMINAL_PROMPT", "0");
            env.put("FDP_GIT_USERNAME", username);
            env.put("FDP_GIT_TOKEN", token);

            CommandExecutor.Result result = executor.execute(command, cwd, env);
            return new CommandExecutor.Result(result.exitCode(), redact(result.output(), token));
        } catch (Exception e) {
            return new CommandExecutor.Result(-1, redact(e.getMessage(), token));
        } finally {
            if (askPass != null) {
                try {
                    Files.deleteIfExists(askPass);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String redact(String value, String token) {
        if (value == null) return "";
        if (token == null || token.isEmpty()) return value;
        return value.replace(token, "******");
    }
}
