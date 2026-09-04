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
async function loadRuns(p){
  selected.value=p;runs.value=[];error.value=''
  try{runs.value=await listYunxiaoPipelineRuns(p.pipelineId,{page:1,perPage:20})}
  catch(e){error.value=err(e)}
}
async function run(p){
  if(!confirm(`运行流水线“${p.pipelineName}”吗？\nFDP 只触发已经在云效配置好的 Flow，不修改流水线步骤。`))return
  running.value=String(p.pipelineId);error.value='';info.value=''
  try{
    const result=await runYunxiaoPipeline(p.pipelineId,{})
    info.value=`流水线已触发，Run #${result.runId||'-'}。构建成功并上传 Packages 后，可在“制品仓库”看到新版本。`
    await loadRuns(p)
  }catch(e){error.value=err(e)}finally{running.value=''}
}

onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="流水线" description="查看并运行项目经理已经在云效 Flow 配置好的流水线。FDP 不修改 CI，只负责触发构建并观察运行结果。">
      <template #actions>
        <button class="soft-button" @click="emit('navigate','/artifacts')"><PackageSearch :size="14" />查看制品仓库</button>
        <button class="primary-button" :disabled="loading" @click="load"><RefreshCw :size="14" />{{loading?'读取中…':'刷新流水线'}}</button>
      </template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>
    <div v-if="info" class="success-banner">{{info}}</div>

    <section v-if="status" class="panel" style="padding:15px 18px">
      <div style="display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap">
        <div><strong>云效 Flow</strong><div style="font-size:12px;color:#64748b;margin-top:4px">{{status.domain}} · Organization {{status.organizationId||'-'}}</div></div>
        <span class="status-text" :class="status.configured?'running':'failed'"><i></i>{{status.configured?'CONNECTED':'NOT CONFIGURED'}}</span>
      </div>
    </section>

    <section class="panel">
      <div class="toolbar">
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索流水线名称或 ID" /></label>
        <span class="toolbar-count">{{filtered.length}} 条流水线</span>
      </div>
      <div class="table-wrap">
        <table class="data-table">
          <thead><tr><th>流水线</th><th>Pipeline ID</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="p in filtered" :key="p.pipelineId">
              <td><div style="display:flex;gap:10px;align-items:center"><span class="preview-icon" style="width:32px;height:32px"><GitBranch :size="17" /></span><strong>{{p.pipelineName}}</strong></div></td>
              <td><code>{{p.pipelineId}}</code></td>
              <td>{{time(p.createTime)}}</td>
              <td><div class="row-actions"><button class="soft-button" @click="loadRuns(p)">运行记录</button><button class="primary-button" :disabled="running!==''" @click="run(p)"><Play :size="14" />{{running===String(p.pipelineId)?'触发中…':'运行流水线'}}</button></div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filtered.length" class="empty-state">暂无可读取流水线。</div>
    </section>

    <section v-if="selected" class="panel">
      <div class="panel-head"><div><h2>{{selected.pipelineName}} · 最近运行</h2><p>Pipeline ID {{selected.pipelineId}}。成功运行产生的 Packages 制品会进入“制品仓库”。</p></div><button class="soft-button" @click="loadRuns(selected)"><RefreshCw :size="14" />刷新运行记录</button></div>
      <div class="table-wrap"><table class="data-table"><thead><tr><th>Run</th><th>状态</th><th>触发方式</th><th>开始时间</th><th>结束时间</th></tr></thead><tbody>
        <tr v-for="r in runs" :key="value(r,'pipelineRunId','id')"><td><code>#{{value(r,'pipelineRunId','id')}}</code></td><td><span class="tag">{{value(r,'status')}}</span></td><td>{{value(r,'triggerMode')}}</td><td>{{time(value(r,'startTime','createTime'))}}</td><td>{{time(value(r,'endTime','updateTime'))}}</td></tr>
      </tbody></table></div>
      <div v-if="!runs.length" class="empty-state">暂无运行记录。</div>
    </section>
  </div>
</template>
