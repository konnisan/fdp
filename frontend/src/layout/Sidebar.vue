<script setup>
import {
  Boxes,
  ChevronRight,
  Eye,
  Gauge,
  Home,
  PackageOpen,
  Rocket,
  Server
} from 'lucide-vue-next'

const props = defineProps({ activePath: { type: String, required: true } })
const emit = defineEmits(['navigate'])

const groups = [
  {
    label: '',
    items: [{ label: '平台首页', path: '/', icon: Home }]
  },
  {
    label: 'POC 管理',
    items: [
      { label: 'POC 项目', path: '/poc-projects', icon: PackageOpen },
      { label: '预览入口', path: '/previews', icon: Eye }
    ]
  },
  {
    label: '发布运维',
    items: [
      { label: '部署中心', path: '/deployments', icon: Rocket },
      { label: '运行环境', path: '/runtime', icon: Server }
    ]
  }
]

function active(path) {
  if (path === '/') return props.activePath === '/'
  return props.activePath === path || props.activePath.startsWith(path + '/')
}
</script>

<template>
  <aside class="sidebar">
    <button class="brand" type="button" @click="emit('navigate', '/')">
      <span class="brand-mark"><Boxes :size="22" /></span>
      <span>
        <strong>FDP</strong>
        <small>POC部署与预览平台</small>
      </span>
    </button>

    <nav class="nav-groups">
      <section v-for="group in groups" :key="group.label || 'root'" class="nav-group">
        <div v-if="group.label" class="nav-label">{{ group.label }}</div>
        <button
          v-for="item in group.items"
          :key="item.path"
          type="button"
          class="nav-item"
          :class="{ active: active(item.path) }"
          @click="emit('navigate', item.path)"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
          <ChevronRight v-if="active(item.path) && item.path !== '/'" class="nav-arrow" :size="14" />
        </button>
      </section>
    </nav>

    <div class="sidebar-status">
      <div class="sidebar-status-title">
        <Gauge :size="15" />
        <span>服务器状态</span>
        <b><i></i>运行中</b>
      </div>
      <div class="sidebar-status-row"><span>外部端口</span><strong>8090</strong></div>
      <div class="sidebar-divider"></div>
      <div class="sidebar-status-row"><span>版本</span><strong>v2.1.0</strong></div>
    </div>
  </aside>
</template>
