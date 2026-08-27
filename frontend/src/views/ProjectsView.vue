<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ExternalLink, KeyRound, Pencil, Plus, RefreshCw, Search, Trash2, X } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  createProject,
  createSourceCredential,
  deleteProject,
  listProjects,
  listSourceCredentials,
  testSourceCredential,
  updateProject
} from '../api'

const emit = defineEmits(['navigate'])
const projects = ref([])
const credentials = ref([])
const keyword = ref('')
const typeFilter = ref('ALL')
const statusFilter = ref('ALL')
const showForm = ref(false)
const showCredentialForm = ref(false)
const editingId = ref(null)
const saving = ref(false)
const credentialSaving = ref(false)
const testingSource = ref(false)
const sourceTest = ref(null)
const error = ref('')

const defaults = () => ({
  projectCode:'', projectName:'', gitUrl:'', gitBranch:'develop', credentialId:null, projectType:'STATIC', projectDirectory:'.',
  buildCommand:'', buildOutput:'dist', dockerfilePath:'Dockerfile', dockerBuildContext:'.', imageName:'', containerName:'',
  hostPort:null, containerPort:3000, cpuLimit:'1', memoryLimit:'512m', hostDataPath:'', containerDataPath:'',
  healthCheckPath:'', previewPath:'/poc/'
})
const form = reactive(defaults())
const credentialForm = reactive({name:'公司 Codeup', provider:'CODEUP', cloneUsername:'', token:''})

const filtered = computed(() => projects.value.filter(p => {
  const text = `${p.projectName || ''} ${p.projectCode || ''} ${p.gitUrl || ''}`.toLowerCase()
  const q = keyword.value.trim().toLowerCase()
  return (!q || text.includes(q)) && (typeFilter.value === 'ALL' || p.projectType === typeFilter.value) && (statusFilter.value === 'ALL' || p.status === statusFilter.value)
}))

function previewUrl(p){return `${window.location.protocol}//${window.location.host}${p.previewPath || ''}`}
async function load(){
  error.value=''
  try{
    const [ps, cs] = await Promise.all([listProjects(), listSourceCredentials()])
    projects.value=ps
    credentials.value=cs
  }catch(e){error.value=e.response?.data?.message||e.message}
}
function openCreate(){
  editingId.value=null
  Object.assign(form,defaults())
  sourceTest.value=null
  showForm.value=true
}
function openEdit(p){
  editingId.value=p.id
  Object.assign(form,defaults(),p)
  sourceTest.value=null
  showForm.value=true
}
function openCredentialCreate(){
  Object.assign(credentialForm,{name:'公司 Codeup',provider:'CODEUP',cloneUsername:'',token:''})
  showCredentialForm.value=true
}
async function saveCredential(){
  if(!credentialForm.name.trim()||!credentialForm.cloneUsername.trim()||!credentialForm.token.trim()){
    error.value='请填写凭据名称、Codeup HTTPS 克隆账号和个人访问令牌'
    return
  }
  credentialSaving.value=true
  error.value=''
  try{
    const created=await createSourceCredential({...credentialForm})
    credentials.value=await listSourceCredentials()
    form.credentialId=created.id
    showCredentialForm.value=false
    sourceTest.value=null
  }catch(e){error.value=e.response?.data?.message||e.message}
  finally{credentialSaving.value=false}
}
async function testSource(){
  if(!form.credentialId){error.value='请先选择 Codeup 凭据';return}
  if(!form.gitUrl.trim()){error.value='请先填写 Codeup Git 地址';return}
  testingSource.value=true
  error.value=''
  sourceTest.value=null
  try{
    sourceTest.value=await testSourceCredential(Number(form.credentialId),{gitUrl:form.gitUrl,gitBranch:form.gitBranch})
    credentials.value=await listSourceCredentials()
  }catch(e){error.value=e.response?.data?.message||e.message}
  finally{testingSource.value=false}
}
async function save(){
  if(!form.projectCode.trim()||!form.projectName.trim()||!form.gitUrl.trim()){error.value='请填写项目编码、项目名称和 Codeup Git 地址';return}
  if(/^https?:\/\//i.test(form.gitUrl)&&!form.credentialId){error.value='HTTPS Codeup Git 需要选择源码凭据';return}
  saving.value=true
  error.value=''
  try{
    const payload={...form,
      credentialId:form.credentialId?Number(form.credentialId):null,
      hostPort:form.projectType==='CONTAINER'?Number(form.hostPort):null,
      containerPort:form.projectType==='CONTAINER'?Number(form.containerPort):null
    }
    editingId.value?await updateProject(editingId.value,payload):await createProject(payload)
    showForm.value=false
    await load()
  }catch(e){error.value=e.response?.data?.message||e.message}
  finally{saving.value=false}
}
async function remove(p){
  if(!confirm(`确定删除 ${p.projectName}？`))return
  try{await deleteProject(p.id);await load()}catch(e){error.value=e.response?.data?.message||e.message}
}
onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="交付项目" description="Codeup 是源码事实源；HTTPS Token 以可复用凭据保存，项目拉取后按 STATIC 或 Docker 容器发布。">
      <template #actions>
        <button class="soft-button" @click="load"><RefreshCw :size="14" />刷新</button>
        <button class="primary-button" @click="openCreate"><Plus :size="15" />新建项目</button>
      </template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{ error }}</div>

    <section class="panel">
      <div class="toolbar">
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索项目名称 / 编码 / Git 地址" /></label>
        <select v-model="typeFilter"><option value="ALL">全部类型</option><option value="STATIC">STATIC</option><option value="CONTAINER">CONTAINER</option></select>
        <select v-model="statusFilter"><option value="ALL">全部状态</option><option value="DRAFT">DRAFT</option><option value="RUNNING">RUNNING</option><option value="PUBLISHED">PUBLISHED</option><option value="STOPPED">STOPPED</option></select>
        <span class="toolbar-count">{{ filtered.length }} 个项目</span>
      </div>
      <div class="table-wrap">
        <table class="data-table project-table">
          <thead><tr><th>项目</th><th>交付方式</th><th>Codeup / Branch</th><th>状态</th><th>访问路径</th><th>操作</th></tr></thead>
          <tbody><tr v-for="p in filtered" :key="p.id">
            <td><button class="project-name" @click="emit('navigate', `/poc-projects/${p.id}`)">{{p.projectName}}</button><code>{{p.projectCode}}</code></td>
            <td><span class="type-badge">{{p.projectType}}</span></td>
            <td><div class="git-cell"><span>{{p.gitUrl}}</span><small>{{p.gitBranch}} · {{p.credentialName||'SSH / 服务器凭据'}} · {{p.projectDirectory||'.'}}</small></div></td>
            <td><span class="status-text" :class="p.status?.toLowerCase()"><i></i>{{p.status}}</span></td>
            <td><a :href="previewUrl(p)" target="_blank">{{p.previewPath}} <ExternalLink :size="12" /></a></td>
            <td><div class="row-actions"><button class="soft-button" @click="emit('navigate', `/poc-projects/${p.id}`)">详情</button><button class="icon-button" title="编辑" @click="openEdit(p)"><Pencil :size="14" /></button><button class="icon-button danger" title="删除" @click="remove(p)"><Trash2 :size="14" /></button></div></td>
          </tr></tbody>
        </table>
      </div>
      <div v-if="!filtered.length" class="empty-state">暂无符合条件的交付项目</div>
    </section>

    <div v-if="showForm" class="modal-mask" @click.self="showForm=false">
      <form class="modal-card large" @submit.prevent="save">
        <header>
          <div><h2>{{editingId?'编辑交付项目':'新建交付项目'}}</h2><p>一个 Git 地址对应一个交付单元；Codeup Token 通过共享凭据注入 Git，不写入仓库 URL。</p></div>
          <button type="button" class="icon-button" @click="showForm=false"><X :size="18" /></button>
        </header>
        <div class="form-grid">
          <label>项目编码 *<input v-model="form.projectCode" placeholder="l2-server" /></label>
          <label>项目名称 *<input v-model="form.projectName" placeholder="L2 Server" /></label>

          <label class="span-2">Codeup Git *
            <input v-model="form.gitUrl" placeholder="https://codeup.aliyun.com/group/repo.git" @input="sourceTest=null" />
          </label>
          <label>Git Branch<input v-model="form.gitBranch" @input="sourceTest=null" /></label>
          <label>交付方式<select v-model="form.projectType"><option value="STATIC">STATIC - 静态资源</option><option value="CONTAINER">CONTAINER - Docker 服务</option></select></label>

          <label>Codeup 凭据
            <select v-model="form.credentialId" @change="sourceTest=null">
              <option :value="null">不使用（仅 SSH / 服务器已有凭据）</option>
              <option v-for="c in credentials" :key="c.id" :value="c.id">{{c.name}} · {{c.cloneUsername}} · {{c.status}}</option>
            </select>
          </label>
          <div style="display:flex;align-items:flex-end;gap:8px">
            <button type="button" class="soft-button" @click="openCredentialCreate"><KeyRound :size="14" />新增凭据</button>
            <button type="button" class="soft-button" :disabled="testingSource||!form.credentialId" @click="testSource"><RefreshCw :size="14" />{{testingSource?'测试中…':'测试 Codeup'}}</button>
          </div>

          <div v-if="sourceTest" class="span-2" :style="{fontSize:'13px',padding:'9px 12px',border:'1px solid #e5e7eb',borderRadius:'6px',background:'#f8fafc'}">
            <strong>{{sourceTest.status}}</strong> · {{sourceTest.message}}
            <code v-if="sourceTest.head" style="margin-left:8px">{{sourceTest.head.slice(0,12)}}</code>
          </div>

          <label class="span-2">仓库内项目目录<input v-model="form.projectDirectory" placeholder=". 或 poc/poc/l2-data-aggregation/l2-server" /></label>
          <label>访问 Path<input v-model="form.previewPath" placeholder="/poc/l2-server" /></label>

          <template v-if="form.projectType==='STATIC'">
            <label>构建产物<input v-model="form.buildOutput" placeholder="dist" /></label>
            <label class="span-2">静态构建命令<input v-model="form.buildCommand" placeholder="npm ci && npm run build；纯 HTML 可留空" /></label>
          </template>

          <template v-else>
            <label>Dockerfile Path<input v-model="form.dockerfilePath" placeholder="Dockerfile" /></label><label>Build Context<input v-model="form.dockerBuildContext" placeholder="." /></label>
            <label>Image Name<input v-model="form.imageName" :placeholder="`fdp/${form.projectCode||'project'}`" /></label><label>Container Name<input v-model="form.containerName" :placeholder="`fdp-${form.projectCode||'project'}`" /></label>
            <label>宿主机端口<input v-model="form.hostPort" type="number" placeholder="3101" /></label><label>容器端口<input v-model="form.containerPort" type="number" placeholder="3000" /></label>
            <label>CPU Limit<input v-model="form.cpuLimit" placeholder="1" /></label><label>Memory Limit<input v-model="form.memoryLimit" placeholder="512m" /></label>
            <label>Host Data Path<input v-model="form.hostDataPath" placeholder="/data/fdp/data/l2-server" /></label><label>Container Data Path<input v-model="form.containerDataPath" placeholder="/app/data" /></label>
            <label class="span-2">Health Check Path<input v-model="form.healthCheckPath" placeholder="/health，可留空" /></label>
          </template>
        </div>
        <footer><button type="button" class="soft-button" @click="showForm=false">取消</button><button class="primary-button" :disabled="saving">{{saving?'保存中…':'保存项目'}}</button></footer>
      </form>
    </div>

    <div v-if="showCredentialForm" class="modal-mask" @click.self="showCredentialForm=false">
      <form class="modal-card" @submit.prevent="saveCredential">
        <header>
          <div><h2>新增 Codeup 凭据</h2><p>同一账号的个人访问令牌可以被该账号有权访问的多个项目复用。</p></div>
          <button type="button" class="icon-button" @click="showCredentialForm=false"><X :size="18" /></button>
        </header>
        <div class="form-grid">
          <label class="span-2">凭据名称 *<input v-model="credentialForm.name" placeholder="公司 Codeup" /></label>
          <label class="span-2">HTTPS 克隆账号 *<input v-model="credentialForm.cloneUsername" placeholder="Codeup 个人设置中的 HTTPS 克隆账号" /></label>
          <label class="span-2">个人访问令牌 *<input v-model="credentialForm.token" type="password" autocomplete="new-password" placeholder="仅本次提交可见" /></label>
          <div class="span-2" :style="{fontSize:'12px',color:'#64748b',lineHeight:'1.7'}">
            Token 仅以 AES-GCM 密文保存；部署时通过临时 GIT_ASKPASS 注入，不会拼进 Git URL 或部署日志。服务器需先配置 FDP_CREDENTIAL_KEY。
          </div>
        </div>
        <footer><button type="button" class="soft-button" @click="showCredentialForm=false">取消</button><button class="primary-button" :disabled="credentialSaving">{{credentialSaving?'保存中…':'保存凭据'}}</button></footer>
      </form>
    </div>
  </div>
</template>
