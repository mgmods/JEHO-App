<template>
  <div>
    <PageHeader :title="t('gameAds.title')" :subtitle="t('gameAds.subtitle')">
      <template #actions>
        <button class="btn btn-outline-light btn-sm" type="button" :disabled="loading" @click="load">
          {{ t('common.refresh') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
          {{ t('common.save') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <LoadingSpinner v-if="loading" />
    <div v-else class="row g-3">
      <div class="col-12">
        <div class="glass p-3">
          <div class="form-check form-switch">
            <input id="gameAdsEnabled" v-model="form.adsEnabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="gameAdsEnabled">{{ t('gameAds.adsEnabled') }}</label>
          </div>
          <div class="small text-muted mt-1">{{ t('gameAds.adsEnabledHint') }}</div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="glass p-3 h-100">
          <h3 class="h6 mb-3">{{ t('gameAds.units') }}</h3>
          <div class="mb-2">
            <label class="form-label small">App ID</label>
            <input v-model="form.appId" class="form-control" dir="ltr" placeholder="ca-app-pub-xxxxx~yyyyy" />
          </div>
          <div class="mb-2">
            <label class="form-label small">Banner</label>
            <input v-model="form.bannerId" class="form-control" dir="ltr" />
          </div>
          <div class="mb-2">
            <label class="form-label small">Interstitial</label>
            <input v-model="form.interstitialId" class="form-control" dir="ltr" />
          </div>
          <div class="mb-0">
            <label class="form-label small">Rewarded</label>
            <input v-model="form.rewardedId" class="form-control" dir="ltr" />
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="glass p-3 h-100">
          <h3 class="h6 mb-3">{{ t('gameAds.interstitial') }}</h3>
          <div class="form-check form-switch mb-3">
            <input id="gameInterstitial" v-model="form.interstitialEnabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="gameInterstitial">{{ t('gameAds.interstitialEnabled') }}</label>
          </div>
          <div class="mb-2">
            <label class="form-label small">{{ t('gameAds.everyNOpens') }}</label>
            <input v-model.number="form.interstitialEveryNOpens" type="number" min="1" class="form-control" />
            <div class="form-text">{{ t('gameAds.everyNOpensHint') }}</div>
          </div>
          <div class="form-check form-switch mb-0">
            <input id="gameInterstitialClose" v-model="form.interstitialOnClose" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="gameInterstitialClose">{{ t('gameAds.interstitialOnClose') }}</label>
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="glass p-3 h-100">
          <h3 class="h6 mb-3">{{ t('gameAds.rewarded') }}</h3>
          <div class="form-check form-switch mb-3">
            <input id="gameRewarded" v-model="form.rewardedEnabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="gameRewarded">{{ t('gameAds.rewardedEnabled') }}</label>
          </div>
          <div class="mb-2">
            <label class="form-label small">{{ t('gameAds.rewardedCoins') }}</label>
            <input v-model.number="form.rewardedCoins" type="number" min="0" class="form-control" />
          </div>
          <div class="mb-0">
            <label class="form-label small">{{ t('gameAds.rewardedDailyCap') }}</label>
            <input v-model.number="form.rewardedDailyCap" type="number" min="0" class="form-control" />
            <div class="form-text">{{ t('gameAds.rewardedDailyCapHint') }}</div>
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="glass p-3 h-100">
          <h3 class="h6 mb-3">{{ t('gameAds.banner') }}</h3>
          <div class="form-check form-switch mb-0">
            <input id="gameBanner" v-model="form.bannerEnabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="gameBanner">{{ t('gameAds.bannerEnabled') }}</label>
          </div>
          <div class="small text-muted mt-2">{{ t('gameAds.bannerHint') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { gameAdsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

const form = reactive({
  adsEnabled: false,
  testMode: false,
  appId: '',
  bannerEnabled: false,
  bannerId: '',
  interstitialEnabled: true,
  interstitialId: '',
  interstitialEveryNOpens: 3,
  interstitialOnClose: false,
  rewardedEnabled: true,
  rewardedId: '',
  rewardedCoins: 10,
  rewardedDailyCap: 5,
})

function applyPayload(data) {
  if (!data || typeof data !== 'object') return
  Object.assign(form, data, { testMode: false })
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await gameAdsApi.settings()
  loading.value = false
  if (err) {
    error.value = err.message || String(err)
    return
  }
  applyPayload(data?.data ?? data)
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = { ...form, testMode: false }
  const { data, error: err } = await gameAdsApi.patchSettings(payload)
  saving.value = false
  if (err) {
    error.value = err.message || String(err)
    toast().danger(error.value)
    return
  }
  applyPayload(data?.data ?? data)
  success.value = t('gameAds.saved')
  toast().success(success.value)
}

onMounted(load)
</script>
