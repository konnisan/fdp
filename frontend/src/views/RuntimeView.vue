<script setup>
import { computed, onMounted, ref } from 'vue'
import { Database, RefreshCw, Server, Workflow } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getRuntimeStatus, listProjects } from '../api'

const runtime=ref(null),projects=ref([]),loading=ref(true),error=ref('')
const containerApps=computed(()=>projects.value.filter(p=>p.projectType==='CONTAINER').length)
const staticApps=computed(()=>projects.value.filter(p=>p.projectType==='STATIC').length)
async function load(){loading.value=true;error.value='';try{[runtime.value,projects.value]=await Promise.all([getRuntimeStatus(),listProjects()])}catch(e){error.value=e.response?.data?.message||e.message}finally{loading.value=false}}
onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="运行环境" description="真实检测当前 FDP 所在设备；Windows 用于 DRY-RUN，Linux 用于正式 Docker 发布。">
      <template #actions><button class="soft-button" @click="load"><RefreshCw :size="14" />重新检测</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div>

    <template v-if="runtime">
      <div class="runtime-summary">
        <article class="panel runtime-primary"><div><span>当前设备</span><h2>{{runtime.os}} · {{runtime.executionMode}}</h2><p>Java {{runtime.javaVersion}} · Nginx 对外端口 {{runtime.publicPort}}</p></div><span class="status-text" :class="runtime.liveReady?'running':'pending'"><i></i>{{runtime.liveReady?'LIVE READY':runtime.executionMode}}</span></article>
        <article class="panel runtime-stat"><span>Container 项目</span><strong>{{containerApps}}</strong><small>Docker 单机交付</small></article>
        <article class="panel runtime-stat"><span>Static 项目</span><strong>{{staticApps}}</strong><small>Nginx 静态发布</small></article>
      </div>

      <section class="panel">
        <div class="panel-head"><div><h2>环境依赖检测</h2><p>来自后端当前机器的真实命令探测结果，不再使用固定展示数据。</p></div></div>
        <div class="runtime-grid">
          <article v-for="tool in runtime.tools" :key="tool.name" class="runtime-item">
            <span class="runtime-icon"><Server v-if="tool.name!=='Docker'" :size="20" /><Workflow v-else :size="20" /></span>
            <div><h3>{{tool.name}}</h3><p>{{tool.detail}}</p></div>
            <span class="status-text" :class="tool.available?'running':'failed'"><i></i>{{tool.available?'可用':'不可用'}}</span>
          </article>
        </div>
      </section>

      <section class="panel env-panel">
        <div class="panel-head"><div><h2>FDP 运行配置</h2><p>Windows 默认保持 DRY_RUN；Linux 验收通过后再开启 FDP_EXECUTION_ENABLED=true。</p></div></div>
        <div class="env-code">
          <code>OS={{runtime.os}}</code>
          <code>MODE={{runtime.executionMode}}</code>
          <code>FDP_WORKSPACE_ROOT={{runtime.workspaceRoot}}</code>
          <code>FDP_STATIC_ROOT={{runtime.staticRoot}}</code>
          <code>FDP_DATA_ROOT={{runtime.dataRoot}}</code>
          <code>FDP_NGINX_CONFIG_FILE={{runtime.nginxConfigFile}}</code>
          <code>FDP_PUBLIC_PORT={{runtime.publicPort}}</code>
        </div>
      </section>
    </template>
    <div v-else-if="loading" class="panel empty-state">正在检测当前设备…</div>
  </div>
</template>
