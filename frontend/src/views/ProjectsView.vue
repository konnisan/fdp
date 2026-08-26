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

const defaults = () => ({ projectCode:'', projectName:'', gitUrl:'', gitBranch:'develop', projectType:'STATIC', buildCommand:'', startCommand:'', buildOutput:'dist', internalPort:null, previewPath:'/poc/', pm2Name:'', sqlitePath:'app.db' })
const form = reactive(defaults())

const filtered = computed(() => projects.value.filter(p => {
  const text = `${p.projectName || ''} ${p.projectCode || ''} ${p.gitUrl || ''}`.toLowerCase()
  const q = keyword.value.trim().toLowerCase()
  return (!q || text.includes(q)) && (typeFilter.value === 'ALL' || p.projectType === typeFilter.value) && (statusFilter.value === 'ALL' || p.status === statusFilter.value)
}))

function previewUrl(p) { return `${window.location.protocol}//${window.location.host}${p.previewPath || ''}` }
async function load() { error.value=''; try { projects.value = await listProjects() } catch(e) { error.value=e.response?.data?.message || e.message } }
function openCreate() { editingId.value=null; Object.assign(form, defaults()); showForm.value=true }
function openEdit(p) { editingId.value=p.id; Object.assign(form, defaults(), p); showForm.value=true }
async function save() {
  if (!form.projectCode.trim() || !form.projectName.trim() || !form.gitUrl.trim()) { error.value='请填写项目编码、项目名称和 Codeup Git 地址'; return }
  saving.value=true; error.value=''
  try {
    const payload={...form,internalPort:form.projectType==='NODE_SQLITE'?Number(form.internalPort):null}
    editingId.value ? await updateProject(editingId.value,payload) : await createProject(payload)
    showForm.value=false; await load()
  } catch(e) { error.value=e.response?.data?.message || e.message } finally { saving.value=false }
}
async function remove(p) { if (!confirm(`确定删除 ${p.projectName}？`)) return; try { await deleteProject(p.id); await load() } catch(e) { error.value=e.response?.data?.message || e.message } }

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="POC 项目" description="Codeup 是唯一源码来源；FDP 只维护部署、运行与客户预览配置。">
      <template #actions><button class="soft-button" @click="load"><RefreshCw :size="14" />刷新</button><button class="primary-button" @click="openCreate"><Plus :size="15" />新建 POC</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{ error }}</div>

    <section class="panel">
      <div class="toolbar">
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索项目名称 / 编码 / Git 地址" /></label>
        <select v-model="typeFilter"><option value="ALL">全部类型</option><option value="STATIC">STATIC</option><option value="NODE_SQLITE">NODE_SQLITE</option></select>
        <select v-model="statusFilter"><option value="ALL">全部状态</option><option value="RUNNING">RUNNING</option><option value="DEPLOYED">DEPLOYED</option><option value="STOPPED">STOPPED</option><option value="FAILED">FAILED</option></select>
        <span class="toolbar-count">{{ filtered.length }} 个项目</span>
      </div>
      <div class="table-wrap">
        <table class="data-table project-table">
          <thead><tr><th>项目</th><th>类型</th><th>Codeup / Branch</th><th>状态</th><th>预览地址</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="p in filtered" :key="p.id">
              <td><button class="project-name" @click="emit('navigate', `/poc-projects/${p.id}`)">{{ p.projectName }}</button><code>{{ p.projectCode }}</code></td>
              <td><span class="type-badge">{{ p.projectType }}</span></td>
              <td><div class="git-cell"><span>{{ p.gitUrl }}</span><small>{{ p.gitBranch }}</small></div></td>
              <td><span class="status-text" :class="p.status?.toLowerCase()"><i></i>{{ p.status }}</span></td>
              <td><a :href="previewUrl(p)" target="_blank">{{ p.previewPath }} <ExternalLink :size="12" /></a></td>
              <td><div class="row-actions"><button class="soft-button" @click="emit('navigate', `/poc-projects/${p.id}`)">详情</button><button class="icon-button" title="编辑" @click="openEdit(p)"><Pencil :size="14" /></button><button class="icon-button danger" title="删除" @click="remove(p)"><Trash2 :size="14" /></button></div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filtered.length" class="empty-state">暂无符合条件的 POC 项目</div>
    </section>

    <div v-if="showForm" class="modal-mask" @click.self="showForm=false">
      <form class="modal-card large" @submit.prevent="save">
        <header><div><h2>{{ editingId ? '编辑 POC' : '新建 POC' }}</h2><p>配置 Codeup 源码、构建方式和统一预览入口</p></div><button type="button" class="icon-button" @click="showForm=false"><X :size="18" /></button></header>
        <div class="form-grid">
          <label>项目编码 *<input v-model="form.projectCode" placeholder="customer-a" /></label><label>项目名称 *<input v-model="form.projectName" placeholder="客户 A POC" /></label>
          <label class="span-2">Codeup Git *<input v-model="form.gitUrl" placeholder="git@codeup.xxx/group/poc.git" /></label>
          <label>Git Branch<input v-model="form.gitBranch" /></label><label>项目类型<select v-model="form.projectType"><option value="STATIC">STATIC - 静态站点</option><option value="NODE_SQLITE">NODE_SQLITE - Node + SQLite</option></select></label>
          <label>预览 Path<input v-model="form.previewPath" placeholder="/poc/customer-a" /></label><label>构建产物<input v-model="form.buildOutput" placeholder="dist" /></label>
          <label class="span-2">构建命令<input v-model="form.buildCommand" placeholder="npm ci && npm run build；纯 HTML 可留空" /></label>
          <template v-if="form.projectType==='NODE_SQLITE'"><label>内部端口<input v-model="form.internalPort" type="number" placeholder="3101" /></label><label>PM2 名称<input v-model="form.pm2Name" placeholder="fdp-customer-a" /></label><label class="span-2">启动命令<input v-model="form.startCommand" placeholder="npm run start" /></label><label>SQLite 文件<input v-model="form.sqlitePath" placeholder="app.db" /></label></template>
        </div>
        <footer><button type="button" class="soft-button" @click="showForm=false">取消</button><button class="primary-button" :disabled="saving">{{ saving ? '保存中…' : '保存 POC' }}</button></footer>
      </form>
    </div>
  </div>
</template>
