<template>
  <div class="app-shell">
    <Sidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    <div v-if="sidebarOpen" class="sidebar-overlay" @click="sidebarOpen = false"></div>
    <div class="main-wrap">
      <Topbar @toggle-sidebar="sidebarOpen = !sidebarOpen" />
      <main class="page-content flex-grow-1">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import Sidebar from '@/components/Sidebar.vue'
import Topbar from '@/components/Topbar.vue'

const sidebarOpen = ref(false)
</script>
