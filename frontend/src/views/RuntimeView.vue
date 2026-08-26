<script setup>
import { computed, onMounted, ref } from 'vue'
import { Database, FolderKanban, GitBranch, Monitor, Network, Workflow } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { listProjects } from '../api'

const projects=ref([])
const host=computed(()=>window.location.hostname||'localhost')
const port=computed(()=>window.location.port||'80')
const containerApps=computed(()=>projects.value.filter(p=>p.projectType==='CONTAINER').length)
const staticApps=computed(()=>projects.value.filter(p=>p.projectType==='STATIC').length)
onMounted(async()=>{projects.value=await listProjects()})
const checks=[
  {name:'Docker Engine',desc:'构建镜像并运行独立交付容器',icon:Monitor},
  {name:'Nginx',desc:'唯一对外端口与 Path 反向代理',icon:Network},
  {name:'Git / Codeup',desc:'从 Codeup clone / fetch / reset 指定分支源码',icon:GitBranch},
  {name:'curl',desc:'容器 Health Check 与运行校验',icon:Workflow},
  {name:'Persistent Data',desc:'Volume 数据保存在宿主机，不随容器删除',icon:Database},
  {name:'Workspace',desc:'/data/fdp/workspaces 仅作为 Codeup 部署副本',icon:FolderKanban}
]
</script>

<template>
  <div class="page-stack">
    <PageHeader title="运行环境" description="V1 使用单台 Linux + Docker，不引入 K8S；未来多服务器横向扩展时再增加编排层。" />
    <div class="runtime-summary"><article class="panel runtime-primary"><div><span>统一访问入口</span><h2>{{host}}:{{port}}</h2><p>Nginx 根据 Path 分流到静态目录或 127.0.0.1 上的 Docker 容器端口。</p></div><span class="status-text running"><i></i>单机 Docker</span></article><article class="panel runtime-stat"><span>Container 服务</span><strong>{{containerApps}}</strong><small>Docker 管理</small></article><article class="panel runtime-stat"><span>Static 项目</span><strong>{{staticApps}}</strong><small>Nginx 共享资源</small></article></div>
    <section class="panel"><div class="panel-head"><div><h2>Linux 部署依赖</h2><p>正式服务器需要满足以下基础能力；Node/Java/Python 版本由各项目 Dockerfile 自己管理。</p></div></div><div class="runtime-grid"><article v-for="c in checks" :key="c.name" class="runtime-item"><span class="runtime-icon"><component :is="c.icon" :size="20" /></span><div><h3>{{c.name}}</h3><p>{{c.desc}}</p></div><span class="status-text running"><i></i>需要安装</span></article></div></section>
    <section class="panel env-panel"><div class="panel-head"><div><h2>推荐服务器目录</h2><p>源码工作区、静态站点和持久化数据相互分离。</p></div></div><div class="env-code"><code>FDP_WORKSPACE_ROOT=/data/fdp/workspaces</code><code>FDP_STATIC_ROOT=/data/fdp/sites</code><code>FDP_DATA_ROOT=/data/fdp/data</code><code>FDP_NGINX_CONFIG_FILE=/etc/nginx/conf.d/fdp.conf</code><code>FDP_PUBLIC_PORT=8090</code></div></section>
  </div>
</template>
