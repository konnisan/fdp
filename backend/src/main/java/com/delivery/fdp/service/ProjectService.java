package com.delivery.fdp.service;

import com.delivery.fdp.dto.PocProjectRequest;
import com.delivery.fdp.model.PocProject;
import com.delivery.fdp.repository.PocProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class ProjectService {
    private final PocProjectRepository repo;
    public ProjectService(PocProjectRepository repo){this.repo=repo;}
    public List<PocProject> list(){return repo.findAll();}
    public PocProject get(Long id){return repo.findById(id).orElseThrow(()->new IllegalArgumentException("POC project not found: "+id));}
    public PocProject create(PocProjectRequest r){normalize(r);validate(r,null);return get(repo.create(r));}
    public PocProject update(Long id,PocProjectRequest r){get(id);normalize(r);validate(r,id);repo.update(id,r);return get(id);}
    public void delete(Long id){PocProject p=get(id);if("RUNNING".equals(p.getStatus()))throw new IllegalStateException("Stop the POC before deleting it.");repo.delete(id);}
    private void normalize(PocProjectRequest r){if(r.getProjectCode()!=null)r.setProjectCode(r.getProjectCode().trim().toLowerCase(Locale.ROOT));if(!StringUtils.hasText(r.getGitBranch()))r.setGitBranch("develop");if(r.getProjectType()!=null)r.setProjectType(r.getProjectType().toUpperCase(Locale.ROOT));if(!StringUtils.hasText(r.getBuildOutput()))r.setBuildOutput("dist");if(StringUtils.hasText(r.getPreviewPath())){String p=r.getPreviewPath().trim();if(!p.startsWith("/"))p="/"+p;if(p.endsWith("/"))p=p.substring(0,p.length()-1);r.setPreviewPath(p);}if(!StringUtils.hasText(r.getPm2Name())&&StringUtils.hasText(r.getProjectCode()))r.setPm2Name("fdp-"+r.getProjectCode());if(!StringUtils.hasText(r.getSqlitePath()))r.setSqlitePath("app.db");}
    private void validate(PocProjectRequest r,Long id){if(!StringUtils.hasText(r.getProjectCode())||!r.getProjectCode().matches("^[a-z0-9][a-z0-9-]{1,49}$"))throw new IllegalArgumentException("Invalid projectCode");if(!StringUtils.hasText(r.getProjectName())||!StringUtils.hasText(r.getGitUrl()))throw new IllegalArgumentException("projectName and gitUrl are required");if(!"STATIC".equals(r.getProjectType())&&!"NODE_SQLITE".equals(r.getProjectType()))throw new IllegalArgumentException("projectType must be STATIC or NODE_SQLITE");if(!StringUtils.hasText(r.getPreviewPath())||!r.getPreviewPath().startsWith("/poc/"))throw new IllegalArgumentException("previewPath must start with /poc/");if("NODE_SQLITE".equals(r.getProjectType())){if(r.getInternalPort()==null||r.getInternalPort()<1024||r.getInternalPort()>65535)throw new IllegalArgumentException("NODE_SQLITE requires internalPort 1024-65535");if(!StringUtils.hasText(r.getStartCommand()))throw new IllegalArgumentException("NODE_SQLITE requires startCommand");}else r.setInternalPort(null);if(repo.count("project_code",r.getProjectCode(),id)>0)throw new IllegalArgumentException("projectCode already exists");if(repo.count("preview_path",r.getPreviewPath(),id)>0)throw new IllegalArgumentException("previewPath already exists");if(r.getInternalPort()!=null&&repo.count("internal_port",r.getInternalPort(),id)>0)throw new IllegalArgumentException("internalPort already exists");}
}
