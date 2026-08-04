<template>
  <div class="app-shell" :class="{ 'is-drawer-open': sidebarOpen }">
    <Sidebar :open="sidebarOpen" @close="closeSidebar" />
    <div
      v-if="sidebarOpen"
      class="sidebar-overlay"
      role="button"
      tabindex="-1"
      aria-label="Close menu"
      @click="closeSidebar"
    ></div>
    <div class="main-wrap">
      <Topbar @toggle-sidebar="toggleSidebar" />
      <main class="page-content flex-grow-1">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from '@/components/Sidebar.vue'
import Topbar from '@/components/Topbar.vue'

const route = useRoute()
const sidebarOpen = ref(false)

function closeSidebar() {
  sidebarOpen.value = false
}

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}

function setBodyLock(open) {
  try {
    document.body.classList.toggle('sidebar-open', !!open)
  } catch {
    /* ignore */
  }
}

watch(sidebarOpen, (open) => setBodyLock(open), { immediate: true })

// Any route navigation closes drawer (mobile)
watch(
  () => route.fullPath,
  () => closeSidebar(),
)

onUnmounted(() => setBodyLock(false))
</script>
