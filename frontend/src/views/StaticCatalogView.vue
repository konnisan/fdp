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

function previewUrl(item){
  return `${window.location.origin}/api/poc-preview/${encodeURIComponent(item.name)}/`
}
function message(e){return e.response?.data?.message||e.message||'操作失败'}

async function load(){
  loading.value=true
  error.value=''
  try{catalog.value=await getStaticCatalog()}
  catch(e){error.value=message(e)}
  finally{loading.value=false}
}

async function refresh(){
  refreshing.value=true
  error.value=''
  try{catalog.value=await refreshStaticCatalog()}
  catch(e){error.value=message(e)}
  finally{refreshing.value=false}
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="静态 POC" description="STATIC POC 已经由上游系统生成并固定存放在原有 Codeup 产物目录中。FDP 只负责同步、识别和对外预览，不负责在这里重新构建项目。">
      <template #actions>
        <button class="primary-button" :disabled="refreshing" @click="refresh"><RefreshCw :size="15" />{{refreshing?'同步中…':'刷新 Codeup'}}</button>
      </template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>

    <section class="panel" style="padding:16px 18px">
      <div style="display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap">
        <div>
          <div style="font-size:13px;color:#64748b;margin-bottom:5px">POC 产物固定来源</div>
          <div style="font-weight:600">{{catalog.gitUrl||'尚未配置 FDP_STATIC_CODEUP_GIT_URL'}}</div>
          <div style="font-size:12px;color:#64748b;margin-top:4px">Branch: {{catalog.branch||'main'}} · 原有目录结构保持不变 · 每个 POC 直接通过当前 FDP 网址/端口访问</div>
        </div>
        <span class="status-text" :class="catalog.configured?'running':'stopped'"><i></i>{{catalog.configured?'CONFIGURED':'NOT CONFIGURED'}}</span>
      </div>
    </section>

    <section class="panel">
      <div class="toolbar">
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索项目名或 index.html 路径" /></label>
        <span class="toolbar-count">{{projects.length}} 个可直接预览 POC</span>
      </div>

      <div v-if="loading" class="empty-state">正在读取静态 POC…</div>
      <div v-else-if="!projects.length" class="empty-state">
        暂未发现静态 POC。请确认固定 Codeup 仓库中存在“项目目录/index.html”，然后点击“刷新 Codeup”。
      </div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead><tr><th>项目</th><th>Codeup 产物路径</th><th>更新时间</th><th>FDP 预览地址</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in projects" :key="item.name">
              <td><div style="display:flex;align-items:center;gap:10px"><span class="preview-icon" style="width:32px;height:32px"><FolderOpen :size="17" /></span><strong>{{item.name}}</strong></div></td>
              <td><code>{{item.indexPath}}</code></td>
              <td>{{item.updatedAt?new Date(item.updatedAt).toLocaleString():'-'}}</td>
              <td><code>/api/poc-preview/{{item.name}}/</code></td>
              <td><a class="soft-button" :href="previewUrl(item)" target="_blank">直接预览 <ExternalLink :size="13" /></a></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
