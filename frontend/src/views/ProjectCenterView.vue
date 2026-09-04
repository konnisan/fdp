<script setup>
import { computed, onMounted, ref } from 'vue'
import { Box, ExternalLink, Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getRuntimeStatus, listArtifactDeliveryProjects } from '../api'
import { normalizeArtifactProject } from '../project-model'

const emit=defineEmits(['navigate'])
const rawProjects=ref([])
const runtime=ref(null)
const keyword=ref('')
const status=ref('ALL')
const loading=ref(false)
const error=ref('')
const projects=computed(()=>rawProjects.value.map(normalizeArtifactProject))
const filtered=computed(()=>projects.value.filter(p=>{
  const q=keyword.value.trim().toLowerCase()
  const text=`${p.projectName||''} ${p.projectCode||''} ${p.pipelineName||''} ${p.artifactName||''} ${p.containerName||''} ${p.image||''}`.toLowerCase()
  return (!q||text.includes(q))&&(status.value==='ALL'||p.status===status.value)
}))
function short(v){return v&&v!=='DRY-RUN'?String(v).slice(0,16):(v||'-')}
function targetPath(p){return `/containers/artifact/${p.id}`}
function previewUrl(p){
  const port=Number(runtime.value?.publicPort||0)
  const base=port?`${window.location.protocol}//${window.location.hostname}:${port}`:`${window.location.protocol}//${window.location.host}`
  return `${base}${p.previewPath||''}`
}
function dockerStatus(){
  const tools=Array.isArray(runtime.value?.tools)?runtime.value.tools:[]
  const daemon=tools.find(t=>String(t.name||'').toLowerCase().includes('docker'))
  if(daemon?.available)return 'Docker Ready'
  if(String(runtime.value?.executionMode||'').includes('DRY'))return 'Windows DRY-RUN'
  return 'Docker 未就绪'
}
async function load(){
  loading.value=true;error.value=''
  try{const [items,rt]=await Promise.all([listArtifactDeliveryProjects(),getRuntimeStatus()]);rawProjects.value=items;runtime.value=rt}
  catch(e){error.value=e.response?.data?.message||e.message}
  finally{loading.value=false}
}
onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="容器部署" description="Flow 构建完成后，Packages 中的制品在这里进入 FDP。选择版本、配置 Docker，并在服务器上运行和管理容器。">
      <template #actions>
        <button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14" />{{loading?'刷新中…':'刷新'}}</button>
        <button class="primary-button" @click="emit('navigate','/containers/new')"><Plus :size="15" />新增容器部署</button>
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
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索项目 / Flow / Artifact / Container" /></label>
        <select v-model="status"><option value="ALL">全部状态</option><option value="DRAFT">DRAFT</option><option value="QUEUED">QUEUED</option><option value="RUNNING">RUNNING</option><option value="STOPPED">STOPPED</option><option value="FAILED">FAILED</option><option value="DEPLOYING">DEPLOYING</option></select>
        <span class="toolbar-count">{{filtered.length}} 个容器部署</span>
      </div>

      <div class="table-wrap">
        <table class="data-table project-table unified-project-table">
          <thead><tr><th>项目</th><th>Flow</th><th>Packages 制品</th><th>当前镜像</th><th>Container</th><th>宿主机端口</th><th>状态</th><th>版本</th><th>访问</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="p in filtered" :key="p.key">
              <td><button class="project-name" @click="emit('navigate',targetPath(p))">{{p.projectName}}</button><code>{{p.projectCode}}</code></td>
              <td><strong>{{p.pipelineName||p.pipelineId||'-'}}</strong><small class="cell-note">#{{p.pipelineId||'-'}}</small></td>
              <td><code>{{p.artifactName||'-'}}</code></td>
              <td><code>{{p.image||'-'}}</code></td>
              <td><code>{{p.containerName||'-'}}</code></td>
              <td><code>{{p.hostPort||'-'}}</code></td>
              <td><span class="status-text" :class="p.status?.toLowerCase()"><i></i>{{p.status}}</span></td>
              <td><code>{{short(p.version)}}</code></td>
              <td><a v-if="p.previewPath" :href="previewUrl(p)" target="_blank">{{p.previewPath}} <ExternalLink :size="12" /></a><span v-else class="muted">-</span></td>
              <td><button class="soft-button" @click="emit('navigate',targetPath(p))">管理 Docker</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filtered.length" class="empty-state">{{loading?'正在读取容器部署…':'暂无容器部署。可以先运行 Flow，在制品仓库选择产物后点击“放入容器部署”。'}}</div>
    </section>
  </div>
</template>
