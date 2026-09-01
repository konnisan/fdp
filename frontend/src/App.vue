<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AppLayout from './layout/Index.vue'
import HomeView from './views/HomeView.vue'
import ProjectsView from './views/ProjectsView.vue'
import StaticCatalogView from './views/StaticCatalogView.vue'
import YunxiaoArtifactsView from './views/YunxiaoArtifactsView.vue'
import ProjectDetailView from './views/ProjectDetailView.vue'
import DeploymentsView from './views/DeploymentsView.vue'
import PreviewsView from './views/PreviewsView.vue'
import RuntimeView from './views/RuntimeView.vue'

const path=ref(window.location.pathname||'/')
function syncPath(){path.value=window.location.pathname||'/'}
function navigate(to){if(window.location.pathname!==to)window.history.pushState({},'',to);path.value=to;window.scrollTo({top:0,behavior:'smooth'})}
const route=computed(()=>{
  const p=path.value
  if(/^\/poc-projects\/\d+$/.test(p))return{component:ProjectDetailView,projectId:Number(p.split('/').pop()),title:'交付项目详情'}
  if(p==='/static-previews')return{component:StaticCatalogView,title:'静态预览'}
  if(p==='/yunxiao-artifacts')return{component:YunxiaoArtifactsView,title:'流水线与制品'}
  if(p==='/poc-projects')return{component:ProjectsView,title:'容器项目'}
  if(p==='/deployments')return{component:DeploymentsView,title:'部署中心'}
  if(p==='/previews')return{component:PreviewsView,title:'访问入口'}
  if(p==='/runtime')return{component:RuntimeView,title:'运行环境'}
  return{component:HomeView,title:'平台首页'}
})
onMounted(()=>window.addEventListener('popstate',syncPath))
onUnmounted(()=>window.removeEventListener('popstate',syncPath))
</script>

<template><AppLayout :active-path="path" :page-title="route.title" @navigate="navigate"><component :is="route.component" :project-id="route.projectId" @navigate="navigate" /></AppLayout></template>
