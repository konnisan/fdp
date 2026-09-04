<script setup>
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, ExternalLink, FolderOpen, Hammer, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { activateStaticProject, getStaticCatalog, refreshStaticCatalog } from '../api'

const emit=defineEmits(['navigate'])
const catalog=ref({projects:[]})
const keyword=ref('')
const loading=ref(true)
const refreshing=ref(false)
const activating=ref('')
const error=ref('')

const projects=computed(()=>{
  const q=keyword.value.trim().toLowerCase()
  const items=Array.isArray(catalog.value?.projects)?catalog.value.projects:[]
  return q?items.filter(item=>String(item.name||'').toLowerCase().includes(q)||String(item.indexPath||'').toLowerCase().includes(q)):items
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

function buildFrom(item){
  sessionStorage.setItem('fdp-build-from-preview',JSON.stringify({name:item.name,indexPath:item.indexPath||'',updatedAt:item.updatedAt||null}))
  emit('navigate','/projects/new')
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="静态预览" description="这是 FDP 的第一入口。直接读取原有 Codeup POC 产物目录，选择任意 index.html 对外预览；客户确认后，再以这个预览为起点创建后续 LIGHTWEIGHT / STANDARD 项目。">
      <template #actions>
        <a v-if="catalog.activeProject" class="soft-button" :href="publicUrl()" target="_blank">打开当前预览 <ExternalLink :size="14" /></a>
        <button class="primary-button" :disabled="refreshing" @click="refresh"><RefreshCw :size="15" />{{refreshing?'同步中…':'刷新 Codeup'}}</button>
      </template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>

    <section class="panel" style="padding:16px 18px">
      <div style="display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap">
        <div>
          <div style="font-size:13px;color:#64748b;margin-bottom:5px">POC 产物固定来源</div>
          <div style="font-weight:600">{{catalog.gitUrl||'尚未配置 FDP_STATIC_CODEUP_GIT_URL'}}</div>
          <div style="font-size:12px;color:#64748b;margin-top:4px">Branch: {{catalog.branch||'main'}} · 原有目录结构保持不变 · 对外端口: {{catalog.publicPort||'-'}}</div>
        </div>
        <div style="display:flex;gap:8px;align-items:center">
          <span class="status-text" :class="catalog.configured?'running':'stopped'"><i></i>{{catalog.configured?'CONFIGURED':'NOT CONFIGURED'}}</span>
          <span v-if="catalog.activeProject" class="type-badge">当前预览：{{catalog.activeProject}}</span>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="toolbar">
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索项目名或 index.html 路径" /></label>
        <span class="toolbar-count">{{projects.length}} 个可预览产物</span>
      </div>

      <div v-if="loading" class="empty-state">正在读取静态产物…</div>
      <div v-else-if="!projects.length" class="empty-state">
        暂未发现静态项目。请确认固定 Codeup 仓库中存在“项目目录/index.html”，然后点击“刷新 Codeup”。
      </div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead><tr><th>项目</th><th>产物路径</th><th>更新时间</th><th>预览状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in projects" :key="item.name">
              <td><div style="display:flex;align-items:center;gap:10px"><span class="preview-icon" style="width:32px;height:32px"><FolderOpen :size="17" /></span><strong>{{item.name}}</strong></div></td>
              <td><code>{{item.indexPath}}</code></td>
              <td>{{item.updatedAt?new Date(item.updatedAt).toLocaleString():'-'}}</td>
              <td>
                <span v-if="item.active" class="status-text running"><i></i>当前预览</span>
                <span v-else class="status-text draft"><i></i>可选择</span>
              </td>
              <td>
                <div class="row-actions">
                  <a v-if="item.active" class="soft-button" :href="publicUrl()" target="_blank">打开预览 <ExternalLink :size="13" /></a>
                  <button v-else class="soft-button" :disabled="activating===item.name" @click="activate(item)">
                    <CheckCircle2 :size="14" />{{activating===item.name?'切换中…':'设为当前预览'}}
                  </button>
                  <button class="primary-button" @click="buildFrom(item)"><Hammer :size="14" />基于此预览构建</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
