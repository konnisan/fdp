<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Copy, ExternalLink, GitBranch, Play, RefreshCw, Square, Terminal } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { deployProject, getDeploymentLogs, getDeploymentSteps, listDeployments, listProjects, restartProject, stopProject } from '../api'

const props = defineProps({ projectId: { type: Number, required: true } })
const emit = defineEmits(['navigate'])
const project = ref(null)
const deployments = ref([])
const logs = ref([])
const steps = ref([])
const selectedDeployment = ref(null)
const activeTab = ref('overview')
const busy = ref('')
const error = ref('')
const externalHost = window.location.host
const tabs = [['overview','概览'],['source','源码与构建'],['access','访问配置'],['deployments','发布记录'],['logs','运行日志']]
const latest = computed(() => deployments.value[0] || null)
const previewUrl = computed(() => project.value ? `${window.location.protocol}//${window.location.host}${project.value.previewPath || ''}` : '')
function short(v){return v&&v!=='DRY-RUN'?String(v).slice(0,8):(v||'-')}
async function load(){error.value='';try{const [ps,ds]=await Promise.all([listProjects(),listDeployments(props.projectId)]);project.value=ps.find(p=>Number(p.id)===Number(props.projectId))||null;deployments.value=ds;if(!project.value)error.value='项目不存在或已删除'}catch(e){error.value=e.response?.data?.message||e.message}}
async function act(type){if(!project.value)return;busy.value=type;error.value='';try{if(type==='deploy')await deployProject(project.value.id);if(type==='restart')await restartProject(project.value.id);if(type==='stop')await stopProject(project.value.id);await load()}catch(e){error.value=e.response?.data?.message||e.message}finally{busy.value=''}}
async function openLogs(d){activeTab.value='logs';selectedDeployment.value=d;try{[steps.value,logs.value]=await Promise.all([getDeploymentSteps(d.id),getDeploymentLogs(d.id)])}catch(e){error.value=e.response?.data?.message||e.message}}
async function copy(text){try{await navigator.clipboard.writeText(text)}catch{}}
onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="project?.projectName || 'POC 项目详情'" :description="project ? `${project.projectType} · ${project.projectCode}` : '加载中…'">
      <template #actions><button class="soft-button" @click="emit('navigate','/poc-projects')"><ArrowLeft :size="14" />返回列表</button><a v-if="project" class="soft-button" :href="previewUrl" target="_blank">打开预览<ExternalLink :size="14" /></a><button v-if="project" class="primary-button" :disabled="busy" @click="act('deploy')"><RefreshCw :size="14" />拉取并部署</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{ error }}</div>

    <template v-if="project">
      <section class="project-hero panel"><div class="project-identity"><span class="project-avatar">{{ project.projectName?.slice(0,1) }}</span><div><div class="hero-line"><h2>{{ project.projectName }}</h2><span class="type-badge">{{ project.projectType }}</span><span class="status-text" :class="project.status?.toLowerCase()"><i></i>{{ project.status }}</span></div><p>{{ project.gitUrl }}</p></div></div><div class="project-version"><span>Branch <b>{{ project.gitBranch }}</b></span><span>Commit <code>{{ short(project.deployedCommit) }}</code></span></div></section>
      <div class="tabs"><button v-for="t in tabs" :key="t[0]" :class="{active:activeTab===t[0]}" @click="activeTab=t[0]">{{t[1]}}</button></div>

      <section v-if="activeTab==='overview'" class="detail-grid">
        <article class="panel info-card"><h3>运行状态</h3><dl><div><dt>当前状态</dt><dd><span class="status-text" :class="project.status?.toLowerCase()"><i></i>{{project.status}}</span></dd></div><div><dt>内部地址</dt><dd>{{project.internalPort?`127.0.0.1:${project.internalPort}`:'静态站点'}}</dd></div><div><dt>PM2</dt><dd>{{project.pm2Name||'-'}}</dd></div></dl></article>
        <article class="panel info-card"><h3>当前版本</h3><dl><div><dt>Branch</dt><dd>{{project.gitBranch}}</dd></div><div><dt>Commit</dt><dd><code>{{short(project.deployedCommit)}}</code></dd></div><div><dt>最近部署</dt><dd>{{latest?.start_time||'-'}}</dd></div></dl></article>
        <article class="panel info-card span-2"><h3>客户预览</h3><div class="preview-highlight"><div><strong>{{ previewUrl }}</strong><p>Nginx 统一入口映射到当前 POC</p></div><div><button class="soft-button" @click="copy(previewUrl)"><Copy :size="14" />复制</button><a class="primary-button" :href="previewUrl" target="_blank">打开<ExternalLink :size="14" /></a></div></div></article>
      </section>

      <section v-if="activeTab==='source'" class="detail-grid">
        <article class="panel info-card span-2"><h3><GitBranch :size="17" />源码仓库</h3><dl><div><dt>Codeup Git</dt><dd class="break">{{project.gitUrl}}</dd></div><div><dt>Branch</dt><dd>{{project.gitBranch}}</dd></div><div><dt>当前部署 Commit</dt><dd><code>{{short(project.deployedCommit)}}</code></dd></div></dl></article>
        <article class="panel info-card"><h3>构建设置</h3><dl><div><dt>构建命令</dt><dd><code>{{project.buildCommand||'无需构建'}}</code></dd></div><div><dt>构建产物</dt><dd>{{project.buildOutput||'-'}}</dd></div></dl></article>
        <article class="panel info-card"><h3>运行设置</h3><dl><div><dt>启动命令</dt><dd><code>{{project.startCommand||'-'}}</code></dd></div><div><dt>SQLite</dt><dd>{{project.sqlitePath||'-'}}</dd></div></dl></article>
      </section>

      <section v-if="activeTab==='access'" class="panel route-panel"><div class="route-head"><div><h3>统一预览入口</h3><p>服务器只暴露一个端口，Nginx 根据 Path 分流到不同 POC。</p></div><a class="primary-button" :href="previewUrl" target="_blank">打开预览<ExternalLink :size="14" /></a></div><div class="route-flow"><div><span>外部访问</span><strong>{{externalHost}}</strong></div><div><span>Preview Path</span><strong>{{project.previewPath}}</strong></div><div><span>Nginx</span><strong>统一路由</strong></div><div><span>内部目标</span><strong>{{project.internalPort?`127.0.0.1:${project.internalPort}`:'STATIC 目录'}}</strong></div></div></section>

      <section v-if="activeTab==='deployments'" class="panel"><div class="panel-head"><div><h2>发布记录</h2><p>一次部署对应一个 Task，后台按步骤执行并持续记录状态。</p></div></div><table class="data-table"><thead><tr><th>ID</th><th>状态</th><th>当前步骤</th><th>Commit</th><th>开始时间</th><th>操作</th></tr></thead><tbody><tr v-for="d in deployments" :key="d.id"><td>#{{d.id}}</td><td><span class="tag">{{d.status}}</span></td><td>{{d.current_step}}</td><td><code>{{short(d.commit_id)}}</code></td><td>{{d.start_time||'-'}}</td><td><button class="link-button" @click="openLogs(d)">执行详情</button></td></tr></tbody></table><div v-if="!deployments.length" class="empty-state">暂无部署记录</div></section>

      <section v-if="activeTab==='logs'" class="panel log-panel"><div class="panel-head"><div><h2><Terminal :size="17" />运行 / 部署日志</h2><p>{{ selectedDeployment ? `Deployment #${selectedDeployment.id} · ${selectedDeployment.status}` : (project.projectType==='STATIC' ? '静态 POC 无常驻 Node.js 进程。' : `PM2: ${project.pm2Name||'-'}`) }}</p></div><div class="row-actions" v-if="project.projectType==='NODE_SQLITE'"><button class="soft-button" :disabled="busy" @click="act('restart')"><Play :size="14" />重启</button><button class="soft-button" :disabled="busy" @click="act('stop')"><Square :size="14" />停止</button></div></div><div class="table-wrap" v-if="steps.length"><table class="data-table"><thead><tr><th>步骤</th><th>状态</th><th>开始</th><th>结束</th></tr></thead><tbody><tr v-for="s in steps" :key="s.id"><td><strong>{{s.step_name}}</strong><br><code>{{s.step_code}}</code></td><td><span class="tag">{{s.status}}</span></td><td>{{s.start_time||'-'}}</td><td>{{s.end_time||'-'}}</td></tr></tbody></table></div><pre class="terminal">{{ logs.length ? logs.join('\n\n') : '请选择发布记录中的“执行详情”，或执行一次部署。' }}</pre></section>
    </template>
  </div>
</template>
