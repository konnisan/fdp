<script setup>
import { Box, ChevronRight, Eye, FolderKanban, GitBranch, PackageSearch, Server } from 'lucide-vue-next'

const props=defineProps({activePath:{type:String,required:true}})
const emit=defineEmits(['navigate'])
const groups=[
  {label:'交付',items:[
    {label:'流水线',path:'/pipelines',icon:GitBranch},
    {label:'制品仓库',path:'/artifacts',icon:PackageSearch},
    {label:'项目部署',path:'/containers',icon:Box}
  ]},
  {label:'项目入口',items:[
    {label:'静态预览',path:'/previews',icon:Eye}
  ]},
  {label:'平台',items:[
    {label:'系统信息',path:'/system',icon:Server}
  ]}
]
function active(path){
  if(path==='/pipelines')return props.activePath==='/pipelines'
  if(path==='/artifacts')return ['/artifacts','/yunxiao-artifacts'].includes(props.activePath)
  if(path==='/containers')return ['/containers','/containers/new','/managed-projects'].includes(props.activePath)
  if(path==='/previews')return ['/', '/previews','/static-previews'].includes(props.activePath)
  if(path==='/system')return ['/system','/integrations','/runtime','/dashboard'].includes(props.activePath)
  return props.activePath===path
}
</script>

<template>
  <aside class="sidebar">
    <button class="brand" type="button" @click="emit('navigate','/containers')">
      <span class="brand-mark"><FolderKanban :size="22" /></span>
      <span><strong>FDP</strong><small>制品部署与运行管理</small></span>
    </button>
    <nav class="nav-groups">
      <section v-for="group in groups" :key="group.label" class="nav-group">
        <div class="nav-label">{{group.label}}</div>
        <button v-for="item in group.items" :key="item.path" type="button" class="nav-item" :class="{active:active(item.path)}" @click="emit('navigate',item.path)">
          <component :is="item.icon" :size="18" /><span>{{item.label}}</span><ChevronRight v-if="active(item.path)" class="nav-arrow" :size="14" />
        </button>
      </section>
    </nav>
    <div class="sidebar-status">
      <div class="sidebar-status-title"><Box :size="15" /><span>Managed Runtime</span><b><i></i>V10</b></div>
      <div class="sidebar-status-row"><span>Artifact</span><strong>Packages</strong></div>
      <div class="sidebar-divider"></div>
      <div class="sidebar-status-row"><span>Runtime</span><strong>Docker</strong></div>
      <div class="sidebar-divider"></div>
      <div class="sidebar-status-row"><span>Preview</span><strong>Local / Nginx</strong></div>
    </div>
  </aside>
</template>
