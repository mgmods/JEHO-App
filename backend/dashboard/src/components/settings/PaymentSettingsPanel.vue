<template>
  <div>
    <AlertMessage v-if="error" :message="error" type="warning" class="mb-3" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" class="mb-3" @dismiss="success = ''" />
    <AlertMessage
      v-if="testMessage"
      :message="testMessage"
      :type="testOk ? 'success' : 'warning'"
      class="mb-3"
      @dismiss="testMessage = ''"
    />
    <LoadingSpinner v-if="loading" />

    <form v-else class="row g-3" @submit.prevent="save">
      <div class="col-12">
        <div class="settings-card">
          <h3 class="settings-card-title">{{ t('paymentSettings.status') }}</h3>
          <div class="row g-2 small">
            <div class="col-md-4">
              <span class="text-secondary">{{ t('paymentSettings.apiKey') }}:</span>
              <span class="ms-1 fw-medium">
                {{ masked.apiKeyConfigured ? t('paymentSettings.configured') : t('paymentSettings.notConfigured') }}
                <span v-if="masked.apiKeyHint" class="text-secondary">(…{{ masked.apiKeyHint }})</span>
              </span>
            </div>
            <div class="col-md-4">
              <span class="text-secondary">{{ t('paymentSettings.secretKey') }}:</span>
              <span class="ms-1 fw-medium">
                {{ masked.secretKeyConfigured ? t('paymentSettings.configured') : t('paymentSettings.notConfigured') }}
              </span>
            </div>
            <div class="col-md-4">
              <span class="text-secondary">{{ t('paymentSettings.baseUrl') }}:</span>
              <span class="ms-1 fw-medium">{{ masked.baseUrl || '—' }}</span>
            </div>
            <div v-if="masked.configSource && masked.configSource !== 'none'" class="col-12 mt-1">
              <span class="badge bg-secondary-subtle text-secondary-emphasis">
                {{ t('paymentSettings.source') }}: {{ t(`paymentSettings.source_${masked.configSource}`) }}
              </span>
              <span v-if="masked.updatedAt" class="text-secondary ms-2">
                {{ t('paymentSettings.lastUpdated') }}: {{ formatDate(masked.updatedAt) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="settings-card h-100">
          <div class="d-flex justify-content-between align-items-center gap-2 mb-3">
            <h3 class="settings-card-title mb-0">{{ t('paymentSettings.credentials') }}</h3>
            <button class="btn btn-sm btn-ghost" type="button" :disabled="revealing" @click="toggleSecrets">
              <i class="bi" :class="showSecrets ? 'bi-eye-slash' : 'bi-eye'"></i>
              {{ showSecrets ? t('paymentSettings.hideKeys') : t('paymentSettings.showKeys') }}
            </button>
          </div>
          <div class="mb-3">
            <label class="form-label">{{ t('paymentSettings.apiKey') }}</label>
            <input
              v-model="form.apiKey"
              :type="showSecrets ? 'text' : 'password'"
              class="form-control font-monospace"
              autocomplete="off"
              :placeholder="masked.apiKeyConfigured ? t('paymentSettings.leaveBlankToKeep') : ''"
            />
            <div class="form-check mt-2">
              <input id="clearApiKey" v-model="form.clearApiKey" class="form-check-input" type="checkbox" />
              <label class="form-check-label" for="clearApiKey">{{ t('paymentSettings.clearApiKey') }}</label>
            </div>
          </div>
          <div class="mb-0">
            <label class="form-label">{{ t('paymentSettings.secretKey') }}</label>
            <input
              v-model="form.secretKey"
              :type="showSecrets ? 'text' : 'password'"
              class="form-control font-monospace"
              autocomplete="off"
              :placeholder="masked.secretKeyConfigured ? t('paymentSettings.leaveBlankToKeep') : ''"
            />
            <div class="form-check mt-2">
              <input id="clearSecretKey" v-model="form.clearSecretKey" class="form-check-input" type="checkbox" />
              <label class="form-check-label" for="clearSecretKey">{{ t('paymentSettings.clearSecretKey') }}</label>
            </div>
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="settings-card h-100">
          <h3 class="settings-card-title">{{ t('paymentSettings.options') }}</h3>
          <div class="mb-3">
            <label class="form-label">{{ t('paymentSettings.accountName') }}</label>
            <input v-model="form.accountName" class="form-control" :placeholder="t('paymentSettings.accountNameHint')" />
          </div>
          <div class="mb-0">
            <label class="form-label">{{ t('paymentSettings.baseUrl') }}</label>
            <select v-model="form.baseUrl" class="form-select">
              <option value="https://api.binance.com">https://api.binance.com</option>
              <option value="https://api.binance.us">https://api.binance.us</option>
            </select>
            <div class="form-text">{{ t('paymentSettings.baseUrlHint') }}</div>
          </div>
        </div>
      </div>

      <div class="col-12 d-flex gap-2 flex-wrap">
        <button class="btn btn-aurora" type="submit" :disabled="saving">
          <span v-if="saving" class="spinner-border spinner-border-sm me-2"></span>
          {{ t('paymentSettings.save') }}
        </button>
        <button class="btn btn-ghost" type="button" :disabled="testing" @click="runTest">
          <span v-if="testing" class="spinner-border spinner-border-sm me-2"></span>
          {{ t('paymentSettings.testConnection') }}
        </button>
        <button class="btn btn-ghost" type="button" :disabled="reconciling" @click="runReconcile">
          <span v-if="reconciling" class="spinner-border spinner-border-sm me-2"></span>
          {{ t('paymentSettings.reconcileDeposits') }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { paymentSettingsApi } from '@/api'
import { toast } from '@/composables/useToast'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const reconciling = ref(false)
const revealing = ref(false)
const showSecrets = ref(false)
const error = ref('')
const success = ref('')
const testMessage = ref('')
const testOk = ref(false)

const masked = reactive({
  apiKeyConfigured: false,
  secretKeyConfigured: false,
  apiKeyHint: null,
  baseUrl: 'https://api.binance.com',
  accountName: '',
  updatedAt: null,
  configSource: 'none',
})

const form = reactive({
  apiKey: '',
  secretKey: '',
  accountName: '',
  baseUrl: 'https://api.binance.com',
  clearApiKey: false,
  clearSecretKey: false,
})

function applyMasked(data) {
  Object.assign(masked, {
    apiKeyConfigured: !!data?.apiKeyConfigured,
    secretKeyConfigured: !!data?.secretKeyConfigured,
    apiKeyHint: data?.apiKeyHint ?? null,
    baseUrl: data?.baseUrl || 'https://api.binance.com',
    accountName: data?.accountName ?? '',
    updatedAt: data?.updatedAt ?? null,
    configSource: data?.configSource ?? 'none',
  })
  form.accountName = masked.accountName
  form.baseUrl = masked.baseUrl
}

function resetSecretFields() {
  form.apiKey = ''
  form.secretKey = ''
  form.clearApiKey = false
  form.clearSecretKey = false
}

async function toggleSecrets() {
  if (showSecrets.value) {
    showSecrets.value = false
    return
  }
  if (!form.apiKey && !form.secretKey && (masked.apiKeyConfigured || masked.secretKeyConfigured)) {
    revealing.value = true
    error.value = ''
    const { data, error: err } = await paymentSettingsApi.revealBinancePay()
    revealing.value = false
    if (err) {
      error.value = err.message
      toast().danger(err.message)
      return
    }
    const credentials = data?.data ?? data ?? {}
    form.apiKey = credentials.apiKey || ''
    form.secretKey = credentials.secretKey || ''
  }
  showSecrets.value = true
}

function formatDate(value) {
  try {
    return new Date(value).toLocaleString()
  } catch {
    return value
  }
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await paymentSettingsApi.getBinancePay()
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  applyMasked(data?.data ?? data ?? {})
  if (!showSecrets.value) resetSecretFields()
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const submittedApiKey = form.apiKey.trim()
  const submittedSecretKey = form.secretKey.trim()
  const payload = {
    accountName: form.accountName,
    baseUrl: form.baseUrl,
    clearApiKey: form.clearApiKey,
    clearSecretKey: form.clearSecretKey,
  }
  if (submittedApiKey) payload.apiKey = submittedApiKey
  if (submittedSecretKey) payload.secretKey = submittedSecretKey
  const { data, error: err } = await paymentSettingsApi.updateBinancePay(payload)
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const next = data?.data ?? data ?? {}
  applyMasked(next)
  if (!showSecrets.value) resetSecretFields()
  if ((submittedApiKey && !next.apiKeyConfigured) || (submittedSecretKey && !next.secretKeyConfigured)) {
    error.value = t('paymentSettings.saveKeysFailed')
    toast().danger(error.value)
    return
  }
  success.value = t('common.saved')
  toast().success(success.value)
}

async function runTest() {
  testing.value = true
  testMessage.value = ''
  const { data, error: err } = await paymentSettingsApi.testBinancePay()
  testing.value = false
  if (err) {
    testOk.value = false
    testMessage.value = err.message
    toast().danger(err.message)
    return
  }
  const result = data?.data ?? data ?? {}
  testOk.value = !!result.ok
  testMessage.value = result.message || (result.ok ? t('paymentSettings.testSuccess') : t('paymentSettings.testFailed'))
  if (testOk.value) toast().success(testMessage.value)
  else toast().warning(testMessage.value)
}

async function runReconcile() {
  reconciling.value = true
  testMessage.value = ''
  const { data, error: err } = await paymentSettingsApi.reconcileBinanceWallet()
  reconciling.value = false
  if (err) {
    testOk.value = false
    testMessage.value = err.message
    toast().danger(err.message)
    return
  }
  const result = data?.data ?? data ?? {}
  testOk.value = !!result.success
  testMessage.value = result.message || t('paymentSettings.reconcileDone', { count: result.processed ?? 0 })
  toast().success(testMessage.value)
}

onMounted(load)
</script>
