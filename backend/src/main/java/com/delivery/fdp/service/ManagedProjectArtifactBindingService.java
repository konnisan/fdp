package com.delivery.fdp.service;

import com.delivery.fdp.dto.ManagedProjectRequest;
import com.delivery.fdp.repository.ManagedProjectRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ManagedProjectArtifactBindingService {
    private static final Pattern SAFE_RELATIVE = Pattern.compile("^[A-Za-z0-9._/-]*$");

    private final JdbcTemplate jdbc;
    private final ManagedProjectRepository projects;

    public ManagedProjectArtifactBindingService(JdbcTemplate jdbc, ManagedProjectRepository projects) {
        this.jdbc = jdbc;
        this.projects = projects;
    }

    @Transactional
    public void replacePreservingVersions(Long projectId,
                                          List<ManagedProjectRequest.ArtifactBindingRequest> requested) {
        projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));
        List<ManagedProjectRequest.ArtifactBindingRequest> artifacts = requested == null ? List.of() : requested;

        Map<String, ManagedProjectRepository.ArtifactBinding> existing = new LinkedHashMap<>();
        for (ManagedProjectRepository.ArtifactBinding binding : projects.artifacts(projectId)) {
            existing.put(key(binding.repositoryId(), binding.artifactName()), binding);
        }

        Map<String, Boolean> retained = new LinkedHashMap<>();
        int order = 0;
        for (ManagedProjectRequest.ArtifactBindingRequest artifact : artifacts) {
            String repositoryId = trim(artifact.getRepositoryId());
            String repositoryName = trim(artifact.getRepositoryName());
            String artifactName = trim(artifact.getArtifactName());
            String targetDirectory = cleanRelative(artifact.getTargetDirectory());
            if (!StringUtils.hasText(repositoryId) || !StringUtils.hasText(artifactName)) {
                throw new IllegalArgumentException("每个制品都需要 repositoryId 和 artifactName");
            }
            String key = key(repositoryId, artifactName);
            if (retained.put(key, true) != null) {
                throw new IllegalArgumentException("项目中不能重复绑定同一个制品: " + artifactName);
            }
            int sortOrder = artifact.getSortOrder() == null ? order : artifact.getSortOrder();
            ManagedProjectRepository.ArtifactBinding old = existing.get(key);
            if (old == null) {
                jdbc.update("""
                        INSERT INTO managed_project_artifact
                        (project_id,repository_id,repository_name,artifact_name,target_directory,sort_order)
                        VALUES(?,?,?,?,?,?)
                        """, projectId, repositoryId, blank(repositoryName), artifactName, targetDirectory, sortOrder);
            } else {
                jdbc.update("""
                        UPDATE managed_project_artifact
                           SET repository_name=?,target_directory=?,sort_order=?
                         WHERE id=? AND project_id=?
                        """, blank(repositoryName), targetDirectory, sortOrder, old.id(), projectId);
            }
            order++;
        }

        for (Map.Entry<String, ManagedProjectRepository.ArtifactBinding> entry : existing.entrySet()) {
            if (!retained.containsKey(entry.getKey())) {
                jdbc.update("DELETE FROM managed_project_artifact WHERE id=? AND project_id=?",
                        entry.getValue().id(), projectId);
            }
        }

        refreshVersionSummaries(projectId);
    }

    private void refreshVersionSummaries(Long projectId) {
        List<String> deployed = new ArrayList<>();
        List<String> running = new ArrayList<>();
        for (ManagedProjectRepository.ArtifactBinding binding : projects.artifacts(projectId)) {
            if (StringUtils.hasText(binding.deployedVersion())) {
                deployed.add(binding.artifactName() + "=" + binding.deployedVersion());
            }
            if (StringUtils.hasText(binding.runningVersion())) {
                running.add(binding.artifactName() + "=" + binding.runningVersion());
            }
        }
        jdbc.update("""
                UPDATE managed_project
                   SET deployed_version_summary=?,running_version_summary=?,update_time=NOW()
                 WHERE id=?
                """, blank(String.join(", ", deployed)), blank(String.join(", ", running)), projectId);
    }

    private String key(String repositoryId, String artifactName) {
        String repo = String.valueOf(repositoryId);
        return repo.length() + ":" + repo + String.valueOf(artifactName);
    }

    private String cleanRelative(String value) {
        String path = value == null ? "." : value.trim().replace('\\', '/');
        if (path.isEmpty()) return ".";
        if (path.startsWith("/") || path.contains("..") || !SAFE_RELATIVE.matcher(path).matches()) {
            throw new IllegalArgumentException("相对路径非法: " + value);
        }
        return path;
    }

    private String trim(String value) { return value == null ? null : value.trim(); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
