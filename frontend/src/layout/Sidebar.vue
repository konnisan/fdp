<script setup>
import { Box, Eye, GitBranch, PackageSearch, Server } from 'lucide-vue-next'

const props=defineProps({activePath:{type:String,required:true}})
const emit=defineEmits(['navigate'])
const groups=[
  {label:'交付资源',items:[
    {label:'流水线',path:'/pipelines',icon:GitBranch},
    {label:'制品仓库',path:'/artifacts',icon:PackageSearch}
  ]},
  {label:'项目',items:[
    {label:'静态预览',path:'/previews',icon:Eye},
    {label:'项目部署',path:'/containers',icon:Box}
  ]},
  {label:'平台',items:[
    {label:'系统信息',path:'/system',icon:Server}
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
  <aside class="sidebar plane-sidebar">
    <button class="brand plane-brand" type="button" @click="emit('navigate','/containers')">
      <span class="brand-mark plane-brand-mark">F</span>
      <span class="brand-copy"><strong>FDP</strong></span>
    </button>

    <nav class="plane-nav plane-nav-grouped">
      <section v-for="group in groups" :key="group.label" class="plane-nav-group">
        <div class="plane-nav-group-title">{{group.label}}</div>
        <button v-for="item in group.items" :key="item.path" type="button" class="nav-item plane-nav-item" :class="{active:active(item.path)}" @click="emit('navigate',item.path)">
          <span class="nav-icon plane-nav-icon"><component :is="item.icon" :size="17" /></span>
          <span class="plane-nav-label">{{item.label}}</span>
        </button>
      </section>
    </nav>
  </aside>
</template>
