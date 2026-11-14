<template>
  <header class="topbar sauti-topbar">
    <div class="d-flex align-items-center gap-3 min-w-0">
      <button
        class="tb-icon"
        type="button"
        @click="$emit('toggle-sidebar')"
        :aria-label="t('app.menu')"
      >
        <i class="bi bi-list fs-5"></i>
      </button>
      <div class="min-w-0">
        <div class="tb-title text-truncate">{{ pageTitle }}</div>
        <div class="tb-sub text-truncate">{{ t('dashboard.welcomePanel') }}</div>
      </div>
    </div>

    <div class="d-flex align-items-center gap-2 flex-shrink-0">
      <select class="form-select form-select-sm tb-lang d-none d-md-block" :value="locale" @change="onLocale">
        <option value="ar">{{ t('app.arabic') }}</option>
        <option value="en">{{ t('app.english') }}</option>
      </select>

      <button class="tb-icon d-none d-sm-inline-flex" type="button" :aria-label="t('common.search')">
        <i class="bi bi-search"></i>
      </button>

      <RouterLink class="tb-icon" :to="{ name: 'notifications' }" :aria-label="t('nav.notifications')">
        <i class="bi bi-bell"></i>
        <span v-if="pendingHint > 0" class="tb-badge">{{ pendingHint > 99 ? '99+' : pendingHint }}</span>
      </RouterLink>

      <RouterLink class="tb-icon" :to="{ name: 'settings' }" :aria-label="t('nav.settings')">
        <i class="bi bi-gear"></i>
      </RouterLink>

      <div class="tb-user">
        <div class="tb-avatar">{{ initials }}</div>
        <div class="d-none d-sm-block">
          <div class="tb-name">{{ auth.displayName }}</div>
          <div class="tb-role">{{ t('app.administrator') }}</div>
        </div>
      </div>

      <button class="tb-icon" type="button" @click="onLogout" :aria-label="t('app.logout')">
        <i class="bi bi-box-arrow-right"></i>
      </button>
    </div>
  </header>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { setDashboardLocale } from '@/i18n'
import { dashboardApi } from '@/api'

defineEmits(['toggle-sidebar'])

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const pendingHint = ref(0)

const pageTitle = computed(() => {
  const key = route.meta?.titleKey
  return key ? t(key) : t('app.brand')
})

const initials = computed(() => {
  const name = auth.displayName || 'A'
  return name.split(/\s+/).map((p) => p[0]).join('').slice(0, 2).toUpperCase()
})

function onLocale(event) {
  setDashboardLocale(event.target.value)
}
function onLogout() {
  auth.logout()
  router.push({ name: 'login' })
}

onMounted(async () => {
  try {
    const { data } = await dashboardApi.overview()
    const o = data || {}
    pendingHint.value = Number(o.pendingReports || 0) + Number(o.pendingWithdraws || 0)
  } catch {
    pendingHint.value = 0
  }
})
</script>

<style scoped>
.sauti-topbar {
  background: transparent !important;
  border-bottom: 0 !important;
  padding-block: 1rem !important;
}
.tb-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: #fff;
  letter-spacing: -0.02em;
}
.tb-sub {
  font-size: 0.78rem;
  color: #7a829c;
  margin-top: 0.12rem;
}
.tb-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: #1c1e2d;
  color: #c5cce0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
  text-decoration: none;
}
.tb-icon:hover {
  border-color: rgba(108, 92, 231, 0.4);
  color: #fff;
}
.tb-badge {
  position: absolute;
  top: 4px;
  inset-inline-end: 4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 0.6rem;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
}
.tb-user {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.25rem 0.65rem 0.25rem 0.25rem;
  border-radius: 999px;
  background: #1c1e2d;
  border: 1px solid rgba(255, 255, 255, 0.06);
}
.tb-avatar {
  width: 38px;
  height: 38px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #6c5ce7, #3b82f6);
  color: #fff;
  font-size: 0.78rem;
  font-weight: 700;
}
.tb-name { font-size: 0.84rem; font-weight: 700; color: #fff; line-height: 1.15; }
.tb-role { font-size: 0.68rem; color: #7a829c; margin-top: 0.1rem; }
.tb-lang {
  width: auto;
  min-width: 5.2rem;
  height: 40px;
  background: #1c1e2d;
  border-color: rgba(255, 255, 255, 0.06);
  color: #eef1f8;
  border-radius: 12px;
}
</style>
