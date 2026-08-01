<template>
  <div>
    <AlertMessage v-if="error" :message="error" type="warning" class="mb-3" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" class="mb-3" @dismiss="success = ''" />
    <LoadingSpinner v-if="loading" />

    <form v-else class="row g-3" @submit.prevent="save">
      <div class="col-12">
        <div class="settings-card">
          <h3 class="settings-card-title">{{ t('zegoSettings.status') }}</h3>
          <div class="row g-2 small">
            <div class="col-md-4">
              <span class="text-secondary">AppID:</span>
              <span class="ms-1 fw-medium">
                {{ masked.appIdConfigured ? masked.appId : t('zegoSettings.notConfigured') }}
              </span>
            </div>
            <div class="col-md-4">
              <span class="text-secondary">AppSign:</span>
              <span class="ms-1 fw-medium">
                {{ masked.appSignConfigured ? t('zegoSettings.configured') : t('zegoSettings.notConfigured') }}
                <span v-if="masked.appSignHint" class="text-secondary">(…{{ masked.appSignHint }})</span>
              </span>
            </div>
            <div class="col-md-4">
              <span class="text-secondary">ServerSecret:</span>
              <span class="ms-1 fw-medium">
                {{ masked.serverSecretConfigured ? t('zegoSettings.configured') : t('zegoSettings.notConfigured') }}
              </span>
            </div>
            <div v-if="masked.configSource && masked.configSource !== 'none'" class="col-12 mt-1">
              <span class="badge bg-secondary-subtle text-secondary-emphasis">
                {{ t('zegoSettings.source') }}: {{ masked.configSource }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="settings-card h-100">
          <div class="d-flex justify-content-between align-items-center mb-3">
            <h3 class="settings-card-title mb-0">{{ t('zegoSettings.credentials') }}</h3>
            <button class="btn btn-sm btn-ghost" type="button" :disabled="revealing" @click="reveal">
              {{ showingKeys ? t('zegoSettings.hideKeys') : t('zegoSettings.showKeys') }}
            </button>
          </div>
          <div class="mb-3">
            <label class="form-label">AppID</label>
            <input v-model="form.appId" class="form-control" inputmode="numeric" autocomplete="off" />
          </div>
          <div class="mb-3">
            <label class="form-label">AppSign</label>
            <input
              v-model="form.appSign"
              class="form-control"
              :type="showingKeys ? 'text' : 'password'"
              autocomplete="off"
              :placeholder="masked.appSignConfigured ? '••••••••' : ''"
            />
            <div class="form-text">{{ t('zegoSettings.appSignHint') }}</div>
          </div>
          <div class="mb-0">
            <label class="form-label">ServerSecret</label>
            <input
              v-model="form.serverSecret"
              class="form-control"
              :type="showingKeys ? 'text' : 'password'"
              autocomplete="off"
              :placeholder="masked.serverSecretConfigured ? '••••••••' : ''"
            />
            <div class="form-text">{{ t('zegoSettings.serverSecretHint') }}</div>
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="settings-card h-100">
          <h3 class="settings-card-title">{{ t('zegoSettings.network') }}</h3>
          <div class="mb-3">
            <label class="form-label">WS URL</label>
            <input v-model="form.wsUrl" class="form-control" placeholder="wss://…" dir="ltr" />
          </div>
          <div class="mb-0">
            <label class="form-label">WS URL Backup</label>
            <input v-model="form.wsUrlBak" class="form-control" placeholder="wss://…" dir="ltr" />
          </div>
        </div>
      </div>

      <div class="col-12">
        <button class="btn btn-aurora" type="submit" :disabled="saving">
          <span v-if="saving" class="spinner-border spinner-border-sm me-2"></span>
          {{ saving ? t('app.saving') : t('app.save') }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { zegoSettingsApi } from '@/api'
import { toast } from '@/composables/useToast'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const revealing = ref(false)
const showingKeys = ref(false)
const error = ref('')
const success = ref('')
const masked = reactive({
  appId: '',
  appIdConfigured: false,
  appSignConfigured: false,
  serverSecretConfigured: false,
  appSignHint: null,
  serverSecretHint: null,
  wsUrl: '',
  wsUrlBak: '',
  updatedAt: null,
  configSource: 'none',
})
const form = reactive({
  appId: '',
  appSign: '',
  serverSecret: '',
  wsUrl: '',
  wsUrlBak: '',
})

function applyMasked(data) {
  Object.assign(masked, data || {})
  form.appId = data?.appId || ''
  form.wsUrl = data?.wsUrl || ''
  form.wsUrlBak = data?.wsUrlBak || ''
  if (!showingKeys.value) {
    form.appSign = ''
    form.serverSecret = ''
  }
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await zegoSettingsApi.get()
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  applyMasked(data?.data || data)
}

async function reveal() {
  if (showingKeys.value) {
    showingKeys.value = false
    form.appSign = ''
    form.serverSecret = ''
    return
  }
  revealing.value = true
  error.value = ''
  const { data, error: err } = await zegoSettingsApi.reveal()
  revealing.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const revealed = data?.data || data || {}
  form.appId = revealed.appId || form.appId
  form.appSign = revealed.appSign || ''
  form.serverSecret = revealed.serverSecret || ''
  form.wsUrl = revealed.wsUrl || form.wsUrl
  form.wsUrlBak = revealed.wsUrlBak || form.wsUrlBak
  showingKeys.value = true
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    appId: form.appId,
    wsUrl: form.wsUrl,
    wsUrlBak: form.wsUrlBak,
  }
  if (form.appSign.trim()) payload.appSign = form.appSign.trim()
  if (form.serverSecret.trim()) payload.serverSecret = form.serverSecret.trim()
  const { data, error: err } = await zegoSettingsApi.update(payload)
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  applyMasked(data?.data || data)
  showingKeys.value = false
  form.appSign = ''
  form.serverSecret = ''
  success.value = t('common.saved')
  toast().success(success.value)
}

onMounted(load)
</script>
