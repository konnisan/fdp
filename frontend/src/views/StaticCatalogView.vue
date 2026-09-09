<script setup>
import { computed, onMounted, ref } from 'vue'
import { ExternalLink, File, Folder, FolderOpen, RefreshCw, Search, X } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getStaticCatalog, getStaticProjectTree, refreshStaticCatalog } from '../api'

const catalog=ref({projects:[]})
const keyword=ref('')
const loading=ref(true)
const refreshing=ref(false)
const treeLoading=ref(false)
const error=ref('')
const selectedProject=ref(null)
const tree=ref({entries:[]})

const projects=computed(()=>{
  const q=keyword.value.trim().toLowerCase()
  const items=Array.isArray(catalog.value?.projects)?catalog.value.projects:[]
  return q?items.filter(item=>String(item.name||'').toLowerCase().includes(q)||String(item.indexPath||'').toLowerCase().includes(q)):items
})

function previewUrl(item){return `${window.location.origin}/api/poc-preview/${encodeURIComponent(item.name)}/`}
function message(e){return e.response?.data?.message||e.message||'操作失败'}
function size(value){
  const n=Number(value)
  if(!Number.isFinite(n))return ''
  if(n<1024)return `${n} B`
  if(n<1024*1024)return `${(n/1024).toFixed(1)} KB`
  return `${(n/1024/1024).toFixed(1)} MB`
}

async function load(){
  loading.value=true;error.value=''
  try{catalog.value=await getStaticCatalog()}
  catch(e){error.value=message(e)}
  finally{loading.value=false}
}

async function refresh(){
  refreshing.value=true;error.value=''
  try{
    catalog.value=await refreshStaticCatalog()
    if(selectedProject.value)await openTree(selectedProject.value)
  }catch(e){error.value=message(e)}
  finally{refreshing.value=false}
}

async function openTree(item){
  selectedProject.value=item;treeLoading.value=true;error.value=''
  try{tree.value=await getStaticProjectTree(item.name)}
  catch(e){error.value=message(e);tree.value={entries:[]}}
  finally{treeLoading.value=false}
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="静态预览" description="Windows 本地可直接同步 Codeup、观察项目目录并预览静态 POC；不需要 Docker 或统一 Nginx。服务器仍可继续使用统一对外预览能力。">
      <template #actions>
        <button class="primary-button" :disabled="refreshing||!catalog.configured" @click="refresh"><RefreshCw :size="15" />{{refreshing?'同步中…':'刷新 Codeup'}}</button>
      </template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>

    <section class="panel" style="padding:16px 18px">
      <div style="display:grid;grid-template-columns:minmax(0,1.4fr) minmax(0,1fr) auto;gap:18px;align-items:center">
        <div>
          <div style="font-size:11px;color:#94a3b8;margin-bottom:5px">Codeup 静态来源</div>
          <div style="font-weight:600;word-break:break-all">{{catalog.gitUrl||'尚未配置静态 Codeup Git URL'}}</div>
          <div style="font-size:11px;color:#64748b;margin-top:5px">Branch: {{catalog.branch||'main'}}</div>
        </div>
        <div>
          <div style="font-size:11px;color:#94a3b8;margin-bottom:5px">本地预览目录</div>
          <code style="font-size:11px;word-break:break-all">{{catalog.workspacePath||'-'}}</code>
          <div style="font-size:11px;color:#16a34a;margin-top:5px">本地直读预览可用 · 不依赖 FDP_EXECUTION_ENABLED</div>
        </div>
        <span class="status-text" :class="catalog.configured?'running':'stopped'"><i></i>{{catalog.configured?'REMOTE READY':'LOCAL ONLY'}}</span>
      </div>
    </section>

    <section class="panel">
      <div class="toolbar">
        <label class="search-box"><Search :size="15" /><input v-model="keyword" placeholder="搜索项目名或 index.html 路径" /></label>
        <span class="toolbar-count">{{projects.length}} 个可预览 POC</span>
      </div>

      <div v-if="loading" class="empty-state">正在读取本地静态 POC…</div>
      <div v-else-if="!projects.length" class="empty-state">
        本地预览目录中暂未发现“项目目录/index.html”。如果已配置 Codeup 凭据，可点击“刷新 Codeup”同步到本机。
      </div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead><tr><th>项目</th><th>本地目录</th><th>更新时间</th><th>预览地址</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in projects" :key="item.name">
              <td><div style="display:flex;align-items:center;gap:10px"><span class="preview-icon" style="width:32px;height:32px"><FolderOpen :size="17" /></span><strong>{{item.name}}</strong></div></td>
              <td><code>{{item.indexPath}}</code></td>
              <td>{{item.updatedAt?new Date(item.updatedAt).toLocaleString():'-'}}</td>
              <td><code>/api/poc-preview/{{item.name}}/</code></td>
              <td><div class="row-actions"><button class="soft-button" @click="openTree(item)"><Folder :size="13" />目录</button><a class="primary-button" :href="previewUrl(item)" target="_blank">预览 <ExternalLink :size="13" /></a></div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="selectedProject" class="panel">
      <div class="panel-head">
        <div><h2>{{selectedProject.name}} · 项目目录</h2><p>只读观察同步后的静态目录；忽略 .git 与 node_modules，最多展示 1200 项。</p></div>
        <button class="icon-button" @click="selectedProject=null;tree={entries:[]}"><X :size="15" /></button>
      </div>
      <div style="padding:12px 16px;border-bottom:1px solid #eef2f7;font-size:11px;color:#64748b"><code>{{tree.root||'-'}}</code></div>
      <div v-if="treeLoading" class="empty-state">正在读取目录…</div>
      <div v-else-if="!tree.entries?.length" class="empty-state">目录为空。</div>
      <div v-else style="max-height:480px;overflow:auto;padding:8px 0">
        <div v-for="entry in tree.entries" :key="entry.path" style="min-height:34px;display:flex;align-items:center;border-bottom:1px solid #f3f4f6;padding-right:16px;font-size:12px" :style="{paddingLeft:`${16+(entry.depth||0)*20}px`}">
          <Folder v-if="entry.directory" :size="14" style="margin-right:8px;color:#2563eb" />
          <File v-else :size="14" style="margin-right:8px;color:#64748b" />
          <code style="color:#334155">{{entry.name}}</code>
          <span style="margin-left:auto;color:#94a3b8;font-size:10px">{{entry.directory?'目录':size(entry.size)}}</span>
        </div>
        <div v-if="tree.truncated" class="empty-state">目录项过多，当前只展示前 1200 项。</div>
      </div>
    </section>
  </div>
</template>
