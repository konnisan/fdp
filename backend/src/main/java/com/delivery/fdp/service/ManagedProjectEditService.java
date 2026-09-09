package com.delivery.fdp.service;

import com.delivery.fdp.dto.ManagedProjectRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

@Service
public class ManagedProjectEditService {
    private final TransactionTemplate transactions;
    private final ManagedProjectArtifactBindingService artifacts;
    private final ManagedProjectConfigService config;
    private final ManagedProjectService projects;

    public ManagedProjectEditService(TransactionTemplate transactions,
                                     ManagedProjectArtifactBindingService artifacts,
                                     ManagedProjectConfigService config,
                                     ManagedProjectService projects) {
        this.transactions = transactions;
        this.artifacts = artifacts;
        this.config = config;
        this.projects = projects;
    }

    /**
     * Configuration and artifact bindings are one logical project edit.
     * Both database changes participate in the same transaction, so a Packages
     * binding failure cannot leave runtime configuration half-saved.
     */
    public Map<String, Object> update(Long id, ManagedProjectRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        transactions.executeWithoutResult(status -> {
            artifacts.replacePreservingVersions(id, request.getArtifacts());
            config.updateRuntimeConfig(id, request);
        });
        return projects.project(id);
    }
}
