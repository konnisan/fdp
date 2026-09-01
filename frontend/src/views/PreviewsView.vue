<script setup>
import { computed, onMounted, ref } from 'vue'
import { Eye, ExternalLink } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getStaticCatalog, listProjects } from '../api'

const emit=defineEmits(['navigate'])
const projects=ref([])
const catalog=ref({})
const loading=ref(true)

function publicOrigin(){
  const port=Number(catalog.value?.publicPort||0)
  const protocol=window.location.protocol
  const host=window.location.hostname
  return port?`${protocol}//${host}:${port}`:`${protocol}//${window.location.host}`
}

const cards=computed(()=>{
  const result=[]
  if(catalog.value?.activeProject){
    result.push({
      id:`static:${catalog.value.activeProject}`,
      projectName:catalog.value.activeProject,
      projectCode:'AUTO STATIC',
      projectType:'STATIC',
      status:'PUBLISHED',
      previewPath:'/',
      gitBranch:catalog.value.branch||'main',
      url:`${publicOrigin()}/`,
      autoStatic:true
    })
  }
  projects.value
    .filter(p=>p.projectType==='CONTAINER')
    .forEach(p=>result.push({...p,url:`${publicOrigin()}${p.previewPath||''}`}))
  return result
})

onMounted(async()=>{
  try{
    const [ps,sc]=await Promise.all([listProjects(),getStaticCatalog()])
    projects.value=ps
    catalog.value=sc
  }finally{loading.value=false}
})
</script>

<template>
  <div class="page-stack">
    <PageHeader title="访问入口" description="统一外部端口：当前选中的 STATIC 直接占用根路径 /；CONTAINER 继续通过各自路径反向代理到宿主机内部端口。">
      <template #actions><button class="soft-button" @click="emit('navigate','/static-previews')">选择静态项目</button><button class="soft-button" @click="emit('navigate','/poc-projects')">管理容器项目</button></template>
    </PageHeader>
    <div class="preview-grid" v-if="cards.length">
      <article v-for="p in cards" :key="p.id" class="preview-card panel">
        <div class="preview-card-top"><span class="preview-icon"><Eye :size="21" /></span><span class="type-badge">{{p.projectType}}</span><span class="status-text" :class="p.status?.toLowerCase()"><i></i>{{p.status}}</span></div>
        <h2>{{p.projectName}}</h2><code>{{p.projectCode}}</code>
        <div class="preview-path"><span>统一访问地址</span><a :href="p.url" target="_blank">{{p.previewPath}} <ExternalLink :size="13" /></a></div>
        <div class="preview-meta"><span>Branch <b>{{p.gitBranch}}</b></span><span v-if="p.hostPort">Internal <b>127.0.0.1:{{p.hostPort}}</b></span><span v-else>Mode <b>AUTO STATIC</b></span></div>
        <div class="row-actions"><a class="primary-button grow" :href="p.url" target="_blank">打开访问<ExternalLink :size="14" /></a><button v-if="p.autoStatic" class="soft-button" @click="emit('navigate','/static-previews')">切换</button><button v-else class="soft-button" @click="emit('navigate',`/poc-projects/${p.id}`)">详情</button></div>
      </article>
    </div>
    <div v-else class="panel empty-state">{{loading?'正在加载…':'暂无可访问项目'}}</div>
  </div>
</template>
