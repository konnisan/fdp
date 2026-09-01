<script setup>
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, ExternalLink, FolderOpen, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { activateStaticProject, getStaticCatalog, refreshStaticCatalog } from '../api'

const catalog=ref({projects:[]})
const keyword=ref('')
const loading=ref(true)
const refreshing=ref(false)
const activating=ref('')
const error=ref('')

const projects=computed(()=>{
  const q=keyword.value.trim().toLowerCase()
  const items=Array.isArray(catalog.value?.projects)?catalog.value.projects:[]
  return q?items.filter(item=>String(item.name||'').toLowerCase().includes(q)):items
})

function publicOrigin(){
  const port=Number(catalog.value?.publicPort||0)
  const protocol=window.location.protocol
  const host=window.location.hostname
  return port?`${protocol}//${host}:${port}`:`${protocol}//${window.location.host}`
}
function publicUrl(){return `${publicOrigin()}/`}
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

async function activate(item){
  if(item.active)return
  activating.value=item.name
  error.value=''
  try{catalog.value=await activateStaticProject(item.name)}
  catch(e){error.value=message(e)}
  finally{activating.value=''}
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="静态预览" description="STATIC 不再逐个配置项目。刷新一次固定的 Codeup POC 仓库，自动识别所有包含 index.html 的项目目录；选择一个项目后，它会直接显示在统一对外端口根路径。">
      <template #actions>
        <a v-if="catalog.activeProject" class="soft-button" :href="publicUrl()" target="_blank">打开当前预览 <ExternalLink :size="14" /></a>
        <button class="primary-button" :disabled="refreshing" @click="refresh"><RefreshCw :size="15" />{{refreshing?'同步中…':'刷新 Codeup'}}</button>
      </template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>

    <section class="panel" style="padding:16px 18px">
      <div style="display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap">
        <div>
          <div style="font-size:13px;color:#64748b;margin-bottom:5px">固定 STATIC 源</div>
          <div style="font-weight:600">{{catalog.gitUrl||'尚未配置 FDP_STATIC_CODEUP_GIT_URL'}}</div>
          <div style="font-size:12px;color:#64748b;margin-top:4px">Branch: {{catalog.branch||'main'}} · 对外端口: {{catalog.publicPort||'-'}}</div>
        </div>
        <div style="display:flex;gap:8px;align-items:center">
          <span class="status-text" :class="catalog.configured?'running':'stopped'"><i></i>{{catalog.configured?'CONFIGURED':'NOT CONFIGURED'}}</span>
          <span v-if="catalog.activeProject" class="type-badge">当前：{{catalog.activeProject}}</span>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="toolbar">
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索 Codeup 中的项目名称" /></label>
        <span class="toolbar-count">{{projects.length}} 个静态项目</span>
      </div>

      <div v-if="loading" class="empty-state">正在读取静态项目…</div>
      <div v-else-if="!projects.length" class="empty-state">
        暂未发现静态项目。请确认 Codeup 仓库根目录下是“项目名/index.html”，然后点击“刷新 Codeup”。
      </div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead><tr><th>项目</th><th>Codeup 路径</th><th>更新时间</th><th>当前状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in projects" :key="item.name">
              <td><div style="display:flex;align-items:center;gap:10px"><span class="preview-icon" style="width:32px;height:32px"><FolderOpen :size="17" /></span><strong>{{item.name}}</strong></div></td>
              <td><code>{{item.indexPath}}</code></td>
              <td>{{item.updatedAt?new Date(item.updatedAt).toLocaleString():'-'}}</td>
              <td>
                <span v-if="item.active" class="status-text running"><i></i>对外展示中</span>
                <span v-else class="status-text draft"><i></i>待选择</span>
              </td>
              <td>
                <div class="row-actions">
                  <a v-if="item.active" class="soft-button" :href="publicUrl()" target="_blank">打开 <ExternalLink :size="13" /></a>
                  <button v-else class="primary-button" :disabled="activating===item.name" @click="activate(item)">
                    <CheckCircle2 :size="14" />{{activating===item.name?'切换中…':'对外展示'}}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
