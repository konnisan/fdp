<script setup>
import { ChevronRight, Database, FileCode2, FolderKanban, LayoutDashboard, Monitor, Workflow } from 'lucide-vue-next'

const props=defineProps({activePath:{type:String,required:true}})
const emit=defineEmits(['navigate'])
const groups=[
  {label:'',items:[{label:'平台首页',path:'/',icon:LayoutDashboard}]},
  {label:'交付管理',items:[{label:'静态预览',path:'/static-previews',icon:FileCode2},{label:'容器项目',path:'/poc-projects',icon:FolderKanban},{label:'访问入口',path:'/previews',icon:Monitor}]},
  {label:'发布运维',items:[{label:'部署中心',path:'/deployments',icon:Workflow},{label:'运行环境',path:'/runtime',icon:Database}]}
]
function active(path){if(path==='/')return props.activePath==='/';return props.activePath===path||props.activePath.startsWith(path+'/')}
</script>

<template>
  <aside class="sidebar">
    <button class="brand" type="button" @click="emit('navigate','/')"><span class="brand-mark"><FolderKanban :size="22" /></span><span><strong>FDP</strong><small>Docker交付与预览平台</small></span></button>
    <nav class="nav-groups"><section v-for="group in groups" :key="group.label||'root'" class="nav-group"><div v-if="group.label" class="nav-label">{{group.label}}</div><button v-for="item in group.items" :key="item.path" type="button" class="nav-item" :class="{active:active(item.path)}" @click="emit('navigate',item.path)"><component :is="item.icon" :size="18" /><span>{{item.label}}</span><ChevronRight v-if="active(item.path)&&item.path!=='/'" class="nav-arrow" :size="14" /></button></section></nav>
    <div class="sidebar-status"><div class="sidebar-status-title"><Database :size="15" /><span>Linux Server</span><b><i></i>Docker V1</b></div><div class="sidebar-status-row"><span>外部端口</span><strong>8090</strong></div><div class="sidebar-divider"></div><div class="sidebar-status-row"><span>编排</span><strong>Single Host</strong></div></div>
  </aside>
</template>
