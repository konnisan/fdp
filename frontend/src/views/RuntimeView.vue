<script setup>
import { computed, onMounted, ref } from 'vue'
import { Database, FolderKanban, GitBranch, Monitor, Network, Workflow } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { listProjects } from '../api'

const projects=ref([])
const host=computed(()=>window.location.hostname||'localhost')
const port=computed(()=>window.location.port||'80')
const nodeApps=computed(()=>projects.value.filter(p=>p.projectType==='NODE_SQLITE').length)
const staticApps=computed(()=>projects.value.filter(p=>p.projectType==='STATIC').length)
onMounted(async()=>{projects.value=await listProjects()})
const checks=[
  {name:'Nginx',desc:'统一对外端口与 POC Path 路由',icon:Network},
  {name:'Git / Codeup',desc:'从 Codeup clone / fetch / reset 源码',icon:GitBranch},
  {name:'Node.js / npm',desc:'构建 Node.js + SQLite 交互 POC',icon:Workflow},
  {name:'PM2',desc:'管理 Node.js POC 常驻进程',icon:Monitor},
  {name:'SQLite Runtime',desc:'运行数据与 Git 工作区分离',icon:Database},
  {name:'Workspace',desc:'/data/fdp/workspaces 与静态发布目录',icon:FolderKanban}
]
</script>

<template>
  <div class="page-stack">
    <PageHeader title="运行环境" description="这里只展示 FDP 部署链路需要的基础运行环境，不承担云原生集群管理。" />
    <div class="runtime-summary"><article class="panel runtime-primary"><div><span>统一访问入口</span><h2>{{host}}:{{port}}</h2><p>Nginx 根据 /poc/* Path 分流到静态目录或内部 Node.js 端口。</p></div><span class="status-text running"><i></i>运行中</span></article><article class="panel runtime-stat"><span>Node POC</span><strong>{{nodeApps}}</strong><small>PM2 管理</small></article><article class="panel runtime-stat"><span>Static POC</span><strong>{{staticApps}}</strong><small>Nginx 静态发布</small></article></div>
    <section class="panel"><div class="panel-head"><div><h2>部署依赖</h2><p>Linux 服务器上需要满足以下基础能力。</p></div></div><div class="runtime-grid"><article v-for="c in checks" :key="c.name" class="runtime-item"><span class="runtime-icon"><component :is="c.icon" :size="20" /></span><div><h3>{{c.name}}</h3><p>{{c.desc}}</p></div><span class="status-text running"><i></i>正常</span></article></div></section>
    <section class="panel env-panel"><div class="panel-head"><div><h2>推荐服务器目录</h2><p>工作区只作为 Codeup 部署副本，SQLite 运行数据独立保存。</p></div></div><div class="env-code"><code>FDP_WORKSPACE_ROOT=/data/fdp/workspaces</code><code>FDP_STATIC_ROOT=/data/fdp/sites</code><code>FDP_DATA_ROOT=/data/fdp/data</code><code>FDP_NGINX_CONFIG_FILE=/etc/nginx/conf.d/fdp-poc.conf</code><code>FDP_PUBLIC_PORT=8090</code></div></section>
  </div>
</template>
