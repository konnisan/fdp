<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Database, ExternalLink, File, Folder, Monitor, PackageCheck, Plus, RefreshCw, Save, Server, Settings2, Trash2 } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import ManagedArtifactDeploymentPanel from '../components/ManagedArtifactDeploymentPanel.vue'
import { getManagedDirectory, getManagedProject, getManagedRuntimeImages, previewManagedProject, updateManagedProjectConfiguration } from '../api'

const props=defineProps({projectId:{type:Number,required:true}})
const emit=defineEmits(['navigate'])
const PICK_TARGET_KEY='fdp-artifact-selection-target'
const EDIT_DRAFT_PREFIX='fdp-managed-project-edit-draft-'

const project=ref(null)
const images=ref([])
const tree=ref({entries:[]})
const artifactBindings=ref([])
const loading=ref(true)
const saving=ref(false)
const switching=ref(false)
const error=ref('')
const info=ref('')
const selectedPath=ref('')

const form=reactive({
  projectName:'',databaseName:'',runtimeImage:'',workDirectory:'.',startCommand:'',
  serviceMode:'DIRECT',servicePort:8080,nginxStaticDirectory:'frontend/dist',
  nginxApiPrefix:'/api/',nginxBackendPort:8080,envContent:''
})

const htmlEntries=computed(()=>Array.isArray(tree.value?.entries)?tree.value.entries.filter(e=>e.html):[])
function message(e){return e.response?.data?.message||e.message||'操作失败'}
function size(value){const n=Number(value);if(!Number.isFinite(n))return '';if(n<1024)return `${n} B`;if(n<1024*1024)return `${(n/1024).toFixed(1)} KB`;return `${(n/1024/1024).toFixed(1)} MB`}
function encodedPath(path){return String(path||'').split('/').map(encodeURIComponent).join('/')}
function localPreviewUrl(path){return `${window.location.origin}/api/managed-projects/${props.projectId}/local-preview/${encodedPath(path)}`}
function selectPreview(entry){if(entry?.html)selectedPath.value=entry.path}
function editDraftKey(){return `${EDIT_DRAFT_PREFIX}${props.projectId}`}
function readJson(key,fallback){try{return JSON.parse(sessionStorage.getItem(key)||'')||fallback}catch{return fallback}}
function formSnapshot(){return {...form}}
function applyForm(value){if(!value)return;for(const key of Object.keys(form))if(value[key]!==undefined&&value[key]!==null)form[key]=value[key]}
function applyProject(p){
  project.value=p
  applyForm({
    projectName:p.projectName||'',databaseName:p.databaseName||'',runtimeImage:p.runtimeImage||'',workDirectory:p.workDirectory||'.',
    startCommand:p.startCommand==='__FDP_START_COMMAND_NOT_CONFIGURED__'?'':(p.startCommand||''),serviceMode:p.serviceMode||'DIRECT',
    servicePort:p.servicePort||8080,nginxStaticDirectory:p.nginxStaticDirectory||'frontend/dist',nginxApiPrefix:p.nginxApiPrefix||'/api/',
    nginxBackendPort:p.nginxBackendPort||8080,envContent:p.envContent||''
  })
}
function normalizeArtifacts(items){
  return (Array.isArray(items)?items:[]).map((a,i)=>({
    id:a.id||null,repositoryId:String(a.repositoryId||a.repoId||''),repositoryName:a.repositoryName||a.repoName||'',artifactName:a.artifactName||'',
    targetDirectory:a.targetDirectory||'.',sortOrder:i,deployedVersion:a.deployedVersion||'',runningVersion:a.runningVersion||'',latestVersion:a.latestVersion||''
  })).filter(a=>a.repositoryId&&a.artifactName)
}
function persistEditDraft(){sessionStorage.setItem(editDraftKey(),JSON.stringify({form:formSnapshot(),artifacts:artifactBindings.value}))}
function chooseArtifacts(){persistEditDraft();sessionStorage.setItem(PICK_TARGET_KEY,JSON.stringify({mode:'edit',projectId:props.projectId,createdAt:Date.now()}));emit('navigate','/artifacts')}
function removeArtifact(index){artifactBindings.value.splice(index,1);artifactBindings.value=artifactBindings.value.map((a,i)=>({...a,sortOrder:i}))}
function back(){sessionStorage.removeItem(editDraftKey());sessionStorage.removeItem(PICK_TARGET_KEY);emit('navigate','/containers')}

async function load(){
  loading.value=true;error.value=''
  try{
    const [p,d,imgs]=await Promise.all([getManagedProject(props.projectId),getManagedDirectory(props.projectId),getManagedRuntimeImages()])
    applyProject(p);tree.value=d;images.value=imgs||[]
    const draft=readJson(editDraftKey(),null)
    if(draft?.form)applyForm(draft.form)
    artifactBindings.value=normalizeArtifacts(draft?.artifacts??p.artifacts)
    sessionStorage.removeItem(PICK_TARGET_KEY)
    if(selectedPath.value&&!d.entries?.some(e=>e.path===selectedPath.value))selectedPath.value=''
    if(!selectedPath.value&&htmlEntries.value.length===1)selectedPath.value=htmlEntries.value[0].path
  }catch(e){error.value=message(e)}finally{loading.value=false}
}

async function save(){
  saving.value=true;error.value='';info.value=''
  try{
    const payload={
      projectName:form.projectName.trim(),databaseName:form.databaseName,runtimeImage:form.runtimeImage,
      workDirectory:form.workDirectory||'.',startCommand:form.startCommand,serviceMode:form.serviceMode,servicePort:Number(form.servicePort),
      nginxStaticDirectory:form.serviceMode==='NGINX'?form.nginxStaticDirectory:null,
      nginxApiPrefix:form.serviceMode==='NGINX'?form.nginxApiPrefix:'/api/',
      nginxBackendPort:form.serviceMode==='NGINX'?Number(form.nginxBackendPort):null,envContent:form.envContent,
      artifacts:artifactBindings.value.map((a,i)=>({
        repositoryId:a.repositoryId,repositoryName:a.repositoryName,artifactName:a.artifactName,
        targetDirectory:a.targetDirectory||'.',sortOrder:i
      }))
    }
    const updated=await updateManagedProjectConfiguration(props.projectId,payload)
    sessionStorage.removeItem(editDraftKey())
    applyProject(updated);artifactBindings.value=normalizeArtifacts(updated.artifacts);project.value=updated
    info.value=form.startCommand.trim()?'项目配置和制品绑定已原子保存。':'项目配置和制品绑定已原子保存，但启动命令仍未配置。'
  }catch(e){error.value=message(e)}finally{saving.value=false}
}

async function onMaterialized(result){
  info.value=result?.message||'制品已下载并解压到 current/'
  await load()
}
async function switchPublicPreview(){
  switching.value=true;error.value='';info.value=''
  try{await previewManagedProject(props.projectId);info.value='已切换为服务器统一对外预览项目。'}
  catch(e){error.value=message(e)}finally{switching.value=false}
}

onMounted(load)
</script>

<template>
<div class="page-stack restructure-page">
  <PageHeader :title="project?`${project.projectName} · 编辑项目`:'编辑项目'" description="项目创建后继续维护制品绑定和运行配置；选择版本下载并解压后，可直接观察 current/ 目录再填写启动命令。">
    <template #actions>
      <button class="soft-button" @click="back"><ArrowLeft :size="14"/>返回项目部署</button>
      <button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14"/>刷新</button>
      <button class="primary-button" :disabled="saving||loading" @click="save"><Save :size="14"/>{{saving?'保存中…':'保存项目配置'}}</button>
    </template>
  </PageHeader>

  <div v-if="error" class="error-banner">{{error}}</div>
  <div v-if="info" class="success-banner">{{info}}</div>
  <div v-if="loading" class="panel empty-state">正在读取项目配置和 current/ 目录…</div>

  <template v-else-if="project">
    <section class="panel project-create-form">
      <div class="panel-head"><div><h2><Database :size="18"/>1. 项目基础配置</h2><p>Database 创建后固定；其余运行配置可以继续调整。</p></div></div>
      <div class="form-grid restructure-form-grid">
        <label>项目名称 *<input v-model="form.projectName"/></label>
        <label>Database<input :value="form.databaseName" disabled/><small>项目创建后不可切换 database。</small></label>
        <label>Runtime Image *<select v-model="form.runtimeImage"><option v-for="image in images" :key="image" :value="image">{{image}}</option></select></label>
        <label>工作目录<input v-model="form.workDirectory" placeholder="."/><small>相对 current/。建议先看下方真实目录。</small></label>
      </div>
    </section>

    <section class="panel project-create-form">
      <div class="panel-head">
        <div><h2><PackageCheck :size="18"/>2. 项目制品</h2><p>制品是项目的持久绑定。可继续添加、修改解压目录或移除。</p></div>
        <button class="soft-button" @click="chooseArtifacts"><Plus :size="14"/>从制品仓库添加</button>
      </div>
      <div v-if="!artifactBindings.length" class="empty-state">当前项目没有绑定制品。<button class="link-button" @click="chooseArtifacts">从制品仓库选择</button></div>
      <div v-else style="padding:14px 16px;display:flex;flex-direction:column;gap:10px">
        <article v-for="(artifact,index) in artifactBindings" :key="`${artifact.repositoryId}-${artifact.artifactName}`" style="border:1px solid #e5e7eb;border-radius:8px;padding:14px;display:grid;grid-template-columns:minmax(220px,1.5fr) minmax(160px,1fr) 180px 36px;gap:12px;align-items:end">
          <div><small class="cell-note">Packages 制品</small><strong style="display:block;margin-top:5px">{{artifact.artifactName}}</strong><small class="cell-note">{{artifact.repositoryName||artifact.repositoryId}} · {{artifact.repositoryId}}</small></div>
          <div><small class="cell-note">当前版本</small><strong style="display:block;margin-top:5px">{{artifact.deployedVersion||'未部署'}}</strong><small class="cell-note">运行：{{artifact.runningVersion||'-'}}</small></div>
          <label>解压目录<input v-model="artifact.targetDirectory" placeholder="."/><small>相对 current/</small></label>
          <button class="icon-button danger" title="移除绑定" @click="removeArtifact(index)"><Trash2 :size="14"/></button>
        </article>
        <div class="inline-note" style="margin:0"><Settings2 :size="15"/>新增或修改制品后先保存项目配置，再在下面选择版本下载并解压。</div>
      </div>
    </section>

    <ManagedArtifactDeploymentPanel :project-id="projectId" :artifacts="project.artifacts||[]" @materialized="onMaterialized" />

    <section class="panel project-create-form">
      <div class="panel-head"><div><h2><Settings2 :size="18"/>3. 启动配置</h2><p>制品解压后先看 current/，再决定工作目录和启动命令。</p></div></div>
      <div style="padding:18px 20px;display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:18px">
        <label style="display:flex;flex-direction:column;gap:7px">启动命令
          <textarea v-model="form.startCommand" rows="6" spellcheck="false" placeholder="java -jar app.jar\n或 node server.js" style="width:100%;resize:vertical;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;line-height:1.55"></textarea>
          <small>Container 启动时由 <code>/fdp/start.sh</code> 调用当前保存的命令。</small>
        </label>
        <div class="inline-note" style="align-self:stretch;margin:0;display:flex;align-items:flex-start"><Settings2 :size="15"/><span>当前工作目录：<code>current/{{form.workDirectory==='.'?'':form.workDirectory}}</code><br/>如果根目录直接有 <code>app.jar</code>，保持 <code>.</code> 即可。</span></div>
      </div>
    </section>

    <section class="panel project-create-form">
      <div class="panel-head"><div><h2><Server :size="18"/>4. 服务配置</h2><p>项目保存后可以继续调整服务模式和端口。</p></div></div>
      <div class="form-grid restructure-form-grid">
        <label>服务模式<select v-model="form.serviceMode"><option value="DIRECT">DIRECT · 应用直接服务</option><option value="NGINX">NGINX · 前端 + API 合并</option></select></label>
        <label>项目服务端口<input v-model="form.servicePort" type="number" min="1" max="65535"/></label>
        <template v-if="form.serviceMode==='NGINX'">
          <label>静态目录<input v-model="form.nginxStaticDirectory" placeholder="frontend/dist"/></label>
          <label>API 前缀<input v-model="form.nginxApiPrefix" placeholder="/api/"/></label>
          <label>后端端口<input v-model="form.nginxBackendPort" type="number" min="1" max="65535"/></label>
        </template>
      </div>
    </section>

    <section class="panel project-create-form">
      <div class="panel-head"><div><h2><Settings2 :size="18"/>5. 环境变量</h2><p>项目自定义变量可以继续修改；数据库变量由 FDP 自动注入。</p></div></div>
      <label style="display:flex;flex-direction:column;gap:7px;padding:18px 20px">环境变量（每行 KEY=VALUE）
        <textarea v-model="form.envContent" rows="7" spellcheck="false" placeholder="SPRING_PROFILES_ACTIVE=prod" style="width:100%;resize:vertical;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;line-height:1.55"></textarea>
        <small>FDP 自动提供 DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD。</small>
      </label>
    </section>

    <section class="panel" style="overflow:hidden">
      <div class="panel-head">
        <div><h2><Folder :size="18"/>6. 解压后的 current/ 目录</h2><p>这里读取项目真实部署目录；下载并解压完成后立即可以确认文件位置。</p></div>
        <div class="row-actions"><button class="soft-button" @click="load"><RefreshCw :size="14"/>刷新目录</button><button class="soft-button" :disabled="switching" @click="switchPublicPreview"><Monitor :size="14"/>服务器预览</button></div>
      </div>
      <div style="padding:10px 16px;border-top:1px solid #eef2f7;border-bottom:1px solid #eef2f7"><code style="font-size:11px;word-break:break-all">{{tree.root||project.projectRoot+'/current'}}</code></div>
      <div style="display:grid;grid-template-columns:minmax(330px,38%) minmax(0,1fr);min-height:560px">
        <div style="border-right:1px solid #e5e7eb;min-width:0">
          <div v-if="!tree.exists||!tree.entries?.length" class="empty-state">current/ 当前为空。先在上方选择版本并执行“下载并解压到 current/”。</div>
          <div v-else style="max-height:560px;overflow:auto;padding:6px 0">
            <button v-for="entry in tree.entries" :key="entry.path" type="button" style="width:100%;min-height:34px;border:0;border-bottom:1px solid #f3f4f6;background:#fff;display:flex;align-items:center;padding-right:12px;text-align:left" :style="{paddingLeft:`${14+(entry.depth||0)*18}px`,background:selectedPath===entry.path?'#eff6ff':'#fff'}" :disabled="entry.directory" @click="selectPreview(entry)">
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
          <div class="panel-head" style="background:#fff"><div><h2>HTML 本地预览</h2><p>{{selectedPath||'左侧 HTML 文件可直接预览；其他文件用于确认启动路径。'}}</p></div><a v-if="selectedPath" class="soft-button" :href="localPreviewUrl(selectedPath)" target="_blank" style="margin-left:auto">新窗口打开 <ExternalLink :size="13"/></a></div>
          <div v-if="!selectedPath" class="empty-state" style="margin:auto">目录用于辅助配置启动命令；选择 HTML 文件时可在这里直接查看页面。</div>
          <iframe v-else :key="selectedPath" :src="localPreviewUrl(selectedPath)" title="Managed project preview" style="width:100%;flex:1;min-height:500px;border:0;background:#fff"></iframe>
        </div>
      </div>
    </section>

    <section class="panel project-create-footer">
      <button class="soft-button" @click="back">返回</button>
      <button class="primary-button" :disabled="saving" @click="save"><Save :size="14"/>{{saving?'保存中…':'保存项目配置'}}</button>
    </section>
  </template>
</div>
</template>