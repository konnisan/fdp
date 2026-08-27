package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import com.delivery.fdp.model.PocProject;
import com.delivery.fdp.repository.PocProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeploymentPlanService {
    private final RuntimeProperties props;
    private final PocProjectRepository projects;

    public DeploymentPlanService(RuntimeProperties props, PocProjectRepository projects) {
        this.props = props;
        this.projects = projects;
    }

    public Map<String, Object> plan(Long projectId) {
        PocProject p = projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery project not found: " + projectId));

        Path workspace = Path.of(props.getWorkspaceRoot()).toAbsolutePath().normalize().resolve(p.getProjectCode()).normalize();
        Path projectRoot = workspace.resolve(p.getProjectDirectory()).normalize();
        List<Map<String, String>> steps = new ArrayList<>();
        steps.add(step("PREPARE", "准备工作目录", workspace.toString()));
        steps.add(step("GIT_SYNC", "同步 Codeup 源码", p.getGitBranch() + " @ " + p.getGitUrl()));

        if ("STATIC".equals(p.getProjectType())) {
            steps.add(step("STATIC_BUILD", "构建静态资源", StringUtils.hasText(p.getBuildCommand()) ? p.getBuildCommand() : "SKIPPED"));
            steps.add(step("STATIC_PUBLISH", "发布静态资源", projectRoot.resolve(p.getBuildOutput()).normalize().toString()));
        } else {
            String image = p.getImageName() + ":<commit>";
            steps.add(step("DOCKER_BUILD", "构建 Docker 镜像",
                    "docker build -t " + image + " -f " + p.getDockerfilePath() + " " + p.getDockerBuildContext()));
            String run = "docker run -d --name " + p.getContainerName()
                    + " --restart unless-stopped --cpus " + p.getCpuLimit()
                    + " --memory " + p.getMemoryLimit()
                    + " -p 127.0.0.1:" + p.getHostPort() + ":" + p.getContainerPort();
            if (StringUtils.hasText(p.getHostDataPath())) run += " -v " + p.getHostDataPath() + ":" + p.getContainerDataPath();
            run += " " + image;
            steps.add(step("CONTAINER_REPLACE", "替换 Docker 容器", run));
        }
        steps.add(step("ROUTE", "更新 Nginx 路由", p.getPreviewPath()));
        steps.add(step("VERIFY", "运行校验", StringUtils.hasText(p.getHealthCheckPath()) ? p.getHealthCheckPath() : "runtime state"));

        List<String> warnings = new ArrayList<>();
        if (!props.isExecutionEnabled()) warnings.add("当前为 DRY_RUN，部署会生成 Task/Step/Log，但不会真正执行 Git、Docker、Nginx 命令。");
        if ("CONTAINER".equals(p.getProjectType()) && !StringUtils.hasText(p.getHealthCheckPath())) warnings.add("未配置 HTTP Health Check，VERIFY 只检查容器是否处于 Running 状态。");
        if ("CONTAINER".equals(p.getProjectType()) && !StringUtils.hasText(p.getHostDataPath())) warnings.add("未配置持久化 Volume；容器内产生的数据会随容器替换而丢失。");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", p.getId());
        result.put("projectName", p.getProjectName());
        result.put("projectType", p.getProjectType());
        result.put("mode", props.isExecutionEnabled() ? "LIVE" : "DRY_RUN");
        result.put("workspace", workspace.toString());
        result.put("projectRoot", projectRoot.toString());
        result.put("previewPath", p.getPreviewPath());
        result.put("steps", steps);
        result.put("warnings", warnings);
        return result;
    }

    private Map<String, String> step(String code, String name, String detail) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("name", name);
        item.put("detail", detail);
        return item;
    }
}
