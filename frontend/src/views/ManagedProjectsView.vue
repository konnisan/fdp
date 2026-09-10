<script setup>
import { computed, onMounted, ref } from 'vue'
import { Pencil, Play, Plus, RefreshCw, RotateCcw, Square, Trash2 } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {deleteManagedProject,listManagedProjects,restartManagedProject,startManagedProject,stopManagedProject} from '../api'

const emit=defineEmits(['navigate'])
const projects=ref([]),loading=ref(false),error=ref(''),info=ref(''),activeFilter=ref('all')
function err(e){return e.response?.data?.message||e.message||'操作失败'}
function startConfigured(project){return Boolean(project.startCommand)&&project.startCommand!=='__FDP_START_COMMAND_NOT_CONFIGURED__'}
function normalizedStatus(project){return String(project.deploymentStatus||'CREATED').toUpperCase()}
const stats=computed(()=>({
  total:projects.value.length,
  deployed:projects.value.filter(p=>Boolean(p.deployedVersionSummary)).length,
  running:projects.value.filter(p=>normalizedStatus(p)==='RUNNING').length,
  pending:projects.value.filter(p=>!startConfigured(p)||['FAILED','ERROR'].includes(normalizedStatus(p))).length
}))
const filteredProjects=computed(()=>{
  if(activeFilter.value==='deployed')return projects.value.filter(p=>Boolean(p.deployedVersionSummary))
  if(activeFilter.value==='running')return projects.value.filter(p=>normalizedStatus(p)==='RUNNING')
  if(activeFilter.value==='pending')return projects.value.filter(p=>!startConfigured(p)||['FAILED','ERROR'].includes(normalizedStatus(p)))
  return projects.value
})
function statusLabel(project){
  if(!startConfigured(project))return '待配置'
  const status=normalizedStatus(project)
  return ({CREATED:'待部署',DEPLOYED:'已部署',RUNNING:'运行中',STOPPED:'已停止',FAILED:'失败',ERROR:'异常'})[status]||status
}
function statusTone(project){
  if(!startConfigured(project))return 'pending'
  const status=normalizedStatus(project)
  if(status==='RUNNING')return 'running'
  if(status==='DEPLOYED')return 'deployed'
  if(status==='FAILED'||status==='ERROR')return 'failed'
  if(status==='STOPPED')return 'stopped'
  return 'pending'
}
function artifactNames(project){return (project.artifacts||[]).map(a=>a.artifactName).filter(Boolean).join('、')||'未绑定'}
function newProject(){
  sessionStorage.removeItem('fdp-managed-project-draft')
  sessionStorage.removeItem('fdp-artifact-selection-target')
  emit('navigate','/containers/new')
}
async function load(){loading.value=true;error.value='';try{projects.value=await listManagedProjects()}catch(e){error.value=err(e)}finally{loading.value=false}}
async function action(id,type){error.value='';info.value='';try{if(type==='start')await startManagedProject(id);if(type==='stop')await stopManagedProject(id);if(type==='restart')await restartManagedProject(id);await load()}catch(e){error.value=err(e)}}
async function removeProject(project){
  if(!window.confirm(`确认删除项目“${project.projectName}”？\n\n不会删除该项目对应的业务 Database。`))return
  error.value='';info.value=''
  try{
    await deleteManagedProject(project.id)
    sessionStorage.removeItem(`fdp-managed-project-edit-draft-${project.id}`)
    info.value=`项目“${project.projectName}”已删除。`
    await load()
  }catch(e){error.value=err(e)}
}
onMounted(()=>{
  const flash=sessionStorage.getItem('fdp-managed-project-flash')
  if(flash){info.value=flash;sessionStorage.removeItem('fdp-managed-project-flash')}
  load()
})
</script>

<template>
<div class="page-stack restructure-page managed-projects-page plane-page">
  <PageHeader title="项目部署">
    <template #actions>
      <button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="15"/>刷新</button>
      <button class="primary-button" @click="newProject"><Plus :size="15"/>新增项目</button>
    </template>
  </PageHeader>

  <div v-if="error" class="error-banner">{{error}}</div>
  <div v-if="info" class="success-banner">{{info}}</div>

  <div class="plane-stat-row" aria-label="项目筛选">
    <button class="plane-stat-filter" :class="{active:activeFilter==='all'}" @click="activeFilter='all'"><span>全部</span><strong>{{stats.total}}</strong></button>
    <button class="plane-stat-filter" :class="{active:activeFilter==='deployed'}" @click="activeFilter='deployed'"><span>已部署</span><strong>{{stats.deployed}}</strong></button>
    <button class="plane-stat-filter" :class="{active:activeFilter==='running'}" @click="activeFilter='running'"><span>运行中</span><strong>{{stats.running}}</strong></button>
    <button class="plane-stat-filter" :class="{active:activeFilter==='pending'}" @click="activeFilter='pending'"><span>待处理</span><strong>{{stats.pending}}</strong></button>
  </div>

  <section class="panel managed-project-panel plane-data-panel">
    <div class="table-wrap">
      <table class="data-table managed-projects-table plane-table">
        <thead><tr><th>项目</th><th>制品</th><th>版本</th><th>Runtime</th><th>Database</th><th>状态</th><th class="actions-column">操作</th></tr></thead>
        <tbody>
          <tr v-for="p in filteredProjects" :key="p.id">
            <td><button class="project-title-button" @click="emit('navigate',`/containers/${p.id}/edit`)">{{p.projectName}}</button></td>
            <td><span class="artifact-inline" :title="artifactNames(p)">{{artifactNames(p)}}</span></td>
            <td><code>{{p.deployedVersionSummary||'未部署'}}</code></td>
            <td>{{p.runtimeImage}}</td>
            <td><code>{{p.databaseName}}</code></td>
            <td><span class="state-badge" :data-state="statusTone(p)"><i></i>{{statusLabel(p)}}</span></td>
            <td class="actions-column">
              <div class="project-actions plane-project-actions">
                <button class="soft-button plane-edit-button" @click="emit('navigate',`/containers/${p.id}/edit`)"><Pencil :size="14"/>编辑</button>
                <button class="plane-icon-action" :disabled="!startConfigured(p)" title="启动" @click="action(p.id,'start')"><Play :size="15"/></button>
                <button class="plane-icon-action" title="停止" @click="action(p.id,'stop')"><Square :size="14"/></button>
                <button class="plane-icon-action" :disabled="!startConfigured(p)" title="重启" @click="action(p.id,'restart')"><RotateCcw :size="15"/></button>
                <button class="plane-icon-action danger-link" title="删除" @click="removeProject(p)"><Trash2 :size="15"/></button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!filteredProjects.length" class="empty-state">{{loading?'正在加载…':'暂无项目'}}</div>
  </section>
</div>
</template>
