<script setup>
import { computed, onMounted, ref } from 'vue'
import { MoreHorizontal, Pencil, Play, Plus, RefreshCw, RotateCcw, Square, Trash2 } from 'lucide-vue-next'
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
  const status=normalizedStatus(project)
  return ({CREATED:'待配置',DEPLOYED:'已下载',RUNNING:'运行中',STOPPED:'已停止',FAILED:'失败',ERROR:'异常'})[status]||status
}
function statusTone(project){
  const status=normalizedStatus(project)
  if(status==='RUNNING')return 'running'
  if(status==='DEPLOYED')return 'deployed'
  if(status==='FAILED'||status==='ERROR')return 'failed'
  if(status==='STOPPED')return 'stopped'
  return 'pending'
}
function newProject(){
  sessionStorage.removeItem('fdp-managed-project-draft')
  sessionStorage.removeItem('fdp-artifact-selection-target')
  emit('navigate','/containers/new')
}
async function load(){loading.value=true;error.value='';try{projects.value=await listManagedProjects()}catch(e){error.value=err(e)}finally{loading.value=false}}
async function action(id,type){error.value='';info.value='';try{if(type==='start')await startManagedProject(id);if(type==='stop')await stopManagedProject(id);if(type==='restart')await restartManagedProject(id);await load()}catch(e){error.value=err(e)}}
async function removeProject(project){
  if(!window.confirm(`确认删除项目“${project.projectName}”？\n\nFDP 会删除项目 Container 与项目 current/config 目录，但不会删除该项目创建的业务 Database。`))return
  error.value='';info.value=''
  try{
    await deleteManagedProject(project.id)
    sessionStorage.removeItem(`fdp-managed-project-edit-draft-${project.id}`)
    info.value=`项目“${project.projectName}”已删除；业务 Database 已保留。`
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
  <PageHeader title="项目部署" description="从制品选择到运行配置，集中管理当前交付项目。">
    <template #actions>
      <button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="13"/>刷新</button>
      <button class="primary-button" @click="newProject"><Plus :size="13"/>新增项目</button>
    </template>
  </PageHeader>

  <div v-if="error" class="error-banner">{{error}}</div>
  <div v-if="info" class="success-banner">{{info}}</div>

  <div class="plane-stat-row" aria-label="项目筛选">
    <button class="plane-stat-filter" :class="{active:activeFilter==='all'}" @click="activeFilter='all'"><span>全部项目</span><strong>{{stats.total}}</strong></button>
    <button class="plane-stat-filter" :class="{active:activeFilter==='deployed'}" @click="activeFilter='deployed'"><span>已下载</span><strong>{{stats.deployed}}</strong></button>
    <button class="plane-stat-filter" :class="{active:activeFilter==='running'}" @click="activeFilter='running'"><span>运行中</span><strong>{{stats.running}}</strong></button>
    <button class="plane-stat-filter" :class="{active:activeFilter==='pending'}" @click="activeFilter='pending'"><span>待处理</span><strong>{{stats.pending}}</strong></button>
  </div>

  <section class="panel managed-project-panel plane-data-panel">
    <div class="plane-panel-toolbar">
      <div class="plane-panel-tabs">
        <strong>项目</strong>
        <button :class="{active:activeFilter==='all'}" @click="activeFilter='all'">全部</button>
        <button :class="{active:activeFilter==='running'}" @click="activeFilter='running'">运行中</button>
        <button :class="{active:activeFilter==='pending'}" @click="activeFilter='pending'">需要处理</button>
      </div>
      <span class="plane-panel-meta">显示 {{filteredProjects.length}} / {{projects.length}}</span>
    </div>
    <div class="table-wrap">
      <table class="data-table managed-projects-table plane-table">
        <thead><tr><th>项目</th><th>Database</th><th>Runtime</th><th>制品</th><th>版本</th><th>状态</th><th class="actions-column">操作</th></tr></thead>
        <tbody>
          <tr v-for="p in filteredProjects" :key="p.id">
            <td class="project-primary-cell">
              <button class="project-title-button" @click="emit('navigate',`/containers/${p.id}/edit`)">{{p.projectName}}</button>
              <small class="cell-note">#{{String(p.id).padStart(2,'0')}} · current/</small>
            </td>
            <td><code>{{p.databaseName}}</code></td>
            <td><span>{{p.runtimeImage}}</span><small class="cell-note">{{p.workDirectory||'.'}}</small></td>
            <td><span>{{p.artifacts?.length||0}} 个</span><small class="cell-note artifact-inline">{{(p.artifacts||[]).map(a=>a.artifactName).join(' · ')||'未绑定'}}</small></td>
            <td><code>{{p.deployedVersionSummary||'未部署'}}</code><small v-if="p.runningVersionSummary" class="cell-note">运行 {{p.runningVersionSummary}}</small></td>
            <td><span class="state-badge" :data-state="statusTone(p)"><i></i>{{statusLabel(p)}}</span><small v-if="p.pendingRestart" class="cell-note pending-text">配置待重启</small></td>
            <td class="actions-column">
              <div class="project-actions plane-project-actions">
                <button class="soft-button plane-edit-button" @click="emit('navigate',`/containers/${p.id}/edit`)"><Pencil :size="12"/>编辑</button>
                <button class="plane-icon-action" :disabled="!startConfigured(p)" :title="startConfigured(p)?'启动项目':'请先配置启动命令'" @click="action(p.id,'start')"><Play :size="14"/></button>
                <button class="plane-icon-action" title="停止项目" @click="action(p.id,'stop')"><Square :size="12"/></button>
                <button class="plane-icon-action" :disabled="!startConfigured(p)" title="重启项目" @click="action(p.id,'restart')"><RotateCcw :size="13"/></button>
                <details class="plane-more-menu">
                  <summary title="更多操作"><MoreHorizontal :size="15"/></summary>
                  <div class="plane-menu-popover"><button class="danger-link" @click.prevent="removeProject(p)"><Trash2 :size="12"/>删除项目</button></div>
                </details>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!filteredProjects.length" class="empty-state">{{loading?'正在读取项目…':'当前筛选下暂无项目。'}}</div>
  </section>
</div>
</template>
