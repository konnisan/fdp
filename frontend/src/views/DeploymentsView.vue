<script setup>
import { computed, onMounted, ref } from 'vue'
import { RefreshCw, Terminal } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getDeploymentLogs, getDeploymentSteps, listArtifactDeliveryHistory, listArtifactDeliveryProjects, listDeployments, listProjects } from '../api'

const sourceProjects=ref([])
const artifactProjects=ref([])
const sourceDeployments=ref([])
const artifactDeployments=ref([])
const selected=ref(null)
const logs=ref([])
const steps=ref([])
const status=ref('ALL')
const loading=ref(true)
const error=ref('')
const rows=computed(()=>[
  ...sourceDeployments.value.map(d=>({...d,kind:'source',projectId:d.project_id,version:d.commit_id,image:d.image_tag,start:d.start_time,end:d.end_time,step:d.current_step,displayId:`S-${d.id}`})),
  ...artifactDeployments.value.map(d=>({...d,kind:'artifact',projectId:d.projectId,version:d.artifactVersion,image:d.imageTag,start:d.startTime,end:d.endTime,step:'Packages Release',displayId:`A-${d.id}`}))
].sort((a,b)=>String(b.start||'').localeCompare(String(a.start||''))))
const filtered=computed(()=>rows.value.filter(d=>status.value==='ALL'||d.status===status.value))
function sourceName(id){return sourceProjects.value.find(p=>Number(p.id)===Number(id))?.projectName||`项目 #${id}`}
function artifactName(id){return artifactProjects.value.find(p=>Number(p.id)===Number(id))?.projectName||`工程 #${id}`}
function projectName(row){return row.kind==='artifact'?artifactName(row.projectId):sourceName(row.projectId)}
function projectProfile(row){if(row.kind==='artifact')return 'STANDARD';const p=sourceProjects.value.find(p=>Number(p.id)===Number(row.projectId));return p?.deploymentProfile||(p?.projectType==='STATIC'?'STATIC':'LIGHTWEIGHT')}
function short(v){return v&&v!=='DRY-RUN'?String(v).slice(0,14):(v||'-')}
async function load(){loading.value=true;error.value='';try{const [sp,ap,sd]=await Promise.all([listProjects(),listArtifactDeliveryProjects(),listDeployments()]);sourceProjects.value=sp;artifactProjects.value=ap;sourceDeployments.value=sd;const histories=await Promise.all(ap.map(p=>listArtifactDeliveryHistory(p.id).catch(()=>[])));artifactDeployments.value=histories.flat()}catch(e){error.value=e.response?.data?.message||e.message}finally{loading.value=false}}
async function show(row){selected.value=row;logs.value=[];steps.value=[];if(row.kind==='artifact'){logs.value=[row.message||'暂无详细日志；STANDARD V1 当前保存部署结果信息。'];return}try{[steps.value,logs.value]=await Promise.all([getDeploymentSteps(row.id),getDeploymentLogs(row.id)])}catch(e){error.value=e.response?.data?.message||e.message}}
onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="部署中心" description="跨 Profile 查看所有 Deployment Task；项目内仍保留各自的版本与部署历史。">
      <template #actions><button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14" />{{loading?'刷新中…':'刷新'}}</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>
    <section class="panel">
      <div class="toolbar"><div class="segmented"><button v-for="s in ['ALL','QUEUED','RUNNING','SUCCESS','FAILED']" :key="s" :class="{active:status===s}" @click="status=s">{{s==='ALL'?'全部':s}}</button></div><span class="toolbar-count">{{filtered.length}} 条记录</span></div>
      <div class="table-wrap"><table class="data-table"><thead><tr><th>ID</th><th>项目</th><th>Profile</th><th>状态</th><th>阶段</th><th>版本</th><th>Image</th><th>开始时间</th><th>操作</th></tr></thead><tbody><tr v-for="d in filtered" :key="`${d.kind}-${d.id}`"><td><code>{{d.displayId}}</code></td><td><strong>{{projectName(d)}}</strong></td><td><span class="type-badge">{{projectProfile(d)}}</span></td><td><span class="tag">{{d.status}}</span></td><td>{{d.step||'-'}}</td><td><code>{{short(d.version)}}</code></td><td><code>{{short(d.image)}}</code></td><td>{{d.start||'-'}}</td><td><button class="link-button" @click="show(d)"><Terminal :size="13" />执行详情</button></td></tr></tbody></table></div>
      <div v-if="!filtered.length" class="empty-state">{{loading?'正在加载…':'暂无部署记录'}}</div>
    </section>

    <div v-if="selected" class="modal-mask" @click.self="selected=null"><div class="modal-card log-modal"><header><div><h2>部署 {{selected.displayId}} 执行详情</h2><p>{{projectName(selected)}} · {{selected.status}}</p></div><button class="soft-button" @click="selected=null">关闭</button></header><div v-if="steps.length" class="table-wrap"><table class="data-table"><thead><tr><th>步骤</th><th>状态</th><th>开始</th><th>结束</th></tr></thead><tbody><tr v-for="s in steps" :key="s.id"><td><strong>{{s.step_name}}</strong><br><code>{{s.step_code}}</code></td><td><span class="tag">{{s.status}}</span></td><td>{{s.start_time||'-'}}</td><td>{{s.end_time||'-'}}</td></tr></tbody></table></div><pre class="terminal">{{logs.join('\n\n')||'暂无日志'}}</pre></div></div>
  </div>
</template>
