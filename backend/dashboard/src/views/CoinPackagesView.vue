<template>
  <div>
    <PageHeader
      :title="t('coinPackages.title')"
      :subtitle="t('coinPackages.subtitle')"
    >
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" @click="addPackage">
          <i class="bi bi-plus-lg me-1"></i> {{ t('coinPackages.new') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
          {{ t('coinPackages.save') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="glass p-3 mb-3 tip">
      <div class="fw-semibold mb-1">{{ t('coinPackages.bags') }}</div>
      <div class="small text-muted">
        {{ t('coinPackages.hint') }}
      </div>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else class="widget-grid">
      <div v-if="!packages.length" class="glass p-4 empty-state">{{ t('coinPackages.empty') }}</div>
      <article v-for="(p, idx) in packages" :key="p.id || idx" class="widget-card">
        <div class="widget-card-media bag-visual">
          <img v-if="packageIcon(p)" :src="packageIcon(p)" alt="" class="pkg-preview" />
          <div v-else class="pkg-preview pkg-preview-empty small text-muted">{{ t('coinPackages.noImage') }}</div>
          <div class="bag-amount">{{ formatNumber(p.coins) }}</div>
        </div>
        <div class="widget-card-body">
          <div class="mb-2">
            <label class="form-label small">{{ t('coinPackages.coinImage') }}</label>
            <div class="d-flex flex-wrap gap-2 align-items-center">
              <input v-model="p.imageUrl" class="form-control form-control-sm flex-grow-1" placeholder="/assets/pack/..." />
              <input
                type="file"
                accept="image/*,.webp"
                class="form-control form-control-sm"
                style="max-width: 160px"
                @change="(e) => uploadPackageImage(e, p)"
              />
              <button
                v-if="p.imageUrl || p.iconUrl"
                class="btn btn-sm btn-outline-danger"
                type="button"
                @click="clearPackageImage(p)"
              >
                {{ t('theme.clearAsset') }}
              </button>
            </div>
          </div>
          <div class="d-flex justify-content-between align-items-center mb-2">
            <input v-model="p.label" class="form-control form-control-sm w-50" :placeholder="t('coinPackages.labelPlaceholder')" />
            <div class="form-check m-0">
              <input :id="'pop'+idx" v-model="p.popular" class="form-check-input" type="checkbox" />
              <label class="form-check-label" :for="'pop'+idx">{{ t('common.popular') }}</label>
            </div>
          </div>
          <div class="row g-2">
            <div class="col-6">
              <label class="form-label small">{{ t('common.coins') }}</label>
              <input v-model.number="p.coins" type="number" min="1" class="form-control" />
            </div>
            <div class="col-6">
              <label class="form-label small">{{ t('common.bonus') }}</label>
              <input v-model.number="p.bonusCoins" type="number" min="0" class="form-control" />
            </div>
            <div class="col-6">
              <label class="form-label small">{{ t('common.priceUsd') }}</label>
              <input v-model.number="p.priceUsd" type="number" min="0" step="0.01" class="form-control" />
            </div>
            <div class="col-6">
              <label class="form-label small">{{ t('common.sku') }}</label>
              <input v-model="p.sku" class="form-control" />
            </div>
          </div>
          <button class="btn btn-sm btn-outline-danger mt-3 w-100" type="button" @click="askRemovePackage(idx)">
            {{ t('coinPackages.delete') }}
          </button>
        </div>
      </article>
    </div>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="t('common.delete')"
      :message="confirmMsg"
      @confirm="doRemovePackage"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi, uploadsApi } from '@/api'
import { formatNumber } from '@/composables/useUtils'
import { resolveAsset } from '@/utils/assets'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const packages = ref([])

function packageIcon(p) {
  const url = p?.imageUrl || p?.iconUrl
  return url ? resolveAsset(url) : ''
}

function clearPackageImage(p) {
  p.imageUrl = ''
  p.iconUrl = ''
}

async function uploadPackageImage(e, p) {
  const file = e.target.files?.[0]
  if (!file) return
  const { data, error: err } = await uploadsApi.upload(file)
  if (err) {
    error.value = err.message
    return
  }
  const url = data?.url || data?.data?.url || ''
  const resolved = url.startsWith('http') ? url : resolveAsset(url)
  p.imageUrl = resolved
  p.iconUrl = resolved
  e.target.value = ''
  toast().success(t('app.success'))
}

function addPackage() {
  packages.value.push({
    id: String(Date.now()),
    sku: `coins_${Date.now()}`,
    coins: 10000,
    bonusCoins: 0,
    priceUsd: 0.99,
    label: '10,000',
    popular: false,
  })
}

const confirmOpen = ref(false)
const confirmMsg = ref('')
const pendingRemoveIdx = ref(-1)

function askRemovePackage(idx) {
  pendingRemoveIdx.value = idx
  confirmMsg.value = t('coinPackages.delete') + '؟'
  confirmOpen.value = true
}

function doRemovePackage() {
  const idx = pendingRemoveIdx.value
  pendingRemoveIdx.value = -1
  if (idx < 0) return
  packages.value.splice(idx, 1)
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await walletApi.packages()
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const payload = data?.data ?? data
  packages.value = Array.isArray(payload?.items) ? payload.items : Array.isArray(payload) ? payload : []
}

async function save() {
  saving.value = true
  const { error: err } = await walletApi.savePackages({ items: packages.value })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('coinPackages.saved')
    toast().success(t('coinPackages.saved'))
  }
}

onMounted(load)
</script>

<style scoped>
.tip {
  border: 1px solid rgba(255, 200, 80, 0.25);
}
.bag-visual {
  display: grid;
  place-items: center;
  position: relative;
  background: radial-gradient(circle at 50% 40%, rgba(255, 196, 72, 0.18), transparent 60%);
  min-height: 140px;
  aspect-ratio: auto;
}
.bag-visual img,
.pkg-preview {
  width: 96px;
  height: 96px;
  object-fit: contain;
  filter: drop-shadow(0 8px 16px rgba(0, 0, 0, 0.35));
  max-width: none;
  max-height: none;
}
.pkg-preview-empty {
  width: 96px;
  height: 96px;
  display: grid;
  place-items: center;
  border: 1px dashed rgba(255, 196, 72, 0.35);
  border-radius: 12px;
}
.bag-amount {
  margin-top: 0.35rem;
  font-family: var(--al-display);
  font-weight: 700;
  font-size: 1.25rem;
  color: #ffe08a;
}
</style>
