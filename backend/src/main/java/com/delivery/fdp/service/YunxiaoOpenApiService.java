package com.delivery.fdp.service;

import com.delivery.fdp.config.YunxiaoProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class YunxiaoOpenApiService {
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};

    private final YunxiaoProperties props;
    private final RestClient http;

    public YunxiaoOpenApiService(YunxiaoProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.build();
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
