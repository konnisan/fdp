<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Box, Copy, Database, ExternalLink, FileArchive, FileCode2, GitBranch, ListChecks, Play, RefreshCw, RotateCcw, Square, Terminal, Trash2 } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  deployArtifactRelease,
  deployProject,
  getArtifactDeploymentPlan,
  getArtifactRuntime,
  getArtifactRuntimeLogs,
  getDeploymentLogs,
  getDeploymentPlan,
  getRuntimeLogs,
  getRuntimeStatus,
  listArtifactDeliveryHistory,
  listArtifactDeliveryProjects,
  listArtifactDeliveryReleases,
  listDeployments,
  listProjects,
  listStaticEntries,
  removeArtifactContainer,
  restartArtifactProject,
  restartProject,
  stopArtifactProject,
  stopProject
} from '../api'
import { normalizeArtifactProject, normalizeSourceProject, profileMeta } from '../project-model'

const props=defineProps({projectKind:{type:String,required:true},projectId:{type:Number,required:true}})
const emit=defineEmits(['navigate'])
const project=ref(null)
const raw=ref(null)
const runtime=ref(null)
const artifactRuntime=ref(null)
const releases=ref([])
const history=ref([])
const deployments=ref([])
const staticEntries=ref([])
const logs=ref('')
const plan=ref(null)
const activeTab=ref('overview')
const loading=ref(true)
const busy=ref('')
const error=ref('')
const info=ref('')
const tabs=[['overview','概览'],['artifacts','版本与制品'],['runtime','运行单元'],['routes','路由与数据'],['deployments','部署记录']]
const isArtifact=computed(()=>props.projectKind==='artifact')
const isStatic=computed(()=>project.value?.profile==='STATIC')
const publicOrigin=computed(()=>{const port=Number(runtime.value?.publicPort||0);return port?`${window.location.protocol}//${window.location.hostname}:${port}`:`${window.location.protocol}//${window.location.host}`})
const previewUrl=computed(()=>project.value?`${publicOrigin.value}${project.value.previewPath||''}`:'')
const windowsDryRun=computed(()=>String(runtime.value?.executionMode||'').toUpperCase().includes('DRY')||String(runtime.value?.os||'').toLowerCase().includes('win'))
function short(v){return v&&v!=='DRY-RUN'?String(v).slice(0,12):(v||'-')}
function err(e){return e.response?.data?.message||e.message}
function time(v){if(!v)return '-';const n=Number(v);return Number.isFinite(n)?new Date(n).toLocaleString():String(v)}
async function copy(text){try{await navigator.clipboard.writeText(text);info.value='已复制'}catch{}}
async function load(){
  loading.value=true;error.value='';info.value=''
  try{
    runtime.value=await getRuntimeStatus()
    if(isArtifact.value){
      const list=await listArtifactDeliveryProjects();raw.value=list.find(p=>Number(p.id)===Number(props.projectId))||null
      if(!raw.value)throw new Error('项目不存在或已删除')
      project.value=normalizeArtifactProject(raw.value)
      const [rs,hs,rt,pl]=await Promise.all([listArtifactDeliveryReleases(props.projectId),listArtifactDeliveryHistory(props.projectId),getArtifactRuntime(props.projectId),getArtifactDeploymentPlan(props.projectId)])
      releases.value=rs;history.value=hs;artifactRuntime.value=rt;plan.value=pl
    }else{
      const list=await listProjects();raw.value=list.find(p=>Number(p.id)===Number(props.projectId))||null
      if(!raw.value)throw new Error('项目不存在或已删除')
      project.value=normalizeSourceProject(raw.value)
      const [ds,pl]=await Promise.all([listDeployments(props.projectId),getDeploymentPlan(props.projectId)])
      deployments.value=ds;plan.value=pl
      if(raw.value.projectType==='STATIC')staticEntries.value=await listStaticEntries(props.projectId)
    }
  }catch(e){error.value=err(e)}finally{loading.value=false}
}
async function deploySource(){busy.value='deploy';error.value='';info.value='';try{const result=await deployProject(props.projectId);info.value=`部署任务 #${result.taskId} 已提交${windowsDryRun.value?'（DRY-RUN）':''}`;await load()}catch(e){error.value=err(e)}finally{busy.value=''}}
async function deployRelease(release){
  if(windowsDryRun.value){activeTab.value='artifacts';info.value='Windows 开发模式不执行正式 Packages 制品部署；下方“部署计划”可用于验证配置。';return}
  if(!confirm(`部署 ${project.value.projectName} 版本 ${release.version}（Flow #${release.runId}）？`))return
  busy.value=`release-${release.runId}`;error.value='';try{await deployArtifactRelease(props.projectId,String(release.runId));info.value='正式制品部署已提交';await load()}catch(e){error.value=err(e)}finally{busy.value=''}
}
async function runtimeAction(action){busy.value=action;error.value='';info.value='';try{let result;if(isArtifact.value){if(action==='restart')result=await restartArtifactProject(props.projectId);if(action==='stop')result=await stopArtifactProject(props.projectId);if(action==='remove')result=await removeArtifactContainer(props.projectId)}else{if(action==='restart')result=await restartProject(props.projectId);if(action==='stop')result=await stopProject(props.projectId)}info.value=result?.output||`${action} 已完成${windowsDryRun.value?'（DRY-RUN）':''}`;await load()}catch(e){error.value=err(e)}finally{busy.value=''}}
async function openLogs(){activeTab.value='runtime';busy.value='logs';error.value='';try{logs.value=isArtifact.value?await getArtifactRuntimeLogs(props.projectId):await getRuntimeLogs(props.projectId)}catch(e){error.value=err(e)}finally{busy.value=''}}
async function openDeploymentLogs(item){busy.value='deployment-log';error.value='';try{logs.value=(await getDeploymentLogs(item.id)).join('\n\n');activeTab.value='runtime'}catch(e){error.value=err(e)}finally{busy.value=''}}
onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader :title="project?.projectName||'项目详情'" :description="project?`${project.profile} · ${project.projectCode}`:'加载中…'">
      <template #actions><button class="soft-button" @click="emit('navigate','/projects')"><ArrowLeft :size="14" />返回项目中心</button><button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14" />刷新</button><a v-if="project" class="soft-button" :href="previewUrl" target="_blank">客户预览 <ExternalLink :size="14" /></a><button v-if="project&&!isArtifact" class="primary-button" :disabled="busy" @click="deploySource"><Play :size="14" />{{windowsDryRun?'模拟部署':'部署'}}</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>
    <div v-if="info" class="success-banner">{{info}}</div>

    <template v-if="project">
      <section class="project-hero panel unified-project-hero">
        <div class="project-identity"><span class="project-avatar">{{project.projectName?.slice(0,1)}}</span><div><div class="hero-line"><h2>{{project.projectName}}</h2><span class="profile-badge" :data-tone="profileMeta(project.profile).tone">{{project.profile}}</span><span class="status-text" :class="project.status?.toLowerCase()"><i></i>{{project.status}}</span></div><p>{{profileMeta(project.profile).description}}</p></div></div>
        <div class="project-version"><span>Version <code>{{short(project.version)}}</code></span><span>Runtime <b>{{isStatic?'Nginx Static':project.containerName||'-'}}</b></span><span>Mode <b>{{runtime?.executionMode||'-'}}</b></span></div>
      </section>

      <div class="tabs project-detail-tabs"><button v-for="tab in tabs" :key="tab[0]" :class="{active:activeTab===tab[0]}" @click="activeTab=tab[0]">{{tab[1]}}</button></div>

      <section v-if="activeTab==='overview'" class="detail-grid">
        <article class="panel info-card"><h3>当前 Release</h3><dl><div><dt>Profile</dt><dd>{{project.profile}}</dd></div><div><dt>状态</dt><dd><span class="status-text" :class="project.status?.toLowerCase()"><i></i>{{project.status}}</span></dd></div><div><dt>版本</dt><dd><code>{{short(project.version)}}</code></dd></div><div><dt>交付来源</dt><dd>{{isArtifact?'Flow / Packages':'Codeup'}}</dd></div></dl></article>
        <article class="panel info-card"><h3>运行目标</h3><dl><div><dt>Frontend</dt><dd>{{isArtifact||isStatic?'Nginx Static':'Container 内或项目镜像决定'}}</dd></div><div><dt>Backend</dt><dd>{{isStatic?'无':project.containerName||'-'}}</dd></div><div><dt>Host Port</dt><dd>{{project.hostPort||'-'}}</dd></div><div><dt>Image</dt><dd><code>{{project.image||'-'}}</code></dd></div></dl></article>
        <article class="panel info-card span-2"><h3>客户预览入口</h3><div class="preview-highlight"><div><strong>{{previewUrl}}</strong><p>Nginx 对外预览端口 {{runtime?.publicPort||'-'}}；管理端与客户预览端保持分离。</p></div><div><button class="soft-button" @click="copy(previewUrl)"><Copy :size="14" />复制</button><a class="primary-button" :href="previewUrl" target="_blank">打开 <ExternalLink :size="14" /></a></div></div></article>
        <article class="panel info-card span-2"><h3><ListChecks :size="16" />部署计划</h3><div class="plan-strip"><div v-for="(step,index) in plan?.steps||[]" :key="step.code||index"><span>{{String(index+1).padStart(2,'0')}}</span><strong>{{step.name||step.stepName||step.code}}</strong><small>{{step.code}}</small></div></div></article>
      </section>

      <section v-if="activeTab==='artifacts'" class="page-stack">
        <article v-if="isArtifact" class="panel info-card">
          <div class="panel-head"><div><h2><FileArchive :size="18" />Flow / Packages 绑定</h2><p>CI 由云效 Flow 维护，FDP 只选择成功版本并部署。</p></div></div>
          <dl><div><dt>Pipeline</dt><dd>{{raw.pipelineName||raw.pipelineId}} <code>#{{raw.pipelineId}}</code></dd></div><div><dt>Packages</dt><dd>{{raw.packageRepoName||raw.packageRepoId}}</dd></div><div><dt>Artifact</dt><dd><code>{{raw.artifactName}}</code></dd></div><div><dt>当前 Run</dt><dd><code>{{raw.currentRunId||'-'}}</code></dd></div></dl>
        </article>
        <article v-if="isArtifact" class="panel">
          <div class="panel-head"><div><h2>可部署版本</h2><p>{{windowsDryRun?'Windows DRY-RUN：可读取版本与部署计划，但不执行正式 docker load / Nginx 发布。':'选择成功 Flow Run 对应的 Packages 版本部署。'}}</p></div></div>
          <div class="table-wrap"><table class="data-table"><thead><tr><th>建议</th><th>Flow Run</th><th>版本</th><th>更新时间</th><th>MD5</th><th>操作</th></tr></thead><tbody><tr v-for="r in releases" :key="r.runId"><td><span v-if="r.recommended" class="tag">推荐</span><span v-else>-</span></td><td><code>#{{r.runId}}</code></td><td><strong>{{r.version}}</strong></td><td>{{time(r.updateTime||r.createTime)}}</td><td><code>{{r.md5||'-'}}</code></td><td><button class="primary-button" :disabled="busy!==''" @click="deployRelease(r)">{{windowsDryRun?'验证计划':'部署此版本'}}</button></td></tr></tbody></table></div><div v-if="!releases.length" class="empty-state">暂无可部署成功版本。</div>
        </article>
        <article v-else class="panel info-card">
          <h3><GitBranch :size="17" />Codeup 源码与版本</h3><dl><div><dt>Git</dt><dd class="break">{{raw.gitUrl}}</dd></div><div><dt>Branch</dt><dd>{{raw.gitBranch}}</dd></div><div><dt>仓库目录</dt><dd><code>{{raw.projectDirectory||'.'}}</code></dd></div><div><dt>当前 Commit</dt><dd><code>{{short(raw.deployedCommit)}}</code></dd></div><div v-if="isStatic"><dt>发布目录</dt><dd><code>{{raw.buildOutput||'.'}}</code></dd></div></dl>
        </article>
        <article v-if="isStatic&&staticEntries.length" class="panel"><div class="panel-head"><div><h2><FileCode2 :size="18" />静态页面入口</h2><p>Codeup 静态目录中已识别的 index.html。</p></div></div><div class="table-wrap"><table class="data-table"><thead><tr><th>名称</th><th>路径</th><th>状态</th></tr></thead><tbody><tr v-for="entry in staticEntries" :key="entry.previewPath"><td><strong>{{entry.name}}</strong></td><td><code>{{entry.relativePath||'.'}}/index.html</code></td><td><span class="tag">{{entry.published?'已发布':'已识别'}}</span></td></tr></tbody></table></div></article>
      </section>

      <section v-if="activeTab==='runtime'" class="page-stack">
        <article v-if="isStatic" class="panel empty-state">STATIC Profile 没有 Docker 运行单元，前端文件直接由 Nginx 提供。</article>
        <template v-else>
          <article class="panel runtime-unit-card">
            <div class="runtime-unit-head"><div><span class="runtime-icon"><Box :size="21" /></span><div><h2>{{project.containerName}}</h2><p>{{isArtifact?(artifactRuntime?.runtimeImage||project.image||'-'):(project.image||'-')}}</p></div></div><span class="status-text" :class="project.status?.toLowerCase()"><i></i>{{artifactRuntime?.containerStatus||project.status}}</span></div>
            <div class="runtime-facts"><div><span>Host Port</span><strong>{{project.hostPort||'-'}}</strong></div><div><span>Container Port</span><strong>{{project.containerPort||'由镜像 manifest 声明'}}</strong></div><div><span>CPU</span><strong>{{raw.cpuLimit||'-'}}</strong></div><div><span>Memory</span><strong>{{raw.memoryLimit||'-'}}</strong></div><div><span>Health</span><strong>{{raw.healthCheckPath||'Container / Manifest'}}</strong></div><div><span>Runtime Mode</span><strong>{{artifactRuntime?.runtimeMode||runtime?.executionMode||'-'}}</strong></div></div>
            <div class="runtime-actions"><button class="soft-button" :disabled="busy" @click="runtimeAction('restart')"><RotateCcw :size="14" />重启</button><button class="soft-button" :disabled="busy" @click="runtimeAction('stop')"><Square :size="14" />停止</button><button class="soft-button" :disabled="busy" @click="openLogs"><Terminal :size="14" />查看日志</button><button v-if="isArtifact" class="icon-button danger" :disabled="busy" title="删除容器" @click="runtimeAction('remove')"><Trash2 :size="14" /></button></div>
          </article>
          <article v-if="logs" class="panel log-panel"><div class="panel-head"><div><h2><Terminal :size="17" />运行日志</h2><p>{{project.containerName}}</p></div><button class="soft-button" @click="logs=''">收起</button></div><pre class="terminal">{{logs}}</pre></article>
        </template>
      </section>

      <section v-if="activeTab==='routes'" class="detail-grid">
        <article class="panel info-card"><h3>统一 Nginx 路由</h3><dl><div><dt>Public</dt><dd>{{publicOrigin}}</dd></div><div><dt>Preview Path</dt><dd><code>{{project.previewPath}}</code></dd></div><div><dt>Frontend</dt><dd>{{isArtifact||isStatic?'Static Root':'Proxy to Container'}}</dd></div><div><dt>API / Backend</dt><dd>{{isArtifact?`127.0.0.1:${project.hostPort}/api/`:isStatic?'无':`127.0.0.1:${project.hostPort}`}}</dd></div></dl></article>
        <article class="panel info-card"><h3><Database :size="16" />数据与环境</h3><dl><div><dt>Profile</dt><dd>{{project.profile}}</dd></div><div><dt>Volume</dt><dd class="break">{{raw.hostDataPath&&raw.containerDataPath?`${raw.hostDataPath} → ${raw.containerDataPath}`:project.profile==='STANDARD'?'外部 / Shared DB，由 env 注入':'未配置'}}</dd></div><div><dt>Env File</dt><dd class="break">{{raw.envFile||'-'}}</dd></div><div><dt>数据库建议</dt><dd>{{project.profile==='LIGHTWEIGHT'?'SQLite 放宿主机 Volume':project.profile==='STANDARD'?'Shared MySQL / 项目独立 schema':'按项目配置'}}</dd></div></dl></article>
      </section>

      <section v-if="activeTab==='deployments'" class="panel">
        <div class="panel-head"><div><h2>部署记录</h2><p>同一个项目内保留版本、状态、时间与结果；历史成功版本可作为回退依据。</p></div></div>
        <div v-if="isArtifact" class="table-wrap"><table class="data-table"><thead><tr><th>ID</th><th>Flow Run</th><th>版本</th><th>状态</th><th>镜像</th><th>开始</th><th>结果</th></tr></thead><tbody><tr v-for="h in history" :key="h.id"><td>#{{h.id}}</td><td>#{{h.pipelineRunId}}</td><td><code>{{h.artifactVersion}}</code></td><td><span class="tag">{{h.status}}</span></td><td><code>{{h.imageTag||'-'}}</code></td><td>{{time(h.startTime)}}</td><td>{{h.message||'-'}}</td></tr></tbody></table></div>
        <div v-else class="table-wrap"><table class="data-table"><thead><tr><th>ID</th><th>状态</th><th>当前步骤</th><th>Commit</th><th>Image</th><th>开始</th><th>操作</th></tr></thead><tbody><tr v-for="d in deployments" :key="d.id"><td>#{{d.id}}</td><td><span class="tag">{{d.status}}</span></td><td>{{d.current_step}}</td><td><code>{{short(d.commit_id)}}</code></td><td><code>{{d.image_tag||'-'}}</code></td><td>{{d.start_time||'-'}}</td><td><button class="link-button" @click="openDeploymentLogs(d)">日志</button></td></tr></tbody></table></div>
        <div v-if="isArtifact?!history.length:!deployments.length" class="empty-state">暂无部署记录。</div>
      </section>
    </template>
    <div v-else-if="loading" class="panel empty-state">正在加载项目…</div>
  </div>
</template>
