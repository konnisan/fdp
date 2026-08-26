<script setup>
import { computed, onMounted, ref } from 'vue'
import { ExternalLink, FileText, FolderKanban, Monitor, Workflow } from 'lucide-vue-next'
import { listDeployments, listProjects } from '../api'

const emit = defineEmits(['navigate'])
const projects = ref([])
const deployments = ref([])
const loading = ref(true)
const running = computed(() => projects.value.filter(p => p.status === 'RUNNING' || p.status === 'DEPLOYED').length)
const failed = computed(() => deployments.value.filter(d => d.status === 'FAILED').length)
const recent = computed(() => deployments.value.slice(0, 5))
const quick = computed(() => projects.value.slice(0, 3))
function projectName(id) { return projects.value.find(p => Number(p.id) === Number(id))?.projectName || `项目 #${id}` }
function short(v) { return v && v !== 'DRY-RUN' ? String(v).slice(0, 8) : (v || '-') }
function previewUrl(p) { return `${window.location.protocol}//${window.location.host}${p.previewPath || ''}` }
onMounted(async () => { try { ;[projects.value, deployments.value] = await Promise.all([listProjects(), listDeployments()]) } finally { loading.value = false } })
</script>

<template>
  <div class="page-stack">
    <div class="metric-grid">
      <article class="metric-card"><span class="metric-icon blue"><FolderKanban :size="25" /></span><div><p>POC 项目总数</p><strong>{{ projects.length }}</strong><small>Codeup 驱动项目</small></div></article>
      <article class="metric-card"><span class="metric-icon green"><Monitor :size="25" /></span><div><p>正在运行</p><strong>{{ running }}</strong><small>当前可预览项目</small></div></article>
      <article class="metric-card"><span class="metric-icon orange"><Workflow :size="25" /></span><div><p>部署记录</p><strong>{{ deployments.length }}</strong><small>累计发布任务</small></div></article>
      <article class="metric-card"><span class="metric-icon red"><FileText :size="25" /></span><div><p>部署失败</p><strong>{{ failed }}</strong><small>需要关注</small></div></article>
    </div>

    <div class="dashboard-grid">
      <section class="panel"><div class="panel-head"><div><h2>最近部署</h2><p>最近的 Codeup 拉取与发布结果</p></div><button class="link-button" @click="emit('navigate','/deployments')">查看全部</button></div><div class="deployment-list" v-if="recent.length"><button v-for="d in recent" :key="d.id" class="deployment-row" @click="emit('navigate', `/poc-projects/${d.project_id}`)"><span class="dot" :class="d.status?.toLowerCase()"></span><strong>{{ projectName(d.project_id) }}</strong><span class="tag">{{ d.status }}</span><code>{{ short(d.commit_id) }}</code><span class="muted">{{ d.start_time || '-' }}</span></button></div><div v-else class="empty-state">{{ loading ? '正在加载…' : '暂无部署记录' }}</div></section>
      <section class="panel"><div class="panel-head"><div><h2>POC 运行状态</h2><p>客户当前可访问的预览项目</p></div><button class="link-button" @click="emit('navigate','/poc-projects')">查看全部</button></div><div class="table-wrap"><table class="data-table"><thead><tr><th>项目名称</th><th>类型</th><th>状态</th><th>预览地址</th><th>操作</th></tr></thead><tbody><tr v-for="p in projects.slice(0,5)" :key="p.id"><td><strong>{{ p.projectName }}</strong></td><td><span class="type-badge">{{ p.projectType }}</span></td><td><span class="status-text" :class="p.status?.toLowerCase()"><i></i>{{ p.status }}</span></td><td><a :href="previewUrl(p)" target="_blank">{{ p.previewPath }} <ExternalLink :size="12" /></a></td><td><button class="soft-button" @click="emit('navigate', `/poc-projects/${p.id}`)">详情</button></td></tr></tbody></table></div></section>
    </div>

    <section><div class="section-title">快速访问</div><div class="quick-grid"><article v-for="p in quick" :key="p.id" class="quick-card"><span class="quick-icon"><Monitor :size="22" /></span><div><h3>{{ p.projectName }}</h3><p>{{ p.projectType === 'STATIC' ? '纯静态 HTML 预览站点' : 'Node.js + SQLite 交互应用' }}</p><a :href="previewUrl(p)" target="_blank">{{ p.previewPath }} <ExternalLink :size="13" /></a></div><a class="outline-button full" :href="previewUrl(p)" target="_blank">打开预览 <ExternalLink :size="14" /></a></article><article class="quick-card more-card"><span class="quick-icon orange"><FolderKanban :size="22" /></span><div><h3>所有 POC 列表</h3><p>查看所有项目状态</p></div><button class="outline-button full" @click="emit('navigate','/poc-projects')">前往查看</button></article></div></section>
  </div>
</template>
