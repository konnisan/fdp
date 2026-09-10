<script setup>
import { Search } from 'lucide-vue-next'
import { ref } from 'vue'

defineProps({ pageTitle: { type: String, default: '平台首页' } })
const emit=defineEmits(['navigate'])
const query=ref('')
const routes=[
  {keys:['项目','部署','容器'],path:'/containers'},
  {keys:['制品','packages','artifact'],path:'/artifacts'},
  {keys:['流水线','flow','pipeline'],path:'/pipelines'},
  {keys:['静态','预览','poc'],path:'/previews'},
  {keys:['系统','环境','runtime'],path:'/system'}
]
function submit(){
  const q=query.value.trim().toLowerCase()
  if(!q)return
  const target=routes.find(item=>item.keys.some(key=>q.includes(key.toLowerCase())))
  if(target){emit('navigate',target.path);query.value=''}
}
</script>

<template>
  <header class="topbar plane-topbar">
    <form class="plane-global-search" @submit.prevent="submit">
      <Search :size="16" />
      <input v-model="query" aria-label="页面搜索" placeholder="搜索页面" />
    </form>
  </header>
</template>
