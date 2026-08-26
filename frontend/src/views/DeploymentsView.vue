<script setup>
import { computed, onMounted, ref } from 'vue'
import { RefreshCw, Terminal } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getDeploymentLogs, listDeployments, listProjects } from '../api'

const projects=ref([]), deployments=ref([]), selected=ref(null), logs=ref([]), status=ref('ALL'), loading=ref(true)
const filtered=computed(()=>deployments.value.filter(d=>status.value==='ALL'||d.status===status.value))
function projectName(id){return projects.value.find(p=>Number(p.id)===Number(id))?.projectName||`项目 #${id}`}
function short(v){return v&&v!=='DRY-RUN'?String(v).slice(0,8):(v||'-')}
async function load(){loading.value=true;try{[projects.value,deployments.value]=await Promise.all([listProjects(),listDeployments()])}finally{loading.value=false}}
async function show(d){selected.value=d;logs.value=await getDeploymentLogs(d.id)}
onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="部署中心" description="集中查看所有 POC 的 Codeup 同步、构建和发布结果。">
      <template #actions><button class="soft-button" @click="load"><RefreshCw :size="14" />刷新</button></template>
    </PageHeader>
    <section class="panel">
      <div class="toolbar"><div class="segmented"><button v-for="s in ['ALL','RUNNING','SUCCESS','FAILED']" :key="s" :class="{active:status===s}" @click="status=s">{{s==='ALL'?'全部':s}}</button></div><span class="toolbar-count">{{filtered.length}} 条记录</span></div>
      <div class="table-wrap"><table class="data-table"><thead><tr><th>ID</th><th>POC 项目</th><th>状态</th><th>当前步骤</th><th>Commit</th><th>开始时间</th><th>操作</th></tr></thead><tbody><tr v-for="d in filtered" :key="d.id"><td>#{{d.id}}</td><td><strong>{{projectName(d.project_id)}}</strong></td><td><span class="tag">{{d.status}}</span></td><td>{{d.current_step}}</td><td><code>{{short(d.commit_id)}}</code></td><td>{{d.start_time||'-'}}</td><td><button class="link-button" @click="show(d)"><Terminal :size="13" />日志</button></td></tr></tbody></table></div>
      <div v-if="!filtered.length" class="empty-state">{{loading?'正在加载…':'暂无部署记录'}}</div>
    </section>
    <div v-if="selected" class="modal-mask" @click.self="selected=null"><div class="modal-card log-modal"><header><div><h2>部署 #{{selected.id}} 日志</h2><p>{{projectName(selected.project_id)}} · {{selected.status}}</p></div><button class="soft-button" @click="selected=null">关闭</button></header><pre class="terminal">{{logs.join('\n\n')||'暂无日志'}}</pre></div></div>
  </div>
</template>
