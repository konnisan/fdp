<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Box, Boxes, FileCode2, PackageCheck, Save, SlidersHorizontal } from 'lucide-vue-next'
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
import { PROFILE_META } from '../project-model'

const emit=defineEmits(['navigate'])
const selectedProfile=ref('STATIC')
const saving=ref(false)
const error=ref('')
const runtime=ref(null)
const credentials=ref([])
const pipelines=ref([])
const repositories=ref([])
const artifacts=ref([])
const source=reactive({projectCode:'',projectName:'',gitUrl:'',gitBranch:'main',credentialId:null,projectDirectory:'.',buildCommand:'',buildOutput:'.',dockerfilePath:'Dockerfile',dockerBuildContext:'.',imageName:'',containerName:'',hostPort:3101,containerPort:3000,cpuLimit:'1',memoryLimit:'512m',hostDataPath:'',containerDataPath:'/app/data',healthCheckPath:'',previewPath:''})
const standard=reactive({projectCode:'',projectName:'',pipelineId:'',pipelineName:'',packageRepoId:'',packageRepoName:'',artifactName:'',previewPath:'',hostPort:3201,containerName:'',envFile:''})
const profileCards=[
  {id:'STATIC',icon:FileCode2},
  {id:'LIGHTWEIGHT',icon:Box},
  {id:'STANDARD',icon:PackageCheck},
  {id:'CUSTOM',icon:SlidersHorizontal}
]
const isContainer=computed(()=>['LIGHTWEIGHT','CUSTOM'].includes(selectedProfile.value))
const selectedPipeline=computed(()=>pipelines.value.find(p=>String(p.pipelineId)===String(standard.pipelineId)))
const selectedRepo=computed(()=>repositories.value.find(r=>String(r.repoId)===String(standard.packageRepoId)))
function err(e){return e.response?.data?.message||e.message}
function deriveSource(){const code=source.projectCode.trim();if(!code)return;if(!source.previewPath)source.previewPath=`/${code}`;if(isContainer.value){if(!source.imageName)source.imageName=`fdp/${code}`;if(!source.containerName)source.containerName=`fdp-${code}`;if(selectedProfile.value==='LIGHTWEIGHT'&&!source.hostDataPath){const root=runtime.value?.resolvedDataRoot||'';source.hostDataPath=root?`${root.replace(/[\\/]$/,'')}/${code}`:''}}}
function deriveStandard(){const code=standard.projectCode.trim();if(!code)return;if(!standard.previewPath)standard.previewPath=`/${code}`;if(!standard.containerName)standard.containerName=`fdp-${code}-backend`}
function chooseProfile(id){selectedProfile.value=id;error.value='';deriveSource()}
async function chooseRepo(){artifacts.value=[];standard.packageRepoName=selectedRepo.value?.repoName||'';if(!standard.packageRepoId)return;try{artifacts.value=await listYunxiaoArtifacts(standard.packageRepoId,{repoType:'GENERIC',page:1,perPage:50})}catch(e){error.value=err(e)}}
function choosePipeline(){standard.pipelineName=selectedPipeline.value?.pipelineName||''}
async function save(){
  saving.value=true;error.value=''
  try{
    if(selectedProfile.value==='STANDARD'){
      deriveStandard()
      if(!standard.projectCode||!standard.projectName||!standard.pipelineId||!standard.packageRepoId||!standard.artifactName||!standard.previewPath||!standard.hostPort||!standard.containerName)throw new Error('请填写项目、Flow、Packages 制品、访问路径、宿主机端口和容器名')
      const created=await createArtifactDeliveryProject({...standard,hostPort:Number(standard.hostPort)})
      emit('navigate',`/projects/artifact/${created.id}`)
      return
    }
    deriveSource()
    if(!source.projectCode||!source.projectName||!source.gitUrl||!source.previewPath)throw new Error('请填写项目编码、项目名称、Codeup Git 和访问路径')
    if(/^https?:\/\//i.test(source.gitUrl)&&!source.credentialId)throw new Error('HTTPS Codeup Git 需要选择源码凭据，请先到系统集成配置凭据')
    const profile=selectedProfile.value
    const payload={...source,deploymentProfile:profile,projectType:profile==='STATIC'?'STATIC':'CONTAINER',credentialId:source.credentialId?Number(source.credentialId):null,hostPort:profile==='STATIC'?null:Number(source.hostPort),containerPort:profile==='STATIC'?null:Number(source.containerPort),buildOutput:profile==='STATIC'?(source.buildOutput||'.'):source.buildOutput}
    const created=await createProject(payload)
    emit('navigate',`/projects/source/${created.id}`)
  }catch(e){error.value=err(e)}finally{saving.value=false}
}
onMounted(async()=>{try{const [rt,cs,ps,repos]=await Promise.all([getRuntimeStatus(),listSourceCredentials(),listYunxiaoPipelines({page:1,perPage:50}),listYunxiaoRepositories({repoTypes:'GENERIC',page:1,perPage:50})]);runtime.value=rt;credentials.value=cs;pipelines.value=ps;repositories.value=repos}catch(e){error.value=err(e)}})
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="新建项目" description="先选择 Deployment Profile，再填写该 Profile 需要的最小部署配置。">
      <template #actions><button class="soft-button" @click="emit('navigate','/projects')"><ArrowLeft :size="14" />返回项目中心</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>

    <section class="panel profile-picker">
      <div class="panel-head"><div><h2>1. 选择 Deployment Profile</h2><p>Profile 只是默认模板；底层仍使用项目、制品、运行单元、路由和数据配置。</p></div></div>
      <div class="profile-create-grid">
        <button v-for="card in profileCards" :key="card.id" type="button" class="profile-create-card" :class="{selected:selectedProfile===card.id}" @click="chooseProfile(card.id)">
          <span class="profile-create-icon"><component :is="card.icon" :size="22" /></span>
          <span><strong>{{card.id}}</strong><b>{{PROFILE_META[card.id].label}}</b><small>{{PROFILE_META[card.id].description}}</small></span>
        </button>
      </div>
    </section>

    <form class="panel project-create-form" @submit.prevent="save">
      <div class="panel-head"><div><h2>2. 配置 {{selectedProfile}}</h2><p v-if="selectedProfile==='STANDARD'">正式工程从 Flow / Packages 获取制品；FDP 不在部署服务器重新编译源码。</p><p v-else-if="selectedProfile==='STATIC'">静态 POC 从 Codeup 拉取已经生成的 HTML，并发布到 Nginx。</p><p v-else>LIGHTWEIGHT / CUSTOM 当前 V1 延续 Codeup + Docker 单运行单元模式，Windows 开发环境默认 DRY-RUN。</p></div><span class="tag">{{runtime?.executionMode||'检测中'}}</span></div>

      <div v-if="selectedProfile!=='STANDARD'" class="form-grid restructure-form-grid">
        <label>项目编码 *<input v-model="source.projectCode" placeholder="customer-poc" @blur="deriveSource" /></label>
        <label>项目名称 *<input v-model="source.projectName" placeholder="客户快速原型" /></label>
        <label class="span-2">Codeup Git *<input v-model="source.gitUrl" placeholder="https://codeup.aliyun.com/group/repo.git" /></label>
        <label>Git Branch<input v-model="source.gitBranch" /></label>
        <label>源码凭据<select v-model="source.credentialId"><option :value="null">不使用（SSH / 本机已有凭据）</option><option v-for="c in credentials" :key="c.id" :value="c.id">{{c.name}} · {{c.status}}</option></select></label>
        <label>仓库内目录<input v-model="source.projectDirectory" placeholder="." /></label>
        <label>客户访问 Path *<input v-model="source.previewPath" placeholder="/customer-poc" /></label>

        <template v-if="selectedProfile==='STATIC'">
          <label>静态发布目录<input v-model="source.buildOutput" placeholder=". 或 dist" /></label>
          <label class="span-2">可选构建命令<input v-model="source.buildCommand" placeholder="纯 HTML 留空" /></label>
        </template>

        <template v-else>
          <div class="form-section-title span-2"><Box :size="15" /> Docker 运行单元</div>
          <label>Dockerfile<input v-model="source.dockerfilePath" /></label><label>Build Context<input v-model="source.dockerBuildContext" /></label>
          <label>Image Name<input v-model="source.imageName" :placeholder="`fdp/${source.projectCode||'project'}`" /></label><label>Container Name<input v-model="source.containerName" :placeholder="`fdp-${source.projectCode||'project'}`" /></label>
          <label>宿主机端口<input v-model="source.hostPort" type="number" /></label><label>容器端口<input v-model="source.containerPort" type="number" /></label>
          <label>CPU Limit<input v-model="source.cpuLimit" /></label><label>Memory Limit<input v-model="source.memoryLimit" /></label>
          <label>Host Volume Path<input v-model="source.hostDataPath" :placeholder="selectedProfile==='LIGHTWEIGHT'?'SQLite 持久化目录':''" /></label><label>Container Volume Path<input v-model="source.containerDataPath" placeholder="/app/data" /></label>
          <label class="span-2">Health Check Path<input v-model="source.healthCheckPath" placeholder="/health，可留空" /></label>
          <div v-if="selectedProfile==='CUSTOM'" class="inline-note span-2">CUSTOM V1 先支持一个结构化 Docker 运行单元。多容器 / worker / Redis 会在后续 Runtime Unit 模型中扩展，不开放任意 shell 输入。</div>
        </template>
      </div>

      <div v-else class="form-grid restructure-form-grid">
        <label>项目编码 *<input v-model="standard.projectCode" placeholder="financial-system" @blur="deriveStandard" /></label>
        <label>项目名称 *<input v-model="standard.projectName" placeholder="Financial System" /></label>
        <label>Flow 流水线 *<select v-model="standard.pipelineId" @change="choosePipeline"><option value="">请选择</option><option v-for="p in pipelines" :key="p.pipelineId" :value="String(p.pipelineId)">{{p.pipelineName}} · {{p.pipelineId}}</option></select></label>
        <label>Packages 仓库 *<select v-model="standard.packageRepoId" @change="chooseRepo"><option value="">请选择</option><option v-for="r in repositories" :key="r.repoId" :value="String(r.repoId)">{{r.repoName}} · {{r.repoId}}</option></select></label>
        <label class="span-2">交付制品 *<select v-model="standard.artifactName"><option value="">请选择</option><option v-for="a in artifacts" :key="a.id" :value="a.module">{{a.module}} · 最新 {{a.versions?.[0]?.version||'-'}}</option></select></label>
        <label>客户访问 Path *<input v-model="standard.previewPath" placeholder="/financial-system" /></label>
        <label>后端宿主机端口 *<input v-model="standard.hostPort" type="number" /></label>
        <label>后端容器名 *<input v-model="standard.containerName" placeholder="fdp-financial-system-backend" /></label>
        <label>环境变量文件<input v-model="standard.envFile" placeholder="/data/fdp/env/financial-system.env" /></label>
        <div class="inline-note span-2"><Boxes :size="14" /> 当前 STANDARD V1 兼容现有单交付包协议；后续可升级为 frontend artifact + backend artifact 的同 Release 组合，不影响项目中心信息架构。</div>
      </div>

      <footer class="project-create-footer"><button type="button" class="soft-button" @click="emit('navigate','/projects')">取消</button><button class="primary-button" :disabled="saving"><Save :size="14" />{{saving?'保存中…':'创建项目'}}</button></footer>
    </form>
  </div>
</template>
