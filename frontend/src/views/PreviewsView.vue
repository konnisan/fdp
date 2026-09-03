<script setup>
import { computed, onMounted, ref } from 'vue'
import { Eye, ExternalLink } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getStaticCatalog, listArtifactDeliveryProjects, listProjects } from '../api'

const emit=defineEmits(['navigate'])
const projects=ref([])
const artifactProjects=ref([])
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
      source:catalog.value.branch||'main',
      url:`${publicOrigin()}/`,
      autoStatic:true
    })
  }
  artifactProjects.value
    .filter(p=>p.status==='RUNNING')
    .forEach(p=>result.push({
      ...p,
      id:`artifact:${p.id}`,
      projectType:'PIPELINE_ARTIFACT',
      source:p.currentRunId?`Flow #${p.currentRunId}`:'Flow',
      url:`${publicOrigin()}${p.previewPath||''}/`,
      artifactDelivery:true
    }))
  projects.value
    .filter(p=>p.projectType==='CONTAINER'&&(p.status==='RUNNING'||p.status==='PUBLISHED'))
    .forEach(p=>result.push({...p,source:p.gitBranch||'-',url:`${publicOrigin()}${p.previewPath||''}`}))
  return result
})

onMounted(async()=>{
  try{
    const [ps,aps,sc]=await Promise.all([listProjects(),listArtifactDeliveryProjects(),getStaticCatalog()])
    projects.value=ps
    artifactProjects.value=aps
    catalog.value=sc
  }finally{loading.value=false}
})
</script>

<template>
  <div class="page-stack">
    <PageHeader title="访问入口" description="统一 Nginx 对外端口：STATIC 可占用根路径；正式 Flow 制品工程通过各自 previewPath 提供静态前端，并把项目路径下的 /api/ 转发到后端 Docker。">
      <template #actions><button class="soft-button" @click="emit('navigate','/static-previews')">选择静态项目</button><button class="soft-button" @click="emit('navigate','/artifact-delivery')">工程制品交付</button></template>
    </PageHeader>
    <div class="preview-grid" v-if="cards.length">
      <article v-for="p in cards" :key="p.id" class="preview-card panel">
        <div class="preview-card-top"><span class="preview-icon"><Eye :size="21" /></span><span class="type-badge">{{p.projectType}}</span><span class="status-text" :class="p.status?.toLowerCase()"><i></i>{{p.status}}</span></div>
        <h2>{{p.projectName}}</h2><code>{{p.projectCode}}</code>
        <div class="preview-path"><span>统一访问地址</span><a :href="p.url" target="_blank">{{p.previewPath}} <ExternalLink :size="13" /></a></div>
        <div class="preview-meta"><span>Source <b>{{p.source}}</b></span><span v-if="p.hostPort">Backend <b>127.0.0.1:{{p.hostPort}}</b></span><span v-else>Mode <b>AUTO STATIC</b></span></div>
        <div class="row-actions"><a class="primary-button grow" :href="p.url" target="_blank">打开访问<ExternalLink :size="14" /></a><button v-if="p.autoStatic" class="soft-button" @click="emit('navigate','/static-previews')">切换</button><button v-else-if="p.artifactDelivery" class="soft-button" @click="emit('navigate','/artifact-delivery')">版本</button><button v-else class="soft-button" @click="emit('navigate',`/poc-projects/${p.id}`)">详情</button></div>
      </article>
    </div>
    <div v-else class="panel empty-state">{{loading?'正在加载…':'暂无可访问项目'}}</div>
  </div>
</template>
