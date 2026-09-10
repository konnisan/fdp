<script setup>
import { Box, Eye, FolderKanban, GitBranch, PackageSearch, Server } from 'lucide-vue-next'

const props=defineProps({activePath:{type:String,required:true}})
const emit=defineEmits(['navigate'])
const groups=[
  {label:'交付资源',items:[
    {label:'流水线',description:'Flow',path:'/pipelines',icon:GitBranch},
    {label:'制品仓库',description:'Packages',path:'/artifacts',icon:PackageSearch}
  ]},
  {label:'项目',items:[
    {label:'项目部署',description:'Managed Runtime',path:'/containers',icon:Box},
    {label:'静态预览',description:'Static POC',path:'/previews',icon:Eye}
  ]},
  {label:'平台',items:[
    {label:'系统信息',description:'Environment',path:'/system',icon:Server}
  ]}
]
function active(path){
  if(path==='/pipelines')return props.activePath==='/pipelines'
  if(path==='/artifacts')return ['/artifacts','/yunxiao-artifacts'].includes(props.activePath)
  if(path==='/containers')return props.activePath==='/containers'||props.activePath==='/containers/new'||props.activePath==='/managed-projects'||/^\/containers\/\d+\/(edit|preview)$/.test(props.activePath)
  if(path==='/previews')return ['/', '/previews','/static-previews'].includes(props.activePath)
  if(path==='/system')return ['/system','/integrations','/runtime','/dashboard'].includes(props.activePath)
  return props.activePath===path
}
</script>

<template>
  <aside class="sidebar">
    <button class="brand" type="button" @click="emit('navigate','/containers')">
      <span class="brand-mark"><FolderKanban :size="20" /></span>
      <span class="brand-copy"><strong>FDP</strong><small>Delivery Console</small></span>
    </button>

    <nav class="nav-groups">
      <section v-for="group in groups" :key="group.label" class="nav-group">
        <div class="nav-label">{{group.label}}</div>
        <button v-for="item in group.items" :key="item.path" type="button" class="nav-item" :class="{active:active(item.path)}" @click="emit('navigate',item.path)">
          <div class="nav-icon"><component :is="item.icon" :size="17" /></div>
          <div class="nav-copy"><strong>{{item.label}}</strong><small>{{item.description}}</small></div>
        </button>
      </section>
    </nav>

    <div class="sidebar-status">
      <div class="sidebar-status-title"><span class="runtime-dot"></span><span>交付运行时</span><b>READY</b></div>
      <div class="sidebar-status-row"><span>Artifact</span><strong>Packages</strong></div>
      <div class="sidebar-status-row"><span>Runtime</span><strong>Docker</strong></div>
      <div class="sidebar-status-row"><span>Preview</span><strong>Nginx</strong></div>
    </div>
  </aside>
</template>
