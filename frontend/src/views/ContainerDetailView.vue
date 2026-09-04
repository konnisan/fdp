<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Box, Copy, ExternalLink, FileArchive, GitBranch, Play, RefreshCw, RotateCcw, Square, Terminal, Trash2 } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  deployArtifactRelease,
  deployProject,
  getArtifactRuntime,
  getArtifactRuntimeLogs,
  getDeploymentLogs,
  getRuntimeLogs,
  getRuntimeStatus,
  listArtifactDeliveryHistory,
  listArtifactDeliveryProjects,
  listArtifactDeliveryReleases,
  listDeployments,
  listProjects,
  removeArtifactContainer,
  restartArtifactProject,
  restartProject,
  stopArtifactProject,
  stopProject
} from '../api'
import { normalizeArtifactProject, normalizeSourceProject } from '../project-model'

const props=defineProps({projectKind:{type:String,required:true},projectId:{type:Number,required:true}})
const emit=defineEmits(['navigate'])
const project=ref(null)
const raw=ref(null)
const runtime=ref(null)
const artifactRuntime=ref(null)
const releases=ref([])
const history=ref([])
const deployments=ref([])
const logs=ref('')
const activeTab=ref('overview')
const loading=ref(true)
const busy=ref('')
const error=ref('')
const info=ref('')
const tabs=[['overview','概览'],['docker','Docker'],['source','来源与版本'],['history','部署记录']]
const isArtifact=computed(()=>props.projectKind==='artifact')
const sourceLabel=computed(()=>isArtifact.value?'Flow / Packages':'Codeup + Dockerfile')
const windowsDryRun=computed(()=>String(runtime.value?.executionMode||'').toUpperCase().includes('DRY')||String(runtime.value?.os||'').toLowerCase().includes('win'))
const previewUrl=computed(()=>{
  if(!project.value?.previewPath)return''
  const port=Number(runtime.value?.publicPort||0)
  const base=port?`${window.location.protocol}//${window.location.hostname}:${port}`:`${window.location.protocol}//${window.location.host}`
  return `${base}${project.value.previewPath}`
})
function err(e){return e.response?.data?.message||e.message}
function short(v){return v&&v!=='DRY-RUN'?String(v).slice(0,12):(v||'-')}
function time(v){if(!v)return'-';const n=Number(v);return Number.isFinite(n)?new Date(n).toLocaleString():String(v)}
async function copy(text){try{await navigator.clipboard.writeText(text);info.value='已复制'}catch{}}
async function load(){
  loading.value=true;error.value=''
  try{
    runtime.value=await getRuntimeStatus()
    if(isArtifact.value){
      const list=await listArtifactDeliveryProjects();raw.value=list.find(p=>Number(p.id)===Number(props.projectId))||null
      if(!raw.value)throw new Error('容器项目不存在或已删除')
      project.value=normalizeArtifactProject(raw.value)
      const [rs,hs,rt]=await Promise.all([listArtifactDeliveryReleases(props.projectId),listArtifactDeliveryHistory(props.projectId),getArtifactRuntime(props.projectId)])
      releases.value=rs;history.value=hs;artifactRuntime.value=rt
    }else{
      const list=await listProjects();raw.value=list.find(p=>Number(p.id)===Number(props.projectId))||null
      if(!raw.value)throw new Error('容器项目不存在或已删除')
      if(raw.value.projectType==='STATIC')throw new Error('静态 POC 请在“静态预览”中查看')
      project.value=normalizeSourceProject(raw.value)
      deployments.value=await listDeployments(props.projectId)
    }
  }catch(e){error.value=err(e)}finally{loading.value=false}
}
async function deploySource(){
  busy.value='deploy';error.value='';info.value=''
  try{const result=await deployProject(props.projectId);info.value=`部署任务 #${result.taskId} 已提交${windowsDryRun.value?'（DRY-RUN）':''}`;await load()}
  catch(e){error.value=err(e)}finally{busy.value=''}
}
async function deployRelease(release){
  if(windowsDryRun.value){info.value=`Windows DRY-RUN：已选择版本 ${release.version}，正式 Docker 部署只在 Linux 执行。`;return}
  if(!confirm(`部署 ${project.value.projectName} 版本 ${release.version}？`))return
  busy.value=`release-${release.runId}`;error.value=''
  try{await deployArtifactRelease(props.projectId,String(release.runId));info.value='制品部署已提交';await load()}
  catch(e){error.value=err(e)}finally{busy.value=''}
}
async function runtimeAction(action){
  busy.value=action;error.value='';info.value=''
  try{
    let result
    if(isArtifact.value){
      if(action==='restart')result=await restartArtifactProject(props.projectId)
      if(action==='stop')result=await stopArtifactProject(props.projectId)
      if(action==='remove')result=await removeArtifactContainer(props.projectId)
    }else{
      if(action==='restart')result=await restartProject(props.projectId)
      if(action==='stop')result=await stopProject(props.projectId)
    }
    info.value=result?.output||`${action} 已执行${windowsDryRun.value?'（DRY-RUN）':''}`
    await load()
  }catch(e){error.value=err(e)}finally{busy.value=''}
}
async function openLogs(){
  activeTab.value='docker';busy.value='logs';error.value=''
  try{logs.value=isArtifact.value?await getArtifactRuntimeLogs(props.projectId):await getRuntimeLogs(props.projectId)}
  catch(e){error.value=err(e)}finally{busy.value=''}
}
async function openDeploymentLogs(item){
  busy.value='history-log';error.value=''
  try{logs.value=(await getDeploymentLogs(item.id)).join('\n\n');activeTab.value='docker'}
  catch(e){error.value=err(e)}finally{busy.value=''}
}
onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader :title="project?.projectName||'容器项目'" :description="project?`${project.projectCode} · ${sourceLabel}`:'加载中…'">
      <template #actions>
        <button class="soft-button" @click="emit('navigate','/containers')"><ArrowLeft :size="14" />返回容器部署</button>
        <button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14" />刷新</button>
        <a v-if="previewUrl" class="soft-button" :href="previewUrl" target="_blank">打开访问地址 <ExternalLink :size="14" /></a>
        <button v-if="project&&!isArtifact" class="primary-button" :disabled="busy" @click="deploySource"><Play :size="14" />{{windowsDryRun?'模拟部署':'部署'}}</button>
      </template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>
    <div v-if="info" class="success-banner">{{info}}</div>

    <template v-if="project">
      <section class="project-hero panel unified-project-hero">
        <div class="project-identity"><span class="project-avatar"><Box :size="20" /></span><div><div class="hero-line"><h2>{{project.containerName||project.projectName}}</h2><span class="status-text" :class="project.status?.toLowerCase()"><i></i>{{artifactRuntime?.containerStatus||project.status}}</span></div><p>{{project.image||artifactRuntime?.runtimeImage||'镜像将在部署后确定'}}</p></div></div>
        <div class="project-version"><span>Image <code>{{project.image||artifactRuntime?.runtimeImage||'-'}}</code></span><span>Port <b>{{project.hostPort||'-'}} → {{project.containerPort||'manifest'}}</b></span><span>Mode <b>{{runtime?.executionMode||'-'}}</b></span></div>
      </section>

      <div class="tabs project-detail-tabs"><button v-for="tab in tabs" :key="tab[0]" :class="{active:activeTab===tab[0]}" @click="activeTab=tab[0]">{{tab[1]}}</button></div>

      <section v-if="activeTab==='overview'" class="detail-grid">
        <article class="panel info-card"><h3>Docker 运行单元</h3><dl><div><dt>Container</dt><dd><code>{{project.containerName||'-'}}</code></dd></div><div><dt>Image</dt><dd><code>{{project.image||artifactRuntime?.runtimeImage||'-'}}</code></dd></div><div><dt>Host Port</dt><dd>{{project.hostPort||'-'}}</dd></div><div><dt>Container Port</dt><dd>{{project.containerPort||'由制品 manifest 决定'}}</dd></div></dl></article>
        <article class="panel info-card"><h3>部署来源</h3><dl><div><dt>来源</dt><dd>{{sourceLabel}}</dd></div><template v-if="isArtifact"><div><dt>Pipeline</dt><dd>{{raw.pipelineName||raw.pipelineId}}</dd></div><div><dt>Artifact</dt><dd><code>{{raw.artifactName}}</code></dd></div><div><dt>当前版本</dt><dd><code>{{project.version||'-'}}</code></dd></div></template><template v-else><div><dt>Git Branch</dt><dd>{{raw.gitBranch}}</dd></div><div><dt>Dockerfile</dt><dd><code>{{raw.dockerfilePath||'Dockerfile'}}</code></dd></div><div><dt>Commit</dt><dd><code>{{short(raw.deployedCommit)}}</code></dd></div></template></dl></article>
        <article class="panel info-card span-2"><h3>访问地址</h3><div class="preview-highlight"><div><strong>{{previewUrl||'尚未配置'}}</strong><p>FDP 负责把外部访问路由到当前 Container。</p></div><div v-if="previewUrl"><button class="soft-button" @click="copy(previewUrl)"><Copy :size="14" />复制</button><a class="primary-button" :href="previewUrl" target="_blank">打开 <ExternalLink :size="14" /></a></div></div></article>
      </section>

      <section v-if="activeTab==='docker'" class="page-stack">
        <article class="panel runtime-unit-card">
          <div class="runtime-unit-head"><div><span class="runtime-icon"><Box :size="21" /></span><div><h2>{{project.containerName||'-'}}</h2><p>{{project.image||artifactRuntime?.runtimeImage||'-'}}</p></div></div><span class="status-text" :class="project.status?.toLowerCase()"><i></i>{{artifactRuntime?.containerStatus||project.status}}</span></div>
          <div class="runtime-facts"><div><span>Host Port</span><strong>{{project.hostPort||'-'}}</strong></div><div><span>Container Port</span><strong>{{project.containerPort||'manifest'}}</strong></div><div><span>CPU</span><strong>{{raw.cpuLimit||'-'}}</strong></div><div><span>Memory</span><strong>{{raw.memoryLimit||'-'}}</strong></div><div><span>Health</span><strong>{{raw.healthCheckPath||'manifest / Docker'}}</strong></div><div><span>Runtime</span><strong>{{artifactRuntime?.runtimeMode||runtime?.executionMode||'-'}}</strong></div></div>
          <div class="runtime-actions"><button class="soft-button" :disabled="busy" @click="runtimeAction('restart')"><RotateCcw :size="14" />重启</button><button class="soft-button" :disabled="busy" @click="runtimeAction('stop')"><Square :size="14" />停止</button><button class="soft-button" :disabled="busy" @click="openLogs"><Terminal :size="14" />日志</button><button v-if="isArtifact" class="icon-button danger" :disabled="busy" @click="runtimeAction('remove')"><Trash2 :size="14" /></button></div>
        </article>
        <article class="panel log-card"><div class="panel-head"><div><h2>Container Logs</h2><p>{{windowsDryRun?'Windows 开发环境显示 DRY-RUN 或本地可用日志。':'最近 300 行。'}}</p></div><button class="soft-button" :disabled="busy" @click="openLogs"><RefreshCw :size="14" />刷新日志</button></div><pre>{{logs||'点击“日志”读取 Container 输出。'}}</pre></article>
      </section>

      <section v-if="activeTab==='source'" class="page-stack">
        <article v-if="!isArtifact" class="panel info-card"><h3><GitBranch :size="17" />Codeup + Dockerfile</h3><dl><div><dt>Git</dt><dd class="break">{{raw.gitUrl}}</dd></div><div><dt>Branch</dt><dd>{{raw.gitBranch}}</dd></div><div><dt>Project Directory</dt><dd><code>{{raw.projectDirectory||'.'}}</code></dd></div><div><dt>Dockerfile</dt><dd><code>{{raw.dockerfilePath||'Dockerfile'}}</code></dd></div><div><dt>Build Context</dt><dd><code>{{raw.dockerBuildContext||'.'}}</code></dd></div><div><dt>Image</dt><dd><code>{{raw.imageName||'-'}}</code></dd></div></dl></article>
        <template v-else>
          <article class="panel info-card"><h3><FileArchive :size="17" />Flow / Packages</h3><dl><div><dt>Pipeline</dt><dd>{{raw.pipelineName||raw.pipelineId}}</dd></div><div><dt>Packages</dt><dd>{{raw.packageRepoName||raw.packageRepoId}}</dd></div><div><dt>Artifact</dt><dd><code>{{raw.artifactName}}</code></dd></div><div><dt>当前 Run</dt><dd><code>{{raw.currentRunId||'-'}}</code></dd></div></dl></article>
          <article class="panel"><div class="panel-head"><div><h2>可部署版本</h2><p>选择已经构建成功的版本，FDP 只负责部署。</p></div></div><div class="table-wrap"><table class="data-table"><thead><tr><th>版本</th><th>Flow Run</th><th>更新时间</th><th>MD5</th><th>操作</th></tr></thead><tbody><tr v-for="r in releases" :key="r.runId"><td><strong>{{r.version}}</strong></td><td><code>#{{r.runId}}</code></td><td>{{time(r.updateTime||r.createTime)}}</td><td><code>{{r.md5||'-'}}</code></td><td><button class="primary-button" :disabled="busy!==''" @click="deployRelease(r)">{{windowsDryRun?'验证版本':'部署'}}</button></td></tr></tbody></table></div><div v-if="!releases.length" class="empty-state">暂无可部署版本。</div></article>
        </template>
      </section>

      <section v-if="activeTab==='history'" class="panel">
        <div class="panel-head"><div><h2>部署记录</h2><p>查看这个 Container 的部署历史。</p></div></div>
        <div v-if="isArtifact" class="table-wrap"><table class="data-table"><thead><tr><th>版本</th><th>Run</th><th>状态</th><th>时间</th></tr></thead><tbody><tr v-for="h in history" :key="h.id"><td><code>{{h.version||'-'}}</code></td><td><code>{{h.runId||'-'}}</code></td><td><span class="tag">{{h.status}}</span></td><td>{{time(h.createdAt||h.createTime)}}</td></tr></tbody></table></div>
        <div v-else class="table-wrap"><table class="data-table"><thead><tr><th>Task</th><th>状态</th><th>Commit</th><th>开始时间</th><th>日志</th></tr></thead><tbody><tr v-for="d in deployments" :key="d.id"><td><code>#{{d.id}}</code></td><td><span class="tag">{{d.status}}</span></td><td><code>{{short(d.commit_id)}}</code></td><td>{{d.start_time||'-'}}</td><td><button class="link-button" @click="openDeploymentLogs(d)">查看</button></td></tr></tbody></table></div>
        <div v-if="(isArtifact&&!history.length)||(!isArtifact&&!deployments.length)" class="empty-state">暂无部署记录。</div>
      </section>
    </template>
  </div>
</template>
