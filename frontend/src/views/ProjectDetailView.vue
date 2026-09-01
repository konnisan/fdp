<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ArrowLeft, Copy, ExternalLink, FileCode2, FolderGit2, GitBranch, ListChecks, Play, RefreshCw, Square, Terminal } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  deployProject,
  getDeploymentLogs,
  getDeploymentPlan,
  getRuntimeLogs,
  getRuntimeStatus,
  listDeployments,
  listProjects,
  listStaticEntries,
  restartProject,
  stopProject,
  syncProjectSource
} from '../api'

const props=defineProps({projectId:{type:Number,required:true}})
const emit=defineEmits(['navigate'])
const project=ref(null),deployments=ref([]),logs=ref([]),activeTab=ref('overview'),busy=ref(''),error=ref(''),logMode=ref('deployment')
const showPlan=ref(false),plan=ref(null),planLoading=ref(false)
const runtime=ref(null),staticEntries=ref([]),sourceSyncResult=ref(null)
let alive=true
const tabs=[['overview','概览'],['source','源码与构建'],['access','访问配置'],['deployments','发布记录'],['logs','运行日志']]
const latest=computed(()=>deployments.value[0]||null)
const publicOrigin=computed(()=>{
  const port=Number(runtime.value?.publicPort||0)
  const protocol=window.location.protocol
  const host=window.location.hostname
  return port?`${protocol}//${host}:${port}`:`${protocol}//${window.location.host}`
})
const previewUrl=computed(()=>project.value?`${publicOrigin.value}${project.value.previewPath||''}`:'')
function entryUrl(entry){return `${publicOrigin.value}${encodeURI(entry.previewPath||'')}`}
function short(v){return v&&v!=='DRY-RUN'?String(v).slice(0,12):(v||'-')}
function sleep(ms){return new Promise(resolve=>setTimeout(resolve,ms))}
async function refreshStaticEntries(){
  if(!project.value||project.value.projectType!=='STATIC'){staticEntries.value=[];return}
  try{staticEntries.value=await listStaticEntries(project.value.id)}catch(e){error.value=e.response?.data?.message||e.message}
}
async function load(){
  error.value=''
  try{
    const[ps,ds,rt]=await Promise.all([listProjects(),listDeployments(props.projectId),getRuntimeStatus()])
    project.value=ps.find(p=>Number(p.id)===Number(props.projectId))||null
    deployments.value=ds
    runtime.value=rt
    if(!project.value){error.value='项目不存在或已删除';return}
    await refreshStaticEntries()
  }catch(e){error.value=e.response?.data?.message||e.message}
}
async function waitTask(taskId){for(let i=0;i<240&&alive;i++){await sleep(1500);const ds=await listDeployments(props.projectId);deployments.value=ds;const task=ds.find(d=>Number(d.id)===Number(taskId));if(task&&['SUCCESS','FAILED'].includes(task.status)){await load();return task}}return null}
async function act(type){if(!project.value)return;busy.value=type;error.value='';try{if(type==='deploy'){const result=await deployProject(project.value.id);busy.value='watch';await waitTask(result.taskId)}if(type==='restart'){await restartProject(project.value.id);await load()}if(type==='stop'){await stopProject(project.value.id);await load()}}catch(e){error.value=e.response?.data?.message||e.message}finally{busy.value=''}}
async function syncSource(){
  if(!project.value||project.value.projectType!=='STATIC')return
  busy.value='sync'
  error.value=''
  sourceSyncResult.value=null
  try{
    sourceSyncResult.value=await syncProjectSource(project.value.id)
    staticEntries.value=sourceSyncResult.value?.entries||[]
  }catch(e){error.value=e.response?.data?.message||e.message}
  finally{busy.value=''}
}
async function openPlan(){if(!project.value)return;showPlan.value=true;planLoading.value=true;try{plan.value=await getDeploymentPlan(project.value.id)}catch(e){error.value=e.response?.data?.message||e.message;showPlan.value=false}finally{planLoading.value=false}}
async function openLogs(d){activeTab.value='logs';logMode.value='deployment';try{logs.value=await getDeploymentLogs(d.id)}catch(e){error.value=e.response?.data?.message||e.message}}
async function runtimeLogs(){if(!project.value||project.value.projectType!=='CONTAINER')return;activeTab.value='logs';logMode.value='runtime';try{logs.value=[await getRuntimeLogs(project.value.id)]}catch(e){error.value=e.response?.data?.message||e.message}}
async function copy(text){try{await navigator.clipboard.writeText(text)}catch{}}
onMounted(load)
onUnmounted(()=>{alive=false})
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="project?.projectName||'交付项目详情'" :description="project?`${project.projectType} · ${project.projectCode}`:'加载中…'">
      <template #actions>
        <button class="soft-button" @click="emit('navigate','/poc-projects')"><ArrowLeft :size="14" />返回列表</button>
        <button v-if="project" class="soft-button" @click="openPlan"><ListChecks :size="14" />部署计划</button>
        <button v-if="project?.projectType==='STATIC'" class="soft-button" :disabled="busy" @click="syncSource"><FolderGit2 :size="14" />{{busy==='sync'?'拉取中…':'仅拉取源码'}}</button>
        <a v-if="project" class="soft-button" :href="previewUrl" target="_blank">打开访问<ExternalLink :size="14" /></a>
        <button v-if="project" class="primary-button" :disabled="busy" @click="act('deploy')"><RefreshCw :size="14" />{{busy==='watch'?'部署执行中…':project.projectType==='STATIC'?'拉取并发布':'部署'}}</button>
      </template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>
    <div v-if="sourceSyncResult" class="error-banner" style="border-color:#bbf7d0;background:#f0fdf4;color:#166534">
      Codeup 源码已同步：{{short(sourceSyncResult.commit)}} · 已识别 {{staticEntries.length}} 个静态入口。现在可以点击“拉取并发布”。
    </div>

    <template v-if="project">
      <section class="project-hero panel"><div class="project-identity"><span class="project-avatar">{{project.projectName?.slice(0,1)}}</span><div><div class="hero-line"><h2>{{project.projectName}}</h2><span class="type-badge">{{project.projectType}}</span><span class="status-text" :class="project.status?.toLowerCase()"><i></i>{{project.status}}</span></div><p>{{project.gitUrl}}</p></div></div><div class="project-version"><span>Branch <b>{{project.gitBranch}}</b></span><span>Commit <code>{{short(project.deployedCommit)}}</code></span></div></section>
      <div class="tabs"><button v-for="t in tabs" :key="t[0]" :class="{active:activeTab===t[0]}" @click="activeTab=t[0]">{{t[1]}}</button></div>

      <section v-if="activeTab==='overview'" class="detail-grid">
        <article class="panel info-card"><h3>运行状态</h3><dl><div><dt>当前状态</dt><dd><span class="status-text" :class="project.status?.toLowerCase()"><i></i>{{project.status}}</span></dd></div><div><dt>部署方式</dt><dd>{{project.projectType}}</dd></div><div><dt>内部目标</dt><dd>{{project.projectType==='CONTAINER'?`127.0.0.1:${project.hostPort} → container:${project.containerPort}`:'Nginx 静态目录'}}</dd></div></dl></article>
        <article class="panel info-card"><h3>当前版本</h3><dl><div><dt>Branch</dt><dd>{{project.gitBranch}}</dd></div><div><dt>已部署 Commit</dt><dd><code>{{short(project.deployedCommit)}}</code></dd></div><div><dt>Image</dt><dd><code>{{latest?.image_tag||'-'}}</code></dd></div></dl></article>
        <article class="panel info-card span-2"><h3>统一访问入口</h3><div class="preview-highlight"><div><strong>{{previewUrl}}</strong><p>FDP 管理端口与客户预览端口分离；这里使用 Nginx 对外预览端口 {{runtime?.publicPort||'-'}}。</p></div><div><button class="soft-button" @click="copy(previewUrl)"><Copy :size="14" />复制</button><a class="primary-button" :href="previewUrl" target="_blank">打开<ExternalLink :size="14" /></a></div></div></article>
      </section>

      <section v-if="activeTab==='source'" class="detail-grid">
        <article class="panel info-card span-2"><h3><GitBranch :size="17" />Codeup 源码</h3><dl><div><dt>Git</dt><dd class="break">{{project.gitUrl}}</dd></div><div><dt>Branch</dt><dd>{{project.gitBranch}}</dd></div><div><dt>仓库内目录</dt><dd><code>{{project.projectDirectory||'.'}}</code></dd></div><div><dt>已部署 Commit</dt><dd><code>{{short(project.deployedCommit)}}</code></dd></div></dl></article>
        <template v-if="project.projectType==='STATIC'">
          <article class="panel info-card"><h3>静态发布配置</h3><dl><div><dt>构建命令</dt><dd><code>{{project.buildCommand||'无需构建'}}</code></dd></div><div><dt>发布源目录</dt><dd><code>{{project.buildOutput||'.'}}</code></dd></div><div><dt>Preview Path</dt><dd><code>{{project.previewPath}}</code></dd></div></dl></article>
          <article class="panel info-card"><h3>操作说明</h3><dl><div><dt>仅拉取源码</dt><dd>clone / fetch / reset 到最新 Codeup Branch，不改 Nginx。</dd></div><div><dt>拉取并发布</dt><dd>再次同步 Git，再 rsync 到静态目录并刷新 Nginx。</dd></div></dl></article>

          <article class="panel info-card span-2">
            <div class="panel-head"><div><h3><FileCode2 :size="17" />已识别静态页面</h3><p>扫描发布源目录下一层包含 index.html 的目录。FS 上传的每个 POC 会在这里单独显示。</p></div><button class="soft-button" :disabled="busy" @click="syncSource"><RefreshCw :size="14" />重新拉取并扫描</button></div>
            <div v-if="staticEntries.length" class="table-wrap">
              <table class="data-table"><thead><tr><th>POC</th><th>仓库相对路径</th><th>发布状态</th><th>访问地址</th></tr></thead><tbody>
                <tr v-for="entry in staticEntries" :key="entry.previewPath">
                  <td><strong>{{entry.name}}</strong></td>
                  <td><code>{{entry.relativePath||'.'}}/index.html</code></td>
                  <td><span class="tag">{{entry.published?'已发布':'仅已拉取'}}</span></td>
                  <td><a :href="entryUrl(entry)" target="_blank">{{entry.previewPath}} <ExternalLink :size="12" /></a></td>
                </tr>
              </tbody></table>
            </div>
            <div v-else class="empty-state">工作区暂未识别到静态页面。点击“仅拉取源码”，成功后应能看到例如“索通智能工厂/index.html”。</div>
          </article>
        </template>
        <template v-else>
          <article class="panel info-card"><h3>Docker 构建（兼容模式）</h3><dl><div><dt>Dockerfile</dt><dd><code>{{project.dockerfilePath}}</code></dd></div><div><dt>Build Context</dt><dd><code>{{project.dockerBuildContext}}</code></dd></div><div><dt>Image</dt><dd><code>{{project.imageName}}</code></dd></div><div><dt>Container</dt><dd><code>{{project.containerName}}</code></dd></div></dl></article>
          <article class="panel info-card"><h3>资源与持久化</h3><dl><div><dt>CPU / Memory</dt><dd>{{project.cpuLimit}} CPU / {{project.memoryLimit}}</dd></div><div><dt>端口</dt><dd>{{project.hostPort}} → {{project.containerPort}}</dd></div><div><dt>Volume</dt><dd class="break">{{project.hostDataPath&&project.containerDataPath?`${project.hostDataPath} → ${project.containerDataPath}`:'未配置'}}</dd></div><div><dt>Health</dt><dd>{{project.healthCheckPath||'仅检查容器运行状态'}}</dd></div></dl></article>
        </template>
      </section>

      <section v-if="activeTab==='access'" class="panel route-panel"><div class="route-head"><div><h3>统一入口路由</h3><p>客户预览从 Nginx 端口 {{runtime?.publicPort||'-'}} 访问；管理端页面不直接承载 POC。</p></div><a class="primary-button" :href="previewUrl" target="_blank">打开访问<ExternalLink :size="14" /></a></div><div class="route-flow"><div><span>外部预览</span><strong>{{publicOrigin}}</strong></div><div><span>Preview Path</span><strong>{{project.previewPath}}</strong></div><div><span>Nginx</span><strong>统一路由</strong></div><div><span>内部目标</span><strong>{{project.projectType==='CONTAINER'?`127.0.0.1:${project.hostPort}`:'STATIC 目录'}}</strong></div></div></section>

      <section v-if="activeTab==='deployments'" class="panel"><div class="panel-head"><div><h2>发布记录</h2><p>STATIC 每次“拉取并发布”记录 Codeup Commit；正式工程后续将改为 Codeup Pipeline 产物部署。</p></div></div><table class="data-table"><thead><tr><th>ID</th><th>状态</th><th>步骤</th><th>Commit</th><th>Image</th><th>开始时间</th><th>操作</th></tr></thead><tbody><tr v-for="d in deployments" :key="d.id"><td>#{{d.id}}</td><td><span class="tag">{{d.status}}</span></td><td>{{d.current_step}}</td><td><code>{{short(d.commit_id)}}</code></td><td><code>{{d.image_tag||'-'}}</code></td><td>{{d.start_time||'-'}}</td><td><button class="link-button" @click="openLogs(d)">部署日志</button></td></tr></tbody></table><div v-if="!deployments.length" class="empty-state">暂无部署记录</div></section>

      <section v-if="activeTab==='logs'" class="panel log-panel"><div class="panel-head"><div><h2><Terminal :size="17" />{{logMode==='runtime'?'容器运行日志':'部署日志'}}</h2><p>{{project.projectType==='STATIC'?'STATIC 只有部署日志。':`Container: ${project.containerName||'-'}`}}</p></div><div class="row-actions"><button v-if="project.projectType==='CONTAINER'" class="soft-button" @click="runtimeLogs"><RefreshCw :size="14" />容器日志</button><button v-if="project.projectType==='CONTAINER'" class="soft-button" :disabled="busy" @click="act('restart')"><Play :size="14" />重启容器</button><button v-if="project.projectType==='CONTAINER'" class="soft-button" :disabled="busy" @click="act('stop')"><Square :size="14" />停止容器</button></div></div><pre class="terminal">{{logs.length?logs.join('\n\n'):'请选择某次发布日志；容器项目也可以点击“容器日志”。'}}</pre></section>
    </template>

    <div v-if="showPlan" class="modal-mask" @click.self="showPlan=false"><div class="modal-card large"><header><div><h2>部署计划</h2><p>{{plan?.projectName||project?.projectName}} · {{plan?.mode||'加载中'}}</p></div><button class="soft-button" @click="showPlan=false">关闭</button></header><div v-if="planLoading" class="empty-state">正在生成部署计划…</div><template v-else-if="plan"><div v-if="plan.warnings?.length" class="error-banner" style="margin:16px"><div v-for="w in plan.warnings" :key="w">{{w}}</div></div><div class="table-wrap"><table class="data-table"><thead><tr><th>步骤</th><th>名称</th><th>计划内容</th></tr></thead><tbody><tr v-for="s in plan.steps" :key="s.code"><td><code>{{s.code}}</code></td><td><strong>{{s.name}}</strong></td><td class="break"><code>{{s.detail}}</code></td></tr></tbody></table></div></template></div></div>
  </div>
</template>