<script setup>
import { computed, onMounted, ref } from 'vue'
import { Box, Boxes, RefreshCw, Search } from 'lucide-vue-next'
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
  const selected={
    repositoryId:String(selectedRepo.value?.repoId||''),
    repositoryName:selectedRepo.value?.repoName||'',
    artifactName:a.module||'',
    latestVersion:latest.version||'',
    targetDirectory:'.'
  }
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

  let draft
  if(target?.mode==='create'){
    draft=readJson(DRAFT_KEY,{artifacts:[]})
  }else{
    // 从制品仓库直接点“部署”代表开始一个全新的项目，不继承上一次新建草稿。
    draft={artifacts:[]}
  }
  draft.artifacts=appendArtifact(draft.artifacts,selected)
  sessionStorage.setItem(DRAFT_KEY,JSON.stringify(draft))
  sessionStorage.removeItem(PICK_TARGET_KEY)
  emit('navigate','/containers/new')
}

onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="制品仓库" description="查看云效 Packages 中已经构建完成的制品。制品可以带入新建项目，也可以绑定到已有项目。">
      <template #actions>
        <button class="primary-button" :disabled="loading" @click="load"><RefreshCw :size="14" />{{loading?'读取中…':'刷新仓库'}}</button>
      </template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>
    <div v-if="pickTarget" class="success-banner">
      {{pickTarget.mode==='edit'?`正在为项目 #${pickTarget.projectId} 添加制品`:'正在为新项目选择制品'}}。选择后会返回对应项目页面。
    </div>

    <section v-if="status" class="panel" style="padding:15px 18px">
      <div style="display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap">
        <div><strong>云效 Packages</strong><div style="font-size:12px;color:#64748b;margin-top:4px">{{status.domain}} · GENERIC 制品仓库</div></div>
        <span class="status-text" :class="status.configured?'running':'failed'"><i></i>{{status.configured?'CONNECTED':'NOT CONFIGURED'}}</span>
      </div>
    </section>

    <section class="panel">
      <div class="panel-head"><div><h2><Boxes :size="18" />制品仓库</h2><p>先选择仓库，再从其中选择要绑定到项目的 Artifact。</p></div></div>
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
              <td><button class="primary-button" @click="deployArtifact(a)"><Box :size="14" />{{pickTarget?.mode==='edit'?'绑定到项目':'部署'}}</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filtered.length" class="empty-state">该仓库暂无匹配制品。</div>
    </section>
  </div>
</template>
