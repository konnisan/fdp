<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Box, GitBranch, PackageCheck, Save } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  createArtifactDeliveryProject,
  createProject,
  getRuntimeStatus,
  listSourceCredentials,
  listYunxiaoArtifacts,
  listYunxiaoPipelines,
  listYunxiaoRepositories
} from '../api'

const emit=defineEmits(['navigate'])
const sourceMode=ref('CODEUP')
const saving=ref(false)
const error=ref('')
const runtime=ref(null)
const credentials=ref([])
const pipelines=ref([])
const repositories=ref([])
const artifacts=ref([])

const source=reactive({
  projectCode:'',projectName:'',gitUrl:'',gitBranch:'main',credentialId:null,projectDirectory:'.',
  buildCommand:'',buildOutput:'.',dockerfilePath:'Dockerfile',dockerBuildContext:'.',imageName:'',containerName:'',
  hostPort:3101,containerPort:3000,cpuLimit:'1',memoryLimit:'512m',hostDataPath:'',containerDataPath:'/app/data',
  healthCheckPath:'',previewPath:''
})
const artifact=reactive({
  projectCode:'',projectName:'',pipelineId:'',pipelineName:'',packageRepoId:'',packageRepoName:'',artifactName:'',
  previewPath:'',hostPort:3201,containerName:'',envFile:''
})

const selectedPipeline=computed(()=>pipelines.value.find(p=>String(p.pipelineId)===String(artifact.pipelineId)))
const selectedRepo=computed(()=>repositories.value.find(r=>String(r.repoId)===String(artifact.packageRepoId)))
function err(e){return e.response?.data?.message||e.message}
function deriveSource(){
  const code=source.projectCode.trim();if(!code)return
  if(!source.previewPath)source.previewPath=`/${code}`
  if(!source.imageName)source.imageName=`fdp/${code}`
  if(!source.containerName)source.containerName=`fdp-${code}`
  if(!source.hostDataPath&&runtime.value?.resolvedDataRoot){source.hostDataPath=`${String(runtime.value.resolvedDataRoot).replace(/[\\/]$/,'')}/${code}`}
}
function deriveArtifact(){
  const code=artifact.projectCode.trim();if(!code)return
  if(!artifact.previewPath)artifact.previewPath=`/${code}`
  if(!artifact.containerName)artifact.containerName=`fdp-${code}`
}
function choosePipeline(){artifact.pipelineName=selectedPipeline.value?.pipelineName||''}
async function chooseRepo(){
  artifacts.value=[]
  artifact.packageRepoName=selectedRepo.value?.repoName||''
  if(!artifact.packageRepoId)return
  try{artifacts.value=await listYunxiaoArtifacts(artifact.packageRepoId,{repoType:'GENERIC',page:1,perPage:50})}
  catch(e){error.value=err(e)}
}
async function save(){
  saving.value=true;error.value=''
  try{
    if(sourceMode.value==='CODEUP'){
      deriveSource()
      if(!source.projectCode||!source.projectName||!source.gitUrl||!source.previewPath||!source.containerName||!source.imageName||!source.hostPort||!source.containerPort){
        throw new Error('请填写项目、Codeup、镜像、容器、端口和访问路径')
      }
      if(/^https?:\/\//i.test(source.gitUrl)&&!source.credentialId)throw new Error('HTTPS Codeup Git 需要选择源码凭据')
      const created=await createProject({
        ...source,
        deploymentProfile:'CUSTOM',
        projectType:'CONTAINER',
        credentialId:source.credentialId?Number(source.credentialId):null,
        hostPort:Number(source.hostPort),
        containerPort:Number(source.containerPort)
      })
      emit('navigate',`/containers/source/${created.id}`)
      return
    }

    deriveArtifact()
    if(!artifact.projectCode||!artifact.projectName||!artifact.pipelineId||!artifact.packageRepoId||!artifact.artifactName||!artifact.previewPath||!artifact.hostPort||!artifact.containerName){
      throw new Error('请填写项目、Flow、Packages 制品、容器名、端口和访问路径')
    }
    const created=await createArtifactDeliveryProject({...artifact,hostPort:Number(artifact.hostPort)})
    emit('navigate',`/containers/artifact/${created.id}`)
  }catch(e){error.value=err(e)}finally{saving.value=false}
}

onMounted(async()=>{
  try{
    const [rt,cs,ps,repos]=await Promise.all([
      getRuntimeStatus(),
      listSourceCredentials(),
      listYunxiaoPipelines({page:1,perPage:50}),
      listYunxiaoRepositories({repoTypes:'GENERIC',page:1,perPage:50})
    ])
    runtime.value=rt;credentials.value=cs;pipelines.value=ps;repositories.value=repos
  }catch(e){error.value=err(e)}
})
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="接入容器项目" description="项目代码已经存在。这里不选择 Profile，只告诉 FDP：代码/镜像从哪里来，以及 Docker 应该怎么运行。">
      <template #actions><button class="soft-button" @click="emit('navigate','/containers')"><ArrowLeft :size="14" />返回容器部署</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>

    <section class="panel profile-picker">
      <div class="panel-head"><div><h2>1. 选择部署来源</h2><p>这不是项目类型。Node、Spring Boot、Python 等都使用同一套 Docker 配置。</p></div><span class="tag">{{runtime?.executionMode||'检测中'}}</span></div>
      <div class="profile-create-grid" style="grid-template-columns:repeat(2,minmax(0,1fr))">
        <button type="button" class="profile-create-card" :class="{selected:sourceMode==='CODEUP'}" @click="sourceMode='CODEUP'">
          <span class="profile-create-icon"><GitBranch :size="22" /></span>
          <span><strong>CODEUP</strong><b>已有源码 + Dockerfile</b><small>FDP 拉取已有仓库，并按项目自己的 Dockerfile / Docker 参数部署。</small></span>
        </button>
        <button type="button" class="profile-create-card" :class="{selected:sourceMode==='ARTIFACT'}" @click="sourceMode='ARTIFACT'">
          <span class="profile-create-icon"><PackageCheck :size="22" /></span>
          <span><strong>PACKAGES</strong><b>已有构建制品</b><small>Flow 已经完成构建，FDP 选择 Packages 中的版本并创建 Docker Container。</small></span>
        </button>
      </div>
    </section>

    <form class="panel project-create-form" @submit.prevent="save">
      <div class="panel-head"><div><h2>2. Docker 部署配置</h2><p>平台不判断技术栈；最终以 Image、Container、Port、Volume、Env、Health Check 为准。</p></div></div>

      <div v-if="sourceMode==='CODEUP'" class="form-grid restructure-form-grid">
        <div class="form-section-title span-2">项目来源</div>
        <label>项目编码 *<input v-model="source.projectCode" placeholder="customer-app" @blur="deriveSource" /></label>
        <label>项目名称 *<input v-model="source.projectName" placeholder="客户管理系统" /></label>
        <label class="span-2">Codeup Git *<input v-model="source.gitUrl" placeholder="https://codeup.aliyun.com/group/repo.git" /></label>
        <label>Git Branch<input v-model="source.gitBranch" /></label>
        <label>源码凭据<select v-model="source.credentialId"><option :value="null">不使用（SSH / 本机已有凭据）</option><option v-for="c in credentials" :key="c.id" :value="c.id">{{c.name}} · {{c.status}}</option></select></label>
        <label>仓库内目录<input v-model="source.projectDirectory" placeholder="." /></label>
        <label>客户访问 Path *<input v-model="source.previewPath" placeholder="/customer-app" /></label>

        <div class="form-section-title span-2"><Box :size="15" /> Docker</div>
        <label>Dockerfile *<input v-model="source.dockerfilePath" placeholder="Dockerfile" /></label>
        <label>Build Context *<input v-model="source.dockerBuildContext" placeholder="." /></label>
        <label>Image Name *<input v-model="source.imageName" placeholder="fdp/customer-app" /></label>
        <label>Container Name *<input v-model="source.containerName" placeholder="fdp-customer-app" /></label>
        <label>宿主机端口 *<input v-model="source.hostPort" type="number" /></label>
        <label>容器端口 *<input v-model="source.containerPort" type="number" /></label>
        <label>CPU Limit<input v-model="source.cpuLimit" placeholder="1" /></label>
        <label>Memory Limit<input v-model="source.memoryLimit" placeholder="512m" /></label>
        <label>Host Volume Path<input v-model="source.hostDataPath" placeholder="/data/fdp/data/project" /></label>
        <label>Container Volume Path<input v-model="source.containerDataPath" placeholder="/app/data" /></label>
        <label class="span-2">Health Check Path<input v-model="source.healthCheckPath" placeholder="/health 或 /actuator/health，可留空" /></label>
        <div class="inline-note span-2">Node + SQLite 可以把 SQLite 目录挂载到 Host Volume；Spring Boot 可以不配置 Volume，数据库连接通过环境变量处理。</div>
      </div>

      <div v-else class="form-grid restructure-form-grid">
        <div class="form-section-title span-2">已有制品</div>
        <label>项目编码 *<input v-model="artifact.projectCode" placeholder="financial-system" @blur="deriveArtifact" /></label>
        <label>项目名称 *<input v-model="artifact.projectName" placeholder="Financial System" /></label>
        <label>Flow 流水线 *<select v-model="artifact.pipelineId" @change="choosePipeline"><option value="">请选择</option><option v-for="p in pipelines" :key="p.pipelineId" :value="String(p.pipelineId)">{{p.pipelineName}} · {{p.pipelineId}}</option></select></label>
        <label>Packages 仓库 *<select v-model="artifact.packageRepoId" @change="chooseRepo"><option value="">请选择</option><option v-for="r in repositories" :key="r.repoId" :value="String(r.repoId)">{{r.repoName}} · {{r.repoId}}</option></select></label>
        <label class="span-2">交付制品 *<select v-model="artifact.artifactName"><option value="">请选择</option><option v-for="a in artifacts" :key="a.id" :value="a.module">{{a.module}} · 最新 {{a.versions?.[0]?.version||'-'}}</option></select></label>

        <div class="form-section-title span-2"><Box :size="15" /> Docker</div>
        <label>Container Name *<input v-model="artifact.containerName" placeholder="fdp-financial-system" /></label>
        <label>宿主机端口 *<input v-model="artifact.hostPort" type="number" /></label>
        <label>客户访问 Path *<input v-model="artifact.previewPath" placeholder="/financial-system" /></label>
        <label>环境变量文件<input v-model="artifact.envFile" placeholder="/data/fdp/env/financial-system.env" /></label>
        <div class="inline-note span-2">Packages 模式下，Image、Container Port、Health Check 等运行事实由交付制品/manifest 提供；FDP 不在服务器重新编译项目。</div>
      </div>

      <footer class="project-create-footer"><button type="button" class="soft-button" @click="emit('navigate','/containers')">取消</button><button class="primary-button" :disabled="saving"><Save :size="14" />{{saving?'保存中…':'接入并保存'}}</button></footer>
    </form>
  </div>
</template>
