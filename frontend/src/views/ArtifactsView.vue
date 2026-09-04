<script setup>
import { computed, onMounted, ref } from 'vue'
import { Box, Boxes, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getYunxiaoStatus, listYunxiaoArtifacts, listYunxiaoRepositories } from '../api'

const emit=defineEmits(['navigate'])
const status=ref(null)
const repositories=ref([])
const selectedRepo=ref(null)
const artifacts=ref([])
const keyword=ref('')
const loading=ref(false)
const error=ref('')

const filtered=computed(()=>{
  const q=keyword.value.trim().toLowerCase()
  return q?artifacts.value.filter(a=>`${a.module||''} ${a.organization||''}`.toLowerCase().includes(q)):artifacts.value
})
function time(v){if(!v)return '-';const n=Number(v);return Number.isFinite(n)?new Date(n).toLocaleString():String(v)}
function err(e){return e.response?.data?.message||e.message||'操作失败'}
async function load(){
  loading.value=true;error.value=''
  try{
    const [s,repos]=await Promise.all([getYunxiaoStatus(),listYunxiaoRepositories({repoTypes:'GENERIC',page:1,perPage:30})])
    status.value=s;repositories.value=repos
    if(selectedRepo.value){
      selectedRepo.value=repos.find(r=>String(r.repoId)===String(selectedRepo.value.repoId))||null
      if(selectedRepo.value)await openRepo(selectedRepo.value)
    }
  }catch(e){error.value=err(e)}finally{loading.value=false}
}
async function openRepo(repo){
  selectedRepo.value=repo;artifacts.value=[];error.value=''
  try{artifacts.value=await listYunxiaoArtifacts(repo.repoId,{repoType:repo.repoType||'GENERIC',page:1,perPage:30})}
  catch(e){error.value=err(e)}
}
function deployArtifact(a){
  const latest=a.versions?.[0]||{}
  sessionStorage.setItem('fdp-container-artifact-seed',JSON.stringify({
    repoId:selectedRepo.value?.repoId||'',
    repoName:selectedRepo.value?.repoName||'',
    artifactName:a.module||'',
    latestVersion:latest.version||'',
    updatedAt:a.latestUpdate||null
  }))
  emit('navigate','/containers/new')
}

onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="制品仓库" description="查看 Flow 已经上传到云效 Packages 的构建产物。选择需要部署的制品后，交给“容器部署”配置 Docker 并运行。">
      <template #actions>
        <button class="soft-button" @click="emit('navigate','/pipelines')">返回流水线</button>
        <button class="primary-button" :disabled="loading" @click="load"><RefreshCw :size="14" />{{loading?'读取中…':'刷新仓库'}}</button>
      </template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>

    <section v-if="status" class="panel" style="padding:15px 18px">
      <div style="display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap">
        <div><strong>云效 Packages</strong><div style="font-size:12px;color:#64748b;margin-top:4px">{{status.domain}} · GENERIC 制品仓库</div></div>
        <span class="status-text" :class="status.configured?'running':'failed'"><i></i>{{status.configured?'CONNECTED':'NOT CONFIGURED'}}</span>
      </div>
    </section>

    <section class="panel">
      <div class="panel-head"><div><h2><Boxes :size="18" />制品仓库</h2><p>先选择仓库，再查看其中的 Artifact 与版本。</p></div></div>
      <div class="table-wrap"><table class="data-table"><thead><tr><th>仓库</th><th>ID</th><th>类型</th><th>说明</th><th>操作</th></tr></thead><tbody>
        <tr v-for="repo in repositories" :key="repo.repoId"><td><strong>{{repo.repoName}}</strong></td><td><code>{{repo.repoId}}</code></td><td>{{repo.repoType}}</td><td>{{repo.repoDesc||'-'}}</td><td><button class="soft-button" @click="openRepo(repo)">查看制品</button></td></tr>
      </tbody></table></div>
      <div v-if="!repositories.length" class="empty-state">暂无 GENERIC 制品仓库。</div>
    </section>

    <section v-if="selectedRepo" class="panel">
      <div class="toolbar">
        <div><strong>{{selectedRepo.repoName}}</strong><div style="font-size:12px;color:#64748b;margin-top:3px"><code>{{selectedRepo.repoId}}</code></div></div>
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索制品名称" /></label>
        <button class="soft-button" @click="openRepo(selectedRepo)"><RefreshCw :size="14" />刷新制品</button>
      </div>
      <div class="table-wrap">
        <table class="data-table">
          <thead><tr><th>制品</th><th>最新版本</th><th>更新时间</th><th>版本数</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="a in filtered" :key="a.id||a.module">
              <td><strong>{{a.module}}</strong><small class="cell-note">{{a.organization||'-'}}</small></td>
              <td><code>{{a.versions?.[0]?.version||'-'}}</code></td>
              <td>{{time(a.latestUpdate)}}</td>
              <td>{{a.versions?.length||0}}</td>
              <td><button class="primary-button" @click="deployArtifact(a)"><Box :size="14" />放入容器部署</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filtered.length" class="empty-state">该仓库暂无匹配制品。</div>
    </section>
  </div>
</template>
