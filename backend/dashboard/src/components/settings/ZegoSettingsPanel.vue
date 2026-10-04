<template>
  <div>
    <AlertMessage v-if="error" :message="error" type="warning" class="mb-3" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" class="mb-3" @dismiss="success = ''" />
    <LoadingSpinner v-if="loading" />

    <div v-else class="row g-3">
      <div class="col-12">
        <div class="settings-card">
          <h3 class="settings-card-title">{{ t('voiceRtc.title') }}</h3>
          <p class="text-secondary small mb-3">{{ t('voiceRtc.hint') }}</p>
          <div class="d-flex flex-wrap gap-2 align-items-center mb-3">
            <button
              type="button"
              class="btn"
              :class="voice.provider === 'zego' ? 'btn-aurora' : 'btn-outline-secondary'"
              :disabled="voiceSaving"
              @click="setProvider('zego')"
            >
              ZEGO
            </button>
            <button
              type="button"
              class="btn voice-provider-livekit d-inline-flex align-items-center gap-2"
              :class="voice.provider === 'livekit' ? 'btn-aurora' : 'btn-outline-secondary'"
              :disabled="voiceSaving"
              @click="setProvider('livekit')"
            >
              <span
                class="voice-wave"
                :class="{
                  'voice-wave--on': voice.provider === 'livekit',
                  'voice-wave--ready': voice.provider === 'livekit' && voice.ready,
                }"
                aria-hidden="true"
                title="LiveKit مفتوح المصدر"
              >
                <i /><i /><i /><i /><i />
              </span>
              LiveKit ({{ t('voiceRtc.free') }})
            </button>
            <span
              v-if="voice.provider === 'livekit'"
              class="small text-secondary d-inline-flex align-items-center gap-1"
            >
              <span
                class="voice-wave voice-wave--sm"
                :class="{ 'voice-wave--on': true, 'voice-wave--ready': voice.ready }"
                aria-hidden="true"
              >
                <i /><i /><i /><i /><i />
              </span>
              {{ voice.ready ? 'LiveKit مُهيأ — التكلفة تعتمد على الاستضافة والاستخدام' : 'LiveKit محدّد — راجع URL وAPI Key وAPI Secret' }}
            </span>
          </div>
          <div class="d-flex justify-content-between align-items-center mb-2">
            <span class="small text-secondary mb-0">{{ t('voiceRtc.keysSection') }}</span>
            <button
              class="btn btn-sm btn-ghost"
              type="button"
              :disabled="voiceRevealing"
              @click="revealVoiceKeys"
            >
              <span v-if="voiceRevealing" class="spinner-border spinner-border-sm me-1" />
              {{ showingVoiceKeys ? t('voiceRtc.hideKeys') : t('voiceRtc.showKeys') }}
            </button>
          </div>
          <div class="row g-3">
            <div class="col-md-6">
              <label class="form-label">LiveKit URL</label>
              <input
                v-model="voiceForm.url"
                class="form-control"
                dir="ltr"
                placeholder="ws://79.x.x.x:7880 or wss://voice.example.com"
                autocomplete="off"
              />
            </div>
            <div class="col-md-3">
              <label class="form-label">API Key</label>
              <input
                v-model="voiceForm.apiKey"
                class="form-control"
                dir="ltr"
                autocomplete="off"
                :type="showingVoiceKeys ? 'text' : 'password'"
                :placeholder="voice.apiKeyConfigured && !showingVoiceKeys ? '••••••••' : 'APIxxxxxxxx'"
              />
            </div>
            <div class="col-md-3">
              <label class="form-label">API Secret</label>
              <input
                v-model="voiceForm.apiSecret"
                class="form-control"
                :type="showingVoiceKeys ? 'text' : 'password'"
                dir="ltr"
                autocomplete="off"
                :placeholder="voice.apiSecretConfigured && !showingVoiceKeys ? '••••••••' : ''"
              />
            </div>
            <div class="col-12 d-flex flex-wrap gap-2 align-items-center">
              <button class="btn btn-outline-secondary" type="button" :disabled="voiceSaving" @click="saveVoiceKeys">
                <span v-if="voiceSaving" class="spinner-border spinner-border-sm me-2" />
                {{ t('voiceRtc.saveKeys') }}
              </button>
              <span class="small text-secondary">
                {{ t('voiceRtc.active') }}:
                <strong>{{ voice.provider === 'livekit' ? 'LiveKit' : 'ZEGO' }}</strong>
                · {{ voice.provider === 'livekit' ? (voice.ready ? t('voiceRtc.ready') : t('voiceRtc.notReady')) : (masked.appIdConfigured && masked.serverSecretConfigured ? 'ZEGO مُهيأ' : 'ZEGO يحتاج AppID وServerSecret') }}
                <span v-if="voice.configSource && voice.configSource !== 'none'" class="ms-1">
                  · {{ t('zegoSettings.source') }}: {{ voice.configSource }}
                </span>
              </span>
            </div>
          </div>
        </div>
      </div>

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

      <div class="col-12">
        <div class="settings-card">
          <h3 class="settings-card-title">{{ t('zegoSettings.importTitle') }}</h3>
          <p class="text-secondary small mb-3">{{ t('zegoSettings.importHint') }}</p>
          <div class="row g-3">
            <div class="col-lg-8">
              <label class="form-label">{{ t('zegoSettings.importUrl') }}</label>
              <input
                v-model="importForm.url"
                class="form-control"
                dir="ltr"
                placeholder="https://api.example.com"
                autocomplete="off"
              />
            </div>
            <div class="col-lg-4">
              <label class="form-label">{{ t('zegoSettings.importToken') }}</label>
              <input
                v-model="importForm.bearerToken"
                class="form-control"
                type="password"
                dir="ltr"
                :placeholder="t('zegoSettings.importTokenHint')"
                autocomplete="off"
              />
            </div>
            <div class="col-12 d-flex flex-wrap gap-2">
              <button
                class="btn btn-outline-secondary"
                type="button"
                :disabled="importing || !importForm.url.trim()"
                @click="runImport(false)"
              >
                <span v-if="importing && !importApply" class="spinner-border spinner-border-sm me-2" />
                {{ t('zegoSettings.testUrl') }}
              </button>
              <button
                class="btn btn-aurora"
                type="button"
                :disabled="importing || !importForm.url.trim()"
                @click="runImport(true)"
              >
                <span v-if="importing && importApply" class="spinner-border spinner-border-sm me-2" />
                {{ t('zegoSettings.importApply') }}
              </button>
            </div>
            <div v-if="importResult" class="col-12">
              <div class="border rounded-3 p-3 bg-body-tertiary small">
                <div v-if="importResult.probedUrl" class="mb-1" dir="ltr">
                  <strong>{{ t('zegoSettings.probedUrl') }}:</strong> {{ importResult.probedUrl }}
                </div>
                <div class="mb-1">
                  <strong>AppID:</strong> {{ importResult.found?.appId || '—' }}
                  · AppSign: {{ importResult.found?.appSign ? t('zegoSettings.configured') : t('zegoSettings.notConfigured') }}
                  · ServerSecret: {{ importResult.found?.serverSecret ? t('zegoSettings.configured') : t('zegoSettings.notConfigured') }}
                </div>
                <div v-if="importResult.found?.wsUrl" class="mb-1" dir="ltr">
                  <strong>WS:</strong> {{ importResult.found.wsUrl }}
                </div>
                <ul v-if="importResult.warnings?.length" class="mb-0 mt-2 text-warning-emphasis">
                  <li v-for="(w, i) in importResult.warnings" :key="i">{{ w }}</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>

      <form class="col-12 row g-3" @submit.prevent="save">
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { zegoSettingsApi, voiceRtcSettingsApi } from '@/api'
import { toast } from '@/composables/useToast'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const voiceSaving = ref(false)
const voiceRevealing = ref(false)
const revealing = ref(false)
const importing = ref(false)
const importApply = ref(false)
const showingKeys = ref(false)
const showingVoiceKeys = ref(false)
const error = ref('')
const success = ref('')
const importResult = ref(null)
const voice = reactive({
  provider: 'zego',
  url: '',
  urlConfigured: false,
  apiKey: '',
  apiKeyConfigured: false,
  apiSecretConfigured: false,
  ready: false,
  configSource: 'none',
})
const voiceForm = reactive({
  url: '',
  apiKey: '',
  apiSecret: '',
})
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
const importForm = reactive({
  url: '',
  bearerToken: '',
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

function applyVoice(data) {
  Object.assign(voice, data || {})
  voiceForm.url = data?.url || voiceForm.url || ''
  if (!showingVoiceKeys.value) {
    // Masked GET never returns full key/secret — keep fields empty until Reveal.
    if (!data?.apiKeyConfigured) voiceForm.apiKey = ''
    voiceForm.apiSecret = ''
  }
}

async function revealVoiceKeys() {
  if (showingVoiceKeys.value) {
    showingVoiceKeys.value = false
    voiceForm.apiKey = ''
    voiceForm.apiSecret = ''
    return
  }
  voiceRevealing.value = true
  error.value = ''
  const { data, error: err } = await voiceRtcSettingsApi.reveal()
  voiceRevealing.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const revealed = data?.data || data || {}
  voiceForm.url = revealed.url || voiceForm.url
  voiceForm.apiKey = revealed.apiKey || ''
  voiceForm.apiSecret = revealed.apiSecret || ''
  if (revealed.provider) voice.provider = revealed.provider
  showingVoiceKeys.value = true
}

async function loadVoice() {
  const { data, error: err } = await voiceRtcSettingsApi.get()
  if (err) {
    // Older servers without LiveKit endpoint — ignore.
    return
  }
  applyVoice(data?.data || data)
}

async function saveVoiceKeys() {
  voiceSaving.value = true
  error.value = ''
  const payload = {
    url: voiceForm.url.trim(),
  }
  if (voiceForm.apiKey.trim()) payload.apiKey = voiceForm.apiKey.trim()
  if (voiceForm.apiSecret.trim()) payload.apiSecret = voiceForm.apiSecret.trim()
  const { data, error: err } = await voiceRtcSettingsApi.update(payload)
  voiceSaving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  applyVoice(data?.data || data)
  showingVoiceKeys.value = false
  voiceForm.apiKey = ''
  voiceForm.apiSecret = ''
  success.value = t('common.saved')
  toast().success(success.value)
}

async function setProvider(provider) {
  voiceSaving.value = true
  error.value = ''
  const payload = { provider }
  if (provider === 'livekit') {
    if (voiceForm.url.trim()) payload.url = voiceForm.url.trim()
    if (voiceForm.apiKey.trim()) payload.apiKey = voiceForm.apiKey.trim()
    if (voiceForm.apiSecret.trim()) payload.apiSecret = voiceForm.apiSecret.trim()
  }
  const { data, error: err } = await voiceRtcSettingsApi.update(payload)
  voiceSaving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  applyVoice(data?.data || data)
  success.value =
    provider === 'livekit' ? t('voiceRtc.switchedLiveKit') : t('voiceRtc.switchedZego')
  toast().success(success.value)
}

async function load() {
  loading.value = true
  error.value = ''
  const [zegoRes] = await Promise.all([zegoSettingsApi.get(), loadVoice()])
  loading.value = false
  if (zegoRes.error) {
    error.value = zegoRes.error.message
    toast().danger(zegoRes.error.message)
    return
  }
  applyMasked(zegoRes.data?.data || zegoRes.data)
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

async function runImport(apply) {
  importing.value = true
  importApply.value = apply
  error.value = ''
  success.value = ''
  importResult.value = null
  const payload = {
    url: importForm.url.trim(),
    apply: !!apply,
  }
  if (importForm.bearerToken.trim()) payload.bearerToken = importForm.bearerToken.trim()
  const { data, error: err } = await zegoSettingsApi.importFromUrl(payload)
  importing.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const result = data?.data || data || {}
  importResult.value = result
  if (apply && result.settings) {
    applyMasked(result.settings)
    showingKeys.value = false
    form.appSign = ''
    form.serverSecret = ''
    success.value = t('zegoSettings.importSuccess')
    toast().success(success.value)
  } else if (!apply) {
    toast().success(t('zegoSettings.testSuccess'))
  }
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

<style scoped>
.voice-wave {
  display: inline-flex;
  align-items: flex-end;
  justify-content: center;
  gap: 2px;
  height: 14px;
  min-width: 16px;
  opacity: 0.45;
}
.voice-wave--sm {
  height: 12px;
}
.voice-wave i {
  display: block;
  width: 2.5px;
  height: 4px;
  border-radius: 2px;
  background: currentColor;
  transform-origin: bottom center;
}
.voice-wave--on {
  opacity: 0.95;
}
.voice-wave--on i {
  animation: voice-bar 0.85s ease-in-out infinite;
}
.voice-wave--on i:nth-child(1) { animation-delay: 0s; height: 5px; }
.voice-wave--on i:nth-child(2) { animation-delay: 0.12s; height: 10px; }
.voice-wave--on i:nth-child(3) { animation-delay: 0.24s; height: 14px; }
.voice-wave--on i:nth-child(4) { animation-delay: 0.36s; height: 9px; }
.voice-wave--on i:nth-child(5) { animation-delay: 0.48s; height: 6px; }
.voice-wave--ready i {
  animation-duration: 0.55s;
}
.voice-provider-livekit.btn-outline-secondary .voice-wave {
  opacity: 0.35;
}
.voice-provider-livekit.btn-outline-secondary .voice-wave i {
  animation: none;
  height: 5px;
}

@keyframes voice-bar {
  0%, 100% { transform: scaleY(0.35); opacity: 0.65; }
  50% { transform: scaleY(1); opacity: 1; }
}
</style>
