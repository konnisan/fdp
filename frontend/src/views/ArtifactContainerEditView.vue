<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Box, Save, Settings2 } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getArtifactEnvironment, getArtifactRuntime, getRuntimeStatus, listArtifactDeliveryProjects, updateArtifactDeliveryProject } from '../api'

const props=defineProps({projectId:{type:Number,required:true}})
const emit=defineEmits(['navigate'])
const loading=ref(true)
const saving=ref(false)
const error=ref('')
const info=ref('')
const runtime=ref(null)
const artifactRuntime=ref(null)
const raw=ref(null)
const form=reactive({
  projectCode:'',projectName:'',pipelineId:'',pipelineName:'',packageRepoId:'',packageRepoName:'',artifactName:'',
  previewPath:'',hostPort:null,containerPort:null,containerName:'',envFile:'',envContent:'',cpuLimit:'',memoryLimit:'',
  hostDataPath:'',containerDataPath:'',healthCheckPath:''
})

const previewUrl=computed(()=>{
  const port=Number(runtime.value?.publicPort||8090)
  return form.previewPath?`${window.location.protocol}//${window.location.hostname}:${port}${form.previewPath}`:''
})
const envCount=computed(()=>String(form.envContent||'').split(/\r?\n/).filter(line=>{const v=line.trim();return v&&!v.startsWith('#')}).length)
function err(e){return e.response?.data?.message||e.message||'操作失败'}
function fill(project){
  raw.value=project
  Object.assign(form,{
    projectCode:project.projectCode||'',projectName:project.projectName||'',pipelineId:project.pipelineId||'',pipelineName:project.pipelineName||'',
    packageRepoId:project.packageRepoId||'',packageRepoName:project.packageRepoName||'',artifactName:project.artifactName||'',
    previewPath:project.previewPath||'',hostPort:project.hostPort??null,containerPort:project.containerPort??null,containerName:project.containerName||'',
    envFile:project.envFile||'',cpuLimit:project.cpuLimit||'',memoryLimit:project.memoryLimit||'',hostDataPath:project.hostDataPath||'',
    containerDataPath:project.containerDataPath||'',healthCheckPath:project.healthCheckPath||''
  })
}
async function load(){
  loading.value=true;error.value=''
  try{
    const [items,rt,artRt,env]=await Promise.all([
      listArtifactDeliveryProjects(),getRuntimeStatus(),getArtifactRuntime(props.projectId),getArtifactEnvironment(props.projectId)
    ])
    const project=items.find(p=>Number(p.id)===Number(props.projectId))
    if(!project)throw new Error('容器部署不存在或已删除')
    runtime.value=rt;artifactRuntime.value=artRt;fill(project);form.envContent=env?.content||''
  }catch(e){error.value=err(e)}finally{loading.value=false}
}
async function save(){
  saving.value=true;error.value='';info.value=''
  try{
    if(!form.projectName||!form.previewPath||!form.hostPort||!form.containerPort||!form.containerName)throw new Error('项目名称、Container、宿主机端口、容器端口和访问 Path 必填')
    const hasManagedEnv=String(form.envContent||'').trim().length>0
    const updated=await updateArtifactDeliveryProject(props.projectId,{
      ...form,
      envFile:hasManagedEnv?'':form.envFile,
      hostPort:Number(form.hostPort),
      containerPort:Number(form.containerPort)
    })
    fill(updated);info.value=artifactRuntime.value?.containerStatus==='running'
      ? '配置已保存。当前运行中的 Container 不会被立即修改；重新部署一个版本后新配置生效。'
      : 'Docker 配置和环境变量已保存，下一次部署会直接使用。'
    artifactRuntime.value=await getArtifactRuntime(props.projectId)
  }catch(e){error.value=err(e)}finally{saving.value=false}
}
onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="编辑容器部署" :description="raw?`${raw.projectName} · ${raw.artifactName}`:'加载中…'">
      <template #actions><button class="soft-button" @click="emit('navigate',`/containers/artifact/${projectId}`)"><ArrowLeft :size="14" />返回容器</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>
    <div v-if="info" class="success-banner">{{info}}</div>

    <template v-if="!loading&&raw">
      <section class="panel project-create-form">
        <div class="panel-head"><div><h2>制品绑定</h2><p>Flow / Packages 是这个部署的来源，只读显示；Docker 运行参数可以随时修改。</p></div></div>
        <div class="form-grid restructure-form-grid">
          <label>项目名称<input v-model="form.projectName" /></label>
          <label>Flow<input :value="form.pipelineName||form.pipelineId" readonly /></label>
          <label>Packages<input :value="form.packageRepoName||form.packageRepoId" readonly /></label>
          <label>Artifact<input :value="form.artifactName" readonly /></label>
        </div>
      </section>

      <section class="panel project-create-form">
        <div class="panel-head"><div><h2><Box :size="18" />Docker 运行参数</h2><p>这些字段直接参与下一次 <code>docker run</code>。</p></div><span class="tag">{{artifactRuntime?.containerStatus==='NOT_DEPLOYED'?'未部署':artifactRuntime?.containerStatus||raw.status}}</span></div>
        <div class="form-grid restructure-form-grid">
          <label>Container Name *<input v-model="form.containerName" /></label>
          <label>宿主机端口 *<input v-model="form.hostPort" type="number" /></label>
          <label>容器端口 *<input v-model="form.containerPort" type="number" /></label>
          <label>Health Check Path<input v-model="form.healthCheckPath" placeholder="/actuator/health" /></label>
          <label>CPU Limit<input v-model="form.cpuLimit" placeholder="1 / 0.5" /></label>
          <label>Memory Limit<input v-model="form.memoryLimit" placeholder="512m / 1g" /></label>
          <label>Host Volume Path<input v-model="form.hostDataPath" placeholder="/data/fdp/data/..." /></label>
          <label>Container Volume Path<input v-model="form.containerDataPath" placeholder="/app/data" /></label>
        </div>
      </section>

      <section class="panel project-create-form">
        <div class="panel-head"><div><h2><Settings2 :size="18" />Docker 环境变量</h2><p>直接在 FDP 中维护，不需要到服务器手工创建 env 文件。FDP 会在部署时自动生成并传给 Docker。</p></div><span class="tag">{{envCount}} 个变量</span></div>
        <label style="display:flex;flex-direction:column;gap:7px">环境变量（每行 KEY=VALUE）
          <textarea v-model="form.envContent" rows="12" spellcheck="false" placeholder="SPRING_PROFILES_ACTIVE=prod&#10;SPRING_DATASOURCE_URL=jdbc:mysql://...&#10;SPRING_DATASOURCE_USERNAME=...&#10;SPRING_DATASOURCE_PASSWORD=..." style="width:100%;resize:vertical;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;line-height:1.55"></textarea>
          <small>保存后由 FDP 加密保存；部署时自动生成内部 env 文件并使用 <code>docker run --env-file</code> 注入。</small>
        </label>
        <div v-if="raw.envFile&&!form.envContent.trim()" class="inline-note" style="margin-top:12px">这个项目仍保留旧版服务器 Env：<code>{{raw.envFile}}</code>。如果你现在不填写环境变量，旧配置不会被清除；填写并保存后会自动切换为 FDP 托管环境。</div>
      </section>

      <section class="panel project-create-form">
        <div class="panel-head"><div><h2>客户访问入口</h2><p>对外统一使用 Nginx :{{runtime?.publicPort||8090}}，宿主机端口只用于服务器内部转发。</p></div></div>
        <div class="form-grid restructure-form-grid">
          <label>访问 Path *<input v-model="form.previewPath" /></label>
          <label>客户预览地址<input :value="previewUrl" readonly /></label>
        </div>
      </section>

      <section class="panel" style="padding:14px 18px;display:flex;justify-content:space-between;gap:12px;align-items:center">
        <div class="inline-note" style="margin:0">可以保存新的 Docker 配置；如果当前已有实例在运行，重新部署版本后应用新的参数和环境变量。</div>
        <button class="primary-button" :disabled="saving" @click="save"><Save :size="14" />{{saving?'保存中…':'保存配置'}}</button>
      </section>
    </template>
  </div>
</template>
