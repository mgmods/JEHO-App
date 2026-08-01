<template>
  <header class="topbar">
    <div class="d-flex align-items-center gap-2">
      <button class="theme-toggle d-lg-none" type="button" @click="$emit('toggle-sidebar')" :aria-label="t('app.menu')">
        <i class="bi bi-list fs-5"></i>
      </button>
      <div>
        <div class="fw-semibold">{{ pageTitle }}</div>
      </div>
    </div>

    <div class="d-flex align-items-center gap-2">
      <select class="form-select form-select-sm lang-switch" :value="locale" @change="onLocale">
        <option value="ar">{{ t('app.arabic') }}</option>
        <option value="en">{{ t('app.english') }}</option>
      </select>

      <button
        class="theme-toggle"
        type="button"
        @click="theme.toggle()"
        :aria-label="theme.isDark ? t('app.lightMode') : t('app.darkMode')"
      >
        <i :class="theme.isDark ? 'bi bi-sun' : 'bi bi-moon-stars'"></i>
      </button>

      <div class="user-chip">
        <div class="avatar">{{ initials }}</div>
        <div class="d-none d-sm-block">
          <div class="small fw-semibold lh-1">{{ auth.displayName }}</div>
          <div class="text-muted" style="font-size: 0.7rem">{{ t('app.administrator') }}</div>
        </div>
      </div>

      <button class="btn btn-sm btn-ghost" type="button" @click="onLogout">
        <i class="bi bi-box-arrow-right me-1"></i>
        {{ t('app.logout') }}
      </button>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { setDashboardLocale } from '@/i18n'

defineEmits(['toggle-sidebar'])

const auth = useAuthStore()
const theme = useThemeStore()
const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

const pageTitle = computed(() => {
  const key = route.meta?.titleKey
  return key ? t(key) : t('app.brand')
})

const initials = computed(() => {
  const name = auth.displayName || 'A'
  return name
    .split(/\s+/)
    .map((p) => p[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()
})

function onLocale(event) {
  setDashboardLocale(event.target.value)
}

function onLogout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<style scoped>
.lang-switch {
  width: auto;
  min-width: 7.5rem;
  background: transparent;
  color: inherit;
  border-color: var(--border-color);
}
</style>
