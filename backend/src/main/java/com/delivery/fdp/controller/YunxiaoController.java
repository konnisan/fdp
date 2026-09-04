package com.delivery.fdp.controller;

import com.delivery.fdp.service.YunxiaoOpenApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/yunxiao")
public class YunxiaoController {
    private final YunxiaoOpenApiService yunxiao;

    public YunxiaoController(YunxiaoOpenApiService yunxiao) {
        this.yunxiao = yunxiao;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return yunxiao.status();
    }

    @GetMapping("/pipelines")
    public List<Map<String, Object>> pipelines(
            @RequestParam(required = false) String pipelineName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage) {
        return yunxiao.pipelines(pipelineName, page, perPage);
    }

    @PostMapping("/pipelines/{pipelineId}/runs")
    public Map<String, Object> runPipeline(
            @PathVariable String pipelineId,
            @RequestBody(required = false) RunPipelineRequest request) {
        return yunxiao.createPipelineRun(pipelineId, request == null ? null : request.params());
    }

    @GetMapping("/pipelines/{pipelineId}/runs")
    public List<Map<String, Object>> pipelineRuns(
            @PathVariable String pipelineId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage) {
        return yunxiao.pipelineRuns(pipelineId, status, page, perPage);
    }

    @GetMapping("/pipelines/{pipelineId}/runs/{runId}")
    public Map<String, Object> pipelineRun(
            @PathVariable String pipelineId,
            @PathVariable String runId) {
        return yunxiao.pipelineRun(pipelineId, runId);
    }

    @GetMapping("/packages/repositories")
    public List<Map<String, Object>> repositories(
            @RequestParam(required = false, defaultValue = "GENERIC") String repoTypes,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage) {
        return yunxiao.repositories(repoTypes, page, perPage);
    }

    @GetMapping("/packages/repositories/{repoId}/artifacts")
    public List<Map<String, Object>> artifacts(
            @PathVariable String repoId,
            @RequestParam(required = false, defaultValue = "GENERIC") String repoType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage) {
        return yunxiao.artifacts(repoId, repoType, search, page, perPage);
    }

    public record RunPipelineRequest(String params) {}
}
