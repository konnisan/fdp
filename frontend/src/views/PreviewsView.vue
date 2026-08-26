<script setup>
import { computed, onMounted, ref } from 'vue'
import { Eye, ExternalLink } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { listProjects } from '../api'

const emit=defineEmits(['navigate'])
const projects=ref([]),loading=ref(true)
const cards=computed(()=>projects.value.map(p=>({...p,url:`${window.location.protocol}//${window.location.host}${p.previewPath||''}`})))
onMounted(async()=>{try{projects.value=await listProjects()}finally{loading.value=false}})
</script>

<template>
  <div class="page-stack">
    <PageHeader title="访问入口" description="统一外部端口：STATIC 直接读取共享资源，CONTAINER 反向代理到宿主机内部端口。">
      <template #actions><button class="soft-button" @click="emit('navigate','/poc-projects')">管理项目</button></template>
    </PageHeader>
    <div class="preview-grid" v-if="cards.length">
      <article v-for="p in cards" :key="p.id" class="preview-card panel">
        <div class="preview-card-top"><span class="preview-icon"><Eye :size="21" /></span><span class="type-badge">{{p.projectType}}</span><span class="status-text" :class="p.status?.toLowerCase()"><i></i>{{p.status}}</span></div>
        <h2>{{p.projectName}}</h2><code>{{p.projectCode}}</code>
        <div class="preview-path"><span>统一访问地址</span><a :href="p.url" target="_blank">{{p.previewPath}} <ExternalLink :size="13" /></a></div>
        <div class="preview-meta"><span>Branch <b>{{p.gitBranch}}</b></span><span v-if="p.hostPort">Internal <b>127.0.0.1:{{p.hostPort}}</b></span><span v-else>Mode <b>STATIC</b></span></div>
        <div class="row-actions"><a class="primary-button grow" :href="p.url" target="_blank">打开访问<ExternalLink :size="14" /></a><button class="soft-button" @click="emit('navigate',`/poc-projects/${p.id}`)">详情</button></div>
      </article>
    </div>
    <div v-else class="panel empty-state">{{loading?'正在加载…':'暂无交付项目'}}</div>
  </div>
</template>
