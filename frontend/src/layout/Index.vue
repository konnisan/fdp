<script setup>
import AppHeader from './Header.vue'
import AppSidebar from './Sidebar.vue'

defineProps({
  activePath: { type: String, required: true },
  pageTitle: { type: String, default: '平台首页' }
})

defineEmits(['navigate'])
</script>

<template>
  <div class="app-shell">
    <AppSidebar :active-path="activePath" @navigate="$emit('navigate', $event)" />
    <div class="app-main">
      <AppHeader :page-title="pageTitle" />
      <main class="app-content">
        <slot />
      </main>
    </div>
  </div>
</template>
