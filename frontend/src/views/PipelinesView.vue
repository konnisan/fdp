<script setup>
import { computed, onMounted, ref } from 'vue'
import { GitBranch, PackageSearch, Play, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getYunxiaoStatus, listYunxiaoPipelineRuns, listYunxiaoPipelines, runYunxiaoPipeline } from '../api'

const emit=defineEmits(['navigate'])
const status=ref(null)
const pipelines=ref([])
const selected=ref(null)
const runs=ref([])
const keyword=ref('')
const loading=ref(false)
const running=ref('')
const error=ref('')
const info=ref('')

const filtered=computed(()=>{
  const q=keyword.value.trim().toLowerCase()
  return q?pipelines.value.filter(p=>`${p.pipelineName||''} ${p.pipelineId||''}`.toLowerCase().includes(q)):pipelines.value
})
function value(obj,...keys){for(const k of keys){if(obj&&obj[k]!=null)return obj[k]}return '-'}
function time(v){if(!v)return '-';const n=Number(v);return Number.isFinite(n)?new Date(n).toLocaleString():String(v)}
function err(e){return e.response?.data?.message||e.message||'操作失败'}
function runTone(run){
  const state=String(value(run,'status')).toUpperCase()
  if(state==='SUCCESS')return 'deployed'
  if(state==='RUNNING')return 'running'
  if(['FAIL','FAILED','ERROR'].includes(state))return 'failed'
  if(['CANCELED','CANCELLED','STOPPED'].includes(state))return 'stopped'
  return 'pending'
}
async function load(){
  loading.value=true;error.value=''
  try{
    const [s,ps]=await Promise.all([getYunxiaoStatus(),listYunxiaoPipelines({page:1,perPage:30})])
    status.value=s;pipelines.value=ps
    if(selected.value){
      selected.value=ps.find(p=>String(p.pipelineId)===String(selected.value.pipelineId))||null
      if(selected.value)await loadRuns(selected.value)
    }
  }catch(e){error.value=err(e)}finally{loading.value=false}
}
async function loadRuns(p){selected.value=p;runs.value=[];error.value='';try{runs.value=await listYunxiaoPipelineRuns(p.pipelineId,{page:1,perPage:20})}catch(e){error.value=err(e)}}
async function run(p){
  if(!confirm(`运行流水线“${p.pipelineName}”吗？`))return
  running.value=String(p.pipelineId);error.value='';info.value=''
  try{
    const result=await runYunxiaoPipeline(p.pipelineId,{})
    info.value=`流水线已触发，Run #${result.runId||'-'}`
    await loadRuns(p)
  }catch(e){error.value=err(e)}finally{running.value=''}
}

onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page pipelines-page plane-page">
    <PageHeader title="流水线">
      <template #actions>
        <button class="soft-button" @click="emit('navigate','/artifacts')"><PackageSearch :size="15" />制品仓库</button>
        <button class="primary-button" :disabled="loading" @click="load"><RefreshCw :size="15" />{{loading?'读取中…':'刷新'}}</button>
      </template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>
    <div v-if="info" class="success-banner">{{info}}</div>

    <section class="connection-strip" :class="{offline:status&&!status.configured}">
      <div class="connection-main"><GitBranch :size="17"/><strong>云效 Flow</strong></div>
      <span class="state-badge" :data-state="status?.configured?'running':'failed'"><i></i>{{status?.configured?'已连接':'未配置'}}</span>
    </section>

    <section class="panel pipeline-panel">
      <div class="artifact-toolbar">
        <div class="artifact-toolbar-title"><strong>流水线</strong></div>
        <label class="search-box artifact-search"><Search :size="16" /><input v-model="keyword" placeholder="搜索名称或 ID" /></label>
      </div>
      <div class="table-wrap">
        <table class="data-table pipeline-table">
          <thead><tr><th>流水线</th><th>Pipeline ID</th><th>创建时间</th><th class="actions-column">操作</th></tr></thead>
          <tbody>
            <tr v-for="p in filtered" :key="p.pipelineId" :class="{selected:String(selected?.pipelineId||'')===String(p.pipelineId)}">
              <td><div class="artifact-name-cell"><span class="artifact-file-icon"><GitBranch :size="16" /></span><strong>{{p.pipelineName}}</strong></div></td>
              <td><code>{{p.pipelineId}}</code></td>
              <td>{{time(p.createTime)}}</td>
              <td class="actions-column"><div class="project-actions"><button class="soft-button" @click="loadRuns(p)">运行记录</button><button class="primary-button" :disabled="running!==''" @click="run(p)"><Play :size="14" />{{running===String(p.pipelineId)?'触发中…':'运行'}}</button></div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filtered.length" class="empty-state">{{loading?'正在加载…':'暂无流水线'}}</div>
    </section>

    <section v-if="selected" class="panel pipeline-runs-panel">
      <div class="panel-head compact-panel-head"><h2>{{selected.pipelineName}} · 最近运行</h2><button class="soft-button" @click="loadRuns(selected)"><RefreshCw :size="14" />刷新</button></div>
      <div class="table-wrap"><table class="data-table"><thead><tr><th>Run</th><th>状态</th><th>触发方式</th><th>开始时间</th><th>结束时间</th></tr></thead><tbody>
        <tr v-for="r in runs" :key="value(r,'pipelineRunId','id')"><td><code>#{{value(r,'pipelineRunId','id')}}</code></td><td><span class="state-badge" :data-state="runTone(r)"><i></i>{{value(r,'status')}}</span></td><td>{{value(r,'triggerMode')}}</td><td>{{time(value(r,'startTime','createTime'))}}</td><td>{{time(value(r,'endTime','updateTime'))}}</td></tr>
      </tbody></table></div>
      <div v-if="!runs.length" class="empty-state">暂无运行记录</div>
    </section>
  </div>
</template>
