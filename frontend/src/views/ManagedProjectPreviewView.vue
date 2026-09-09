<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, ExternalLink, File, Folder, Monitor, RefreshCw } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getManagedDirectory, getManagedProject, previewManagedProject } from '../api'

const props=defineProps({projectId:{type:Number,required:true}})
const emit=defineEmits(['navigate'])
const project=ref(null)
const tree=ref({entries:[]})
const loading=ref(true)
const switching=ref(false)
const error=ref('')
const info=ref('')
const selectedPath=ref('')

const htmlEntries=computed(()=>Array.isArray(tree.value?.entries)?tree.value.entries.filter(e=>e.html):[])
function message(e){return e.response?.data?.message||e.message||'操作失败'}
function size(value){
  const n=Number(value)
  if(!Number.isFinite(n))return ''
  if(n<1024)return `${n} B`
  if(n<1024*1024)return `${(n/1024).toFixed(1)} KB`
  return `${(n/1024/1024).toFixed(1)} MB`
}
function encodedPath(path){return String(path||'').split('/').map(encodeURIComponent).join('/')}
function localPreviewUrl(path){return `${window.location.origin}/api/managed-projects/${props.projectId}/local-preview/${encodedPath(path)}`}
function selectPreview(entry){selectedPath.value=entry.path}
async function load(){
  loading.value=true;error.value=''
  try{
    const [p,d]=await Promise.all([getManagedProject(props.projectId),getManagedDirectory(props.projectId)])
    project.value=p;tree.value=d
    if(selectedPath.value&&!d.entries?.some(e=>e.path===selectedPath.value))selectedPath.value=''
    if(!selectedPath.value&&htmlEntries.value.length===1)selectedPath.value=htmlEntries.value[0].path
  }catch(e){error.value=message(e)}finally{loading.value=false}
}
async function switchPublicPreview(){
  switching.value=true;error.value='';info.value=''
  try{
    await previewManagedProject(props.projectId)
    info.value='已切换为服务器统一对外预览项目。'
  }catch(e){error.value=message(e)}finally{switching.value=false}
}
onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader :title="project?`${project.projectName} · 预览`:'项目预览'" description="观察容器项目 current/ 目录，并在 Windows 本机直接预览其中的 HTML；服务器正式预览仍通过运行中的 Container 切换。">
      <template #actions>
        <button class="soft-button" @click="emit('navigate','/containers')"><ArrowLeft :size="14"/>返回项目部署</button>
        <button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14"/>刷新目录</button>
        <button class="primary-button" :disabled="switching" @click="switchPublicPreview"><Monitor :size="14"/>{{switching?'切换中…':'设为服务器预览'}}</button>
      </template>
    </PageHeader>

    <div v-if="error" class="error-banner">{{error}}</div>
    <div v-if="info" class="success-banner">{{info}}</div>

    <section v-if="project" class="panel" style="padding:14px 16px">
      <div style="display:grid;grid-template-columns:1.2fr 1fr 1fr 1fr;gap:18px;align-items:start">
        <div><small class="cell-note">current 目录</small><code style="display:block;margin-top:6px;word-break:break-all">{{tree.root||project.projectRoot+'/current'}}</code></div>
        <div><small class="cell-note">Container</small><code style="display:block;margin-top:6px">{{project.containerName||'-'}}</code></div>
        <div><small class="cell-note">部署版本</small><strong style="display:block;margin-top:6px">{{project.deployedVersionSummary||'未部署'}}</strong></div>
        <div><small class="cell-note">运行状态</small><strong style="display:block;margin-top:6px">{{project.deploymentStatus||'-'}}</strong></div>
      </div>
    </section>

    <section class="panel" style="overflow:hidden">
      <div style="display:grid;grid-template-columns:minmax(330px,38%) minmax(0,1fr);min-height:620px">
        <div style="border-right:1px solid #e5e7eb;min-width:0">
          <div class="panel-head"><div><h2>current/ 目录</h2><p>只读观察部署后的目录；HTML 文件可以直接在右侧预览。</p></div></div>
          <div v-if="loading" class="empty-state">正在读取项目目录…</div>
          <div v-else-if="!tree.exists||!tree.entries?.length" class="empty-state">current/ 当前为空。完成制品部署后，这里会显示真实部署目录。</div>
          <div v-else style="max-height:620px;overflow:auto;padding:6px 0">
            <button v-for="entry in tree.entries" :key="entry.path" type="button"
              style="width:100%;min-height:34px;border:0;border-bottom:1px solid #f3f4f6;background:#fff;display:flex;align-items:center;padding-right:12px;text-align:left"
              :style="{paddingLeft:`${14+(entry.depth||0)*18}px`,background:selectedPath===entry.path?'#eff6ff':'#fff'}"
              :disabled="entry.directory"
              @click="entry.html&&selectPreview(entry)">
              <Folder v-if="entry.directory" :size="14" style="margin-right:7px;color:#2563eb;flex:0 0 auto"/>
              <File v-else :size="14" style="margin-right:7px;color:#64748b;flex:0 0 auto"/>
              <code style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#334155">{{entry.name}}</code>
              <span v-if="entry.html" class="tag" style="margin-left:auto">可预览</span>
              <span v-else-if="!entry.directory" style="margin-left:auto;color:#94a3b8;font-size:10px">{{size(entry.size)}}</span>
            </button>
            <div v-if="tree.truncated" class="empty-state">目录过大，只展示前 2000 项。</div>
          </div>
        </div>

        <div style="min-width:0;background:#f8fafc;display:flex;flex-direction:column">
          <div class="panel-head" style="background:#fff">
            <div><h2>本地预览</h2><p>{{selectedPath||'从左侧选择一个 HTML 文件'}}</p></div>
            <a v-if="selectedPath" class="soft-button" :href="localPreviewUrl(selectedPath)" target="_blank" style="margin-left:auto">新窗口打开 <ExternalLink :size="13"/></a>
          </div>
          <div v-if="!selectedPath" class="empty-state" style="margin:auto">选择 current/ 中的 HTML 文件即可预览。Windows 本机不需要启动 Docker 或 Nginx。</div>
          <iframe v-else :key="selectedPath" :src="localPreviewUrl(selectedPath)" title="Managed project preview" style="width:100%;flex:1;min-height:560px;border:0;background:#fff"></iframe>
        </div>
      </div>
    </section>
  </div>
</template>
