package com.delivery.fdp.service;

import com.delivery.fdp.dto.PocProjectRequest;
import com.delivery.fdp.model.PocProject;
import com.delivery.fdp.repository.PocProjectRepository;
import com.delivery.fdp.repository.SourceCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@Service
public class ProjectService {
    private final PocProjectRepository repo;
    private final SourceCredentialRepository credentials;

    public ProjectService(PocProjectRepository repo, SourceCredentialRepository credentials){
        this.repo=repo;
        this.credentials=credentials;
    }

    public List<PocProject> list(){return repo.findAll();}
    public PocProject get(Long id){return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Delivery project not found: " + id));}
    public PocProject create(PocProjectRequest r){normalize(r);validate(r,null);return get(repo.create(r));}
    public PocProject update(Long id,PocProjectRequest r){get(id);normalize(r);validate(r,id);repo.update(id,r);return get(id);}
    public void delete(Long id){PocProject p=get(id);if("RUNNING".equals(p.getStatus()))throw new IllegalStateException("Stop the container before deleting the project.");repo.delete(id);}

    private void normalize(PocProjectRequest r){
        if(r.getProjectCode()!=null)r.setProjectCode(r.getProjectCode().trim().toLowerCase(Locale.ROOT));
        if(r.getGitUrl()!=null)r.setGitUrl(r.getGitUrl().trim());
        if(!StringUtils.hasText(r.getGitBranch()))r.setGitBranch("develop");
        if(r.getProjectType()!=null)r.setProjectType(r.getProjectType().trim().toUpperCase(Locale.ROOT));
        if(!StringUtils.hasText(r.getProjectDirectory()))r.setProjectDirectory(".");
        if(!StringUtils.hasText(r.getBuildOutput()))r.setBuildOutput("dist");
        if(!StringUtils.hasText(r.getDockerfilePath()))r.setDockerfilePath("Dockerfile");
        if(!StringUtils.hasText(r.getDockerBuildContext()))r.setDockerBuildContext(".");
        if(StringUtils.hasText(r.getPreviewPath())){
            String p=r.getPreviewPath().trim();
            if(!p.startsWith("/"))p="/"+p;
            while(p.length()>1&&p.endsWith("/"))p=p.substring(0,p.length()-1);
            r.setPreviewPath(p);
        }
        if("CONTAINER".equals(r.getProjectType())){
            if(!StringUtils.hasText(r.getImageName())&&StringUtils.hasText(r.getProjectCode()))r.setImageName("fdp/"+r.getProjectCode());
            if(!StringUtils.hasText(r.getContainerName())&&StringUtils.hasText(r.getProjectCode()))r.setContainerName("fdp-"+r.getProjectCode());
            if(r.getContainerPort()==null)r.setContainerPort(3000);
            if(!StringUtils.hasText(r.getCpuLimit()))r.setCpuLimit("1");
            if(!StringUtils.hasText(r.getMemoryLimit()))r.setMemoryLimit("512m");
        }else{
            r.setHostPort(null);
            r.setContainerPort(null);
        }
    }

    private void validate(PocProjectRequest r,Long id){
        if(!StringUtils.hasText(r.getProjectCode())||!r.getProjectCode().matches("^[a-z0-9][a-z0-9-]{1,49}$"))throw new IllegalArgumentException("Invalid projectCode");
        if(!StringUtils.hasText(r.getProjectName())||!StringUtils.hasText(r.getGitUrl()))throw new IllegalArgumentException("projectName and gitUrl are required");
        validateGitSource(r);
        if(!"STATIC".equals(r.getProjectType())&&!"CONTAINER".equals(r.getProjectType()))throw new IllegalArgumentException("projectType must be STATIC or CONTAINER");
        validateRelativePath(r.getProjectDirectory(),"projectDirectory");
        if(!StringUtils.hasText(r.getPreviewPath())||"/".equals(r.getPreviewPath())||!r.getPreviewPath().matches("^/[A-Za-z0-9][A-Za-z0-9/_-]*$"))throw new IllegalArgumentException("Invalid previewPath");

        if("STATIC".equals(r.getProjectType())){
            validateRelativePath(r.getBuildOutput(),"buildOutput");
        }else{
            validateRelativePath(r.getDockerfilePath(),"dockerfilePath");
            validateRelativePath(r.getDockerBuildContext(),"dockerBuildContext");
            if(r.getHostPort()==null||r.getHostPort()<1024||r.getHostPort()>65535)throw new IllegalArgumentException("CONTAINER requires hostPort 1024-65535");
            if(r.getContainerPort()==null||r.getContainerPort()<1||r.getContainerPort()>65535)throw new IllegalArgumentException("CONTAINER requires containerPort 1-65535");
            if(!StringUtils.hasText(r.getImageName())||!r.getImageName().matches("^[a-z0-9][a-z0-9._/:\\-]*$"))throw new IllegalArgumentException("Invalid imageName");
            if(!StringUtils.hasText(r.getContainerName())||!r.getContainerName().matches("^[A-Za-z0-9][A-Za-z0-9_.-]{1,127}$"))throw new IllegalArgumentException("Invalid containerName");
            if(!r.getCpuLimit().matches("^[0-9]+(?:\\.[0-9]+)?$"))throw new IllegalArgumentException("Invalid cpuLimit");
            if(!r.getMemoryLimit().matches("^[0-9]+[kKmMgG]?$"))throw new IllegalArgumentException("Invalid memoryLimit");
            if(StringUtils.hasText(r.getHostDataPath())&&!Path.of(r.getHostDataPath()).isAbsolute())throw new IllegalArgumentException("hostDataPath must be absolute");
            if(StringUtils.hasText(r.getContainerDataPath())&&!r.getContainerDataPath().startsWith("/"))throw new IllegalArgumentException("containerDataPath must be absolute");
            if(StringUtils.hasText(r.getHostDataPath())&&!StringUtils.hasText(r.getContainerDataPath()))throw new IllegalArgumentException("containerDataPath is required when hostDataPath is configured");
            if(StringUtils.hasText(r.getContainerDataPath())&&!StringUtils.hasText(r.getHostDataPath()))throw new IllegalArgumentException("hostDataPath is required when containerDataPath is configured");
            if(StringUtils.hasText(r.getHealthCheckPath())&&!r.getHealthCheckPath().matches("^/[A-Za-z0-9_./-]*$"))throw new IllegalArgumentException("Invalid healthCheckPath");
        }

        if(repo.count("project_code",r.getProjectCode(),id)>0)throw new IllegalArgumentException("projectCode already exists");
        if(repo.count("preview_path",r.getPreviewPath(),id)>0)throw new IllegalArgumentException("previewPath already exists");
        if(r.getHostPort()!=null&&repo.count("host_port",r.getHostPort(),id)>0)throw new IllegalArgumentException("hostPort already exists");
    }

    private void validateGitSource(PocProjectRequest r) {
        String gitUrl = r.getGitUrl();
        if (gitUrl.contains("\n") || gitUrl.contains("\r") || gitUrl.contains("\0")) throw new IllegalArgumentException("Invalid gitUrl");
        boolean https = gitUrl.startsWith("https://") || gitUrl.startsWith("http://");
        if (https) {
            try {
                URI uri = URI.create(gitUrl);
                if (uri.getUserInfo() != null) throw new IllegalArgumentException("Do not embed username or token in gitUrl; use Source Credential instead");
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("Do not embed")) throw e;
                throw new IllegalArgumentException("Invalid HTTPS gitUrl");
            }
            if (r.getCredentialId() == null) throw new IllegalArgumentException("HTTPS Codeup Git requires a source credential");
        }
        if (r.getCredentialId() != null && credentials.findById(r.getCredentialId()).isEmpty()) {
            throw new IllegalArgumentException("Source credential not found: " + r.getCredentialId());
        }
    }

    private void validateRelativePath(String value,String field){
        if(!StringUtils.hasText(value))throw new IllegalArgumentException(field+" is required");
        Path path=Path.of(value).normalize();
        if(path.isAbsolute()||path.startsWith(".."))throw new IllegalArgumentException(field+" must stay inside the Git repository");
    }
}
