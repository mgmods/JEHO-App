<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">LV</div>
        <div>
          <h1>LYVO Cloud</h1>
          <span>{{ isAdmin ? 'Platform console' : 'Developer console' }}</span>
        </div>
      </div>

      <div class="nav-label">Workspace</div>
      <button class="nav-item" :class="{ active: route.name === 'home' }" type="button" @click="go('home')">
        ▦ Overview
      </button>
      <button class="nav-item" :class="{ active: route.name === 'apps' || route.name === 'app' }" type="button" @click="go('apps')">
        ◇ Live apps
      </button>
      <button class="nav-item" :class="{ active: route.name === 'sdk' }" type="button" @click="go('sdk')">
        { } SDK &amp; integrate
      </button>
      <button class="nav-item" :class="{ active: route.name === 'packages' }" type="button" @click="go('packages')">
        ◆ Buy minutes
      </button>
      <button class="nav-item" :class="{ active: route.name === 'billing' }" type="button" @click="go('billing')">
        ▤ My balance
      </button>

      <template v-if="isAdmin">
        <div class="nav-label">Platform owner</div>
        <button class="nav-item" :class="{ active: route.name === 'admin' }" type="button" @click="go('admin')">
          ★ Admin · packs &amp; card pay
        </button>
      </template>

      <div class="sidebar-foot">
        <p>
          <strong style="color:#fff">{{ (developer?.totalMinutesRemaining || 0).toLocaleString() }} min</strong><br />
          voice minutes remaining
        </p>
        <button class="btn btn-primary btn-block" type="button" @click="go('packages')">Buy packages</button>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="welcome">
          <span class="who">{{ developer?.displayName || developer?.email }}</span>
          <span class="role-pill" :class="{ admin: isAdmin }">
            {{ isAdmin ? 'Platform admin' : 'Developer' }}
          </span>
        </div>
        <button class="btn btn-ghost btn-sm" type="button" @click="goSite">الموقع</button>
        <button class="btn btn-ghost btn-sm" type="button" @click="logout">Sign out</button>
      </header>
      <div v-if="developer && (developer.totalMinutesRemaining || 0) <= 0" class="alert-red">
        نفدت دقائق الصوت — اشترِ باقة من Buy minutes لمتابعة البث المباشر.
      </div>
      <div class="content">
        <RouterView />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, setToken } from '../api'

const route = useRoute()
const router = useRouter()
const developer = ref(null)
const dash = ref(null)

const isAdmin = computed(() => !!(developer.value?.isAdmin || developer.value?.isSeller))

async function refresh() {
  dash.value = await api.dashboard()
  developer.value = dash.value.developer
}

provide('dash', dash)
provide('refreshDash', refresh)
provide('isAdmin', isAdmin)

function go(name) {
  router.push({ name })
}

function goSite() {
  router.push({ name: 'landing' })
}

function logout() {
  setToken('')
  router.replace({ name: 'landing' })
}

onMounted(async () => {
  try {
    await refresh()
  } catch {
    setToken('')
    router.replace({ name: 'login' })
  }
})

// Block /admin for non-owners
watch(
  [() => route.name, isAdmin, developer],
  () => {
    if (route.meta.admin && developer.value && !isAdmin.value) {
      router.replace({ name: 'home' })
    }
  },
)
</script>
