<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Copy, ExternalLink, GitBranch, Play, RefreshCw, Square, Terminal } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { deployProject, getDeploymentLogs, getRuntimeLogs, listDeployments, listProjects, restartProject, stopProject } from '../api'

const props=defineProps({projectId:{type:Number,required:true}})
const emit=defineEmits(['navigate'])
const project=ref(null),deployments=ref([]),logs=ref([]),activeTab=ref('overview'),busy=ref(''),error=ref(''),logMode=ref('deployment')
const externalHost=window.location.host
const tabs=[['overview','概览'],['source','源码与构建'],['access','访问配置'],['deployments','发布记录'],['logs','运行日志']]
const latest=computed(()=>deployments.value[0]||null)
const previewUrl=computed(()=>project.value?`${window.location.protocol}//${window.location.host}${project.value.previewPath||''}`:'')
function short(v){return v&&v!=='DRY-RUN'?String(v).slice(0,12):(v||'-')}
async function load(){error.value='';try{const[ps,ds]=await Promise.all([listProjects(),listDeployments(props.projectId)]);project.value=ps.find(p=>Number(p.id)===Number(props.projectId))||null;deployments.value=ds;if(!project.value)error.value='项目不存在或已删除'}catch(e){error.value=e.response?.data?.message||e.message}}
async function act(type){if(!project.value)return;busy.value=type;error.value='';try{if(type==='deploy')await deployProject(project.value.id);if(type==='restart')await restartProject(project.value.id);if(type==='stop')await stopProject(project.value.id);await load()}catch(e){error.value=e.response?.data?.message||e.message}finally{busy.value=''}}
async function openLogs(d){activeTab.value='logs';logMode.value='deployment';try{logs.value=await getDeploymentLogs(d.id)}catch(e){error.value=e.response?.data?.message||e.message}}
async function runtimeLogs(){if(!project.value||project.value.projectType!=='CONTAINER')return;activeTab.value='logs';logMode.value='runtime';try{logs.value=[await getRuntimeLogs(project.value.id)]}catch(e){error.value=e.response?.data?.message||e.message}}
async function copy(text){try{await navigator.clipboard.writeText(text)}catch{}}
onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="project?.projectName||'交付项目详情'" :description="project?`${project.projectType} · ${project.projectCode}`:'加载中…'">
      <template #actions><button class="soft-button" @click="emit('navigate','/poc-projects')"><ArrowLeft :size="14" />返回列表</button><a v-if="project" class="soft-button" :href="previewUrl" target="_blank">打开访问<ExternalLink :size="14" /></a><button v-if="project" class="primary-button" :disabled="busy" @click="act('deploy')"><RefreshCw :size="14" />拉取并部署</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>

    <template v-if="project">
      <section class="project-hero panel"><div class="project-identity"><span class="project-avatar">{{project.projectName?.slice(0,1)}}</span><div><div class="hero-line"><h2>{{project.projectName}}</h2><span class="type-badge">{{project.projectType}}</span><span class="status-text" :class="project.status?.toLowerCase()"><i></i>{{project.status}}</span></div><p>{{project.gitUrl}}</p></div></div><div class="project-version"><span>Branch <b>{{project.gitBranch}}</b></span><span>Commit <code>{{short(project.deployedCommit)}}</code></span></div></section>
      <div class="tabs"><button v-for="t in tabs" :key="t[0]" :class="{active:activeTab===t[0]}" @click="activeTab=t[0]">{{t[1]}}</button></div>

      <section v-if="activeTab==='overview'" class="detail-grid">
        <article class="panel info-card"><h3>运行状态</h3><dl><div><dt>当前状态</dt><dd><span class="status-text" :class="project.status?.toLowerCase()"><i></i>{{project.status}}</span></dd></div><div><dt>部署方式</dt><dd>{{project.projectType}}</dd></div><div><dt>内部目标</dt><dd>{{project.projectType==='CONTAINER'?`127.0.0.1:${project.hostPort} → container:${project.containerPort}`:'Nginx 静态目录'}}</dd></div></dl></article>
        <article class="panel info-card"><h3>当前版本</h3><dl><div><dt>Branch</dt><dd>{{project.gitBranch}}</dd></div><div><dt>Commit</dt><dd><code>{{short(project.deployedCommit)}}</code></dd></div><div><dt>Image</dt><dd><code>{{latest?.image_tag||'-'}}</code></dd></div></dl></article>
        <article class="panel info-card span-2"><h3>统一访问入口</h3><div class="preview-highlight"><div><strong>{{previewUrl}}</strong><p>Nginx 统一端口按 Path 分流到静态资源或 Docker 容器。</p></div><div><button class="soft-button" @click="copy(previewUrl)"><Copy :size="14" />复制</button><a class="primary-button" :href="previewUrl" target="_blank">打开<ExternalLink :size="14" /></a></div></div></article>
      </section>

      <section v-if="activeTab==='source'" class="detail-grid">
        <article class="panel info-card span-2"><h3><GitBranch :size="17" />Codeup 源码</h3><dl><div><dt>Git</dt><dd class="break">{{project.gitUrl}}</dd></div><div><dt>Branch</dt><dd>{{project.gitBranch}}</dd></div><div><dt>仓库内目录</dt><dd><code>{{project.projectDirectory||'.'}}</code></dd></div><div><dt>当前 Commit</dt><dd><code>{{short(project.deployedCommit)}}</code></dd></div></dl></article>
        <template v-if="project.projectType==='STATIC'">
          <article class="panel info-card"><h3>静态构建</h3><dl><div><dt>构建命令</dt><dd><code>{{project.buildCommand||'无需构建'}}</code></dd></div><div><dt>构建产物</dt><dd>{{project.buildOutput||'dist'}}</dd></div></dl></article>
          <article class="panel info-card"><h3>发布方式</h3><dl><div><dt>目标</dt><dd>Nginx 静态目录</dd></div><div><dt>共享服务器</dt><dd>是</dd></div></dl></article>
        </template>
        <template v-else>
          <article class="panel info-card"><h3>Docker 构建</h3><dl><div><dt>Dockerfile</dt><dd><code>{{project.dockerfilePath}}</code></dd></div><div><dt>Build Context</dt><dd><code>{{project.dockerBuildContext}}</code></dd></div><div><dt>Image</dt><dd><code>{{project.imageName}}</code></dd></div><div><dt>Container</dt><dd><code>{{project.containerName}}</code></dd></div></dl></article>
          <article class="panel info-card"><h3>资源与持久化</h3><dl><div><dt>CPU / Memory</dt><dd>{{project.cpuLimit}} CPU / {{project.memoryLimit}}</dd></div><div><dt>端口</dt><dd>{{project.hostPort}} → {{project.containerPort}}</dd></div><div><dt>Volume</dt><dd class="break">{{project.hostDataPath&&project.containerDataPath?`${project.hostDataPath} → ${project.containerDataPath}`:'未配置'}}</dd></div><div><dt>Health</dt><dd>{{project.healthCheckPath||'仅检查容器运行状态'}}</dd></div></dl></article>
        </template>
      </section>

      <section v-if="activeTab==='access'" class="panel route-panel"><div class="route-head"><div><h3>统一入口路由</h3><p>服务器只暴露一个外部端口，容器端口仅绑定 127.0.0.1。</p></div><a class="primary-button" :href="previewUrl" target="_blank">打开访问<ExternalLink :size="14" /></a></div><div class="route-flow"><div><span>外部访问</span><strong>{{externalHost}}</strong></div><div><span>Preview Path</span><strong>{{project.previewPath}}</strong></div><div><span>Nginx</span><strong>统一路由</strong></div><div><span>内部目标</span><strong>{{project.projectType==='CONTAINER'?`127.0.0.1:${project.hostPort}`:'STATIC 目录'}}</strong></div></div></section>

      <section v-if="activeTab==='deployments'" class="panel"><div class="panel-head"><div><h2>发布记录</h2><p>每次部署记录 Codeup Commit，并在容器模式下绑定 Docker Image Tag。</p></div></div><table class="data-table"><thead><tr><th>ID</th><th>状态</th><th>步骤</th><th>Commit</th><th>Image</th><th>开始时间</th><th>操作</th></tr></thead><tbody><tr v-for="d in deployments" :key="d.id"><td>#{{d.id}}</td><td><span class="tag">{{d.status}}</span></td><td>{{d.current_step}}</td><td><code>{{short(d.commit_id)}}</code></td><td><code>{{d.image_tag||'-'}}</code></td><td>{{d.start_time||'-'}}</td><td><button class="link-button" @click="openLogs(d)">部署日志</button></td></tr></tbody></table><div v-if="!deployments.length" class="empty-state">暂无部署记录</div></section>

      <section v-if="activeTab==='logs'" class="panel log-panel"><div class="panel-head"><div><h2><Terminal :size="17" />{{logMode==='runtime'?'容器运行日志':'部署日志'}}</h2><p>{{project.projectType==='STATIC'?'STATIC 只有部署日志。':`Container: ${project.containerName||'-'}`}}</p></div><div class="row-actions"><button v-if="project.projectType==='CONTAINER'" class="soft-button" @click="runtimeLogs"><RefreshCw :size="14" />容器日志</button><button v-if="project.projectType==='CONTAINER'" class="soft-button" :disabled="busy" @click="act('restart')"><Play :size="14" />重启容器</button><button v-if="project.projectType==='CONTAINER'" class="soft-button" :disabled="busy" @click="act('stop')"><Square :size="14" />停止容器</button></div></div><pre class="terminal">{{logs.length?logs.join('\n\n'):'请选择某次发布日志；容器项目也可以点击“容器日志”。'}}</pre></section>
    </template>
  </div>
</template>
