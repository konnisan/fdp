<script setup>
import { computed, onMounted, ref } from 'vue'
import { Boxes, ExternalLink, FolderKanban, Monitor, Plus, Server, Workflow } from 'lucide-vue-next'
import { getRuntimeStatus, listArtifactDeliveryProjects, listDeployments, listProjects } from '../api'
import { normalizeArtifactProject, normalizeSourceProject, profileMeta, projectPath } from '../project-model'

const emit=defineEmits(['navigate'])
const sourceProjects=ref([])
const artifactProjects=ref([])
const deployments=ref([])
const runtime=ref(null)
const loading=ref(true)
const error=ref('')
const projects=computed(()=>[
  ...sourceProjects.value.map(normalizeSourceProject),
  ...artifactProjects.value.map(normalizeArtifactProject)
])
const running=computed(()=>projects.value.filter(p=>['RUNNING','PUBLISHED'].includes(p.status)).length)
const containerCount=computed(()=>projects.value.filter(p=>p.profile!=='STATIC').length)
const profileCounts=computed(()=>['STATIC','LIGHTWEIGHT','STANDARD','CUSTOM'].map(profile=>({profile,count:projects.value.filter(p=>p.profile===profile).length})))
const recent=computed(()=>deployments.value.slice(0,5))
function short(v){return v&&v!=='DRY-RUN'?String(v).slice(0,12):(v||'-')}
function sourceProjectName(id){return sourceProjects.value.find(p=>Number(p.id)===Number(id))?.projectName||`项目 #${id}`}
function previewUrl(p){const port=Number(runtime.value?.publicPort||0);const base=port?`${window.location.protocol}//${window.location.hostname}:${port}`:`${window.location.protocol}//${window.location.host}`;return `${base}${p.previewPath||''}`}
async function load(){
  loading.value=true;error.value=''
  try{
    const [source,artifact,ds,rt]=await Promise.all([listProjects(),listArtifactDeliveryProjects(),listDeployments(),getRuntimeStatus()])
    sourceProjects.value=source;artifactProjects.value=artifact;deployments.value=ds;runtime.value=rt
  }catch(e){error.value=e.response?.data?.message||e.message}finally{loading.value=false}
}
onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <section class="overview-hero panel">
      <div>
        <h1>平台总览</h1>
        <p>Codeup / Flow / Packages 负责源码与构建，FDP 统一接管静态发布、Docker 运行单元、路由、数据卷与部署记录。</p>
      </div>
      <div class="row-actions"><button class="soft-button" @click="emit('navigate','/integrations')"><Boxes :size="14" />系统集成</button><button class="primary-button" @click="emit('navigate','/projects/new')"><Plus :size="15" />新建项目</button></div>
    </section>
    <div v-if="error" class="error-banner">{{error}}</div>

    <div class="metric-grid">
      <article class="metric-card"><span class="metric-icon"><FolderKanban :size="25" /></span><div><p>项目总数</p><strong>{{projects.length}}</strong><small>STATIC / LIGHTWEIGHT / STANDARD / CUSTOM</small></div></article>
      <article class="metric-card"><span class="metric-icon green"><Monitor :size="25" /></span><div><p>当前可访问</p><strong>{{running}}</strong><small>已发布或运行中的项目</small></div></article>
      <article class="metric-card"><span class="metric-icon orange"><Server :size="25" /></span><div><p>容器项目</p><strong>{{containerCount}}</strong><small>每个项目独立运行单元</small></div></article>
      <article class="metric-card"><span class="metric-icon red"><Workflow :size="25" /></span><div><p>部署任务</p><strong>{{deployments.length}}</strong><small>{{runtime?.executionMode||'检测中'}} · Windows 可 DRY-RUN</small></div></article>
    </div>

    <section class="panel">
      <div class="panel-head"><div><h2>Deployment Profiles</h2><p>三个主 Profile 覆盖常见项目，自定义 Profile 保留扩展能力。</p></div><button class="link-button" @click="emit('navigate','/projects/new')">创建项目</button></div>
      <div class="profile-summary-grid">
        <article v-for="item in profileCounts" :key="item.profile" class="profile-summary-card">
          <div class="profile-summary-top"><span class="profile-badge" :data-tone="profileMeta(item.profile).tone">{{item.profile}}</span><strong>{{item.count}}</strong></div>
          <h3>{{profileMeta(item.profile).label}}</h3><p>{{profileMeta(item.profile).description}}</p>
        </article>
      </div>
    </section>

    <div class="dashboard-grid">
      <section class="panel">
        <div class="panel-head"><div><h2>最近部署</h2><p>当前 V1 先保留原有 Task / Step / Log 执行模型。</p></div><button class="link-button" @click="emit('navigate','/deployments')">查看全部</button></div>
        <div v-if="recent.length" class="deployment-list"><button v-for="d in recent" :key="d.id" class="deployment-row compact-row" @click="emit('navigate',`/projects/source/${d.project_id}`)"><span class="dot" :class="d.status?.toLowerCase()"></span><strong>{{sourceProjectName(d.project_id)}}</strong><span class="tag">{{d.status}}</span><code>{{short(d.commit_id)}}</code><span class="muted">{{d.start_time||'-'}}</span></button></div>
        <div v-else class="empty-state">{{loading?'正在加载…':'暂无部署记录'}}</div>
      </section>

      <section class="panel">
        <div class="panel-head"><div><h2>项目运行状态</h2><p>源码型项目与 Flow/Packages 正式工程统一展示。</p></div><button class="link-button" @click="emit('navigate','/projects')">项目中心</button></div>
        <div class="table-wrap"><table class="data-table"><thead><tr><th>项目</th><th>Profile</th><th>状态</th><th>版本</th><th>访问</th></tr></thead><tbody>
          <tr v-for="p in projects.slice(0,6)" :key="p.key"><td><button class="project-name" @click="emit('navigate',projectPath(p))">{{p.projectName}}</button><code>{{p.projectCode}}</code></td><td><span class="profile-badge" :data-tone="profileMeta(p.profile).tone">{{p.profile}}</span></td><td><span class="status-text" :class="p.status?.toLowerCase()"><i></i>{{p.status}}</span></td><td><code>{{short(p.version)}}</code></td><td><a :href="previewUrl(p)" target="_blank">{{p.previewPath}} <ExternalLink :size="12" /></a></td></tr>
        </tbody></table></div>
      </section>
    </div>
  </div>
</template>
