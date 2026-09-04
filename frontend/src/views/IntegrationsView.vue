<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Boxes, Database, KeyRound, Plus, RefreshCw, Server, Workflow, X } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  createSourceCredential,
  getRuntimeStatus,
  getYunxiaoStatus,
  listSourceCredentials,
  listYunxiaoPipelines,
  listYunxiaoRepositories,
  testSourceCredential
} from '../api'

const runtime=ref(null)
const yunxiao=ref(null)
const pipelines=ref([])
const repositories=ref([])
const credentials=ref([])
const loading=ref(false)
const error=ref('')
const info=ref('')
const showCredential=ref(false)
const saving=ref(false)
const testing=ref('')
const form=reactive({name:'公司 Codeup',provider:'CODEUP',cloneUsername:'',token:''})
function err(e){return e.response?.data?.message||e.message}
async function load(){loading.value=true;error.value='';try{const [rt,ys,ps,repos,cs]=await Promise.all([getRuntimeStatus(),getYunxiaoStatus(),listYunxiaoPipelines({page:1,perPage:10}),listYunxiaoRepositories({repoTypes:'GENERIC',page:1,perPage:10}),listSourceCredentials()]);runtime.value=rt;yunxiao.value=ys;pipelines.value=ps;repositories.value=repos;credentials.value=cs}catch(e){error.value=err(e)}finally{loading.value=false}}
async function saveCredential(){if(!form.name||!form.cloneUsername||!form.token){error.value='请填写凭据名称、HTTPS 克隆账号和 Token';return}saving.value=true;error.value='';try{await createSourceCredential({...form});showCredential.value=false;Object.assign(form,{name:'公司 Codeup',provider:'CODEUP',cloneUsername:'',token:''});info.value='Codeup 凭据已保存';await load()}catch(e){error.value=err(e)}finally{saving.value=false}}
async function testCredential(c){testing.value=String(c.id);error.value='';info.value='';try{const sample=prompt('输入一个用于测试的 Codeup HTTPS Git 地址');if(!sample)return;const result=await testSourceCredential(c.id,{gitUrl:sample,gitBranch:'main'});info.value=`${c.name}: ${result.status} · ${result.message}`;await load()}catch(e){error.value=err(e)}finally{testing.value=''}}
onMounted(load)
</script>

<template>
  <div class="page-stack restructure-page">
    <PageHeader title="系统信息" description="查看 FDP 当前服务器、Docker / Nginx、Codeup 凭据以及云效 Flow / Packages 的连接状态。">
      <template #actions><button class="soft-button" :disabled="loading" @click="load"><RefreshCw :size="14" />{{loading?'检测中…':'重新检测'}}</button></template>
    </PageHeader>
    <div v-if="error" class="error-banner">{{error}}</div><div v-if="info" class="success-banner">{{info}}</div>

    <div v-if="runtime" class="runtime-summary integration-summary">
      <article class="panel runtime-primary"><div><span>当前开发 / 部署设备</span><h2>{{runtime.os}} · {{runtime.executionMode}}</h2><p>Java {{runtime.javaVersion}} · 对外端口 {{runtime.publicPort}}</p></div><span class="status-text" :class="runtime.liveReady?'running':'pending'"><i></i>{{runtime.liveReady?'LIVE READY':'DRY-RUN SAFE'}}</span></article>
      <article class="panel runtime-stat"><span>Flow</span><strong>{{pipelines.length}}</strong><small>当前读取的流水线</small></article>
      <article class="panel runtime-stat"><span>Packages</span><strong>{{repositories.length}}</strong><small>GENERIC 仓库</small></article>
    </div>

    <section v-if="runtime" class="panel">
      <div class="panel-head"><div><h2><Server :size="18" />运行依赖</h2><p>Windows 开发建议保持 FDP_EXECUTION_ENABLED=false；Linux 上线后再开启真实 Docker / Nginx 执行。</p></div></div>
      <div class="runtime-grid"><article v-for="tool in runtime.tools" :key="tool.name" class="runtime-item"><span class="runtime-icon"><Workflow v-if="tool.name==='Docker'" :size="20" /><Server v-else :size="20" /></span><div><h3>{{tool.name}}</h3><p>{{tool.detail}}</p></div><span class="status-text" :class="tool.available?'running':'failed'"><i></i>{{tool.available?'可用':'不可用'}}</span></article></div>
      <div class="env-code integration-env"><code>FDP_EXECUTION_ENABLED={{runtime.executionMode==='LIVE'?'true':'false'}}</code><code>FDP_WORKSPACE_ROOT={{runtime.workspaceRoot}}</code><code>FDP_STATIC_ROOT={{runtime.staticRoot}}</code><code>FDP_DATA_ROOT={{runtime.dataRoot}}</code><code>FDP_PUBLIC_PORT={{runtime.publicPort}}</code></div>
    </section>

    <section class="panel">
      <div class="panel-head"><div><h2><KeyRound :size="18" />Codeup 凭据</h2><p>用于 FDP 访问需要认证的 Codeup 仓库。Token 不写进 Git URL。</p></div><button class="primary-button" @click="showCredential=true"><Plus :size="14" />新增凭据</button></div>
      <div class="table-wrap"><table class="data-table"><thead><tr><th>名称</th><th>Provider</th><th>账号</th><th>状态</th><th>最近测试</th><th>操作</th></tr></thead><tbody><tr v-for="c in credentials" :key="c.id"><td><strong>{{c.name}}</strong></td><td>{{c.provider}}</td><td><code>{{c.cloneUsername}}</code></td><td><span class="tag">{{c.status}}</span></td><td>{{c.lastTestMessage||'-'}}</td><td><button class="soft-button" :disabled="testing!==''" @click="testCredential(c)">{{testing===String(c.id)?'测试中…':'测试连接'}}</button></td></tr></tbody></table></div><div v-if="!credentials.length" class="empty-state">暂无 Codeup 凭据。</div>
    </section>

    <section class="detail-grid">
      <article class="panel info-card"><h3><Workflow :size="17" />云效 Flow</h3><dl><div><dt>OpenAPI</dt><dd><span class="tag">{{yunxiao?.configured?'已配置':'未配置'}}</span></dd></div><div><dt>Endpoint</dt><dd><code>{{yunxiao?.domain||'-'}}</code></dd></div><div><dt>Organization</dt><dd><code>{{yunxiao?.organizationId||'-'}}</code></dd></div><div><dt>可读取 Pipeline</dt><dd>{{pipelines.length}}</dd></div><div><dt>运行权限</dt><dd>触发 Flow 需要流水线读写权限</dd></div></dl></article>
      <article class="panel info-card"><h3><Boxes :size="17" />Packages</h3><dl><div><dt>GENERIC 仓库</dt><dd>{{repositories.length}}</dd></div><div v-for="repo in repositories.slice(0,3)" :key="repo.repoId"><dt>{{repo.repoName}}</dt><dd><code>{{repo.repoId}}</code></dd></div></dl></article>
      <article class="panel info-card span-2"><h3><Database :size="17" />平台工作链路</h3><div class="integration-boundary"><span>Codeup</span><b>已有源码</b><i>→</i><span>Flow</span><b>运行构建</b><i>→</i><span>Packages</span><b>查看制品</b><i>→</i><span>容器部署</span><b>Docker 配置</b><i>→</i><span>Docker + Nginx</span><b>对外运行</b></div></article>
    </section>

    <div v-if="showCredential" class="modal-mask" @click.self="showCredential=false"><form class="modal-card" @submit.prevent="saveCredential"><header><div><h2>新增 Codeup 凭据</h2><p>同一账号可复用到该账号有权限访问的多个仓库。</p></div><button type="button" class="icon-button" @click="showCredential=false"><X :size="18" /></button></header><div class="form-grid"><label class="span-2">凭据名称 *<input v-model="form.name" /></label><label class="span-2">HTTPS 克隆账号 *<input v-model="form.cloneUsername" /></label><label class="span-2">Personal Access Token *<input v-model="form.token" type="password" autocomplete="new-password" /></label></div><footer><button type="button" class="soft-button" @click="showCredential=false">取消</button><button class="primary-button" :disabled="saving">{{saving?'保存中…':'保存凭据'}}</button></footer></form></div>
  </div>
</template>
