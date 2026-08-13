<template>
  <div>
    <AlertMessage v-if="error" :message="error" type="warning" class="mb-3" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" class="mb-3" @dismiss="success = ''" />
    <LoadingSpinner v-if="loading" />

    <form v-else class="settings-form" @submit.prevent="save('preserve')">
      <section class="settings-card mb-3">
        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
          <div>
            <h3 class="settings-card-title mb-1">{{ t('economySettings.title') }}</h3>
            <p class="form-text mb-0">{{ t('economySettings.subtitle') }}</p>
          </div>
          <span class="badge bg-info-subtle text-info-emphasis">
            {{ t('economySettings.livePill') }}
          </span>
        </div>

        <label class="settings-switch mb-3">
          <div>
            <div class="settings-switch-title">{{ t('economySettings.showDiamondsTitle') }}</div>
            <div class="settings-switch-hint">{{ t('economySettings.showDiamondsHint') }}</div>
          </div>
          <input v-model="form.showDiamondValueInApp" class="form-check-input" type="checkbox" role="switch" />
        </label>

        <div class="row g-3">
          <div class="col-md-4">
            <label class="form-label">{{ t('economySettings.giftRatio') }}</label>
            <div class="input-group">
              <input
                v-model.number="form.giftDiamondRatio"
                type="number"
                step="0.01"
                min="0.01"
                max="1"
                class="form-control"
              />
              <span class="input-group-text">×</span>
            </div>
            <div class="form-text">
              {{ t('economySettings.giftRatioHint') }} ({{ percent(form.giftDiamondRatio) }})
            </div>
          </div>
          <div class="col-md-4">
            <label class="form-label">{{ t('economySettings.luckyRatio') }}</label>
            <div class="input-group">
              <input
                v-model.number="form.luckyGiftDiamondRatio"
                type="number"
                step="0.01"
                min="0.01"
                max="1"
                class="form-control"
              />
              <span class="input-group-text">×</span>
            </div>
            <div class="form-text">
              {{ t('economySettings.luckyRatioHint') }} ({{ percent(form.luckyGiftDiamondRatio) }})
            </div>
          </div>
          <div class="col-md-4">
            <label class="form-label">{{ t('economySettings.maxDiamonds') }}</label>
            <div class="input-group">
              <input
                v-model.number="form.maxDiamondsPerUnit"
                type="number"
                step="1"
                min="1"
                max="10000000"
                class="form-control"
              />
              <span class="input-group-text">♦</span>
            </div>
            <div class="form-text">{{ t('economySettings.maxDiamondsHint') }}</div>
          </div>
          <div class="col-md-4">
            <label class="form-label">{{ t('economySettings.luckyMaxMult') }}</label>
            <div class="input-group">
              <input
                v-model.number="form.luckyGiftMaxMultiplier"
                type="number"
                step="1"
                min="1"
                max="1000"
                class="form-control"
              />
              <span class="input-group-text">×</span>
            </div>
            <div class="form-text">{{ t('economySettings.luckyMaxMultHint') }}</div>
          </div>
          <div class="col-md-4">
            <label class="form-label">{{ t('economySettings.luckyTargetEv') }}</label>
            <input
              v-model.number="form.luckyGiftTargetEv"
              type="number"
              step="0.01"
              min="0.01"
              max="1"
              class="form-control"
            />
            <div class="form-text">{{ t('economySettings.luckyTargetEvHint') }}</div>
          </div>
        </div>
      </section>

      <section class="settings-card mb-3">
        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
          <div>
            <h3 class="settings-card-title mb-1">{{ t('economySettings.splitTitle') }}</h3>
            <p class="form-text mb-0">{{ t('economySettings.splitSubtitle') }}</p>
          </div>
          <span
            class="badge"
            :class="splitSumOk ? 'bg-success-subtle text-success-emphasis' : 'bg-warning-subtle text-warning-emphasis'"
          >
            {{ t('economySettings.splitSum') }}: {{ splitSum }}%
          </span>
        </div>
        <div class="row g-3">
          <div class="col-md-6">
            <label class="form-label">{{ t('economySettings.splitHost') }}</label>
            <div class="input-group">
              <input
                v-model.number="form.defaultGiftSplit.hostPercent"
                type="number"
                step="0.5"
                min="0"
                max="100"
                class="form-control"
              />
              <span class="input-group-text">%</span>
            </div>
          </div>
          <div class="col-md-6">
            <label class="form-label">{{ t('economySettings.splitAgency') }}</label>
            <div class="input-group">
              <input
                v-model.number="form.defaultGiftSplit.agencyOwnerPercent"
                type="number"
                step="0.5"
                min="0"
                max="100"
                class="form-control"
              />
              <span class="input-group-text">%</span>
            </div>
          </div>
        </div>
        <p class="form-text mt-2 mb-0">{{ t('economySettings.splitNormalizeHint') }}</p>
      </section>

      <section class="settings-card mb-3">
        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
          <div>
            <h3 class="settings-card-title mb-1">{{ t('economySettings.currencyTitle') }}</h3>
            <p class="form-text mb-0">{{ t('economySettings.currencySubtitle') }}</p>
          </div>
          <span
            class="badge"
            :class="profitable ? 'bg-success-subtle text-success-emphasis' : 'bg-danger-subtle text-danger-emphasis'"
          >
            {{ profitable ? t('economySettings.profitable') : t('economySettings.notProfitable') }}
            · {{ marginPct }}%
          </span>
        </div>
        <div class="row g-3">
          <div class="col-md-4">
            <label class="form-label">{{ t('economySettings.coinsPerUsd') }}</label>
            <div class="input-group">
              <input
                v-model.number="form.coinsPerUsd"
                type="number"
                step="100"
                min="100"
                class="form-control"
              />
              <span class="input-group-text">/$</span>
            </div>
            <div class="form-text">{{ t('economySettings.coinsPerUsdHint') }}</div>
          </div>
          <div class="col-md-4">
            <label class="form-label">{{ t('economySettings.diamondUsd') }}</label>
            <div class="input-group">
              <span class="input-group-text">$</span>
              <input
                v-model.number="form.diamondUsd"
                type="number"
                step="0.00001"
                min="0.000001"
                class="form-control"
              />
            </div>
            <div class="form-text">{{ t('economySettings.diamondUsdHint') }}</div>
          </div>
          <div class="col-md-4">
            <label class="form-label">{{ t('economySettings.minWithdraw') }}</label>
            <div class="input-group">
              <input
                v-model.number="form.minWithdrawDiamonds"
                type="number"
                step="1000"
                min="1"
                class="form-control"
              />
              <span class="input-group-text">♦</span>
            </div>
            <div class="form-text">
              {{ t('economySettings.minWithdrawHint') }} (≈ ${{ minWithdrawUsd }})
            </div>
          </div>
        </div>
      </section>

      <section class="settings-card mb-3">
        <h3 class="settings-card-title mb-3">{{ t('economySettings.previewTitle') }}</h3>
        <div class="table-responsive">
          <table class="table table-sm align-middle mb-0">
            <thead>
              <tr>
                <th>{{ t('economySettings.previewCoin') }}</th>
                <th>{{ t('economySettings.previewGiftDiamond') }}</th>
                <th>{{ t('economySettings.previewLuckyDiamond') }}</th>
                <th>{{ t('economySettings.previewProfit') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in previewPrices" :key="p">
                <td>{{ p.toLocaleString() }}</td>
                <td>{{ mint(p, form.giftDiamondRatio) }}</td>
                <td>{{ mint(p, form.luckyGiftDiamondRatio) }}</td>
                <td :class="giftProfit(p) >= 0 ? 'text-success' : 'text-danger'">
                  ${{ giftProfit(p).toFixed(5) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="settings-card">
        <h3 class="settings-card-title mb-3">{{ t('economySettings.actionsTitle') }}</h3>
        <div class="d-flex flex-wrap gap-2">
          <button type="submit" class="btn btn-aurora" :disabled="saving">
            <span v-if="saving" class="spinner-border spinner-border-sm me-1"></span>
            {{ t('economySettings.saveOnly') }}
          </button>
          <button
            type="button"
            class="btn btn-outline-primary"
            :disabled="saving"
            @click="save('renormalize')"
          >
            {{ t('economySettings.saveAndRenormalize') }}
          </button>
          <button
            type="button"
            class="btn btn-outline-warning"
            :disabled="saving"
            @click="save('force')"
          >
            {{ t('economySettings.saveAndForce') }}
          </button>
          <button
            type="button"
            class="btn btn-outline-secondary"
            :disabled="saving"
            @click="renormalize('preserve')"
          >
            {{ t('economySettings.renormalizeOnly') }}
          </button>
          <button
            type="button"
            class="btn btn-outline-danger ms-auto"
            :disabled="saving"
            @click="resetDefaults"
          >
            {{ t('economySettings.resetDefaults') }}
          </button>
        </div>
        <p class="form-text mt-2 mb-0">{{ t('economySettings.actionsHint') }}</p>
      </section>
    </form>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { economyApi } from '@/api'
import { toast } from '@/composables/useToast'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')

const form = reactive({
  coinsPerUsd: 10000,
  diamondUsd: 0.00005,
  minWithdrawDiamonds: 200000,
  giftDiamondRatio: 0.8,
  luckyGiftDiamondRatio: 0.3,
  maxDiamondsPerUnit: 2000000,
  luckyGiftMaxMultiplier: 4,
  luckyGiftTargetEv: 0.38,
  showDiamondValueInApp: false,
  defaultGiftSplit: {
    platformPercent: 0,
    hostPercent: 70,
    agencyOwnerPercent: 30,
  },
})

const previewPrices = [10, 50, 100, 300, 500, 1000, 2000, 5000, 10000, 25000]

const splitSum = computed(() => {
  const s =
    Number(form.defaultGiftSplit.hostPercent || 0) +
    Number(form.defaultGiftSplit.agencyOwnerPercent || 0)
  return Math.round(s * 100) / 100
})

const splitSumOk = computed(() => Math.abs(splitSum.value - 100) < 0.01)

/** Cost to the platform of one minted diamond ($ paid out on withdraw). */
const coinUsd = computed(() => {
  const c = Number(form.coinsPerUsd) || 10000
  return c > 0 ? 1 / c : 0
})

/** Platform margin fraction on a regular gift = 1 − ratio × (diamondUsd/coinUsd). */
const marginFraction = computed(() => {
  const cu = coinUsd.value
  if (cu <= 0) return 0
  const liabilityPerCoin = Number(form.giftDiamondRatio || 0) * Number(form.diamondUsd || 0)
  return 1 - liabilityPerCoin / cu
})

const marginPct = computed(() => Math.round(marginFraction.value * 1000) / 10)
const profitable = computed(() => marginFraction.value > 0)

const minWithdrawUsd = computed(() => {
  const v = Number(form.minWithdrawDiamonds || 0) * Number(form.diamondUsd || 0)
  return (Math.round(v * 100) / 100).toFixed(2)
})

function percent(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return `${(n * 100).toFixed(1)}%`
}

function mint(coinPrice, ratio) {
  const p = Math.max(0, Math.floor(coinPrice))
  if (p <= 0) return 0
  const r = Math.min(1, Math.max(0.01, Number(ratio) || 0.4))
  const cap = Math.max(1, Math.floor(form.maxDiamondsPerUnit))
  const raw = Math.floor(p * r)
  return Math.min(cap, raw)
}

/** Platform $ profit on a single regular gift at this coin price. */
function giftProfit(coinPrice) {
  const paid = Number(coinPrice) * coinUsd.value
  const liability = mint(coinPrice, form.giftDiamondRatio) * Number(form.diamondUsd || 0)
  return paid - liability
}

async function load() {
  loading.value = true
  const { data, error: err } = await economyApi.get()
  loading.value = false
  if (err) {
    error.value = err.message
    return
  }
  const cur = data?.current || data?.data?.current
  if (cur) applyToForm(cur)
}

function applyToForm(cur) {
  form.coinsPerUsd = Number(cur.coinsPerUsd) || 10000
  form.diamondUsd = Number(cur.diamondUsd) || 0.00005
  form.minWithdrawDiamonds = Number(cur.minWithdrawDiamonds) || 200000
  form.giftDiamondRatio = Number(cur.giftDiamondRatio) || 0.4
  form.luckyGiftDiamondRatio = Number(cur.luckyGiftDiamondRatio) || 0.15
  form.maxDiamondsPerUnit = Number(cur.maxDiamondsPerUnit) || 1000
  form.luckyGiftMaxMultiplier = Number(cur.luckyGiftMaxMultiplier) || 4
  form.luckyGiftTargetEv = Number(cur.luckyGiftTargetEv) || 0.38
  form.showDiamondValueInApp = !!cur.showDiamondValueInApp
  const s = cur.defaultGiftSplit || {}
  form.defaultGiftSplit.platformPercent = 0
  form.defaultGiftSplit.hostPercent = Number(s.hostPercent) || 70
  form.defaultGiftSplit.agencyOwnerPercent = Number(s.agencyOwnerPercent) || 30
}

async function save(mode) {
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    coinsPerUsd: form.coinsPerUsd,
    diamondUsd: form.diamondUsd,
    minWithdrawDiamonds: form.minWithdrawDiamonds,
    giftDiamondRatio: form.giftDiamondRatio,
    luckyGiftDiamondRatio: form.luckyGiftDiamondRatio,
    maxDiamondsPerUnit: form.maxDiamondsPerUnit,
    luckyGiftMaxMultiplier: form.luckyGiftMaxMultiplier,
    luckyGiftTargetEv: form.luckyGiftTargetEv,
    showDiamondValueInApp: form.showDiamondValueInApp,
    defaultGiftSplit: {
      platformPercent: 0,
      hostPercent: form.defaultGiftSplit.hostPercent,
      agencyOwnerPercent: form.defaultGiftSplit.agencyOwnerPercent,
    },
  }
  const renormalize = mode === 'renormalize' ? 'preserve' : mode === 'force' ? 'force' : null
  const { data, error: err } = await economyApi.update(payload, { renormalize })
  saving.value = false
  if (err) {
    error.value = err.message
    return
  }
  const cur = data?.current || data?.data?.current
  if (cur) applyToForm(cur)
  const fixed = data?.renormalize?.diamondFixed ?? data?.data?.renormalize?.diamondFixed
  success.value =
    renormalize && fixed != null
      ? t('economySettings.savedWithRenormalize', { count: fixed })
      : t('economySettings.saved')
  toast(success.value, 'success')
}

async function renormalize(mode) {
  saving.value = true
  error.value = ''
  const { data, error: err } = await economyApi.renormalizeGifts(mode === 'force' ? 'force' : null)
  saving.value = false
  if (err) {
    error.value = err.message
    return
  }
  const fixed = data?.diamondFixed ?? data?.data?.diamondFixed ?? 0
  success.value = t('economySettings.renormalized', { count: fixed })
  toast(success.value, 'success')
}

async function resetDefaults() {
  if (!confirm(t('economySettings.confirmReset'))) return
  saving.value = true
  error.value = ''
  const { data, error: err } = await economyApi.reset()
  saving.value = false
  if (err) {
    error.value = err.message
    return
  }
  const cur = data?.current || data?.data?.current
  if (cur) applyToForm(cur)
  success.value = t('economySettings.resetDone')
  toast(success.value, 'success')
}

onMounted(load)
</script>
