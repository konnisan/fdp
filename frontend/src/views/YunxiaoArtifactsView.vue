<script setup>
import { onMounted, ref } from 'vue'
import { Boxes, GitBranch, PackageSearch, RefreshCw } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  getYunxiaoStatus,
  listYunxiaoArtifacts,
  listYunxiaoPipelineRuns,
  listYunxiaoPipelines,
  listYunxiaoRepositories
} from '../api'

const status=ref(null)
const pipelines=ref([])
const runs=ref([])
const repositories=ref([])
const artifacts=ref([])
const selectedPipeline=ref(null)
const selectedRepository=ref(null)
const loading=ref(false)
const error=ref('')
const pipelineKeyword=ref('')
const artifactKeyword=ref('')

function value(obj,...keys){for(const k of keys){if(obj&&obj[k]!=null)return obj[k]}return '-'}
function time(v){if(!v)return '-';const n=Number(v);return Number.isFinite(n)?new Date(n).toLocaleString():String(v)}

async function loadAll(){
  loading.value=true
  error.value=''
  try{
    status.value=await getYunxiaoStatus()
    const [ps,rs]=await Promise.all([
      listYunxiaoPipelines({pipelineName:pipelineKeyword.value||undefined,perPage:30,page:1}),
      listYunxiaoRepositories({repoTypes:'GENERIC',perPage:30,page:1})
    ])
    pipelines.value=ps
    repositories.value=rs
    if(selectedPipeline.value){
      selectedPipeline.value=ps.find(p=>String(p.pipelineId)===String(selectedPipeline.value.pipelineId))||null
    }
    if(selectedRepository.value){
      selectedRepository.value=rs.find(r=>String(r.repoId)===String(selectedRepository.value.repoId))||null
    }
  }catch(e){error.value=e.response?.data?.message||e.message}
  finally{loading.value=false}
}

async function openPipeline(p){
  selectedPipeline.value=p
  runs.value=[]
  error.value=''
  try{runs.value=await listYunxiaoPipelineRuns(p.pipelineId,{perPage:20,page:1})}
  catch(e){error.value=e.response?.data?.message||e.message}
}

async function openRepository(repo){
  selectedRepository.value=repo
  artifacts.value=[]
  error.value=''
  try{artifacts.value=await listYunxiaoArtifacts(repo.repoId,{repoType:repo.repoType||'GENERIC',search:artifactKeyword.value||undefined,perPage:30,page:1})}
  catch(e){error.value=e.response?.data?.message||e.message}
}

onMounted(loadAll)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="流水线与制品" description="FDP 只读取云效 Flow 和 Packages；项目经理继续在 Codeup / Flow 中维护 CI，FDP 后续只消费构建产物并部署到私有云。">
      <template #actions><button class="soft-button" :disabled="loading" @click="loadAll"><RefreshCw :size="14" />{{loading?'读取中…':'刷新云效'}}</button></template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>
    <section v-if="status" class="panel info-card">
      <h3>云效 OpenAPI</h3>
      <dl>
        <div><dt>状态</dt><dd><span class="tag">{{status.configured?'已配置':'未配置'}}</span></dd></div>
        <div><dt>接入点</dt><dd><code>{{status.domain}}</code></dd></div>
        <div><dt>Organization</dt><dd><code>{{status.organizationId||'-'}}</code></dd></div>
        <div><dt>模式</dt><dd>{{status.mode}}</dd></div>
      </dl>
    </section>

    <section class="detail-grid">
      <article class="panel info-card span-2">
        <div class="panel-head"><div><h2><GitBranch :size="18" /> Flow 流水线</h2><p>读取项目经理已经配置好的流水线，不在 FDP 内修改 CI 步骤。</p></div><div class="row-actions"><input v-model="pipelineKeyword" placeholder="流水线名称" /><button class="soft-button" @click="loadAll">检索</button></div></div>
        <div class="table-wrap"><table class="data-table"><thead><tr><th>ID</th><th>流水线</th><th>创建时间</th><th>操作</th></tr></thead><tbody>
          <tr v-for="p in pipelines" :key="p.pipelineId"><td><code>{{p.pipelineId}}</code></td><td><strong>{{p.pipelineName}}</strong></td><td>{{time(p.createTime)}}</td><td><button class="link-button" @click="openPipeline(p)">查看运行记录</button></td></tr>
        </tbody></table></div>
        <div v-if="!pipelines.length" class="empty-state">暂无可读取流水线；请确认 PAT 具有“流水线-只读”权限。</div>
      </article>

      <article v-if="selectedPipeline" class="panel info-card span-2">
        <div class="panel-head"><div><h3>{{selectedPipeline.pipelineName}} · 最近运行</h3><p>Pipeline ID {{selectedPipeline.pipelineId}}</p></div></div>
        <div class="table-wrap"><table class="data-table"><thead><tr><th>Run</th><th>状态</th><th>触发方式</th><th>开始</th><th>结束</th></tr></thead><tbody>
          <tr v-for="r in runs" :key="value(r,'pipelineRunId','id')"><td><code>#{{value(r,'pipelineRunId','id')}}</code></td><td><span class="tag">{{value(r,'status')}}</span></td><td>{{value(r,'triggerMode')}}</td><td>{{time(value(r,'startTime','createTime'))}}</td><td>{{time(value(r,'endTime','updateTime'))}}</td></tr>
        </tbody></table></div>
        <div v-if="!runs.length" class="empty-state">暂无运行记录。</div>
      </article>

      <article class="panel info-card span-2">
        <div class="panel-head"><div><h2><Boxes :size="18" /> Packages 通用制品仓库</h2><p>正式工程建议由 Flow 将最终可部署包上传到 GENERIC 制品仓库。</p></div></div>
        <div class="table-wrap"><table class="data-table"><thead><tr><th>仓库</th><th>ID</th><th>类型</th><th>模式</th><th>操作</th></tr></thead><tbody>
          <tr v-for="repo in repositories" :key="repo.repoId"><td><strong>{{repo.repoName}}</strong><small style="display:block">{{repo.repoDesc}}</small></td><td><code>{{repo.repoId}}</code></td><td>{{repo.repoType}}</td><td>{{repo.repoCategory}}</td><td><button class="link-button" @click="openRepository(repo)">检索制品</button></td></tr>
        </tbody></table></div>
        <div v-if="!repositories.length" class="empty-state">暂无 GENERIC 制品仓库；请确认 PAT 具有“制品仓库-只读”权限。</div>
      </article>

      <article v-if="selectedRepository" class="panel info-card span-2">
        <div class="panel-head"><div><h3><PackageSearch :size="17" /> {{selectedRepository.repoName}} · 制品</h3><p>{{selectedRepository.repoId}}</p></div><div class="row-actions"><input v-model="artifactKeyword" placeholder="按制品名检索" /><button class="soft-button" @click="openRepository(selectedRepository)">检索</button></div></div>
        <div class="table-wrap"><table class="data-table"><thead><tr><th>制品</th><th>组织</th><th>最新版本</th><th>更新时间</th></tr></thead><tbody>
          <tr v-for="a in artifacts" :key="a.id"><td><strong>{{a.module}}</strong></td><td>{{a.organization||'-'}}</td><td><code>{{a.versions?.[0]?.version||'-'}}</code></td><td>{{time(a.latestUpdate)}}</td></tr>
        </tbody></table></div>
        <div v-if="!artifacts.length" class="empty-state">该仓库暂无匹配制品。</div>
        <div style="margin-top:12px;font-size:12px;color:#64748b">当前阶段只做读取与检索。下一阶段再增加“选择版本 → 下载制品 → 校验 → 私有云部署”。</div>
      </article>
    </section>
  </div>
</template>
