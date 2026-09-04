export const PROFILE_META={
  STATIC:{label:'静态 POC',tone:'blue',description:'纯 HTML / 已构建静态页面，由 FDP 同步并发布到 Nginx。'},
  LIGHTWEIGHT:{label:'轻量全栈 POC',tone:'orange',description:'Node.js 前后端 + SQLite 等快速项目，单项目独立 Docker 容器。'},
  STANDARD:{label:'标准前后端工程',tone:'green',description:'Flow 负责 CI，Packages 提供交付制品，FDP 负责正式部署。'},
  CUSTOM:{label:'自定义',tone:'slate',description:'高级 Docker 配置。V1 支持单运行单元，后续可扩展多容器。'}
}

export function sourceProfile(project){
  if(project?.deploymentProfile)return project.deploymentProfile
  return project?.projectType==='STATIC'?'STATIC':'LIGHTWEIGHT'
}

export function normalizeSourceProject(project){
  const profile=sourceProfile(project)
  return {
    key:`source-${project.id}`,
    kind:'source',
    id:project.id,
    profile,
    projectCode:project.projectCode,
    projectName:project.projectName,
    status:project.status,
    previewPath:project.previewPath,
    version:project.deployedCommit,
    image:project.imageName,
    containerName:project.containerName,
    hostPort:project.hostPort,
    containerPort:project.containerPort,
    gitUrl:project.gitUrl,
    gitBranch:project.gitBranch,
    raw:project
  }
}

export function normalizeArtifactProject(project){
  return {
    key:`artifact-${project.id}`,
    kind:'artifact',
    id:project.id,
    profile:'STANDARD',
    projectCode:project.projectCode,
    projectName:project.projectName,
    status:project.status,
    previewPath:project.previewPath,
    version:project.currentVersion,
    image:project.currentImage,
    containerName:project.containerName,
    hostPort:project.hostPort,
    containerPort:null,
    pipelineId:project.pipelineId,
    pipelineName:project.pipelineName,
    artifactName:project.artifactName,
    raw:project
  }
}

export function projectPath(project){
  return `/projects/${project.kind}/${project.id}`
}

export function profileMeta(profile){
  return PROFILE_META[profile]||PROFILE_META.CUSTOM
}
