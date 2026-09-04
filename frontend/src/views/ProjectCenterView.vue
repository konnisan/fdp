<script setup>
import { computed, onMounted, ref } from 'vue'
import { Box, ExternalLink, Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getRuntimeStatus, listArtifactDeliveryProjects, listProjects } from '../api'
import { normalizeArtifactProject, normalizeSourceProject, projectPath } from '../project-model'

const emit=defineEmits(['navigate'])
const sourceProjects=ref([])
const artifactProjects=ref([])
const runtime=ref(null)
const keyword=ref('')
const sourceType=ref('ALL')
const status=ref('ALL')
const loading=ref(false)
const error=ref('')

const projects=computed(()=>[
  ...sourceProjects.value
    .filter(p=>p.projectType!=='STATIC')
    .map(normalizeSourceProject),
  ...artifactProjects.value.map(normalizeArtifactProject)
])

const filtered=computed(()=>projects.value.filter(p=>{
  const q=keyword.value.trim().toLowerCase()
  const text=`${p.projectName||''} ${p.projectCode||''} ${p.gitUrl||''} ${p.pipelineName||''} ${p.containerName||''} ${p.image||''}`.toLowerCase()
  const type=p.kind==='artifact'?'ARTIFACT':'CODEUP'
  return (!q||text.includes(q))&&(sourceType.value==='ALL'||type===sourceType.value)&&(status.value==='ALL'||p.status===status.value)
}))

function short(v){return v&&v!=='DRY-RUN'?String(v).slice(0,12):(v||'-')}
function sourceLabel(p){return p.kind==='artifact'?'Flow / Packages':'Codeup + Dockerfile'}
function targetPath(p){return `/containers/${p.kind}/${p.id}`}
function previewUrl(p){
  const port=Number(runtime.value?.publicPort||0)
  const base=port?`${window.location.protocol}//${window.location.hostname}:${port}`:`${window.location.protocol}//${window.location.host}`
  return `${base}${p.previewPath||''}`
}
function dockerStatus(){
  const tools=Array.isArray(runtime.value?.tools)?runtime.value.tools:[]
  const daemon=tools.find(t=>t.name==='Docker daemon')
  if(daemon?.available)return 'Docker Ready'
  if(String(runtime.value?.executionMode||'').includes('DRY'))return 'Windows DRY-RUN'
  return 'Docker 未就绪'
}
async function load(){
  loading.value=true;error.value=''
  try{
    const [source,artifact,rt]=await Promise.all([listProjects(),listArtifactDeliveryProjects(),getRuntimeStatus()])
    sourceProjects.value=source;artifactProjects.value=artifact;runtime.value=rt
  }catch(e){error.value=e.response?.data?.message||e.message}
  finally{loading.value=false}
}
onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="容器部署" description="这里不区分 Node、Spring Boot 或其他技术栈。项目已经存在，FDP 只负责读取代码/制品，并按项目自己的 Docker 配置部署、运行和管理。">
      <template #actions>
        <button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14" />{{loading?'刷新中…':'刷新'}}</button>
        <button class="primary-button" @click="emit('navigate','/containers/new')"><Plus :size="15" />接入容器项目</button>
      </template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>

    <section class="panel" style="padding:15px 18px">
      <div style="display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap">
        <div style="display:flex;align-items:center;gap:10px"><span class="runtime-icon"><Box :size="18" /></span><div><strong>Docker 运行环境</strong><div style="font-size:12px;color:#64748b;margin-top:3px">{{runtime?.os||'-'}} · {{runtime?.executionMode||'-'}} · 对外端口 {{runtime?.publicPort||'-'}}</div></div></div>
        <span class="tag">{{dockerStatus()}}</span>
      </div>
    </section>

    <section class="panel">
      <div class="toolbar">
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索项目 / 容器 / 镜像 / Git / Flow" /></label>
        <select v-model="sourceType"><option value="ALL">全部来源</option><option value="CODEUP">Codeup 源码</option><option value="ARTIFACT">Flow / Packages</option></select>
        <select v-model="status"><option value="ALL">全部状态</option><option value="DRAFT">DRAFT</option><option value="RUNNING">RUNNING</option><option value="STOPPED">STOPPED</option><option value="FAILED">FAILED</option><option value="DEPLOYING">DEPLOYING</option><option value="PUBLISHED">PUBLISHED</option></select>
        <span class="toolbar-count">{{filtered.length}} 个容器项目</span>
      </div>

      <div class="table-wrap">
        <table class="data-table project-table unified-project-table">
          <thead><tr><th>项目</th><th>部署来源</th><th>镜像</th><th>Container</th><th>端口</th><th>状态</th><th>版本</th><th>访问</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="p in filtered" :key="p.key">
              <td><button class="project-name" @click="emit('navigate',targetPath(p))">{{p.projectName}}</button><code>{{p.projectCode}}</code></td>
              <td><strong>{{sourceLabel(p)}}</strong><small class="cell-note">{{p.kind==='artifact'?(p.pipelineName||p.pipelineId):(p.gitBranch||'-')}}</small></td>
              <td><code>{{p.image||'-'}}</code></td>
              <td><code>{{p.containerName||'-'}}</code></td>
              <td><code>{{p.hostPort||'-'}} → {{p.containerPort||'manifest'}}</code></td>
              <td><span class="status-text" :class="p.status?.toLowerCase()"><i></i>{{p.status}}</span></td>
              <td><code>{{short(p.version)}}</code></td>
              <td><a v-if="p.previewPath" :href="previewUrl(p)" target="_blank">{{p.previewPath}} <ExternalLink :size="12" /></a><span v-else class="muted">-</span></td>
              <td><button class="soft-button" @click="emit('navigate',targetPath(p))">管理 Docker</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filtered.length" class="empty-state">{{loading?'正在读取容器项目…':'暂无容器项目。点击“接入容器项目”，绑定已有 Codeup 代码或 Packages 制品。'}}</div>
    </section>
  </div>
</template>
