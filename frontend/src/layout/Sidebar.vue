<script setup>
import { Boxes, ChevronRight, Database, Eye, FolderKanban, LayoutDashboard, PackageSearch, Workflow } from 'lucide-vue-next'

const props=defineProps({activePath:{type:String,required:true}})
const emit=defineEmits(['navigate'])
const groups=[
  {label:'开始',items:[{label:'静态预览',path:'/',icon:Eye},{label:'制品仓库',path:'/artifacts',icon:PackageSearch},{label:'平台总览',path:'/dashboard',icon:LayoutDashboard}]},
  {label:'交付管理',items:[{label:'项目中心',path:'/projects',icon:FolderKanban},{label:'部署中心',path:'/deployments',icon:Workflow}]},
  {label:'平台配置',items:[{label:'系统集成',path:'/integrations',icon:Boxes}]}
]
function active(path){
  if(path==='/')return ['/', '/static-previews','/previews'].includes(props.activePath)
  if(path==='/artifacts')return ['/artifacts','/yunxiao-artifacts'].includes(props.activePath)
  if(path==='/dashboard')return props.activePath==='/dashboard'
  if(path==='/projects')return props.activePath.startsWith('/projects')||props.activePath.startsWith('/poc-projects')||props.activePath==='/artifact-delivery'
  if(path==='/integrations')return ['/integrations','/runtime'].includes(props.activePath)
  return props.activePath===path||props.activePath.startsWith(path+'/')
}
</script>

<template>
  <aside class="sidebar">
    <button class="brand" type="button" @click="emit('navigate','/')"><span class="brand-mark"><FolderKanban :size="22" /></span><span><strong>FDP</strong><small>交付与预览平台</small></span></button>
    <nav class="nav-groups"><section v-for="group in groups" :key="group.label||'root'" class="nav-group"><div v-if="group.label" class="nav-label">{{group.label}}</div><button v-for="item in group.items" :key="item.path" type="button" class="nav-item" :class="{active:active(item.path)}" @click="emit('navigate',item.path)"><component :is="item.icon" :size="18" /><span>{{item.label}}</span><ChevronRight v-if="active(item.path)" class="nav-arrow" :size="14" /></button></section></nav>
    <div class="sidebar-status"><div class="sidebar-status-title"><Database :size="15" /><span>Deployment Engine</span><b><i></i>Profile V1</b></div><div class="sidebar-status-row"><span>入口</span><strong>先预览，再构建</strong></div><div class="sidebar-divider"></div><div class="sidebar-status-row"><span>CI / Artifact</span><strong>Flow / Packages</strong></div></div>
  </aside>
</template>
