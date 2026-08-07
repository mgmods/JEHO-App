<template>
  <aside class="sidebar" :class="{ open }">
    <div class="sidebar-brand">
      <div class="brand-mark">
        <img :src="logoUrl" :alt="t('app.brand')" />
      </div>
      <div class="min-w-0">
        <div class="brand-text">{{ t('app.brand') }}</div>
        <div class="brand-sub">{{ t('dashboard.brandTag') }}</div>
      </div>
      <button class="sidebar-close" type="button" :aria-label="t('app.close')" @click="close">
        <i class="bi bi-x-lg"></i>
      </button>
    </div>

    <nav class="sidebar-nav" aria-label="Main">
      <div
        v-for="section in visibleSections"
        :key="section.id"
        class="nav-section"
      >
        <div class="nav-section-label">{{ section.label }}</div>
        <div class="nav-section-body">
          <RouterLink
            v-for="item in section.items"
            :key="item.name + String(item.query?.tab || '')"
            class="sidebar-link"
            :class="{ active: isItemActive(item) }"
            :to="itemTo(item)"
            @click="close"
          >
            <i :class="['bi', item.icon]" aria-hidden="true"></i>
            <span>{{ item.label }}</span>
          </RouterLink>
        </div>
      </div>
    </nav>

    <div class="sidebar-footer">
      <RouterLink class="sidebar-support" :to="{ name: 'notifications' }" @click="close">
        <div class="sidebar-support-icon">
          <i class="bi bi-headset"></i>
        </div>
        <div class="sidebar-support-title">{{ t('dashboard.support24') }}</div>
        <div class="sidebar-support-sub">{{ t('dashboard.supportStrip') }}</div>
        <span class="sidebar-support-cta">{{ t('dashboard.contactSupport') }}</span>
      </RouterLink>
    </div>
  </aside>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { settingsApi } from '@/api'
import brandLogo from '@/assets/brand/logo.png'
import { useAuthStore } from '@/stores/auth'
import { ROUTE_MODULE } from '@/utils/dashboard-permissions'

defineProps({
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])
const route = useRoute()
const { t } = useI18n()
const auth = useAuthStore()
const logoUrl = brandLogo
const femaleVerifyEnabled = ref(false)

/**
 * One page = one job. No flattened mess, no duplicate settings links
 * mixed into unrelated groups.
 */
const sections = computed(() => [
  {
    id: 'overview',
    label: t('nav.overview'),
    items: [
      { name: 'dashboard', icon: 'bi-speedometer2', label: t('nav.dashboard') },
      { name: 'policy-brochure', icon: 'bi-file-earmark-pdf', label: t('nav.policyBrochure') },
    ],
  },
  {
    id: 'community',
    label: t('nav.community'),
    items: [
      { name: 'users', icon: 'bi-people', label: t('nav.users'), also: ['user-detail'] },
      { name: 'agencies', icon: 'bi-building', label: t('nav.agencies') },
      ...(femaleVerifyEnabled.value
        ? [{ name: 'gender-verifications', icon: 'bi-person-check', label: t('nav.genderVerifications') }]
        : []),
    ],
  },
  {
    id: 'live',
    label: t('nav.liveRooms'),
    items: [
      { name: 'rooms', icon: 'bi-mic', label: t('nav.rooms') },
      {
        name: 'settings',
        query: { tab: 'zego' },
        icon: 'bi-broadcast-pin',
        label: t('nav.zegoSettings'),
        module: 'settings',
      },
    ],
  },
  {
    id: 'money',
    label: t('nav.money'),
    items: [
      { name: 'gifts', icon: 'bi-gift', label: t('nav.gifts') },
      { name: 'coins', icon: 'bi-coin', label: t('nav.coins') },
      { name: 'wallet', icon: 'bi-wallet2', label: t('nav.wallet') },
      { name: 'withdrawals', icon: 'bi-cash-stack', label: t('nav.withdrawals') },
      {
        name: 'settings',
        query: { tab: 'payment' },
        icon: 'bi-credit-card-2-front',
        label: t('nav.paymentSettings'),
        module: 'settings',
      },
      { name: 'recharge-agents', icon: 'bi-person-badge', label: t('nav.rechargeAgents') },
    ],
  },
  {
    id: 'memberships',
    label: t('nav.memberships'),
    items: [
      { name: 'vip', icon: 'bi-diamond', label: t('nav.vip') },
      { name: 'vanity-ids', icon: 'bi-hash', label: t('nav.vanityIds') },
      { name: 'cosmetics', icon: 'bi-palette2', label: t('nav.cosmetics') },
      { name: 'offers', icon: 'bi-box-seam', label: t('nav.offers') },
      { name: 'promos', icon: 'bi-stars', label: t('nav.promos') },
    ],
  },
  {
    id: 'engage',
    label: t('nav.engagement'),
    items: [
      { name: 'lucky-boxes', icon: 'bi-box2-heart', label: t('nav.luckyBoxes') },
      { name: 'tasks', icon: 'bi-list-check', label: t('nav.tasks') },
      { name: 'host-target', icon: 'bi-bullseye', label: t('nav.hostTarget') },
      { name: 'contests', icon: 'bi-trophy', label: t('nav.contests') },
      { name: 'ranking', icon: 'bi-bar-chart', label: t('nav.ranking') },
      { name: 'room-cup', icon: 'bi-trophy-fill', label: t('nav.roomCup') },
    ],
  },
  {
    id: 'media',
    label: t('nav.mediaGames'),
    items: [
      { name: 'games', icon: 'bi-controller', label: t('nav.games') },
      { name: 'game-ads', icon: 'bi-badge-ad', label: t('nav.gameAds') },
      { name: 'drama', icon: 'bi-film', label: t('nav.drama') },
      { name: 'banners', icon: 'bi-images', label: t('nav.banners') },
      { name: 'nav-icons', icon: 'bi-grid-1x2', label: t('nav.navIcons') },
    ],
  },
  {
    id: 'ops',
    label: t('nav.ops'),
    items: [
      { name: 'reports', icon: 'bi-flag', label: t('nav.reports') },
      { name: 'notifications', icon: 'bi-bell', label: t('nav.notifications') },
      { name: 'settings', icon: 'bi-gear', label: t('nav.settings'), settingsRoot: true },
      { name: 'logs', icon: 'bi-journal-text', label: t('nav.logs') },
    ],
  },
])

function itemAllowed(item) {
  if (auth.isSuperAdmin) return true
  const mod = item.module || ROUTE_MODULE[item.name]
  if (!mod) return true
  return auth.can(mod, 'read')
}

const visibleSections = computed(() =>
  sections.value
    .map((s) => ({
      ...s,
      items: (s.items || []).filter(itemAllowed),
    }))
    .filter((s) => s.items && s.items.length > 0),
)

function itemTo(item) {
  if (item.query) return { name: item.name, query: item.query }
  // Root settings must not keep stale ?tab= from other links
  if (item.settingsRoot) return { name: item.name, query: {} }
  return { name: item.name }
}

function isItemActive(item) {
  if (item.settingsRoot) {
    // Active only on general-ish settings (no payment/zego deep-link tabs).
    if (route.name !== 'settings') return false
    const tab = String(route.query.tab || '')
    return !tab || tab === 'general' || tab === 'economy' || tab === 'account' || tab === 'features' || tab === 'admins' || tab === 'moderation' || tab === 'other'
  }
  if (item.query?.tab) {
    return route.name === 'settings' && String(route.query.tab || '') === item.query.tab
  }
  if (item.also?.includes(route.name)) return true
  return route.name === item.name
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
  try {
    const { data } = await settingsApi.get()
    femaleVerifyEnabled.value = readFeatureFlag(data?.data ?? data)
  } catch {
    femaleVerifyEnabled.value = false
  }
}

onMounted(() => {
  loadFeatureFlags()
  try {
    localStorage.removeItem('jeho_admin_nav_collapse')
  } catch {
    /* ignore */
  }
})

watch(
  () => route.name,
  (name, prev) => {
    if (prev === 'settings' || name === 'settings' || name === 'gender-verifications') {
      loadFeatureFlags()
    }
  },
)
</script>
