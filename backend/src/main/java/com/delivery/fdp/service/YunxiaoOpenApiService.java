package com.delivery.fdp.service;

import com.delivery.fdp.config.YunxiaoProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class YunxiaoOpenApiService {
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};

    private final YunxiaoProperties props;
    private final RestClient http;
    private final ObjectMapper json;

    public YunxiaoOpenApiService(YunxiaoProperties props, RestClient.Builder builder, ObjectMapper json) {
        this.props = props;
        this.http = builder.build();
        this.json = json;
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", props.isEnabled());
        result.put("configured", configured());
        result.put("domain", props.getDomain());
        result.put("organizationId", props.getOrganizationId());
        result.put("tokenConfigured", StringUtils.hasText(props.getToken()));
        result.put("mode", "CENTER");
        return result;
    }

    public List<Map<String, Object>> pipelines(String pipelineName, Integer page, Integer perPage) {
        validate();
        UriComponentsBuilder uri = base("/oapi/v1/flow/organizations/" + segment(props.getOrganizationId()) + "/pipelines")
                .queryParam("page", pageValue(page))
                .queryParam("perPage", pageSize(perPage));
        if (StringUtils.hasText(pipelineName)) uri.queryParam("pipelineName", pipelineName.trim());
        return getList(uri.build().encode().toUri());
    }

    public List<Map<String, Object>> pipelineRuns(String pipelineId,
                                                  String status,
                                                  Integer page,
                                                  Integer perPage) {
        validate();
        requireId(pipelineId, "pipelineId");
        UriComponentsBuilder uri = base("/oapi/v1/flow/organizations/" + segment(props.getOrganizationId())
                + "/pipelines/" + segment(pipelineId) + "/runs")
                .queryParam("page", pageValue(page))
                .queryParam("perPage", pageSize(perPage));
        if (StringUtils.hasText(status)) uri.queryParam("status", status.trim().toUpperCase());
        return getList(uri.build().encode().toUri());
    }

    public Map<String, Object> pipelineRun(String pipelineId, String runId) {
        validate();
        requireId(pipelineId, "pipelineId");
        requireId(runId, "pipelineRunId");
        URI uri = base("/oapi/v1/flow/organizations/" + segment(props.getOrganizationId())
                + "/pipelines/" + segment(pipelineId) + "/runs/" + segment(runId))
                .build().encode().toUri();
        return getMap(uri);
    }

    /**
     * Return deployable Packages artifacts produced by successful Flow runs.
     * The first row is the recommended release (latest successful run that emitted the configured artifact).
     */
    public List<Map<String, Object>> releaseCandidates(String pipelineId, String repoId, String artifactName) {
        validate();
        requireId(pipelineId, "pipelineId");
        if (!StringUtils.hasText(repoId)) throw new IllegalArgumentException("repoId is required");
        if (!StringUtils.hasText(artifactName)) throw new IllegalArgumentException("artifactName is required");

        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> runs = pipelineRuns(pipelineId, "SUCCESS", 1, 20);
        for (Map<String, Object> run : runs) {
            String runId = firstText(run, "pipelineRunId", "id");
            if (!StringUtils.hasText(runId)) continue;
            Map<String, Object> detail = pipelineRun(pipelineId, runId);
            Map<String, Object> artifact = findPackageArtifact(detail, repoId, artifactName);
            if (artifact == null) continue;
            result.add(release(detail, artifact, false));
        }
        if (!result.isEmpty()) result.get(0).put("recommended", true);
        return result;
    }

    public Map<String, Object> releaseCandidate(String pipelineId,
                                                String runId,
                                                String repoId,
                                                String artifactName) {
        Map<String, Object> detail = pipelineRun(pipelineId, runId);
        String status = firstText(detail, "status");
        if (!"SUCCESS".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Only successful Flow runs can be deployed: run #" + runId);
        }
        Map<String, Object> artifact = findPackageArtifact(detail, repoId, artifactName);
        if (artifact == null) {
            throw new IllegalStateException("Flow run #" + runId + " does not contain Packages artifact '"
                    + artifactName + "' in repository '" + repoId + "'");
        }
        return release(detail, artifact, false);
    }

    public List<Map<String, Object>> repositories(String repoTypes,
                                                  Integer page,
                                                  Integer perPage) {
        validate();
        UriComponentsBuilder uri = base("/oapi/v1/packages/organizations/" + segment(props.getOrganizationId())
                + "/repositories")
                .queryParam("page", pageValue(page))
                .queryParam("perPage", pageSize(perPage));
        if (StringUtils.hasText(repoTypes)) uri.queryParam("repoTypes", repoTypes.trim().toUpperCase());
        return getList(uri.build().encode().toUri());
    }

    public List<Map<String, Object>> artifacts(String repoId,
                                               String repoType,
                                               String search,
                                               Integer page,
                                               Integer perPage) {
        validate();
        requireId(repoId, "repoId");
        String type = StringUtils.hasText(repoType) ? repoType.trim().toUpperCase() : "GENERIC";
        UriComponentsBuilder uri = base("/oapi/v1/packages/organizations/" + segment(props.getOrganizationId())
                + "/repositories/" + segment(repoId) + "/artifacts")
                .queryParam("repoType", type)
                .queryParam("page", pageValue(page))
                .queryParam("perPage", pageSize(perPage))
                .queryParam("orderBy", "latestUpdate")
                .queryParam("sort", "desc");
        if (StringUtils.hasText(search)) uri.queryParam("search", search.trim());
        return getList(uri.build().encode().toUri());
    }

    private Map<String, Object> release(Map<String, Object> detail,
                                        Map<String, Object> artifact,
                                        boolean recommended) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("runId", firstText(detail, "pipelineRunId", "id"));
        row.put("status", firstText(detail, "status"));
        row.put("triggerMode", detail.get("triggerMode"));
        row.put("createTime", detail.get("createTime"));
        row.put("updateTime", detail.get("updateTime"));
        row.put("artifactName", firstText(artifact, "artifact", "artifactName", "name"));
        row.put("repoId", firstText(artifact, "repoId", "repositoryId"));
        row.put("version", firstText(artifact, "version"));
        row.put("downloadUrl", firstText(artifact, "downloadUrl"));
        row.put("md5", firstText(artifact, "md5"));
        row.put("recommended", recommended);
        return row;
    }

    private Map<String, Object> findPackageArtifact(Map<String, Object> run,
                                                    String repoId,
                                                    String artifactName) {
        for (Map<String, Object> artifact : runArtifacts(run)) {
            String type = firstText(artifact, "type");
            String candidateRepo = firstText(artifact, "repoId", "repositoryId");
            String candidateName = firstText(artifact, "artifact", "artifactName", "name");
            if (!"packages".equalsIgnoreCase(type)) continue;
            if (!repoId.trim().equals(candidateRepo)) continue;
            if (!artifactName.trim().equals(candidateName)) continue;
            if (!StringUtils.hasText(firstText(artifact, "version"))) continue;
            if (!StringUtils.hasText(firstText(artifact, "downloadUrl"))) continue;
            return artifact;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> runArtifacts(Map<String, Object> run) {
        List<Map<String, Object>> artifacts = new ArrayList<>();
        Object stagesValue = run.get("stages");
        if (!(stagesValue instanceof List<?> stages)) return artifacts;
        for (Object stageValue : stages) {
            if (!(stageValue instanceof Map<?, ?> stage)) continue;
            Object stageInfoValue = stage.get("stageInfo");
            if (!(stageInfoValue instanceof Map<?, ?> stageInfo)) continue;
            Object jobsValue = stageInfo.get("jobs");
            if (!(jobsValue instanceof List<?> jobs)) continue;
            for (Object jobValue : jobs) {
                if (!(jobValue instanceof Map<?, ?> job)) continue;
                Object resultValue = job.get("result");
                if (!(resultValue instanceof String resultText) || !StringUtils.hasText(resultText)) continue;
                try {
                    Map<String, Object> result = json.readValue(resultText, JSON_MAP);
                    Object dataValue = result.get("data");
                    if (!(dataValue instanceof Map<?, ?> data)) continue;
                    collectArtifacts(data.get("ARTIFACTSV2"), artifacts);
                    collectArtifacts(data.get("ARTIFACTS"), artifacts);
                } catch (Exception ignored) {
                    // A non-artifact job result must not make the whole pipeline unreadable.
                }
            }
        }
        return artifacts;
    }

    @SuppressWarnings("unchecked")
    private void collectArtifacts(Object value, List<Map<String, Object>> target) {
        if (value == null) return;
        if (value instanceof String text) {
            if (!StringUtils.hasText(text)) return;
            try {
                collectArtifacts(json.readValue(text, Object.class), target);
            } catch (Exception ignored) {
                // Ignore values that are plain strings rather than embedded JSON.
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) collectArtifacts(item, target);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> candidate = new LinkedHashMap<>();
            map.forEach((key, item) -> candidate.put(String.valueOf(key), item));
            if (StringUtils.hasText(firstText(candidate, "type"))
                    && StringUtils.hasText(firstText(candidate, "artifact", "artifactName", "name"))) {
                target.add(candidate);
                return;
            }
            for (Object item : map.values()) collectArtifacts(item, target);
        }
    }

    private String firstText(Map<String, Object> map, String... keys) {
        if (map == null) return "";
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) return String.valueOf(value).trim();
        }
        return "";
    }

    private List<Map<String, Object>> getList(URI uri) {
        try {
            List<Map<String, Object>> body = http.get()
                    .uri(uri)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header("x-yunxiao-token", props.getToken())
                    .retrieve()
                    .body(LIST_OF_MAPS);
            return body == null ? List.of() : body;
        } catch (RestClientResponseException e) {
            throw apiFailure(e);
        }
    }

    private Map<String, Object> getMap(URI uri) {
        try {
            Map<String, Object> body = http.get()
                    .uri(uri)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header("x-yunxiao-token", props.getToken())
                    .retrieve()
                    .body(MAP);
            return body == null ? Map.of() : body;
        } catch (RestClientResponseException e) {
            throw apiFailure(e);
        }
    }

    private IllegalStateException apiFailure(RestClientResponseException e) {
        String response = e.getResponseBodyAsString();
        if (response != null && response.length() > 800) response = response.substring(0, 800);
        return new IllegalStateException("Yunxiao OpenAPI request failed (HTTP " + e.getStatusCode().value()
                + "): " + (response == null || response.isBlank() ? e.getMessage() : response), e);
    }

    private UriComponentsBuilder base(String path) {
        String domain = props.getDomain() == null ? "" : props.getDomain().trim();
        String base = domain.startsWith("http://") || domain.startsWith("https://")
                ? domain
                : "https://" + domain;
        return UriComponentsBuilder.fromHttpUrl(base).path(path);
    }

    private boolean configured() {
        return props.isEnabled()
                && StringUtils.hasText(props.getDomain())
                && StringUtils.hasText(props.getOrganizationId())
                && StringUtils.hasText(props.getToken());
    }

    private void validate() {
        if (!props.isEnabled()) {
            throw new IllegalStateException("云效 Flow / Packages 集成未启用，请设置 FDP_YUNXIAO_ENABLED=true");
        }
        if (!configured()) {
            throw new IllegalStateException("请配置 FDP_YUNXIAO_DOMAIN / ORGANIZATION_ID / TOKEN");
        }
    }

    private int pageValue(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private int pageSize(Integer value) {
        int size = value == null || value < 1 ? props.getPageSize() : value;
        return Math.max(1, Math.min(size, 30));
    }

    private void requireId(String value, String field) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(field + " is required");
    }

    private String segment(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.matches("^[A-Za-z0-9._-]+$")) {
            throw new IllegalArgumentException("Invalid Yunxiao identifier: " + text);
        }
        return text;
    }
}
