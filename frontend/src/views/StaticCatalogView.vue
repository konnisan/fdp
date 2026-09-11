<script setup>
import { computed, onMounted, ref } from 'vue'
import { ExternalLink, FolderOpen, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getStaticCatalog, refreshStaticCatalog } from '../api'

const catalog=ref({projects:[]})
const keyword=ref('')
const loading=ref(true)
const refreshing=ref(false)
const error=ref('')

const projects=computed(()=>{
  const q=keyword.value.trim().toLowerCase()
  const items=Array.isArray(catalog.value?.projects)?catalog.value.projects:[]
  return q?items.filter(item=>String(item.name||'').toLowerCase().includes(q)||String(item.indexPath||'').toLowerCase().includes(q)):items
})

function previewUrl(item){return `${window.location.origin}/api/poc-preview/${encodeURIComponent(item.name)}/`}
function message(e){return e.response?.data?.message||e.message||'操作失败'}
async function load(){loading.value=true;error.value='';try{catalog.value=await getStaticCatalog()}catch(e){error.value=message(e)}finally{loading.value=false}}
async function refresh(){refreshing.value=true;error.value='';try{catalog.value=await refreshStaticCatalog()}catch(e){error.value=message(e)}finally{refreshing.value=false}}
onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="静态 POC">
      <template #actions>
        <button class="primary-button" :disabled="refreshing" @click="refresh"><RefreshCw :size="15" />{{refreshing?'同步中…':'刷新 Codeup'}}</button>
      </template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>

    <section class="panel static-source-bar">
      <strong>{{catalog.gitUrl||'未配置 Codeup 仓库'}}</strong>
      <span class="status-text" :class="catalog.configured?'running':'stopped'"><i></i>{{catalog.configured?'已配置':'未配置'}}</span>
    </section>

    <section class="panel">
      <div class="toolbar">
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索项目" /></label>
        <span class="toolbar-count">{{projects.length}} 个项目</span>
      </div>

      <div v-if="loading" class="empty-state">正在读取静态 POC…</div>
      <div v-else-if="!projects.length" class="empty-state">暂无静态 POC。</div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead><tr><th>项目</th><th>产物路径</th><th>更新时间</th><th>预览地址</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in projects" :key="item.name">
              <td><div style="display:flex;align-items:center;gap:10px"><span class="preview-icon" style="width:32px;height:32px"><FolderOpen :size="17" /></span><strong>{{item.name}}</strong></div></td>
              <td><code>{{item.indexPath}}</code></td>
              <td>{{item.updatedAt?new Date(item.updatedAt).toLocaleString():'-'}}</td>
              <td><code>/api/poc-preview/{{item.name}}/</code></td>
              <td><a class="soft-button" :href="previewUrl(item)" target="_blank">预览 <ExternalLink :size="13" /></a></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
