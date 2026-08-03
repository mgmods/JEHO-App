<template>
  <aside class="sidebar" :class="{ open }">
    <div class="sidebar-brand">
      <img :src="logoUrl" :alt="t('app.brand')" />
      <div>
        <div class="brand-text">{{ t('app.brand') }}</div>
        <div class="brand-sub">Command Center</div>
      </div>
    </div>

    <nav class="sidebar-nav">
      <div
        v-for="section in visibleSections"
        :key="section.id"
        class="nav-section"
        :class="{ 'is-collapsed': collapsed[section.id] }"
      >
        <button
          type="button"
          class="nav-section-toggle"
          @click="toggleSection(section.id)"
        >
          <span>{{ section.label }}</span>
          <i class="bi bi-chevron-down"></i>
        </button>
        <div class="nav-section-body">
          <div class="nav-section-inner">
            <RouterLink
              v-for="item in section.items"
              :key="item.name + String(item.query?.tab || '')"
              class="sidebar-link"
              :class="{ active: isItemActive(item) }"
              :to="itemTo(item)"
              @click="close"
            >
              <i :class="['bi', item.icon]"></i>
              <span>{{ item.label }}</span>
            </RouterLink>
          </div>
        </div>
      </div>
    </nav>
  </aside>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { settingsApi } from '@/api'
import brandLogo from '@/assets/brand/logo.png'

defineProps({
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])
const route = useRoute()
const { t } = useI18n()
const logoUrl = brandLogo
const femaleVerifyEnabled = ref(false)
const COLLAPSE_KEY = 'jeho_admin_nav_collapse'

const collapsed = reactive({})

function loadCollapse() {
  try {
    const raw = JSON.parse(localStorage.getItem(COLLAPSE_KEY) || '{}')
    Object.assign(collapsed, raw)
  } catch {
    /* ignore */
  }
}

function persistCollapse() {
  try {
    localStorage.setItem(COLLAPSE_KEY, JSON.stringify({ ...collapsed }))
  } catch {
    /* ignore */
  }
}

function toggleSection(id) {
  collapsed[id] = !collapsed[id]
  persistCollapse()
}

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
      { name: 'rooms', icon: 'bi-door-open', label: t('nav.rooms') },
      { name: 'settings', query: { tab: 'zego' }, icon: 'bi-broadcast-pin', label: t('nav.zegoSettings') },
    ],
  },
  {
    id: 'money',
    label: t('nav.monetization'),
    items: [
      { name: 'gifts', icon: 'bi-gift', label: t('nav.gifts') },
      { name: 'coins', icon: 'bi-coin', label: t('nav.coins') },
      { name: 'offers', icon: 'bi-box-seam', label: t('nav.offers') },
      { name: 'promos', icon: 'bi-stars', label: t('nav.promos') },
      { name: 'wallet', icon: 'bi-wallet2', label: t('nav.wallet') },
      { name: 'settings', query: { tab: 'payment' }, icon: 'bi-credit-card', label: t('nav.paymentSettings') },
      { name: 'recharge-agents', icon: 'bi-person-badge', label: t('nav.rechargeAgents') },
      { name: 'vip', icon: 'bi-diamond', label: t('nav.vip') },
      { name: 'vanity-ids', icon: 'bi-hash', label: t('nav.vanityIds') },
    ],
  },
  {
    id: 'engage',
    label: t('nav.engagement'),
    items: [
      { name: 'cosmetics', icon: 'bi-palette2', label: t('nav.cosmetics') },
      { name: 'banners', icon: 'bi-images', label: t('nav.banners') },
      { name: 'lucky-boxes', icon: 'bi-box2-heart', label: t('nav.luckyBoxes') },
      { name: 'tasks', icon: 'bi-list-check', label: t('nav.tasks') },
      { name: 'host-target', icon: 'bi-bullseye', label: t('nav.hostTarget') },
      { name: 'contests', icon: 'bi-trophy', label: t('nav.contests') },
      { name: 'ranking', icon: 'bi-bar-chart', label: t('nav.ranking') },
      { name: 'room-cup', icon: 'bi-trophy-fill', label: t('nav.roomCup') },
      { name: 'games', icon: 'bi-controller', label: t('nav.games') },
      { name: 'game-ads', icon: 'bi-badge-ad', label: t('nav.gameAds') },
      { name: 'drama', icon: 'bi-film', label: t('nav.drama') },
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

const visibleSections = computed(() =>
  sections.value.filter((s) => s.items && s.items.length > 0),
)

function itemTo(item) {
  if (item.query) return { name: item.name, query: item.query }
  return { name: item.name }
}

function isItemActive(item) {
  if (item.settingsRoot) {
    return route.name === 'settings'
      && !route.query.tab
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
  const { data } = await settingsApi.get()
  femaleVerifyEnabled.value = readFeatureFlag(data?.data ?? data)
}

onMounted(() => {
  loadCollapse()
  loadFeatureFlags()
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
