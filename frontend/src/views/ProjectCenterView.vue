<script setup>
import { computed, onMounted, ref } from 'vue'
import { ExternalLink, Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getRuntimeStatus, listArtifactDeliveryProjects, listProjects } from '../api'
import { normalizeArtifactProject, normalizeSourceProject, profileMeta, projectPath } from '../project-model'

const emit=defineEmits(['navigate'])
const sourceProjects=ref([])
const artifactProjects=ref([])
const runtime=ref(null)
const keyword=ref('')
const profile=ref('ALL')
const status=ref('ALL')
const loading=ref(false)
const error=ref('')
const projects=computed(()=>[
  ...sourceProjects.value.map(normalizeSourceProject),
  ...artifactProjects.value.map(normalizeArtifactProject)
])
const filtered=computed(()=>projects.value.filter(p=>{
  const q=keyword.value.trim().toLowerCase()
  const text=`${p.projectName||''} ${p.projectCode||''} ${p.gitUrl||''} ${p.pipelineName||''}`.toLowerCase()
  return (!q||text.includes(q))&&(profile.value==='ALL'||p.profile===profile.value)&&(status.value==='ALL'||p.status===status.value)
}))
function short(v){return v&&v!=='DRY-RUN'?String(v).slice(0,12):(v||'-')}
function deliverySource(p){return p.kind==='artifact'?'Flow / Packages':p.profile==='STATIC'?'Codeup / Static':'Codeup / Docker'}
function previewUrl(p){const port=Number(runtime.value?.publicPort||0);const base=port?`${window.location.protocol}//${window.location.hostname}:${port}`:`${window.location.protocol}//${window.location.host}`;return `${base}${p.previewPath||''}`}
async function load(){loading.value=true;error.value='';try{const [source,artifact,rt]=await Promise.all([listProjects(),listArtifactDeliveryProjects(),getRuntimeStatus()]);sourceProjects.value=source;artifactProjects.value=artifact;runtime.value=rt}catch(e){error.value=e.response?.data?.message||e.message}finally{loading.value=false}}
onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="项目中心" description="所有交付项目使用统一项目模型；Profile 决定默认部署配置，底层仍由同一套部署能力执行。">
      <template #actions><button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14" />{{loading?'刷新中…':'刷新'}}</button><button class="primary-button" @click="emit('navigate','/projects/new')"><Plus :size="15" />新建项目</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>

    <section class="panel">
      <div class="toolbar">
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索项目名称 / 编码 / Flow / Git" /></label>
        <select v-model="profile"><option value="ALL">全部 Profile</option><option value="STATIC">STATIC</option><option value="LIGHTWEIGHT">LIGHTWEIGHT</option><option value="STANDARD">STANDARD</option><option value="CUSTOM">CUSTOM</option></select>
        <select v-model="status"><option value="ALL">全部状态</option><option value="DRAFT">DRAFT</option><option value="PUBLISHED">PUBLISHED</option><option value="RUNNING">RUNNING</option><option value="STOPPED">STOPPED</option><option value="FAILED">FAILED</option><option value="DEPLOYING">DEPLOYING</option></select>
        <span class="toolbar-count">{{filtered.length}} 个项目</span>
      </div>
      <div class="table-wrap">
        <table class="data-table project-table unified-project-table">
          <thead><tr><th>项目</th><th>Profile</th><th>交付来源</th><th>当前版本</th><th>运行状态</th><th>客户入口</th><th>运行单元</th><th>操作</th></tr></thead>
          <tbody><tr v-for="p in filtered" :key="p.key">
            <td><button class="project-name" @click="emit('navigate',projectPath(p))">{{p.projectName}}</button><code>{{p.projectCode}}</code></td>
            <td><span class="profile-badge" :data-tone="profileMeta(p.profile).tone">{{p.profile}}</span><small class="cell-note">{{profileMeta(p.profile).label}}</small></td>
            <td><strong>{{deliverySource(p)}}</strong><small class="cell-note">{{p.kind==='artifact'?(p.pipelineName||p.pipelineId):(p.gitBranch||'-')}}</small></td>
            <td><code>{{short(p.version)}}</code></td>
            <td><span class="status-text" :class="p.status?.toLowerCase()"><i></i>{{p.status}}</span></td>
            <td><a :href="previewUrl(p)" target="_blank">{{p.previewPath}} <ExternalLink :size="12" /></a></td>
            <td><template v-if="p.profile==='STATIC'"><span class="muted">Nginx Static</span></template><template v-else><code>{{p.containerName||'-'}}</code><small class="cell-note">127.0.0.1:{{p.hostPort||'-'}}</small></template></td>
            <td><button class="soft-button" @click="emit('navigate',projectPath(p))">进入项目</button></td>
          </tr></tbody>
        </table>
      </div>
      <div v-if="!filtered.length" class="empty-state">{{loading?'正在读取项目…':'暂无符合条件的项目'}}</div>
    </section>
  </div>
</template>
