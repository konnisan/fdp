<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { Download, PackageCheck, RefreshCw } from 'lucide-vue-next'
import { listManagedArtifactVersions } from '../api'
import { materializeManagedArtifacts } from '../managedArtifactMaterializeApi'

const props=defineProps({projectId:{type:Number,required:true},artifacts:{type:Array,default:()=>[]}})
const emit=defineEmits(['materialized'])
const versions=reactive({})
const selected=reactive({})
const loading=ref(false)
const deploying=ref(false)
const error=ref('')
const info=ref('')

function message(e){return e.response?.data?.message||e.message||'操作失败'}
async function loadVersions(){
  loading.value=true;error.value=''
  try{
    for(const artifact of props.artifacts||[]){
      if(!artifact.id)continue
      const rows=await listManagedArtifactVersions(props.projectId,artifact.id)
      versions[artifact.id]=Array.isArray(rows)?rows:[]
      const values=versions[artifact.id].map(v=>String(v.version||''))
      if(!selected[artifact.id]||!values.includes(String(selected[artifact.id]))){
        selected[artifact.id]=artifact.deployedVersion&&values.includes(String(artifact.deployedVersion))
          ? artifact.deployedVersion
          : (versions[artifact.id][0]?.version||'')
      }
    }
  }catch(e){error.value=message(e)}finally{loading.value=false}
}
function selectedVersionRow(artifact){return (versions[artifact.id]||[]).find(v=>String(v.version||'')===String(selected[artifact.id]||''))||{}}
async function materialize(){
  deploying.value=true;error.value='';info.value=''
  try{
    const items=(props.artifacts||[]).map(a=>{
      const row=selectedVersionRow(a)
      return {artifactId:a.id,version:selected[a.id],downloadUrl:row.downloadUrl||row.downloadURL||row.url||null}
    })
    if(!items.length)throw new Error('当前项目没有绑定制品')
    if(items.find(item=>!item.version))throw new Error('请为所有制品选择版本')
    const result=await materializeManagedArtifacts(props.projectId,items)
    info.value=result.message||'制品已下载并解压到 current/'
    emit('materialized',result)
  }catch(e){error.value=message(e)}finally{deploying.value=false}
}

watch(()=>props.artifacts,loadVersions,{deep:true})
onMounted(loadVersions)
</script>

<template>
  <section class="panel project-create-form">
    <div class="panel-head">
      <h2><PackageCheck :size="18"/>制品版本与下载</h2>
      <button class="soft-button" :disabled="loading" @click="loadVersions"><RefreshCw :size="14"/>刷新版本</button>
    </div>
    <div v-if="error" class="error-banner" style="margin:0 16px 12px">{{error}}</div>
    <div v-if="info" class="success-banner" style="margin:0 16px 12px">{{info}}</div>
    <div v-if="!artifacts.length" class="empty-state">暂无绑定制品。</div>
    <div v-else class="artifact-download-list">
      <article v-for="artifact in artifacts" :key="artifact.id||`${artifact.repositoryId}-${artifact.artifactName}`" class="artifact-download-row">
        <strong>{{artifact.artifactName}}</strong>
        <label>版本
          <select v-model="selected[artifact.id]" :disabled="loading||!artifact.id">
            <option value="">请选择版本</option>
            <option v-for="v in versions[artifact.id]||[]" :key="v.id||v.version" :value="v.version">{{v.version}}</option>
          </select>
        </label>
        <div class="artifact-download-target"><span>解压目录</span><code>current/{{artifact.targetDirectory==='.'?'':artifact.targetDirectory}}</code></div>
      </article>
      <div class="artifact-download-actions">
        <button class="primary-button" :disabled="deploying||loading" @click="materialize"><Download :size="14"/>{{deploying?'下载解压中…':'下载并解压'}}</button>
      </div>
    </div>
  </section>
</template>
