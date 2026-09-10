<script setup>
import { computed, onMounted, ref } from 'vue'
import { Boxes, CheckCircle2, Pencil, Play, Plus, RefreshCw, RotateCcw, Settings2, Square, Trash2 } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {deleteManagedProject,listManagedProjects,restartManagedProject,startManagedProject,stopManagedProject} from '../api'

const emit=defineEmits(['navigate'])
const projects=ref([]),loading=ref(false),error=ref(''),info=ref('')
function err(e){return e.response?.data?.message||e.message||'操作失败'}
function startConfigured(project){return Boolean(project.startCommand)&&project.startCommand!=='__FDP_START_COMMAND_NOT_CONFIGURED__'}
function normalizedStatus(project){return String(project.deploymentStatus||'CREATED').toUpperCase()}
const stats=computed(()=>({
  total:projects.value.length,
  deployed:projects.value.filter(p=>Boolean(p.deployedVersionSummary)).length,
  running:projects.value.filter(p=>normalizedStatus(p)==='RUNNING').length,
  pending:projects.value.filter(p=>!startConfigured(p)).length
}))
function statusLabel(project){
  const status=normalizedStatus(project)
  return ({CREATED:'待配置',DEPLOYED:'已部署',RUNNING:'运行中',STOPPED:'已停止',FAILED:'失败',ERROR:'异常'})[status]||status
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
<div class="page-stack restructure-page managed-projects-page">
  <PageHeader title="项目部署" description="管理 Packages 制品、运行配置与项目生命周期。制品下载并解压到 current/ 后，再按实际目录完成启动配置。">
    <template #actions>
      <button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14"/>刷新</button>
      <button class="primary-button" @click="newProject"><Plus :size="14"/>新增项目</button>
    </template>
  </PageHeader>

  <div v-if="error" class="error-banner">{{error}}</div>
  <div v-if="info" class="success-banner">{{info}}</div>

  <section class="console-summary">
    <article class="summary-item">
      <span class="summary-icon"><Boxes :size="17"/></span>
      <div><small>项目总数</small><strong>{{stats.total}}</strong></div>
    </article>
    <article class="summary-item">
      <span class="summary-icon"><CheckCircle2 :size="17"/></span>
      <div><small>已下载制品</small><strong>{{stats.deployed}}</strong></div>
    </article>
    <article class="summary-item">
      <span class="summary-icon success"><Play :size="16"/></span>
      <div><small>运行中</small><strong>{{stats.running}}</strong></div>
    </article>
    <article class="summary-item">
      <span class="summary-icon warning"><Settings2 :size="17"/></span>
      <div><small>待配置启动</small><strong>{{stats.pending}}</strong></div>
    </article>
  </section>

  <section class="panel managed-project-panel">
    <div class="panel-head compact-panel-head">
      <div><h2>Managed Projects</h2><p>一个项目对应一个独立 Container；删除项目不会删除业务 Database。</p></div>
      <span class="table-meta">{{projects.length}} 个项目</span>
    </div>
    <div class="table-wrap">
      <table class="data-table managed-projects-table">
        <thead><tr><th>项目</th><th>制品 / 版本</th><th>运行配置</th><th>Database</th><th>状态</th><th class="actions-column">操作</th></tr></thead>
        <tbody>
          <tr v-for="p in projects" :key="p.id">
            <td class="project-primary-cell">
              <button class="project-title-button" @click="emit('navigate',`/containers/${p.id}/edit`)">{{p.projectName}}</button>
              <small class="cell-note"><code>{{p.containerName}}</code></small>
            </td>
            <td>
              <div class="artifact-summary"><strong>{{p.artifacts?.length||0}} 个制品</strong><span>{{(p.artifacts||[]).map(a=>a.artifactName).join(' · ')||'尚未绑定制品'}}</span></div>
              <small class="cell-note">部署版本：{{p.deployedVersionSummary||'未下载'}}</small>
            </td>
            <td>
              <code>{{p.runtimeImage}}</code>
              <small class="cell-note">工作目录 {{p.workDirectory||'.'}}<template v-if="!startConfigured(p)"> · <b class="pending-text">待填写启动命令</b></template></small>
            </td>
            <td><code>{{p.databaseName}}</code></td>
            <td><span class="state-badge" :data-state="statusTone(p)"><i></i>{{statusLabel(p)}}</span><small v-if="p.pendingRestart" class="cell-note pending-text">配置变更待重启</small></td>
            <td class="actions-column">
              <div class="project-actions">
                <button class="soft-button action-edit" @click="emit('navigate',`/containers/${p.id}/edit`)"><Pencil :size="13"/>编辑</button>
                <div class="runtime-actions-group">
                  <button class="icon-button" :disabled="!startConfigured(p)" :title="startConfigured(p)?'启动项目':'请先配置启动命令'" @click="action(p.id,'start')"><Play :size="14"/></button>
                  <button class="icon-button" title="停止项目" @click="action(p.id,'stop')"><Square :size="13"/></button>
                  <button class="icon-button" :disabled="!startConfigured(p)" title="重启项目" @click="action(p.id,'restart')"><RotateCcw :size="14"/></button>
                </div>
                <button class="icon-button danger" title="删除项目" @click="removeProject(p)"><Trash2 :size="14"/></button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!projects.length" class="empty-state">{{loading?'正在读取项目…':'暂无项目。点击右上角“新增项目”开始绑定 Packages 制品。'}}</div>
  </section>
</div>
</template>
