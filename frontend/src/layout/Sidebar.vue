<script setup>
import { Box, ChevronRight, Eye, FolderKanban, PackageSearch, Server, Workflow } from 'lucide-vue-next'

const props=defineProps({activePath:{type:String,required:true}})
const emit=defineEmits(['navigate'])
const groups=[
  {label:'构建与制品',items:[
    {label:'流水线',path:'/pipelines',icon:Workflow},
    {label:'制品仓库',path:'/artifacts',icon:PackageSearch}
  ]},
  {label:'项目入口',items:[
    {label:'静态预览',path:'/previews',icon:Eye},
    {label:'容器部署',path:'/containers',icon:Box}
  ]},
  {label:'平台',items:[
    {label:'系统信息',path:'/system',icon:Server}
  ]}
]
function active(path){
  if(path==='/pipelines')return ['/pipelines'].includes(props.activePath)
  if(path==='/artifacts')return ['/artifacts','/yunxiao-artifacts'].includes(props.activePath)
  if(path==='/previews')return ['/', '/previews','/static-previews'].includes(props.activePath)
  if(path==='/containers')return props.activePath.startsWith('/containers')||props.activePath.startsWith('/projects')||props.activePath.startsWith('/poc-projects')||props.activePath==='/artifact-delivery'
  if(path==='/system')return ['/system','/integrations','/runtime','/dashboard'].includes(props.activePath)
  return props.activePath===path
}
</script>

<template>
  <aside class="sidebar">
    <button class="brand" type="button" @click="emit('navigate','/previews')">
      <span class="brand-mark"><FolderKanban :size="22" /></span>
      <span><strong>FDP</strong><small>交付与部署管理平台</small></span>
    </button>

    <nav class="nav-groups">
      <section v-for="group in groups" :key="group.label" class="nav-group">
        <div class="nav-label">{{group.label}}</div>
        <button v-for="item in group.items" :key="item.path" type="button" class="nav-item" :class="{active:active(item.path)}" @click="emit('navigate',item.path)">
          <component :is="item.icon" :size="18" />
          <span>{{item.label}}</span>
          <ChevronRight v-if="active(item.path)" class="nav-arrow" :size="14" />
        </button>
      </section>
    </nav>

    <div class="sidebar-status">
      <div class="sidebar-status-title"><Workflow :size="15" /><span>Delivery Flow</span><b><i></i>Connected</b></div>
      <div class="sidebar-status-row"><span>CI</span><strong>Flow</strong></div>
      <div class="sidebar-divider"></div>
      <div class="sidebar-status-row"><span>Artifact</span><strong>Packages</strong></div>
      <div class="sidebar-divider"></div>
      <div class="sidebar-status-row"><span>Runtime</span><strong>Docker</strong></div>
    </div>
  </aside>
</template>
