<script setup>
import { Server, Wifi } from 'lucide-vue-next'

defineProps({ pageTitle: { type: String, default: '平台首页' } })

const host = window.location.host || 'localhost:5173'
</script>

<template>
  <header class="topbar">
    <div class="topbar-title">{{ pageTitle }}</div>
    <div class="topbar-spacer"></div>
    <div class="server-pill"><Server :size="15" />服务器：{{ host }}</div>
    <div class="running-pill"><Wifi :size="14" />运行中</div>
  </header>
</template>
