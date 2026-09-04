<script setup>
import { Box, ChevronRight, Eye, FolderKanban } from 'lucide-vue-next'

const props=defineProps({activePath:{type:String,required:true}})
const emit=defineEmits(['navigate'])
const items=[
  {label:'静态预览',path:'/',icon:Eye,description:'查看固定 POC 产物'},
  {label:'容器部署',path:'/containers',icon:Box,description:'管理 Docker 项目'}
]
function active(path){
  if(path==='/')return ['/', '/static-previews','/previews'].includes(props.activePath)
  if(path==='/containers')return props.activePath.startsWith('/containers')||props.activePath.startsWith('/projects')||props.activePath.startsWith('/poc-projects')||['/artifact-delivery','/deployments','/integrations','/runtime','/artifacts','/yunxiao-artifacts','/dashboard'].includes(props.activePath)
  return false
}
</script>

<template>
  <aside class="sidebar">
    <button class="brand" type="button" @click="emit('navigate','/')">
      <span class="brand-mark"><FolderKanban :size="22" /></span>
      <span><strong>FDP</strong><small>项目部署管理平台</small></span>
    </button>

    <nav class="nav-groups simple-nav">
      <section class="nav-group">
        <button v-for="item in items" :key="item.path" type="button" class="nav-item" :class="{active:active(item.path)}" @click="emit('navigate',item.path)">
          <component :is="item.icon" :size="18" />
          <span><b>{{item.label}}</b><small>{{item.description}}</small></span>
          <ChevronRight v-if="active(item.path)" class="nav-arrow" :size="14" />
        </button>
      </section>
    </nav>

    <div class="sidebar-status">
      <div class="sidebar-status-title"><Box :size="15" /><span>Docker Runtime</span><b><i></i>Managed</b></div>
      <div class="sidebar-status-row"><span>STATIC</span><strong>直接预览</strong></div>
      <div class="sidebar-divider"></div>
      <div class="sidebar-status-row"><span>APP</span><strong>Docker 部署</strong></div>
    </div>
  </aside>
</template>
