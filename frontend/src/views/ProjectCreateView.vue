<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Box, PackageCheck, Save, Server } from 'lucide-vue-next'
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
  previewPath:'',hostPort:3201,containerPort:null,containerName:'',envFile:'',cpuLimit:'',memoryLimit:'',
  hostDataPath:'',containerDataPath:'',healthCheckPath:''
})

const selectedPipeline=computed(()=>pipelines.value.find(p=>String(p.pipelineId)===String(form.pipelineId)))
const selectedRepo=computed(()=>repositories.value.find(r=>String(r.repoId)===String(form.packageRepoId)))
const previewUrl=computed(()=>{
  const port=Number(runtime.value?.publicPort||8090)
  const host=window.location.hostname
  return form.previewPath?`${window.location.protocol}//${host}:${port}${form.previewPath}`:`${window.location.protocol}//${host}:${port}/<访问路径>`
})
function err(e){return e.response?.data?.message||e.message||'操作失败'}
function slug(value){return String(value||'').toLowerCase().replace(/[^a-z0-9._-]+/g,'-').replace(/^-+|-+$/g,'').slice(0,50)}
function deriveDefaults(){
  const base=slug(form.artifactName)||'app'
  if(!form.projectName&&form.artifactName)form.projectName=form.artifactName.replace(/[-_](backend|frontend)$/i,'')
  if(!form.previewPath)form.previewPath=`/${base.replace(/-(backend|frontend)$/,'')}`
  if(!form.containerName)form.containerName=`fdp-${base.replace(/-(backend|frontend)$/,'')}`
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
    deriveDefaults()
    if(!form.projectName||!form.pipelineId||!form.packageRepoId||!form.artifactName||!form.previewPath||!form.hostPort||!form.containerPort||!form.containerName){
      throw new Error('请填写项目名称、Flow、Packages 制品、Container、宿主机端口、容器端口和访问 Path')
    }
    const payload={...form,projectCode:'',hostPort:Number(form.hostPort),containerPort:Number(form.containerPort)}
    const created=await createArtifactDeliveryProject(payload)
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
    deriveDefaults()
    info.value=`已从制品仓库带入 ${seed.value.artifactName||'制品'} ${seed.value.latestVersion?`· version ${seed.value.latestVersion}`:''}`
  }
  try{
    const [rt,ps,repos]=await Promise.all([
      getRuntimeStatus(),
      listYunxiaoPipelines({page:1,perPage:50}),
      listYunxiaoRepositories({repoTypes:'GENERIC',page:1,perPage:50})
    ])
    runtime.value=rt;pipelines.value=ps;repositories.value=repos
    if(form.packageRepoId){const wanted=form.artifactName;await chooseRepo();form.artifactName=wanted;deriveDefaults()}
  }catch(e){error.value=err(e)}
})
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="新增容器部署" description="选择 Flow 已经产出的 Packages 制品，然后填写这台 FDP 服务器真正要使用的 Docker 参数。项目编码由平台内部自动生成，不需要人工维护。">
      <template #actions><button class="soft-button" @click="emit('navigate','/containers')"><ArrowLeft :size="14" />返回容器部署</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>
    <div v-if="info" class="success-banner">{{info}}</div>

    <form class="page-stack" @submit.prevent="save">
      <section class="panel project-create-form">
        <div class="panel-head"><div><h2><PackageCheck :size="18" />1. 选择制品</h2><p>Flow 负责构建，Packages 保存结果。FDP 从这里开始接管部署。</p></div><span class="tag">{{runtime?.executionMode||'检测中'}}</span></div>
        <div class="form-grid restructure-form-grid">
          <label>项目名称 *<input v-model="form.projectName" placeholder="Financial System" /></label>
          <label>Flow 流水线 *<select v-model="form.pipelineId" @change="choosePipeline"><option value="">请选择</option><option v-for="p in pipelines" :key="p.pipelineId" :value="String(p.pipelineId)">{{p.pipelineName}} · {{p.pipelineId}}</option></select></label>
          <label>Packages 仓库 *<select v-model="form.packageRepoId" @change="chooseRepo"><option value="">请选择</option><option v-for="r in repositories" :key="r.repoId" :value="String(r.repoId)">{{r.repoName}} · {{r.repoId}}</option></select></label>
          <label>交付制品 *<select v-model="form.artifactName" @change="deriveDefaults"><option value="">请选择</option><option v-for="a in artifacts" :key="a.id||a.module" :value="a.module">{{a.module}} · 最新 {{a.versions?.[0]?.version||'-'}}</option></select></label>
        </div>
      </section>

      <section class="panel project-create-form">
        <div class="panel-head"><div><h2><Box :size="18" />2. Docker 运行参数</h2><p>这里填写的就是最终 <code>docker run</code> 会使用的参数，不再从 Profile 推断。</p></div></div>
        <div class="form-grid restructure-form-grid">
          <label>Container Name *<input v-model="form.containerName" placeholder="fdp-financial-system" /></label>
          <label>宿主机端口 *<input v-model="form.hostPort" type="number" placeholder="3201" /><small>仅绑定 127.0.0.1，由 Nginx 转发，不直接暴露给客户。</small></label>
          <label>容器端口 *<input v-model="form.containerPort" type="number" placeholder="Spring Boot 常见 8080 / Node 常见 3000" /><small>应用在 Container 内真正监听的端口。</small></label>
          <label>Health Check Path<input v-model="form.healthCheckPath" placeholder="/actuator/health 或 /health，可留空" /></label>
          <label>CPU Limit<input v-model="form.cpuLimit" placeholder="1 或 0.5，可留空" /></label>
          <label>Memory Limit<input v-model="form.memoryLimit" placeholder="512m / 1g，可留空" /></label>

          <div class="form-section-title span-2"><Server :size="15" /> 服务器环境</div>
          <label class="span-2">服务器 Env 文件<input v-model="form.envFile" placeholder="/data/fdp/env/financial-system.env" /><small>这是 FDP Linux 服务器上已经存在的文件。平台不会在线生成或修改 env；部署时直接使用 <code>--env-file</code>。不需要环境变量可留空。</small></label>
          <label>Host Volume Path<input v-model="form.hostDataPath" placeholder="/data/fdp/data/financial-system" /></label>
          <label>Container Volume Path<input v-model="form.containerDataPath" placeholder="/app/data" /><small>SQLite 等需要持久化时成对填写；普通 Spring Boot + MySQL 可以留空。</small></label>
        </div>
      </section>

      <section class="panel project-create-form">
        <div class="panel-head"><div><h2>3. 客户访问入口</h2><p>Nginx 对外端口由 FDP 系统统一管理，当前固定使用 <strong>{{runtime?.publicPort||8090}}</strong>。</p></div></div>
        <div class="form-grid restructure-form-grid">
          <label>访问 Path *<input v-model="form.previewPath" placeholder="/financial-system" /></label>
          <label>最终预览地址<input :value="previewUrl" readonly /></label>
          <div class="inline-note span-2">客户访问 <code>:{{runtime?.publicPort||8090}}{{form.previewPath||'/...'}}</code>；Nginx 再把后端请求转发到 <code>127.0.0.1:{{form.hostPort||'宿主机端口'}}</code>。因此 8090 和 Container 的 Host Port 是两层不同的端口。</div>
        </div>
      </section>

      <section class="panel" style="padding:14px 18px;display:flex;justify-content:flex-end;gap:8px">
        <button type="button" class="soft-button" @click="emit('navigate','/containers')">取消</button>
        <button class="primary-button" :disabled="saving"><Save :size="14" />{{saving?'保存中…':'保存并进入容器管理'}}</button>
      </section>
    </form>
  </div>
</template>
