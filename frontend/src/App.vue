<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AppLayout from './layout/Index.vue'
import PipelinesView from './views/PipelinesView.vue'
import ArtifactsView from './views/ArtifactsView.vue'
import StaticCatalogView from './views/StaticCatalogView.vue'
import ProjectCenterView from './views/ProjectCenterView.vue'
import ProjectCreateView from './views/ProjectCreateView.vue'
import ContainerDetailView from './views/ContainerDetailView.vue'
import ArtifactContainerEditView from './views/ArtifactContainerEditView.vue'
import IntegrationsView from './views/IntegrationsView.vue'
import ManagedProjectsView from './views/ManagedProjectsView.vue'

const path=ref(window.location.pathname||'/')
function syncPath(){path.value=window.location.pathname||'/'}
function navigate(to){if(window.location.pathname!==to)window.history.pushState({},'',to);path.value=to;window.scrollTo({top:0,behavior:'smooth'})}

const route=computed(()=>{
  const p=path.value
  if(['/containers','/containers/new','/managed-projects'].includes(p))return{component:ManagedProjectsView,title:'项目部署'}

  const editMatch=p.match(/^\/containers\/artifact\/(\d+)\/edit$/)
  if(editMatch)return{component:ArtifactContainerEditView,title:'旧版容器配置',props:{projectId:Number(editMatch[1])}}
  const containerMatch=p.match(/^\/containers\/(source|artifact)\/(\d+)$/)
  if(containerMatch)return{component:ContainerDetailView,title:'旧版容器项目',props:{projectKind:containerMatch[1],projectId:Number(containerMatch[2])}}
  const legacyProject=p.match(/^\/projects\/(source|artifact)\/(\d+)$/)
  if(legacyProject)return{component:ContainerDetailView,title:'旧版容器项目',props:{projectKind:legacyProject[1],projectId:Number(legacyProject[2])}}
  const legacyPoc=p.match(/^\/poc-projects\/(\d+)$/)
  if(legacyPoc)return{component:ContainerDetailView,title:'旧版容器项目',props:{projectKind:'source',projectId:Number(legacyPoc[1])}}

  if(p==='/pipelines')return{component:PipelinesView,title:'流水线（兼容）'}
  if(['/artifacts','/yunxiao-artifacts'].includes(p))return{component:ArtifactsView,title:'制品仓库'}
  if(p==='/legacy-containers/new')return{component:ProjectCreateView,title:'旧版新增容器部署'}
  if(['/legacy-containers','/projects','/artifact-delivery'].includes(p))return{component:ProjectCenterView,title:'旧版容器部署'}
  if(['/system','/integrations','/runtime','/dashboard'].includes(p))return{component:IntegrationsView,title:'系统信息'}
  if(['/', '/previews','/static-previews'].includes(p))return{component:StaticCatalogView,title:'静态预览'}
  return{component:ManagedProjectsView,title:'项目部署'}
})

onMounted(()=>window.addEventListener('popstate',syncPath))
onUnmounted(()=>window.removeEventListener('popstate',syncPath))
</script>

<template><AppLayout :active-path="path" :page-title="route.title" @navigate="navigate"><component :is="route.component" v-bind="route.props||{}" @navigate="navigate" /></AppLayout></template>
