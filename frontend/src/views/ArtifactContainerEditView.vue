<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Box, Save, Server } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getArtifactRuntime, getRuntimeStatus, listArtifactDeliveryProjects, updateArtifactDeliveryProject } from '../api'

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
  previewPath:'',hostPort:null,containerPort:null,containerName:'',envFile:'',cpuLimit:'',memoryLimit:'',
  hostDataPath:'',containerDataPath:'',healthCheckPath:''
})

const previewUrl=computed(()=>{
  const port=Number(runtime.value?.publicPort||8090)
  return form.previewPath?`${window.location.protocol}//${window.location.hostname}:${port}${form.previewPath}`:''
})
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
    const [items,rt,artRt]=await Promise.all([listArtifactDeliveryProjects(),getRuntimeStatus(),getArtifactRuntime(props.projectId)])
    const project=items.find(p=>Number(p.id)===Number(props.projectId))
    if(!project)throw new Error('容器部署不存在或已删除')
    runtime.value=rt;artifactRuntime.value=artRt;fill(project)
  }catch(e){error.value=err(e)}finally{loading.value=false}
}
async function save(){
  saving.value=true;error.value='';info.value=''
  try{
    if(!form.projectName||!form.previewPath||!form.hostPort||!form.containerPort||!form.containerName)throw new Error('项目名称、Container、宿主机端口、容器端口和访问 Path 必填')
    const updated=await updateArtifactDeliveryProject(props.projectId,{...form,hostPort:Number(form.hostPort),containerPort:Number(form.containerPort)})
    fill(updated);info.value='Docker 部署配置已保存。下一次部署会使用新配置。'
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
        <div class="panel-head"><div><h2>制品绑定</h2><p>这里仅展示来源。需要切换 Pipeline / Artifact 时建议新建一个容器部署，避免把历史版本关系改乱。</p></div></div>
        <div class="form-grid restructure-form-grid">
          <label>项目名称<input v-model="form.projectName" /></label>
          <label>Flow<input :value="form.pipelineName||form.pipelineId" readonly /></label>
          <label>Packages<input :value="form.packageRepoName||form.packageRepoId" readonly /></label>
          <label>Artifact<input :value="form.artifactName" readonly /></label>
        </div>
      </section>

      <section class="panel project-create-form">
        <div class="panel-head"><div><h2><Box :size="18" />Docker 运行参数</h2><p>这些字段会直接参与下一次 <code>docker run</code>。</p></div><span class="tag">{{artifactRuntime?.containerStatus==='NOT_DEPLOYED'?'未部署':artifactRuntime?.containerStatus||raw.status}}</span></div>
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
        <div class="panel-head"><div><h2><Server :size="18" />服务器 Env 与访问入口</h2><p>Env 文件由你们提前在服务器创建；FDP 只检查并在部署时通过 <code>--env-file</code> 使用。</p></div></div>
        <div class="form-grid restructure-form-grid">
          <label class="span-2">服务器 Env 文件<input v-model="form.envFile" placeholder="/data/fdp/env/financial-system.env" /><small v-if="form.envFile">当前检测：<strong>{{artifactRuntime?.envFileReady?'文件存在':'文件不存在 / 当前环境无法读取'}}</strong></small><small v-else>未配置 env 文件。</small></label>
          <label>访问 Path *<input v-model="form.previewPath" /></label>
          <label>客户预览地址<input :value="previewUrl" readonly /><small>统一绑定 Nginx 对外端口 {{runtime?.publicPort||8090}}。</small></label>
        </div>
      </section>

      <section class="panel" style="padding:14px 18px;display:flex;justify-content:space-between;gap:12px;align-items:center">
        <div class="inline-note" style="margin:0">如果 Container 正在 RUNNING，请先停止后再修改运行参数；修改后重新选择版本部署。</div>
        <button class="primary-button" :disabled="saving" @click="save"><Save :size="14" />{{saving?'保存中…':'保存配置'}}</button>
      </section>
    </template>
  </div>
</template>
