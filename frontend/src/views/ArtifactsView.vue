<script setup>
import { computed, onMounted, ref } from 'vue'
import { Box, Boxes, ChevronRight, PackageSearch, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getYunxiaoStatus, listYunxiaoArtifacts, listYunxiaoRepositories } from '../api'

const emit=defineEmits(['navigate'])
const DRAFT_KEY='fdp-managed-project-draft'
const PICK_TARGET_KEY='fdp-artifact-selection-target'
const EDIT_DRAFT_PREFIX='fdp-managed-project-edit-draft-'
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
const pickTarget=computed(()=>readPickTarget())
function time(v){if(!v)return '-';const n=Number(v);return Number.isFinite(n)?new Date(n).toLocaleString():String(v)}
function err(e){return e.response?.data?.message||e.message||'操作失败'}
function readJson(key,fallback){try{return JSON.parse(sessionStorage.getItem(key)||'')||fallback}catch{return fallback}}
function readPickTarget(){
  const value=readJson(PICK_TARGET_KEY,null)
  if(!value||!value.mode)return null
  if(value.createdAt&&Date.now()-Number(value.createdAt)>30*60*1000){sessionStorage.removeItem(PICK_TARGET_KEY);return null}
  return value
}
function editDraftKey(id){return `${EDIT_DRAFT_PREFIX}${id}`}
function appendArtifact(list,selected){
  const items=Array.isArray(list)?[...list]:[]
  const index=items.findIndex(item=>String(item.repositoryId||item.repoId||'')===selected.repositoryId&&String(item.artifactName||'')===selected.artifactName)
  if(index>=0)items[index]={...items[index],...selected}
  else items.push(selected)
  return items.map((item,index)=>({...item,sortOrder:index}))
}
async function load(){
  loading.value=true;error.value=''
  try{
    const [s,repos]=await Promise.all([getYunxiaoStatus(),listYunxiaoRepositories({repoTypes:'GENERIC',page:1,perPage:30})])
    status.value=s;repositories.value=repos
    if(selectedRepo.value){
      selectedRepo.value=repos.find(r=>String(r.repoId)===String(selectedRepo.value.repoId))||null
      if(selectedRepo.value)await openRepo(selectedRepo.value)
    }else if(repos.length===1){
      await openRepo(repos[0])
    }
  }catch(e){error.value=err(e)}finally{loading.value=false}
}
async function openRepo(repo){
  selectedRepo.value=repo;artifacts.value=[];keyword.value='';error.value=''
  try{artifacts.value=await listYunxiaoArtifacts(repo.repoId,{repoType:repo.repoType||'GENERIC',page:1,perPage:30})}
  catch(e){error.value=err(e)}
}
function deployArtifact(a){
  const latest=a.versions?.[0]||{}
  const selected={repositoryId:String(selectedRepo.value?.repoId||''),repositoryName:selectedRepo.value?.repoName||'',artifactName:a.module||'',latestVersion:latest.version||'',targetDirectory:'.'}
  const target=readPickTarget()
  if(target?.mode==='edit'&&target.projectId){
    const key=editDraftKey(target.projectId)
    const draft=readJson(key,{form:null,artifacts:[]})
    draft.artifacts=appendArtifact(draft.artifacts,selected)
    sessionStorage.setItem(key,JSON.stringify(draft))
    sessionStorage.removeItem(PICK_TARGET_KEY)
    emit('navigate',`/containers/${target.projectId}/edit`)
    return
  }
  const draft=target?.mode==='create'?readJson(DRAFT_KEY,{artifacts:[]}):{artifacts:[]}
  draft.artifacts=appendArtifact(draft.artifacts,selected)
  sessionStorage.setItem(DRAFT_KEY,JSON.stringify(draft))
  sessionStorage.removeItem(PICK_TARGET_KEY)
  emit('navigate','/containers/new')
}

onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page artifacts-page plane-page">
    <PageHeader title="制品仓库">
      <template #actions>
        <button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="15" />{{loading?'读取中…':'刷新'}}</button>
      </template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>
    <div v-if="pickTarget" class="selection-context">
      <Box :size="16"/>
      <strong>{{pickTarget.mode==='edit'?`为项目 #${pickTarget.projectId} 选择制品`:'为新项目选择制品'}}</strong>
    </div>

    <section class="connection-strip" :class="{offline:status&&!status.configured}">
      <div class="connection-main"><PackageSearch :size="17"/><strong>云效 Packages</strong></div>
      <span class="state-badge" :data-state="status?.configured?'running':'failed'"><i></i>{{status?.configured?'已连接':'未配置'}}</span>
    </section>

    <section class="panel artifact-browser">
      <aside class="repository-rail">
        <div class="repository-rail-head"><h2><Boxes :size="17"/>仓库</h2></div>
        <div class="repository-list">
          <button v-for="repo in repositories" :key="repo.repoId" type="button" class="repository-item" :class="{active:String(selectedRepo?.repoId||'')===String(repo.repoId)}" @click="openRepo(repo)">
            <span class="repository-item-icon"><Boxes :size="16"/></span>
            <span class="repository-item-copy"><strong>{{repo.repoName}}</strong></span>
            <ChevronRight :size="15"/>
          </button>
          <div v-if="!repositories.length" class="rail-empty">{{loading?'正在加载…':'暂无仓库'}}</div>
        </div>
      </aside>

      <div class="artifact-pane">
        <template v-if="selectedRepo">
          <div class="artifact-toolbar">
            <div class="artifact-toolbar-title"><strong>{{selectedRepo.repoName}}</strong></div>
            <label class="search-box artifact-search"><Search :size="16" /><input v-model="keyword" placeholder="搜索制品" /></label>
            <button class="icon-button" title="刷新" @click="openRepo(selectedRepo)"><RefreshCw :size="15" /></button>
          </div>
          <div class="artifact-table-wrap">
            <table class="data-table artifact-table">
              <thead><tr><th>制品</th><th>最新版本</th><th>更新时间</th><th>版本数</th><th class="actions-column">操作</th></tr></thead>
              <tbody>
                <tr v-for="a in filtered" :key="a.id||a.module">
                  <td><div class="artifact-name-cell"><span class="artifact-file-icon"><PackageSearch :size="16"/></span><strong>{{a.module}}</strong></div></td>
                  <td><code>{{a.versions?.[0]?.version||'-'}}</code></td>
                  <td>{{time(a.latestUpdate)}}</td>
                  <td>{{a.versions?.length||0}}</td>
                  <td class="actions-column"><button class="primary-button" @click="deployArtifact(a)"><Box :size="14" />{{pickTarget?.mode==='edit'?'绑定':'用于部署'}}</button></td>
                </tr>
              </tbody>
            </table>
            <div v-if="!filtered.length" class="empty-state">{{loading?'正在加载…':'暂无制品'}}</div>
          </div>
        </template>
        <div v-else class="artifact-placeholder"><strong>请选择仓库</strong></div>
      </div>
    </section>
  </div>
</template>
