<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ExternalLink, Pencil, Plus, RefreshCw, Search, Trash2, X } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { createProject, deleteProject, listProjects, updateProject } from '../api'

const emit = defineEmits(['navigate'])
const projects = ref([])
const keyword = ref('')
const typeFilter = ref('ALL')
const statusFilter = ref('ALL')
const showForm = ref(false)
const editingId = ref(null)
const saving = ref(false)
const error = ref('')

const defaults = () => ({
  projectCode:'', projectName:'', gitUrl:'', gitBranch:'develop', projectType:'STATIC', projectDirectory:'.',
  buildCommand:'', buildOutput:'dist', dockerfilePath:'Dockerfile', dockerBuildContext:'.', imageName:'', containerName:'',
  hostPort:null, containerPort:3000, cpuLimit:'1', memoryLimit:'512m', hostDataPath:'', containerDataPath:'',
  healthCheckPath:'', previewPath:'/poc/'
})
const form = reactive(defaults())

const filtered = computed(() => projects.value.filter(p => {
  const text = `${p.projectName || ''} ${p.projectCode || ''} ${p.gitUrl || ''}`.toLowerCase()
  const q = keyword.value.trim().toLowerCase()
  return (!q || text.includes(q)) && (typeFilter.value === 'ALL' || p.projectType === typeFilter.value) && (statusFilter.value === 'ALL' || p.status === statusFilter.value)
}))

function previewUrl(p){return `${window.location.protocol}//${window.location.host}${p.previewPath || ''}`}
async function load(){error.value='';try{projects.value=await listProjects()}catch(e){error.value=e.response?.data?.message||e.message}}
function openCreate(){editingId.value=null;Object.assign(form,defaults());showForm.value=true}
function openEdit(p){editingId.value=p.id;Object.assign(form,defaults(),p);showForm.value=true}
async function save(){
  if(!form.projectCode.trim()||!form.projectName.trim()||!form.gitUrl.trim()){error.value='请填写项目编码、项目名称和 Codeup Git 地址';return}
  saving.value=true;error.value=''
  try{
    const payload={...form,
      hostPort:form.projectType==='CONTAINER'?Number(form.hostPort):null,
      containerPort:form.projectType==='CONTAINER'?Number(form.containerPort):null
    }
    editingId.value?await updateProject(editingId.value,payload):await createProject(payload)
    showForm.value=false;await load()
  }catch(e){error.value=e.response?.data?.message||e.message}finally{saving.value=false}
}
async function remove(p){if(!confirm(`确定删除 ${p.projectName}？`))return;try{await deleteProject(p.id);await load()}catch(e){error.value=e.response?.data?.message||e.message}}
onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="交付项目" description="Codeup 是源码事实源；STATIC 共享服务器资源，代码型服务统一通过 Docker 容器发布。">
      <template #actions><button class="soft-button" @click="load"><RefreshCw :size="14" />刷新</button><button class="primary-button" @click="openCreate"><Plus :size="15" />新建项目</button></template>
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
            <td><div class="git-cell"><span>{{p.gitUrl}}</span><small>{{p.gitBranch}} · {{p.projectDirectory||'.'}}</small></div></td>
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
        <header><div><h2>{{editingId?'编辑交付项目':'新建交付项目'}}</h2><p>一个 Git 地址对应一个交付单元；容器项目由 Dockerfile 决定具体技术栈。</p></div><button type="button" class="icon-button" @click="showForm=false"><X :size="18" /></button></header>
        <div class="form-grid">
          <label>项目编码 *<input v-model="form.projectCode" placeholder="l2-server" /></label><label>项目名称 *<input v-model="form.projectName" placeholder="L2 Server" /></label>
          <label class="span-2">Codeup Git *<input v-model="form.gitUrl" placeholder="git@codeup.xxx/group/repo.git" /></label>
          <label>Git Branch<input v-model="form.gitBranch" /></label><label>交付方式<select v-model="form.projectType"><option value="STATIC">STATIC - 静态资源</option><option value="CONTAINER">CONTAINER - Docker 服务</option></select></label>
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
  </div>
</template>
