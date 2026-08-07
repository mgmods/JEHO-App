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
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from '@/components/Sidebar.vue'
import Topbar from '@/components/Topbar.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()
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

watch(
  () => route.fullPath,
  () => closeSidebar(),
)

onMounted(() => {
  if (auth.isAuthenticated) auth.fetchMe()
})

onUnmounted(() => setBodyLock(false))
</script>
