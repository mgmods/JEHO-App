<template>
  <aside class="sidebar" :class="{ open }">
    <div class="sidebar-brand">
      <img :src="logoUrl" :alt="t('app.brand')" />
      <div>
        <div class="brand-text">{{ t('app.brand') }}</div>
        <div class="brand-sub">{{ t('app.adminConsole') }}</div>
      </div>
    </div>

    <nav class="sidebar-nav">
      <div class="nav-section-label">{{ t('nav.overview') }}</div>
      <RouterLink class="sidebar-link" :class="{ active: isActive('dashboard') }" :to="{ name: 'dashboard' }" @click="close">
        <i class="bi bi-speedometer2"></i>
        <span>{{ t('nav.dashboard') }}</span>
      </RouterLink>

      <div class="nav-section-label">{{ t('nav.community') }}</div>
      <RouterLink class="sidebar-link" :class="{ active: isActive('users') }" :to="{ name: 'users' }" @click="close">
        <i class="bi bi-people"></i>
        <span>{{ t('nav.users') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('agencies') }" :to="{ name: 'agencies' }" @click="close">
        <i class="bi bi-building"></i>
        <span>{{ t('nav.agencies') }}</span>
      </RouterLink>
      <RouterLink
        v-if="femaleVerifyEnabled"
        class="sidebar-link"
        :class="{ active: isActive('gender-verifications') }"
        :to="{ name: 'gender-verifications' }"
        @click="close"
      >
        <i class="bi bi-person-check"></i>
        <span>{{ t('nav.genderVerifications') }}</span>
      </RouterLink>

      <div class="nav-section-label">{{ t('nav.liveRooms') }}</div>
      <RouterLink class="sidebar-link" :class="{ active: isActive('rooms') }" :to="{ name: 'rooms' }" @click="close">
        <i class="bi bi-door-open"></i>
        <span>{{ t('nav.rooms') }}</span>
      </RouterLink>
      <RouterLink
        class="sidebar-link"
        :class="{ active: isSettingsTab('zego') }"
        :to="{ name: 'settings', query: { tab: 'zego' } }"
        @click="close"
      >
        <i class="bi bi-broadcast-pin"></i>
        <span>{{ t('nav.zegoSettings') }}</span>
      </RouterLink>

      <div class="nav-section-label">{{ t('nav.monetization') }}</div>
      <RouterLink class="sidebar-link" :class="{ active: isActive('gifts') }" :to="{ name: 'gifts' }" @click="close">
        <i class="bi bi-gift"></i>
        <span>{{ t('nav.gifts') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('coins') }" :to="{ name: 'coins' }" @click="close">
        <i class="bi bi-coin"></i>
        <span>{{ t('nav.coins') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('offers') }" :to="{ name: 'offers' }" @click="close">
        <i class="bi bi-box-seam"></i>
        <span>{{ t('nav.offers') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('wallet') }" :to="{ name: 'wallet' }" @click="close">
        <i class="bi bi-wallet2"></i>
        <span>{{ t('nav.wallet') }}</span>
      </RouterLink>
      <RouterLink
        class="sidebar-link"
        :class="{ active: isSettingsTab('payment') }"
        :to="{ name: 'settings', query: { tab: 'payment' } }"
        @click="close"
      >
        <i class="bi bi-credit-card"></i>
        <span>{{ t('nav.paymentSettings') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('recharge-agents') }" :to="{ name: 'recharge-agents' }" @click="close">
        <i class="bi bi-person-badge"></i>
        <span>{{ t('nav.rechargeAgents') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('vip') }" :to="{ name: 'vip' }" @click="close">
        <i class="bi bi-diamond"></i>
        <span>{{ t('nav.vip') }}</span>
      </RouterLink>

      <div class="nav-section-label">{{ t('nav.engagement') }}</div>
      <RouterLink class="sidebar-link" :class="{ active: isActive('cosmetics') }" :to="{ name: 'cosmetics' }" @click="close">
        <i class="bi bi-stars"></i>
        <span>{{ t('nav.cosmetics') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('banners') }" :to="{ name: 'banners' }" @click="close">
        <i class="bi bi-images"></i>
        <span>{{ t('nav.banners') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('lucky-boxes') }" :to="{ name: 'lucky-boxes' }" @click="close">
        <i class="bi bi-box2-heart"></i>
        <span>{{ t('nav.luckyBoxes') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('tasks') }" :to="{ name: 'tasks' }" @click="close">
        <i class="bi bi-list-check"></i>
        <span>{{ t('nav.tasks') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('host-target') }" :to="{ name: 'host-target' }" @click="close">
        <i class="bi bi-bullseye"></i>
        <span>{{ t('nav.hostTarget') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('contests') }" :to="{ name: 'contests' }" @click="close">
        <i class="bi bi-trophy"></i>
        <span>{{ t('nav.contests') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('ranking') }" :to="{ name: 'ranking' }" @click="close">
        <i class="bi bi-bar-chart"></i>
        <span>{{ t('nav.ranking') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('room-cup') }" :to="{ name: 'room-cup' }" @click="close">
        <i class="bi bi-trophy-fill"></i>
        <span>{{ t('nav.roomCup') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('games') }" :to="{ name: 'games' }" @click="close">
        <i class="bi bi-controller"></i>
        <span>{{ t('nav.games') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('game-ads') }" :to="{ name: 'game-ads' }" @click="close">
        <i class="bi bi-badge-ad"></i>
        <span>{{ t('nav.gameAds') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('drama') }" :to="{ name: 'drama' }" @click="close">
        <i class="bi bi-film"></i>
        <span>{{ t('nav.drama') }}</span>
      </RouterLink>

      <div class="nav-section-label">{{ t('nav.ops') }}</div>
      <RouterLink class="sidebar-link" :class="{ active: isActive('reports') }" :to="{ name: 'reports' }" @click="close">
        <i class="bi bi-flag"></i>
        <span>{{ t('nav.reports') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('notifications') }" :to="{ name: 'notifications' }" @click="close">
        <i class="bi bi-bell"></i>
        <span>{{ t('nav.notifications') }}</span>
      </RouterLink>
      <RouterLink
        class="sidebar-link"
        :class="{ active: isActive('settings') && !isSettingsTab('payment') && !isSettingsTab('zego') }"
        :to="{ name: 'settings' }"
        @click="close"
      >
        <i class="bi bi-gear"></i>
        <span>{{ t('nav.settings') }}</span>
      </RouterLink>
      <RouterLink class="sidebar-link" :class="{ active: isActive('logs') }" :to="{ name: 'logs' }" @click="close">
        <i class="bi bi-journal-text"></i>
        <span>{{ t('nav.logs') }}</span>
      </RouterLink>
    </nav>
  </aside>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { settingsApi } from '@/api'

defineProps({
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])
const route = useRoute()
const { t } = useI18n()
const logoUrl = '/logo.png'
const femaleVerifyEnabled = ref(false)

function isActive(name) {
  if (name === 'users') return route.name === 'users' || route.name === 'user-detail'
  return route.name === name
}

function isSettingsTab(tab) {
  return route.name === 'settings' && String(route.query.tab || '') === tab
}

function close() {
  emit('close')
}

function readFeatureFlag(raw) {
  const map = {}
  if (Array.isArray(raw)) {
    for (const row of raw) {
      if (row?.key != null) map[row.key] = row.value
    }
  } else if (raw && typeof raw === 'object') {
    Object.assign(map, raw)
  }
  const v = map['features.female_only_voice_hosts'] ?? map.femaleOnlyVoiceHosts
  if (v === undefined || v === null || v === '') return false
  if (typeof v === 'boolean') return v
  const s = String(v).toLowerCase()
  return s === '1' || s === 'true' || s === 'yes'
}

async function loadFeatureFlags() {
  const { data } = await settingsApi.get()
  femaleVerifyEnabled.value = readFeatureFlag(data?.data ?? data)
}

onMounted(loadFeatureFlags)

watch(
  () => route.name,
  (name, prev) => {
    if (prev === 'settings' || name === 'settings' || name === 'gender-verifications') {
      loadFeatureFlags()
    }
  },
)
</script>
