<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Box, ExternalLink, PackageCheck, Plus, RefreshCw, Rocket, X } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  createArtifactDeliveryProject,
  deployArtifactRelease,
  getRuntimeStatus,
  getYunxiaoStatus,
  listArtifactDeliveryHistory,
  listArtifactDeliveryProjects,
  listArtifactDeliveryReleases,
  listYunxiaoArtifacts,
  listYunxiaoPipelines,
  listYunxiaoRepositories
} from '../api'

const projects=ref([])
const pipelines=ref([])
const repositories=ref([])
const artifacts=ref([])
const releases=ref([])
const history=ref([])
const runtime=ref(null)
const yunxiao=ref(null)
const selected=ref(null)
const showForm=ref(false)
const loading=ref(false)
const saving=ref(false)
const deployingRun=ref('')
const error=ref('')

const defaults=()=>({
  projectCode:'',projectName:'',pipelineId:'',pipelineName:'',packageRepoId:'',packageRepoName:'',artifactName:'',
  previewPath:'',hostPort:3201,containerName:'',envFile:''
})
const form=reactive(defaults())

const selectedPipeline=computed(()=>pipelines.value.find(p=>String(p.pipelineId)===String(form.pipelineId)))
const selectedRepo=computed(()=>repositories.value.find(r=>String(r.repoId)===String(form.packageRepoId)))

function err(e){return e.response?.data?.message||e.message}
function value(obj,...keys){for(const k of keys){if(obj&&obj[k]!=null)return obj[k]}return '-'}
function time(v){if(!v)return '-';const n=Number(v);return Number.isFinite(n)?new Date(n).toLocaleString():String(v)}
function publicOrigin(){const port=Number(runtime.value?.publicPort||0);return port?`${window.location.protocol}//${window.location.hostname}:${port}`:`${window.location.protocol}//${window.location.host}`}
function previewUrl(p){return `${publicOrigin()}${p.previewPath}/`}

async function load(){
  loading.value=true;error.value=''
  try{
    const [ps,pls,repos,rt,ys]=await Promise.all([
      listArtifactDeliveryProjects(),
      listYunxiaoPipelines({page:1,perPage:30}),
      listYunxiaoRepositories({repoTypes:'GENERIC',page:1,perPage:30}),
      getRuntimeStatus(),
      getYunxiaoStatus()
    ])
    projects.value=ps;pipelines.value=pls;repositories.value=repos;runtime.value=rt;yunxiao.value=ys
    if(selected.value){selected.value=ps.find(p=>p.id===selected.value.id)||null}
  }catch(e){error.value=err(e)}finally{loading.value=false}
}

function openCreate(){Object.assign(form,defaults());artifacts.value=[];showForm.value=true}
async function chooseRepo(){
  artifacts.value=[]
  form.packageRepoName=selectedRepo.value?.repoName||''
  if(!form.packageRepoId)return
  try{artifacts.value=await listYunxiaoArtifacts(form.packageRepoId,{repoType:'GENERIC',page:1,perPage:30})}
  catch(e){error.value=err(e)}
}
function choosePipeline(){form.pipelineName=selectedPipeline.value?.pipelineName||''}
function deriveDefaults(){
  const code=form.projectCode.trim()
  if(code){if(!form.previewPath)form.previewPath=`/${code}`;if(!form.containerName)form.containerName=`fdp-${code}-backend`}
}
async function save(){
  deriveDefaults()
  if(!form.projectCode||!form.projectName||!form.pipelineId||!form.packageRepoId||!form.artifactName||!form.previewPath||!form.hostPort||!form.containerName){
    error.value='请填写项目、Flow、Packages 制品、访问路径、后端端口和容器名';return
  }
  saving.value=true;error.value=''
  try{
    await createArtifactDeliveryProject({...form,hostPort:Number(form.hostPort)})
    showForm.value=false
    await load()
  }catch(e){error.value=err(e)}finally{saving.value=false}
}

async function openProject(p){
  selected.value=p;releases.value=[];history.value=[];error.value=''
  try{[releases.value,history.value]=await Promise.all([listArtifactDeliveryReleases(p.id),listArtifactDeliveryHistory(p.id)])}
  catch(e){error.value=err(e)}
}
async function deployRelease(r){
  if(!confirm(`部署 ${selected.value.projectName} 版本 ${r.version}（Flow #${r.runId}）？\n旧后端容器会被停止并替换。`))return
  deployingRun.value=String(r.runId);error.value=''
  try{
    await deployArtifactRelease(selected.value.id,String(r.runId))
    await load()
    selected.value=projects.value.find(p=>p.id===selected.value.id)||selected.value
    history.value=await listArtifactDeliveryHistory(selected.value.id)
  }catch(e){error.value=err(e)}finally{deployingRun.value=''}
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="工程制品交付" description="Flow 负责 CI 和 Docker build；FDP 从成功流水线中选择 Packages 版本，在 Linux 上发布前端静态资源、加载后端镜像并更新 Nginx。">
      <template #actions>
        <button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14" />{{loading?'刷新中…':'刷新'}}</button>
        <button class="primary-button" @click="openCreate"><Plus :size="15" />绑定工程</button>
      </template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>

    <section v-if="yunxiao" class="panel info-card">
      <div class="panel-head"><div><h3><PackageCheck :size="17" /> 交付边界</h3><p>Codeup → Flow → Packages 属于构建侧；Packages 成功制品之后由 FDP 接管部署。</p></div><span class="tag">{{yunxiao.configured?'云效已连接':'云效未配置'}}</span></div>
      <div style="font-size:13px;line-height:1.8;color:#475569">V1 约定每个可部署版本是一个 <code>.tgz</code> 交付包，根目录包含 <code>fdp-manifest.yml</code>、前端 <code>.tar.gz</code> 和后端 <code>image.tar</code>。数据库 migration 字段可以随包保留，但本阶段暂不自动执行。</div>
    </section>

    <section class="panel">
      <div class="panel-head"><div><h2><Box :size="18" /> 已绑定工程</h2><p>环境侧参数由 FDP 保存；容器内部端口、镜像名、健康检查由制品中的 manifest 声明。</p></div><span class="toolbar-count">{{projects.length}} 个工程</span></div>
      <div class="table-wrap"><table class="data-table"><thead><tr><th>工程</th><th>Flow / Packages</th><th>当前版本</th><th>状态</th><th>客户入口</th><th>操作</th></tr></thead><tbody>
        <tr v-for="p in projects" :key="p.id">
          <td><strong>{{p.projectName}}</strong><code style="display:block">{{p.projectCode}}</code></td>
          <td><span>{{p.pipelineName||p.pipelineId}}</span><small style="display:block">{{p.packageRepoName||p.packageRepoId}} / {{p.artifactName}}</small></td>
          <td><code>{{p.currentVersion||'-'}}</code><small v-if="p.currentRunId" style="display:block">Flow #{{p.currentRunId}}</small></td>
          <td><span class="tag">{{p.status}}</span></td>
          <td><a :href="previewUrl(p)" target="_blank">{{p.previewPath}} <ExternalLink :size="12" /></a><small style="display:block">backend 127.0.0.1:{{p.hostPort}}</small></td>
          <td><button class="soft-button" @click="openProject(p)">版本与部署</button></td>
        </tr>
      </tbody></table></div>
      <div v-if="!projects.length" class="empty-state">还没有正式工程绑定。先选择 Flow + Packages 通用制品，再配置 FDP 的访问路径和后端宿主机端口。</div>
    </section>

    <section v-if="selected" class="detail-grid">
      <article class="panel info-card span-2">
        <div class="panel-head"><div><h2><Rocket :size="18" /> {{selected.projectName}} · 可部署版本</h2><p>只展示成功 Flow Run 中真实产出的指定 Packages 制品；第一条自动标记为推荐版本。</p></div><button class="soft-button" @click="openProject(selected)"><RefreshCw :size="14" />重新读取</button></div>
        <div class="table-wrap"><table class="data-table"><thead><tr><th>建议</th><th>Flow Run</th><th>Packages 版本</th><th>完成时间</th><th>MD5</th><th>操作</th></tr></thead><tbody>
          <tr v-for="r in releases" :key="r.runId">
            <td><span v-if="r.recommended" class="tag">推荐</span><span v-else>-</span></td>
            <td><code>#{{r.runId}}</code></td><td><strong>{{r.version}}</strong></td><td>{{time(r.updateTime||r.createTime)}}</td><td><code>{{r.md5||'-'}}</code></td>
            <td><button class="primary-button" :disabled="deployingRun!==''" @click="deployRelease(r)">{{deployingRun===String(r.runId)?'已提交…':'部署此版本'}}</button></td>
          </tr>
        </tbody></table></div>
        <div v-if="!releases.length" class="empty-state">最近成功流水线中没有找到 {{selected.artifactName}}。请确认 Flow 最终将这个制品上传到 {{selected.packageRepoId}}，并且运行结果包含 Packages artifact。</div>
      </article>

      <article class="panel info-card span-2">
        <div class="panel-head"><div><h3>部署历史</h3><p>选择历史成功 Flow 版本再次部署，即是当前平台的轻量回退方式。</p></div></div>
        <div class="table-wrap"><table class="data-table"><thead><tr><th>ID</th><th>Flow Run</th><th>版本</th><th>状态</th><th>镜像</th><th>开始</th><th>结果</th></tr></thead><tbody>
          <tr v-for="h in history" :key="h.id"><td>#{{h.id}}</td><td>#{{h.pipelineRunId}}</td><td><code>{{h.artifactVersion}}</code></td><td><span class="tag">{{h.status}}</span></td><td><code>{{h.imageTag||'-'}}</code></td><td>{{time(h.startTime)}}</td><td>{{h.message||'-'}}</td></tr>
        </tbody></table></div>
        <div v-if="!history.length" class="empty-state">暂无部署历史。</div>
      </article>
    </section>

    <div v-if="showForm" class="modal-mask" @click.self="showForm=false">
      <form class="modal-card large" @submit.prevent="save">
        <header><div><h2>绑定正式工程</h2><p>应用参数来自 Flow 制品 manifest；这里只配置 FDP 所属的部署环境参数。</p></div><button type="button" class="icon-button" @click="showForm=false"><X :size="18" /></button></header>
        <div class="form-grid">
          <label>项目编码 *<input v-model="form.projectCode" placeholder="financial-system" @blur="deriveDefaults" /></label>
          <label>项目名称 *<input v-model="form.projectName" placeholder="Financial System" /></label>
          <label>Flow 流水线 *<select v-model="form.pipelineId" @change="choosePipeline"><option value="">请选择</option><option v-for="p in pipelines" :key="p.pipelineId" :value="String(p.pipelineId)">{{p.pipelineName}} · {{p.pipelineId}}</option></select></label>
          <label>Packages 仓库 *<select v-model="form.packageRepoId" @change="chooseRepo"><option value="">请选择</option><option v-for="r in repositories" :key="r.repoId" :value="String(r.repoId)">{{r.repoName}} · {{r.repoId}}</option></select></label>
          <label class="span-2">交付包制品 *<select v-model="form.artifactName"><option value="">请选择 Packages 制品</option><option v-for="a in artifacts" :key="a.id" :value="a.module">{{a.module}} · 最新 {{a.versions?.[0]?.version||'-'}}</option></select></label>
          <label>客户访问 Path *<input v-model="form.previewPath" placeholder="/financial-system" /></label>
          <label>后端宿主机端口 *<input v-model="form.hostPort" type="number" placeholder="3201" /></label>
          <label>后端容器名 *<input v-model="form.containerName" placeholder="fdp-financial-system-backend" /></label>
          <label>环境变量文件<input v-model="form.envFile" placeholder="/data/fdp/env/financial-system.env" /></label>
          <div class="span-2" style="font-size:12px;line-height:1.8;color:#475569;padding:10px 12px;border:1px solid #dbeafe;background:#eff6ff">
            环境文件用于数据库地址、账号密码等服务器配置，不进入 Codeup/Packages。后端容器通过 <code>--env-file</code> 加载。留空表示镜像自身不需要额外环境变量。
          </div>
        </div>
        <footer><button type="button" class="soft-button" @click="showForm=false">取消</button><button class="primary-button" :disabled="saving">{{saving?'保存中…':'保存绑定'}}</button></footer>
      </form>
    </div>
  </div>
</template>
