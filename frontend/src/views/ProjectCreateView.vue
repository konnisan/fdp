<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Box, PackageCheck, Save } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  createArtifactDeliveryProject,
  getRuntimeStatus,
  listYunxiaoArtifacts,
  listYunxiaoPipelines,
  listYunxiaoRepositories
} from '../api'

const emit=defineEmits(['navigate'])
const saving=ref(false)
const error=ref('')
const info=ref('')
const runtime=ref(null)
const pipelines=ref([])
const repositories=ref([])
const artifacts=ref([])
const seed=ref(null)
const form=reactive({
  projectCode:'',projectName:'',pipelineId:'',pipelineName:'',packageRepoId:'',packageRepoName:'',artifactName:'',
  previewPath:'',hostPort:3201,containerName:'',envFile:''
})

const selectedPipeline=computed(()=>pipelines.value.find(p=>String(p.pipelineId)===String(form.pipelineId)))
const selectedRepo=computed(()=>repositories.value.find(r=>String(r.repoId)===String(form.packageRepoId)))
function err(e){return e.response?.data?.message||e.message||'操作失败'}
function derive(){
  const code=form.projectCode.trim();if(!code)return
  if(!form.previewPath)form.previewPath=`/${code}`
  if(!form.containerName)form.containerName=`fdp-${code}`
}
function choosePipeline(){form.pipelineName=selectedPipeline.value?.pipelineName||''}
async function chooseRepo(){
  artifacts.value=[]
  form.packageRepoName=selectedRepo.value?.repoName||form.packageRepoName||''
  if(!form.packageRepoId)return
  try{artifacts.value=await listYunxiaoArtifacts(form.packageRepoId,{repoType:'GENERIC',page:1,perPage:50})}
  catch(e){error.value=err(e)}
}
async function save(){
  saving.value=true;error.value='';info.value=''
  try{
    derive()
    if(!form.projectCode||!form.projectName||!form.pipelineId||!form.packageRepoId||!form.artifactName||!form.previewPath||!form.hostPort||!form.containerName){
      throw new Error('请填写项目、Flow、Packages 制品、Docker 容器名、端口和访问路径')
    }
    const created=await createArtifactDeliveryProject({...form,hostPort:Number(form.hostPort)})
    emit('navigate',`/containers/artifact/${created.id}`)
  }catch(e){error.value=err(e)}finally{saving.value=false}
}
function readSeed(){
  try{const text=sessionStorage.getItem('fdp-container-artifact-seed');return text?JSON.parse(text):null}catch{return null}
}

onMounted(async()=>{
  seed.value=readSeed()
  sessionStorage.removeItem('fdp-container-artifact-seed')
  if(seed.value){
    form.packageRepoId=String(seed.value.repoId||'')
    form.packageRepoName=seed.value.repoName||''
    form.artifactName=seed.value.artifactName||''
    info.value=`已从制品仓库带入 ${seed.value.artifactName||'制品'} ${seed.value.latestVersion?`· version ${seed.value.latestVersion}`:''}`
  }
  try{
    const [rt,ps,repos]=await Promise.all([
      getRuntimeStatus(),
      listYunxiaoPipelines({page:1,perPage:50}),
      listYunxiaoRepositories({repoTypes:'GENERIC',page:1,perPage:50})
    ])
    runtime.value=rt;pipelines.value=ps;repositories.value=repos
    if(form.packageRepoId){const wanted=form.artifactName;await chooseRepo();form.artifactName=wanted}
  }catch(e){error.value=err(e)}
})
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="接入容器部署" description="容器项目已经由 Flow 构建并上传 Packages。这里选择制品，再配置 FDP 服务器上的 Docker 运行方式。">
      <template #actions><button class="soft-button" @click="emit('navigate','/containers')"><ArrowLeft :size="14" />返回容器部署</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>
    <div v-if="info" class="success-banner">{{info}}</div>

    <form class="page-stack" @submit.prevent="save">
      <section class="panel project-create-form">
        <div class="panel-head"><div><h2><PackageCheck :size="18" />1. 选择构建产物</h2><p>流水线只负责构建；Packages 保存版本；FDP 从这里接手部署。</p></div><span class="tag">{{runtime?.executionMode||'检测中'}}</span></div>
        <div class="form-grid restructure-form-grid">
          <label>项目编码 *<input v-model="form.projectCode" placeholder="financial-system" @blur="derive" /></label>
          <label>项目名称 *<input v-model="form.projectName" placeholder="Financial System" /></label>
          <label>Flow 流水线 *<select v-model="form.pipelineId" @change="choosePipeline"><option value="">请选择</option><option v-for="p in pipelines" :key="p.pipelineId" :value="String(p.pipelineId)">{{p.pipelineName}} · {{p.pipelineId}}</option></select></label>
          <label>Packages 仓库 *<select v-model="form.packageRepoId" @change="chooseRepo"><option value="">请选择</option><option v-for="r in repositories" :key="r.repoId" :value="String(r.repoId)">{{r.repoName}} · {{r.repoId}}</option></select></label>
          <label class="span-2">交付制品 *<select v-model="form.artifactName"><option value="">请选择</option><option v-for="a in artifacts" :key="a.id||a.module" :value="a.module">{{a.module}} · 最新 {{a.versions?.[0]?.version||'-'}}</option></select></label>
        </div>
      </section>

      <section class="panel project-create-form">
        <div class="panel-head"><div><h2><Box :size="18" />2. Docker 运行配置</h2><p>这些是 FDP 服务器上的运行参数，不区分 Node、Spring Boot 或其他技术栈。</p></div></div>
        <div class="form-grid restructure-form-grid">
          <label>Container Name *<input v-model="form.containerName" placeholder="fdp-financial-system" /></label>
          <label>宿主机端口 *<input v-model="form.hostPort" type="number" /></label>
          <label>客户访问 Path *<input v-model="form.previewPath" placeholder="/financial-system" /></label>
          <label>环境变量文件<input v-model="form.envFile" placeholder="/data/fdp/env/financial-system.env" /></label>
          <div class="inline-note span-2">当前交付制品中的 Docker Image、Container Port、Health Check 由制品内的 <code>fdp-manifest.yml</code> 声明；这里配置宿主机侧 Container、Port、Env 和访问入口。后续如需覆盖 CPU / Memory / Volume，可继续在这一块扩展，而不再增加 Profile。</div>
        </div>
      </section>

      <section class="panel" style="padding:14px 18px;display:flex;justify-content:flex-end;gap:8px">
        <button type="button" class="soft-button" @click="emit('navigate','/containers')">取消</button>
        <button class="primary-button" :disabled="saving"><Save :size="14" />{{saving?'保存中…':'保存并进入容器管理'}}</button>
      </section>
    </form>
  </div>
</template>
