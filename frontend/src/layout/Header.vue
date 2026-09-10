<script setup>
import { CircleCheck, Server } from 'lucide-vue-next'

defineProps({ pageTitle: { type: String, default: '平台首页' } })

const host = window.location.host || 'localhost:5173'
</script>

<template>
  <header class="topbar">
    <div class="topbar-title-wrap">
      <span class="topbar-kicker">FDP Console</span>
      <div class="topbar-title">{{ pageTitle }}</div>
    </div>
    <div class="topbar-spacer"></div>
    <div class="server-pill"><Server :size="14" /><span>{{ host }}</span></div>
    <div class="running-pill"><CircleCheck :size="14" /><span>控制台在线</span></div>
  </header>
</template>
