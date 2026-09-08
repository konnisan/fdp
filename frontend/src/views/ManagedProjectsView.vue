<script setup>
import { onMounted, ref } from 'vue'
import { Play, Plus, RefreshCw, RotateCcw, Square } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {listManagedProjects,previewManagedProject,restartManagedProject,startManagedProject,stopManagedProject} from '../api'

const emit=defineEmits(['navigate'])
const projects=ref([]),loading=ref(false),error=ref(''),info=ref('')
function err(e){return e.response?.data?.message||e.message||'操作失败'}
function startConfigured(project){return Boolean(project.startCommand)&&project.startCommand!=='__FDP_START_COMMAND_NOT_CONFIGURED__'}
async function load(){loading.value=true;error.value='';try{projects.value=await listManagedProjects()}catch(e){error.value=err(e)}finally{loading.value=false}}
async function action(id,type){error.value='';info.value='';try{if(type==='start')await startManagedProject(id);if(type==='stop')await stopManagedProject(id);if(type==='restart')await restartManagedProject(id);if(type==='preview')await previewManagedProject(id);await load()}catch(e){error.value=err(e)}}
onMounted(()=>{
  const flash=sessionStorage.getItem('fdp-managed-project-flash')
  if(flash){info.value=flash;sessionStorage.removeItem('fdp-managed-project-flash')}
  load()
})
</script>

<template><div class="page-stack restructure-page">
<PageHeader title="项目部署" description="项目绑定 Packages 制品；FDP 负责下载、解压到 current、准备 Container，部署后由使用者人工启动。"><template #actions><button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14"/>刷新</button><button class="primary-button" @click="emit('navigate','/containers/new')"><Plus :size="14"/>新增项目</button></template></PageHeader>
<div v-if="error" class="error-banner">{{error}}</div><div v-if="info" class="success-banner">{{info}}</div>
<section class="panel"><div class="table-wrap"><table class="data-table"><thead><tr><th>项目</th><th>Database</th><th>Runtime</th><th>制品</th><th>Container</th><th>版本</th><th>状态</th><th>操作</th></tr></thead><tbody>
<tr v-for="p in projects" :key="p.id">
<td><strong>{{p.projectName}}</strong><small v-if="!startConfigured(p)" class="cell-note">待配置启动命令</small></td>
<td><code>{{p.databaseName}}</code></td>
<td><code>{{p.runtimeImage}}</code><small class="cell-note">工作目录：{{p.workDirectory||'.'}}</small></td>
<td><strong>{{p.artifacts?.length||0}} 个</strong><small class="cell-note">{{(p.artifacts||[]).map(a=>a.artifactName).join(' · ')||'未绑定'}}</small></td>
<td><code>{{p.containerName}}</code></td>
<td><code>{{p.deployedVersionSummary||'未部署'}}</code><small class="cell-note">运行：{{p.runningVersionSummary||'-'}} <b v-if="p.pendingRestart">待重启</b></small></td>
<td>{{p.deploymentStatus}}</td>
<td><div class="row-actions"><button class="soft-button" :disabled="!startConfigured(p)" :title="startConfigured(p)?'启动项目':'请先配置启动命令'" @click="action(p.id,'start')"><Play :size="13"/>启动</button><button class="soft-button" @click="action(p.id,'stop')"><Square :size="13"/>停止</button><button class="soft-button" :disabled="!startConfigured(p)" @click="action(p.id,'restart')"><RotateCcw :size="13"/>重启</button><button class="soft-button" @click="action(p.id,'preview')">预览</button></div></td>
</tr></tbody></table></div><div v-if="!projects.length" class="empty-state">{{loading?'加载中…':'暂无项目。点击“新增项目”，然后从制品仓库选择项目制品。'}}</div></section>
</div></template>
