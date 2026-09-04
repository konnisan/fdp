<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AppLayout from './layout/Index.vue'
import StaticCatalogView from './views/StaticCatalogView.vue'
import ProjectCenterView from './views/ProjectCenterView.vue'
import ProjectCreateView from './views/ProjectCreateView.vue'
import UnifiedProjectDetailView from './views/UnifiedProjectDetailView.vue'

const path=ref(window.location.pathname||'/')
function syncPath(){path.value=window.location.pathname||'/'}
function navigate(to){if(window.location.pathname!==to)window.history.pushState({},'',to);path.value=to;window.scrollTo({top:0,behavior:'smooth'})}

const route=computed(()=>{
  const p=path.value
  const containerMatch=p.match(/^\/containers\/(source|artifact)\/(\d+)$/)
  if(containerMatch)return{component:UnifiedProjectDetailView,title:'容器项目',props:{projectKind:containerMatch[1],projectId:Number(containerMatch[2])}}

  // 兼容旧链接，统一收敛到容器部署页面模型。
  const legacyProject=p.match(/^\/projects\/(source|artifact)\/(\d+)$/)
  if(legacyProject)return{component:UnifiedProjectDetailView,title:'容器项目',props:{projectKind:legacyProject[1],projectId:Number(legacyProject[2])}}
  const legacyPoc=p.match(/^\/poc-projects\/(\d+)$/)
  if(legacyPoc)return{component:UnifiedProjectDetailView,title:'容器项目',props:{projectKind:'source',projectId:Number(legacyPoc[1])}}

  if(['/containers/new','/projects/new'].includes(p))return{component:ProjectCreateView,title:'接入容器项目'}
  if(['/containers','/projects','/artifact-delivery','/deployments','/integrations','/runtime','/artifacts','/yunxiao-artifacts','/dashboard'].includes(p))return{component:ProjectCenterView,title:'容器部署'}
  if(['/', '/static-previews','/previews'].includes(p))return{component:StaticCatalogView,title:'静态预览'}
  return{component:StaticCatalogView,title:'静态预览'}
})

onMounted(()=>window.addEventListener('popstate',syncPath))
onUnmounted(()=>window.removeEventListener('popstate',syncPath))
</script>

<template><AppLayout :active-path="path" :page-title="route.title" @navigate="navigate"><component :is="route.component" v-bind="route.props||{}" @navigate="navigate" /></AppLayout></template>
