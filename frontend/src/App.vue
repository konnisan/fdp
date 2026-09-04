<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AppLayout from './layout/Index.vue'
import HomeView from './views/HomeView.vue'
import ProjectCenterView from './views/ProjectCenterView.vue'
import ProjectCreateView from './views/ProjectCreateView.vue'
import UnifiedProjectDetailView from './views/UnifiedProjectDetailView.vue'
import DeploymentsView from './views/DeploymentsView.vue'
import IntegrationsView from './views/IntegrationsView.vue'

const path=ref(window.location.pathname||'/')
function syncPath(){path.value=window.location.pathname||'/'}
function navigate(to){if(window.location.pathname!==to)window.history.pushState({},'',to);path.value=to;window.scrollTo({top:0,behavior:'smooth'})}
const route=computed(()=>{
  const p=path.value
  const projectMatch=p.match(/^\/projects\/(source|artifact)\/(\d+)$/)
  if(projectMatch)return{component:UnifiedProjectDetailView,title:'项目详情',props:{projectKind:projectMatch[1],projectId:Number(projectMatch[2])}}
  const legacyProject=p.match(/^\/poc-projects\/(\d+)$/)
  if(legacyProject)return{component:UnifiedProjectDetailView,title:'项目详情',props:{projectKind:'source',projectId:Number(legacyProject[1])}}
  if(p==='/projects/new')return{component:ProjectCreateView,title:'新建项目'}
  if(['/projects','/poc-projects','/artifact-delivery','/static-previews','/previews'].includes(p))return{component:ProjectCenterView,title:'项目中心'}
  if(p==='/deployments')return{component:DeploymentsView,title:'部署中心'}
  if(['/integrations','/yunxiao-artifacts','/runtime'].includes(p))return{component:IntegrationsView,title:'系统集成'}
  return{component:HomeView,title:'平台总览'}
})
onMounted(()=>window.addEventListener('popstate',syncPath))
onUnmounted(()=>window.removeEventListener('popstate',syncPath))
</script>

<template><AppLayout :active-path="path" :page-title="route.title" @navigate="navigate"><component :is="route.component" v-bind="route.props||{}" @navigate="navigate" /></AppLayout></template>
