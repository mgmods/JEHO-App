<template>
  <div class="settings-page">
    <PageHeader :title="t('settings.title')" :subtitle="t('settings.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="loading" @click="reloadActive">
          {{ t('common.reload') }}
        </button>
        <button
          v-if="isCoreTab"
          class="btn btn-aurora btn-sm"
          type="button"
          :disabled="saving || loading"
          @click="save"
        >
          <span v-if="saving" class="spinner-border spinner-border-sm me-1" />
          {{ t('settings.saveSettings') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="settings-shell">
      <nav class="settings-rail" aria-label="Settings sections">
        <button
          v-for="tab in settingTabs"
          :key="tab.id"
          type="button"
          class="settings-rail-item"
          :class="{ active: settingsTab === tab.id }"
          @click="selectTab(tab.id)"
        >
          <i class="bi" :class="tab.icon" />
          <span>{{ tab.label }}</span>
        </button>
      </nav>

      <div class="settings-main">
        <LoadingSpinner v-if="loading && isCoreTab" />

        <template v-else-if="settingsTab === 'payment'">
          <PaymentSettingsPanel />
          <FourthwallPaymentPanel />
          <ShamCashPaymentPanel />
        </template>

        <template v-else-if="settingsTab === 'zego'">
          <ZegoSettingsPanel />
        </template>

        <template v-else-if="settingsTab === 'account'">
          <section class="settings-card">
            <h3 class="settings-card-title">{{ t('settings.accountTitle') }}</h3>
            <p class="form-text mb-3">{{ t('settings.accountHint') }}</p>
            <div class="row g-3">
              <div class="col-md-6">
                <label class="form-label">{{ t('settings.accountEmail') }}</label>
                <input v-model="accountForm.email" type="email" class="form-control" dir="ltr" autocomplete="username" />
              </div>
              <div class="col-md-6">
                <label class="form-label">{{ t('settings.accountCurrentPassword') }}</label>
                <input
                  v-model="accountForm.currentPassword"
                  type="password"
                  class="form-control"
                  dir="ltr"
                  autocomplete="current-password"
                />
              </div>
              <div class="col-md-6">
                <label class="form-label">{{ t('settings.accountNewPassword') }}</label>
                <input
                  v-model="accountForm.newPassword"
                  type="password"
                  class="form-control"
                  dir="ltr"
                  autocomplete="new-password"
                  placeholder="••••••••"
                />
              </div>
              <div class="col-md-6">
                <label class="form-label">{{ t('settings.accountConfirmPassword') }}</label>
                <input
                  v-model="accountForm.confirmPassword"
                  type="password"
                  class="form-control"
                  dir="ltr"
                  autocomplete="new-password"
                  placeholder="••••••••"
                />
              </div>
            </div>
            <div class="mt-3">
              <button
                class="btn btn-aurora"
                type="button"
                :disabled="accountSaving"
                @click="saveAccount"
              >
                <span v-if="accountSaving" class="spinner-border spinner-border-sm me-1" />
                {{ t('settings.accountSave') }}
              </button>
            </div>
          </section>
        </template>

        <form v-else class="settings-form" @submit.prevent="save">
          <!-- GENERAL -->
          <template v-if="settingsTab === 'general'">
            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.general') }}</h3>
              <div class="row g-3">
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.dashboardLanguage') }}</label>
                  <select v-model="dashboardLocale" class="form-select" @change="setDashboardLocale(dashboardLocale)">
                    <option value="en">{{ t('app.english') }}</option>
                    <option value="ar">{{ t('app.arabic') }}</option>
                  </select>
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.defaultLocale') }}</label>
                  <select v-model="form.defaultLocale" class="form-select">
                    <option value="en">{{ t('app.english') }}</option>
                    <option value="ar">{{ t('app.arabic') }}</option>
                  </select>
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.appName') }}</label>
                  <input v-model="form.appName" class="form-control" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.supportEmail') }}</label>
                  <input v-model="form.supportEmail" type="email" class="form-control" dir="ltr" />
                </div>
              </div>
            </section>

            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.supportChannels') }}</h3>
              <div class="row g-3">
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.supportWhatsapp') }}</label>
                  <input v-model="form.supportWhatsapp" class="form-control" placeholder="+905xxxxxxxxx" dir="ltr" />
                  <div class="form-text">{{ t('settings.supportWhatsappHint') }}</div>
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.supportTelegram') }}</label>
                  <input v-model="form.supportTelegram" class="form-control" placeholder="@username" dir="ltr" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.supportInstagram') }}</label>
                  <input v-model="form.supportInstagram" class="form-control" placeholder="@username" dir="ltr" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.supportPhone') }}</label>
                  <input v-model="form.supportPhone" class="form-control" placeholder="+90..." dir="ltr" />
                </div>
              </div>
            </section>

            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.platformToggles') }}</h3>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.maintenanceMode') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.maintenanceModeHint') }}</div>
                </div>
                <input v-model="form.maintenanceMode" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.allowRegistrations') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.allowRegistrationsHint') }}</div>
                </div>
                <input v-model="form.registrationEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
            </section>
          </template>

          <!-- ECONOMY -->
          <template v-else-if="settingsTab === 'economy'">
            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.economy') }}</h3>
              <div class="row g-3">
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.coinUsdRate') }}</label>
                  <input v-model.number="form.coinToUsd" type="number" step="0.0001" min="0" class="form-control" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.minWithdraw') }}</label>
                  <input v-model.number="form.minWithdraw" type="number" step="0.01" min="0" class="form-control" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.hostGiftShare') }}</label>
                  <input v-model.number="form.hostGiftShare" type="number" min="0" max="100" class="form-control" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.agencyCommission') }}</label>
                  <input v-model.number="form.agencyCommission" type="number" min="0" max="100" class="form-control" />
                </div>
              </div>
            </section>

            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.roomEconomy') }}</h3>
              <div class="row g-3">
                <div class="col-md-4">
                  <label class="form-label">{{ t('settings.supporterMinCoins') }}</label>
                  <input v-model.number="form.supporterMinCoins" type="number" min="0" class="form-control" />
                </div>
                <div class="col-md-4">
                  <label class="form-label">{{ t('settings.legendaryMinCoins') }}</label>
                  <input v-model.number="form.legendaryMinCoins" type="number" min="0" class="form-control" />
                </div>
                <div class="col-md-4">
                  <label class="form-label">{{ t('settings.gameWinReward') }}</label>
                  <input v-model.number="form.gameWinRewardCoins" type="number" min="0" class="form-control" />
                  <div class="form-text">{{ t('settings.gameWinRewardHint') }}</div>
                </div>
              </div>
            </section>
          </template>

          <!-- FEATURES -->
          <template v-else-if="settingsTab === 'features'">
            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.appFeatures') }}</h3>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.luckyBoxesEnabled') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.luckyBoxesHint') }}</div>
                </div>
                <input v-model="form.luckyBoxEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.femaleOnlyVoiceHosts') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.femaleOnlyVoiceHostsHint') }}</div>
                </div>
                <input v-model="form.femaleOnlyVoiceHosts" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.genderAutoAccept') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.genderAutoAcceptHint') }}</div>
                </div>
                <input v-model="form.genderVerificationAutoAccept" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.micWithoutHostApproval') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.micWithoutHostApprovalHint') }}</div>
                </div>
                <input v-model="form.micWithoutHostApproval" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.giftSoundsEnabled') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.giftSoundsEnabledHint') }}</div>
                </div>
                <input v-model="form.giftSoundsEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.requireGiftToDm') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.requireGiftToDmHint') }}</div>
                </div>
                <input v-model="form.requireGiftToDm" class="form-check-input" type="checkbox" role="switch" />
              </label>
            </section>
          </template>

          <!-- MODERATION / CONDITIONS -->
          <template v-else-if="settingsTab === 'moderation'">
            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.moderation') }}</h3>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.autoModeration') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.autoModerationHint') }}</div>
                </div>
                <input v-model="form.autoModeration" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.liveNsfwEnabled') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.nsfwHint') }}</div>
                </div>
                <input v-model="form.liveNsfwEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.chatPromoFilter') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.chatPromoFilterHint') }}</div>
                </div>
                <input v-model="form.chatPromoFilterEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.chatPromoKick') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.chatPromoKickHint') }}</div>
                </div>
                <input v-model="form.chatPromoKickEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.requireStreamReview') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.requireStreamReviewHint') }}</div>
                </div>
                <input v-model="form.requireStreamReview" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <div class="mt-3">
                <label class="form-label">{{ t('settings.chatBlockedKeywords') }}</label>
                <textarea
                  v-model="form.chatBlockedExtraKeywords"
                  class="form-control"
                  rows="3"
                  :placeholder="t('settings.chatBlockedKeywordsHint')"
                />
              </div>
            </section>

            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.nsfwRules') }}</h3>
              <div class="row g-3">
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.reportSla') }}</label>
                  <input v-model.number="form.reportSlaHours" type="number" min="1" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwConfidence') }}</label>
                  <input v-model.number="form.liveNsfwConfidence" type="number" min="0.5" max="0.99" step="0.01" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwConsecutive') }}</label>
                  <input v-model.number="form.liveNsfwConsecutive" type="number" min="2" max="8" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwStreamBanHours') }}</label>
                  <input v-model.number="form.liveNsfwStreamBanHours" type="number" min="1" max="720" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwWarnStrikes') }}</label>
                  <input v-model.number="form.liveNsfwWarnStrikes" type="number" min="1" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwMuteStrikes') }}</label>
                  <input v-model.number="form.liveNsfwMuteStrikes" type="number" min="1" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwStreamBanStrikes') }}</label>
                  <input v-model.number="form.liveNsfwStreamBanStrikes" type="number" min="1" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwPermBanStrikes') }}</label>
                  <input v-model.number="form.liveNsfwPermBanStrikes" type="number" min="1" class="form-control" />
                </div>
              </div>
            </section>
          </template>

          <!-- OTHER -->
          <template v-else-if="settingsTab === 'other'">
            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.roomRules') }}</h3>
              <label class="form-label">{{ t('settings.roomRulesText') }}</label>
              <textarea v-model="form.roomRulesText" class="form-control" rows="3" maxlength="200" />
              <div class="form-text">{{ t('settings.roomRulesHint') }}</div>
            </section>

            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.quickLinks') }}</h3>
              <div class="settings-links">
                <RouterLink class="settings-link" :to="{ name: 'settings', query: { tab: 'payment' } }">
                  <i class="bi bi-credit-card" /> {{ t('nav.paymentSettings') }}
                </RouterLink>
                <RouterLink class="settings-link" :to="{ name: 'settings', query: { tab: 'zego' } }">
                  <i class="bi bi-broadcast-pin" /> {{ t('nav.zegoSettings') }}
                </RouterLink>
                <RouterLink class="settings-link" :to="{ name: 'vip' }">
                  <i class="bi bi-diamond" /> {{ t('nav.vip') }}
                </RouterLink>
                <RouterLink class="settings-link" :to="{ name: 'coins' }">
                  <i class="bi bi-coin" /> {{ t('nav.coins') }}
                </RouterLink>
              </div>
            </section>
          </template>

          <div v-if="isCoreTab" class="settings-footer">
            <button class="btn btn-aurora" type="submit" :disabled="saving">
              <span v-if="saving" class="spinner-border spinner-border-sm me-2" />
              {{ t('settings.saveSettings') }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { settingsApi, authApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import PaymentSettingsPanel from '@/components/settings/PaymentSettingsPanel.vue'
import FourthwallPaymentPanel from '@/components/settings/FourthwallPaymentPanel.vue'
import ShamCashPaymentPanel from '@/components/settings/ShamCashPaymentPanel.vue'
import ZegoSettingsPanel from '@/components/settings/ZegoSettingsPanel.vue'
import { setDashboardLocale } from '@/i18n'
import { readDashboardLocale } from '@/utils/locale'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const dashboardLocale = ref(readDashboardLocale())
const settingsTab = ref('general')
const CORE_TABS = new Set(['general', 'economy', 'features', 'moderation', 'other'])
const isCoreTab = computed(() => CORE_TABS.has(settingsTab.value))

const settingTabs = computed(() => [
  { id: 'general', label: t('settings.tabGeneral'), icon: 'bi-sliders' },
  { id: 'economy', label: t('settings.tabEconomy'), icon: 'bi-cash-coin' },
  { id: 'features', label: t('settings.tabFeatures'), icon: 'bi-toggles' },
  { id: 'moderation', label: t('settings.tabModeration'), icon: 'bi-shield-check' },
  { id: 'payment', label: t('settings.tabPayment'), icon: 'bi-credit-card' },
  { id: 'zego', label: t('settings.tabZego'), icon: 'bi-broadcast-pin' },
  { id: 'account', label: t('settings.tabAccount'), icon: 'bi-person-lock' },
  { id: 'other', label: t('settings.tabOther'), icon: 'bi-three-dots' },
])

const loading = ref(false)
const saving = ref(false)
const accountSaving = ref(false)
const error = ref('')
const success = ref('')

const accountForm = reactive({
  email: '',
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function fillAccountFromAuth() {
  accountForm.email = String(auth.user?.email || '').trim()
  accountForm.currentPassword = ''
  accountForm.newPassword = ''
  accountForm.confirmPassword = ''
}

async function saveAccount() {
  error.value = ''
  success.value = ''
  const email = String(accountForm.email || '').trim().toLowerCase()
  const currentPassword = String(accountForm.currentPassword || '')
  const newPassword = String(accountForm.newPassword || '')
  const confirmPassword = String(accountForm.confirmPassword || '')
  if (!currentPassword) {
    error.value = t('settings.accountCurrentPassword')
    toast().danger(error.value)
    return
  }
  const currentEmail = String(auth.user?.email || '').trim().toLowerCase()
  const emailChanged = email && email !== currentEmail
  if (!emailChanged && !newPassword) {
    error.value = t('settings.accountNeedChange')
    toast().danger(error.value)
    return
  }
  if (newPassword && newPassword.length < 8) {
    error.value = t('settings.accountPasswordShort')
    toast().danger(error.value)
    return
  }
  if (newPassword && newPassword !== confirmPassword) {
    error.value = t('settings.accountPasswordMismatch')
    toast().danger(error.value)
    return
  }
  accountSaving.value = true
  const payload = { currentPassword }
  if (emailChanged) payload.newEmail = email
  if (newPassword) payload.newPassword = newPassword
  const { data, error: err } = await authApi.updateCredentials(payload)
  accountSaving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const nextEmail = data?.email || email || currentEmail
  if (auth.user) {
    auth.setSession(auth.token, { ...auth.user, email: nextEmail })
  }
  fillAccountFromAuth()
  success.value = t('settings.accountUpdated')
  toast().success(success.value)
}
const form = reactive({
  appName: 'JEHO CHAT',
  supportEmail: 'support@adnova.bbs.tr',
  supportWhatsapp: '',
  supportTelegram: '',
  supportInstagram: '',
  supportPhone: '',
  defaultLocale: 'en',
  maintenanceMode: false,
  registrationEnabled: true,
  coinToUsd: 0.01,
  minWithdraw: 50,
  hostGiftShare: 60,
  agencyCommission: 20,
  supporterMinCoins: 10000,
  legendaryMinCoins: 50000,
  gameWinRewardCoins: 100,
  luckyBoxEnabled: true,
  femaleOnlyVoiceHosts: false,
  genderVerificationAutoAccept: true,
  micWithoutHostApproval: true,
  giftSoundsEnabled: true,
  requireGiftToDm: false,
  roomRulesText: 'احترموا القوانين واستمتعوا بالجلسة',
  autoModeration: true,
  liveNsfwEnabled: true,
  chatPromoFilterEnabled: true,
  chatPromoKickEnabled: true,
  chatBlockedExtraKeywords: '',
  requireStreamReview: false,
  reportSlaHours: 24,
  liveNsfwConfidence: 0.78,
  liveNsfwConsecutive: 3,
  liveNsfwStreamBanHours: 24,
  liveNsfwWarnStrikes: 1,
  liveNsfwMuteStrikes: 2,
  liveNsfwStreamBanStrikes: 3,
  liveNsfwPermBanStrikes: 5,
})

function normalizeAppName(raw) {
  const name = String(raw ?? '').trim()
  if (!name) return 'JEHO CHAT'
  const lower = name.toLowerCase()
  if (
    name.includes('همس')
    || lower.includes('auralive')
    || lower.includes('aura live')
    || lower.includes('jeho live')
    || name.includes('جيرو')
    || lower.includes('giro')
  ) {
    return 'JEHO CHAT'
  }
  return name
}

function selectTab(id) {
  settingsTab.value = id
  router.replace({ name: 'settings', query: { tab: id } })
  if (id === 'account') fillAccountFromAuth()
}

function syncTabFromRoute() {
  const raw = String(route.query.tab || 'general')
  const allowed = new Set(settingTabs.value.map((x) => x.id))
  settingsTab.value = allowed.has(raw) ? raw : 'general'
  if (settingsTab.value === 'account') fillAccountFromAuth()
}

watch(() => route.query.tab, syncTabFromRoute)

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await settingsApi.get()
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const raw = data?.settings || data?.data || data || {}
  const map = {}
  if (Array.isArray(raw)) {
    for (const row of raw) {
      if (row?.key != null) map[row.key] = row.value
    }
  } else if (raw && typeof raw === 'object') {
    Object.assign(map, raw)
  }
  const bool = (v, fallback) => {
    if (v === undefined || v === null || v === '') return fallback
    if (typeof v === 'boolean') return v
    const s = String(v).toLowerCase()
    return s === '1' || s === 'true' || s === 'yes'
  }
  const num = (v, fallback) => {
    const n = Number(v)
    return Number.isFinite(n) ? n : fallback
  }
  Object.assign(form, {
    appName: normalizeAppName(map.appName ?? map.app_name ?? form.appName),
    supportEmail: map.supportEmail ?? map.support_email ?? form.supportEmail,
    supportWhatsapp: map.support_whatsapp ?? map.supportWhatsapp ?? form.supportWhatsapp,
    supportTelegram: map.support_telegram ?? map.supportTelegram ?? form.supportTelegram,
    supportInstagram: map.support_instagram ?? map.supportInstagram ?? form.supportInstagram,
    supportPhone: map.support_phone ?? map.supportPhone ?? form.supportPhone,
    defaultLocale: map.defaultLocale ?? map.default_locale ?? form.defaultLocale,
    maintenanceMode: bool(map.maintenanceMode ?? map.maintenance_mode, form.maintenanceMode),
    registrationEnabled: bool(map.registrationEnabled ?? map.registration_enabled, form.registrationEnabled),
    coinToUsd: num(map.coinToUsd ?? map.coin_to_usd, form.coinToUsd),
    minWithdraw: num(map.minWithdraw ?? map.min_withdraw, form.minWithdraw),
    hostGiftShare: num(map.hostGiftShare ?? map.host_gift_share, form.hostGiftShare),
    agencyCommission: num(
      map.agencyCommission ?? map.agency_default_commission_percent ?? map.agency_commission,
      form.agencyCommission,
    ),
    supporterMinCoins: num(map['room.supporter_min_coins'] ?? map.supporterMinCoins, form.supporterMinCoins),
    legendaryMinCoins: num(map['room.legendary_min_coins'] ?? map.legendaryMinCoins, form.legendaryMinCoins),
    gameWinRewardCoins: num(
      map['games.win_reward_coins'] ?? map['game.win_reward_coins'] ?? map.gameWinRewardCoins,
      form.gameWinRewardCoins,
    ),
    luckyBoxEnabled: bool(map['lucky_box.enabled'] ?? map.luckyBoxEnabled, form.luckyBoxEnabled),
    femaleOnlyVoiceHosts: bool(
      map['features.female_only_voice_hosts'] ?? map.femaleOnlyVoiceHosts,
      form.femaleOnlyVoiceHosts,
    ),
    genderVerificationAutoAccept: bool(
      map['gender_verification.auto_accept'] ?? map.genderVerificationAutoAccept,
      form.genderVerificationAutoAccept,
    ),
    micWithoutHostApproval: bool(
      map['rooms.mic_without_host_approval'] ?? map.micWithoutHostApproval,
      form.micWithoutHostApproval,
    ),
    giftSoundsEnabled: bool(
      map['gifts.sound_enabled'] ?? map.giftSoundsEnabled,
      form.giftSoundsEnabled,
    ),
    requireGiftToDm: bool(
      map['chat.requireGiftToDm'] ?? map.requireGiftToDm,
      form.requireGiftToDm,
    ),
    roomRulesText: String(map['rooms.rules_text'] ?? map.roomRulesText ?? form.roomRulesText),
    autoModeration: bool(map.autoModeration ?? map.auto_moderation, form.autoModeration),
    liveNsfwEnabled: bool(map.live_nsfw_enabled ?? map.liveNsfwEnabled, form.liveNsfwEnabled),
    chatPromoFilterEnabled: bool(
      map.chat_promo_filter_enabled ?? map.chatPromoFilterEnabled,
      form.chatPromoFilterEnabled,
    ),
    chatPromoKickEnabled: bool(
      map.chat_promo_kick_enabled ?? map.chatPromoKickEnabled,
      form.chatPromoKickEnabled,
    ),
    chatBlockedExtraKeywords: String(
      map.chat_blocked_extra_keywords ?? map.chatBlockedExtraKeywords ?? form.chatBlockedExtraKeywords,
    ),
    requireStreamReview: bool(map.requireStreamReview ?? map.require_stream_review, form.requireStreamReview),
    reportSlaHours: num(map.reportSlaHours ?? map.report_sla_hours, form.reportSlaHours),
    liveNsfwConfidence: num(map.live_nsfw_confidence ?? map.liveNsfwConfidence, form.liveNsfwConfidence),
    liveNsfwConsecutive: num(map.live_nsfw_consecutive ?? map.liveNsfwConsecutive, form.liveNsfwConsecutive),
    liveNsfwStreamBanHours: num(map.live_nsfw_stream_ban_hours ?? map.liveNsfwStreamBanHours, form.liveNsfwStreamBanHours),
    liveNsfwWarnStrikes: num(map.live_nsfw_warn_strikes ?? map.liveNsfwWarnStrikes, form.liveNsfwWarnStrikes),
    liveNsfwMuteStrikes: num(map.live_nsfw_mute_strikes ?? map.liveNsfwMuteStrikes, form.liveNsfwMuteStrikes),
    liveNsfwStreamBanStrikes: num(map.live_nsfw_stream_ban_strikes ?? map.liveNsfwStreamBanStrikes, form.liveNsfwStreamBanStrikes),
    liveNsfwPermBanStrikes: num(map.live_nsfw_perm_ban_strikes ?? map.liveNsfwPermBanStrikes, form.liveNsfwPermBanStrikes),
  })
}

function reloadActive() {
  if (isCoreTab.value) load()
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    appName: String(form.appName ?? ''),
    supportEmail: String(form.supportEmail ?? ''),
    support_whatsapp: String(form.supportWhatsapp ?? ''),
    support_telegram: String(form.supportTelegram ?? ''),
    support_instagram: String(form.supportInstagram ?? ''),
    support_phone: String(form.supportPhone ?? ''),
    defaultLocale: String(form.defaultLocale ?? 'ar'),
    maintenanceMode: String(!!form.maintenanceMode),
    registrationEnabled: String(!!form.registrationEnabled),
    coinToUsd: String(form.coinToUsd ?? ''),
    minWithdraw: String(form.minWithdraw ?? ''),
    hostGiftShare: String(form.hostGiftShare ?? ''),
    agencyCommission: String(form.agencyCommission ?? ''),
    agency_default_commission_percent: String(form.agencyCommission ?? ''),
    'room.supporter_min_coins': String(form.supporterMinCoins ?? 10000),
    'room.legendary_min_coins': String(form.legendaryMinCoins ?? 50000),
    'games.win_reward_coins': String(form.gameWinRewardCoins ?? 100),
    'lucky_box.enabled': String(!!form.luckyBoxEnabled),
    'features.female_only_voice_hosts': String(!!form.femaleOnlyVoiceHosts),
    'gender_verification.auto_accept': String(!!form.genderVerificationAutoAccept),
    'rooms.mic_without_host_approval': String(!!form.micWithoutHostApproval),
    'gifts.sound_enabled': String(!!form.giftSoundsEnabled),
    'chat.requireGiftToDm': String(!!form.requireGiftToDm),
    'rooms.rules_text': String(form.roomRulesText ?? ''),
    autoModeration: String(!!form.autoModeration),
    auto_moderation: String(!!form.autoModeration),
    live_nsfw_enabled: String(!!form.liveNsfwEnabled),
    chat_promo_filter_enabled: String(!!form.chatPromoFilterEnabled),
    chat_promo_kick_enabled: String(!!form.chatPromoKickEnabled),
    chat_blocked_extra_keywords: String(form.chatBlockedExtraKeywords ?? ''),
    requireStreamReview: String(!!form.requireStreamReview),
    reportSlaHours: String(form.reportSlaHours ?? ''),
    live_nsfw_confidence: String(form.liveNsfwConfidence ?? 0.78),
    live_nsfw_consecutive: String(form.liveNsfwConsecutive ?? 3),
    live_nsfw_stream_ban_hours: String(form.liveNsfwStreamBanHours ?? 24),
    live_nsfw_warn_strikes: String(form.liveNsfwWarnStrikes ?? 1),
    live_nsfw_mute_strikes: String(form.liveNsfwMuteStrikes ?? 2),
    live_nsfw_stream_ban_strikes: String(form.liveNsfwStreamBanStrikes ?? 3),
    live_nsfw_perm_ban_strikes: String(form.liveNsfwPermBanStrikes ?? 5),
  }
  const { error: err } = await settingsApi.update(payload)
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = t('common.saved')
  toast().success(success.value)
}

onMounted(async () => {
  syncTabFromRoute()
  if (!auth.user?.email) {
    await auth.fetchMe()
  }
  fillAccountFromAuth()
  load()
})
</script>
