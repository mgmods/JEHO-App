<template>
  <div>
    <PageHeader
      :title="t('offers.title')"
      :subtitle="t('offers.subtitle')"
    >
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" @click="addOffer">
          <i class="bi bi-plus-lg me-1"></i> {{ t('offers.new') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
          {{ t('offers.save') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="glass p-3 mb-3 tip">
      <div class="fw-semibold mb-1">{{ t('offers.boxOffers') }}</div>
      <div class="small text-muted mb-3">
        {{ t('offers.hint') }}
      </div>
      <div class="form-check form-switch">
        <input id="fabVisible" v-model="fabVisible" class="form-check-input" type="checkbox" />
        <label class="form-check-label" for="fabVisible">{{ t('offers.showIcon') }}</label>
      </div>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else class="widget-grid">
      <article v-for="(o, idx) in offers" :key="o.id || idx" class="widget-card">
        <div class="widget-card-body">
          <div class="d-flex justify-content-between align-items-center mb-2">
            <h3 class="widget-card-title mb-0">{{ t('offers.offerNumber', { number: idx + 1 }) }}</h3>
            <div class="form-check m-0">
              <input :id="'act'+idx" v-model="o.active" class="form-check-input" type="checkbox" />
              <label class="form-check-label" :for="'act'+idx">{{ t('common.active') }}</label>
            </div>
          </div>
          <div class="row g-2">
            <div class="col-12">
              <label class="form-label small">{{ t('common.title') }}</label>
              <input v-model="o.title" class="form-control" />
            </div>
            <div class="col-12">
              <label class="form-label small">{{ t('common.description') }}</label>
              <input v-model="o.subtitle" class="form-control" />
            </div>
            <div class="col-6">
              <label class="form-label small">{{ t('common.coins') }}</label>
              <input v-model.number="o.coins" type="number" min="1" class="form-control" />
            </div>
            <div class="col-6">
              <label class="form-label small">{{ t('offers.bonus') }}</label>
              <input v-model.number="o.bonusCoins" type="number" min="0" class="form-control" />
            </div>
            <div class="col-6">
              <label class="form-label small">{{ t('common.priceUsd') }}</label>
              <input v-model.number="o.priceUsd" type="number" min="0" step="0.01" class="form-control" />
            </div>
            <div class="col-6">
              <label class="form-label small">SKU</label>
              <input v-model="o.sku" class="form-control" />
            </div>
            <div class="col-12">
              <label class="form-label small">رابط أنيميشن (GIF/MP4 اختياري)</label>
              <input v-model="o.lottieUrl" class="form-control" placeholder="https://.../gift.gif أو .mp4" />
            </div>
          </div>
          <button class="btn btn-sm btn-outline-danger mt-3 w-100" type="button" @click="askRemoveOffer(idx)">
              {{ t('offers.delete') }}
          </button>
        </div>
      </article>
    </div>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="t('common.delete')"
      :message="confirmMsg"
      @confirm="doRemoveOffer"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { settingsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const offers = ref([])
const fabVisible = ref(true)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

function addOffer() {
  offers.value.push({
    id: String(Date.now()),
    title: t('offers.new'),
    subtitle: t('offers.defaultSubtitle'),
    sku: 'coins_70000',
    coins: 70000,
    bonusCoins: 5000,
    priceUsd: 4.99,
    lottieUrl: '',
    imageUrl: '',
    popular: false,
    active: true,
  })
}

const confirmOpen = ref(false)
const confirmMsg = ref('')
const pendingRemoveIdx = ref(-1)

function askRemoveOffer(idx) {
  pendingRemoveIdx.value = idx
  confirmMsg.value = t('offers.delete') + '؟'
  confirmOpen.value = true
}

function doRemoveOffer() {
  const idx = pendingRemoveIdx.value
  pendingRemoveIdx.value = -1
  if (idx < 0) return
  offers.value.splice(idx, 1)
}

function parseSetting(list, key, fallback) {
  const found = (Array.isArray(list) ? list : list?.items || []).find((s) => s.key === key)
  if (!found?.value) return fallback
  try {
    const parsed = JSON.parse(found.value)
    return Array.isArray(parsed) ? parsed : fallback
  } catch {
    return fallback
  }
}

function parseBool(list, key, fallback = true) {
  const found = (Array.isArray(list) ? list : list?.items || []).find((s) => s.key === key)
  if (!found?.value) return fallback
  return found.value !== 'false'
}

async function load() {
  loading.value = true
  const { data, error: err } = await settingsApi.get()
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  offers.value = parseSetting(data, 'store_offers', [])
  fabVisible.value = parseBool(data, 'offers_fab_visible', true)
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const { error: err } = await settingsApi.update({
    store_offers: JSON.stringify(offers.value),
    offers_fab_visible: fabVisible.value ? 'true' : 'false',
  })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('offers.saved')
    toast().success(t('offers.saved'))
  }
}

onMounted(load)
</script>
