<template>
  <div>
    <PageHeader :title="t('wallet.title')" :subtitle="t('wallet.subtitle')">
      <template #actions>
        <button v-if="tab === 'packages'" class="btn btn-ghost btn-sm" type="button" @click="addPackage">
          <i class="bi bi-plus-lg me-1"></i> {{ t('wallet.newPackage') }}
        </button>
        <button v-if="tab === 'withdrawPackages'" class="btn btn-ghost btn-sm" type="button" @click="addWithdrawPackage">
          <i class="bi bi-plus-lg me-1"></i> {{ t('wallet.newWithdrawPackage') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" @click="showAdjust = true">
          <i class="bi bi-plus-slash-minus me-1"></i> {{ t('wallet.adjustBalance') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="glass p-3 mb-3">
      <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <div>
          <h6 class="mb-1">{{ t('wallet.diamondEconomy') }}</h6>
          <div class="small text-muted">{{ t('wallet.economyHint') }}</div>
        </div>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="savingEconomy" @click="saveEconomy">
          {{ t('wallet.saveEconomy') }}
        </button>
      </div>

      <div class="row g-3">
        <div class="col-md-4">
          <label class="form-label">{{ t('wallet.withdrawTarget') }}</label>
          <input v-model.number="economy.minWithdrawDiamonds" type="number" min="1" step="1" class="form-control" />
          <div class="form-text">{{ t('wallet.withdrawTargetHint') }}</div>
        </div>
        <div class="col-md-4">
          <label class="form-label">{{ t('wallet.diamondUsdRate') }}</label>
          <input v-model.number="economy.diamondUsdRate" type="number" min="0.0000001" step="0.0000001" class="form-control" />
          <div class="form-text">{{ t('wallet.diamondUsdRateHint') }}</div>
        </div>
        <div class="col-md-4">
          <label class="form-label">{{ t('wallet.diamondCoinRate') }}</label>
          <input v-model.number="economy.diamondCoinRate" type="number" min="0.01" step="0.01" class="form-control" />
          <div class="form-text">{{ t('wallet.diamondCoinRateHint') }}</div>
        </div>
      </div>

      <hr class="border-secondary opacity-25 my-3" />

      <div class="row g-3">
        <div class="col-md-4">
          <label class="form-label">{{ t('wallet.silverToCoinRate') }}</label>
          <input v-model.number="economy.silverToCoinRate" type="number" min="0.0001" step="0.0001" class="form-control" />
          <div class="form-text">{{ t('wallet.silverToCoinRateHint') }}</div>
        </div>
        <div class="col-md-4">
          <label class="form-label">{{ t('wallet.minSilverExchange') }}</label>
          <input v-model.number="economy.minSilverExchange" type="number" min="1" step="1" class="form-control" />
          <div class="form-text">{{ t('wallet.minSilverExchangeHint') }}</div>
        </div>
        <div class="col-md-4">
          <label class="form-label">{{ t('wallet.coinToSilverRate') }}</label>
          <input v-model.number="economy.coinToSilverRate" type="number" min="1" step="1" class="form-control" />
          <div class="form-text">{{ t('wallet.coinToSilverRateHint') }}</div>
        </div>
      </div>

      <div class="alert alert-info mt-3 mb-0 small">
        <div><strong>{{ t('wallet.livePreview') }}</strong></div>
        <div>{{ economyPreviewLine }}</div>
      </div>
    </div>

    <ul class="nav nav-pills gap-2 mb-3">
      <li class="nav-item" v-for="tabItem in tabs" :key="tabItem.id">
        <button class="btn" :class="tab === tabItem.id ? 'btn-aurora' : 'btn-ghost'" type="button" @click="switchTab(tabItem.id)">
          {{ tabItem.label }}
        </button>
      </li>
    </ul>

    <div v-if="tab === 'packages'" class="glass p-3">
      <LoadingSpinner v-if="loading" />
      <template v-else>
        <p class="small text-muted mb-3">
          {{ t('wallet.packagesHint') }}
        </p>
        <div class="widget-grid mb-3">
          <article v-for="(p, idx) in packages" :key="p.id || idx" class="widget-card">
            <div class="widget-card-body">
              <div class="d-flex gap-3 mb-2">
                <img v-if="packageIcon(p)" :src="packageIcon(p)" alt="" class="pkg-thumb rounded" />
                <div class="flex-grow-1 min-w-0">
                  <div class="d-flex justify-content-between align-items-start mb-2">
                    <div>
                      <div class="widget-card-title">{{ p.label || formatNumber(p.coins) }}</div>
                      <div class="widget-card-meta">{{ p.sku }}</div>
                    </div>
                    <span v-if="p.popular" class="badge text-bg-warning">{{ t('common.popular') }}</span>
                  </div>
                  <div class="mb-2"><strong>{{ formatNumber(p.coins) }}</strong> {{ t('common.coins') }}
                    <span v-if="p.bonusCoins" class="text-success small"> +{{ formatNumber(p.bonusCoins) }}</span>
                  </div>
                  <div class="mb-2 text-warning">${{ Number(p.priceUsd || 0).toFixed(2) }}</div>
                </div>
              </div>
              <div class="mb-2">
                <label class="form-label mb-1 small">{{ t('coinPackages.coinImage') }}</label>
                <div class="d-flex flex-wrap gap-2 align-items-center">
                  <input v-model="p.imageUrl" class="form-control form-control-sm flex-grow-1" placeholder="/assets/pack/..." />
                  <input
                    type="file"
                    accept="image/*,.webp"
                    class="form-control form-control-sm"
                    style="max-width: 140px"
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
              <div class="row g-2 small">
                <div class="col-6">
                  <label class="form-label mb-1">{{ t('common.coins') }}</label>
                  <input v-model.number="p.coins" type="number" min="1" class="form-control form-control-sm" />
                </div>
                <div class="col-6">
                  <label class="form-label mb-1">{{ t('common.bonus') }}</label>
                  <input v-model.number="p.bonusCoins" type="number" min="0" class="form-control form-control-sm" />
                </div>
                <div class="col-6">
                  <label class="form-label mb-1">{{ t('common.priceUsd') }}</label>
                  <input v-model.number="p.priceUsd" type="number" min="0" step="0.01" class="form-control form-control-sm" />
                </div>
                <div class="col-6">
                  <label class="form-label mb-1">{{ t('common.title') }}</label>
                  <input v-model="p.label" class="form-control form-control-sm" />
                </div>
                <div class="col-8">
                  <label class="form-label mb-1">{{ t('common.sku') }}</label>
                  <input v-model="p.sku" class="form-control form-control-sm" />
                </div>
                <div class="col-4 d-flex align-items-end">
                  <div class="form-check">
                    <input :id="'pop'+idx" v-model="p.popular" class="form-check-input" type="checkbox" />
                    <label class="form-check-label" :for="'pop'+idx">{{ t('common.popular') }}</label>
                  </div>
                </div>
              </div>
              <button class="btn btn-sm btn-outline-danger mt-3" type="button" @click="askRemovePackage(idx)">{{ t('common.delete') }}</button>
            </div>
          </article>
        </div>
        <button class="btn btn-aurora" type="button" :disabled="saving" @click="savePackages">{{ t('wallet.savePackages') }}</button>
      </template>
    </div>

    <div v-else-if="tab === 'withdrawPackages'" class="glass p-3">
      <LoadingSpinner v-if="loading" />
      <template v-else>
        <p class="small text-muted mb-3">{{ t('wallet.withdrawPackagesHint') }}</p>
        <div class="widget-grid mb-3">
          <article v-for="(p, idx) in withdrawPackages" :key="p.id || idx" class="widget-card">
            <div class="widget-card-body">
              <div class="widget-card-title mb-2">{{ p.label || ('$' + Number(p.usd || 0).toFixed(2)) }}</div>
              <div class="mb-2 text-warning">${{ Number(p.usd || 0).toFixed(2) }}</div>
              <div class="mb-2"><strong>{{ formatNumber(p.diamonds) }}</strong> {{ t('wallet.diamondsLabel') }}</div>
              <div class="row g-2 small">
                <div class="col-6">
                  <label class="form-label mb-1">USD</label>
                  <input v-model.number="p.usd" type="number" min="0.01" step="0.01" class="form-control form-control-sm" />
                </div>
                <div class="col-6">
                  <label class="form-label mb-1">{{ t('wallet.diamondsLabel') }}</label>
                  <input v-model.number="p.diamonds" type="number" min="1" class="form-control form-control-sm" />
                </div>
                <div class="col-12">
                  <label class="form-label mb-1">{{ t('common.title') }}</label>
                  <input v-model="p.label" class="form-control form-control-sm" />
                </div>
              </div>
              <button class="btn btn-sm btn-outline-danger mt-3" type="button" @click="askRemoveWithdraw(idx)">{{ t('common.delete') }}</button>
            </div>
          </article>
        </div>
        <button class="btn btn-aurora" type="button" :disabled="saving" @click="saveWithdrawPackages">{{ t('wallet.saveWithdrawPackages') }}</button>
      </template>
    </div>

    <div v-else class="glass p-0 overflow-hidden">
      <LoadingSpinner v-if="loading" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle">
          <thead>
            <tr>
              <th v-for="h in headers" :key="h">{{ h }}</th>
              <th v-if="tab === 'withdraws'"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!rows.length">
              <td :colspan="headers.length + (tab === 'withdraws' ? 1 : 0)" class="empty-state">{{ t('common.noRecords') }}</td>
            </tr>

            <template v-if="tab === 'transactions'">
              <tr v-for="r in rows" :key="r.id">
                <td class="small text-muted">{{ shortId(r.id) }}</td>
                <td>
                  <div class="fw-medium">{{ displayUser(r) }}</div>
                  <div class="small text-muted" v-if="r.userId">{{ shortId(r.userId) }}</div>
                </td>
                <td>{{ r.type || r.direction || '—' }}</td>
                <td>{{ formatNumber(r.amount ?? r.coins ?? 0) }} {{ r.currency || 'coins' }}</td>
                <td><StatusBadge :status="r.status || 'completed'" /></td>
                <td>{{ formatDate(r.createdAt) }}</td>
              </tr>
            </template>

            <template v-else-if="tab === 'withdraws'">
              <tr v-for="r in rows" :key="r.id">
                <td class="small text-muted">{{ shortId(r.id) }}</td>
                <td>
                  <div class="fw-medium">{{ displayUser(r) }}</div>
                  <div class="small text-muted" v-if="r.userId">{{ shortId(r.userId) }}</div>
                </td>
                <td>{{ formatNumber(r.amount ?? r.diamonds ?? 0) }} {{ t('common.diamonds') }}</td>
                <td>
                  <div>{{ r.method || r.paymentMethod || '—' }}</div>
                  <div class="small text-warning" v-if="payoutAccount(r)">{{ payoutAccount(r) }}</div>
                  <div class="small text-muted" v-if="r.amountFiat != null">${{ Number(r.amountFiat).toFixed(2) }}</div>
                </td>
                <td><StatusBadge :status="r.status || 'pending'" /></td>
                <td>{{ formatDate(r.createdAt) }}</td>
                <td class="text-end">
                  <div class="action-btns justify-content-end" v-if="(r.status || '').toLowerCase() === 'pending'">
                    <button class="btn btn-sm btn-outline-success" type="button" @click="approve(r)">{{ t('wallet.approvePaid') }}</button>
                    <button class="btn btn-sm btn-outline-danger" type="button" @click="reject(r)">{{ t('wallet.reject') }}</button>
                  </div>
                </td>
              </tr>
            </template>

            <template v-else>
              <tr v-for="r in rows" :key="r.id">
                <td class="small text-muted">{{ shortId(r.id) }}</td>
                <td>
                  <div class="fw-medium">{{ displayUser(r) }}</div>
                  <div class="small text-muted" v-if="r.userId">{{ shortId(r.userId) }}</div>
                </td>
                <td>{{ formatMoney(r.amountFiat ?? r.amount ?? 0, r.currency || 'USD') }}</td>
                <td>
                  {{ formatNumber(r.coins ?? r.credits ?? 0) }}
                  <span v-if="r.bonusCoins" class="text-success small"> +{{ formatNumber(r.bonusCoins) }}</span>
                </td>
                <td>{{ r.provider || r.gateway || '—' }}</td>
                <td><StatusBadge :status="r.status || 'completed'" /></td>
                <td>{{ formatDate(r.createdAt) }}</td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="showAdjust" class="modal fade show d-block" tabindex="-1" style="background: rgba(0,0,0,.45)">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content glass-modal">
          <div class="modal-header">
            <h5 class="modal-title">{{ t('wallet.adjustBalance') }}</h5>
            <button type="button" class="btn-close" @click="showAdjust = false"></button>
          </div>
          <form @submit.prevent="adjust">
            <div class="modal-body">
              <div class="mb-3">
                <label class="form-label">{{ t('wallet.adjustUserId') }}</label>
                <input
                  v-model="adjustForm.userId"
                  class="form-control"
                  :placeholder="t('wallet.adjustUserIdHint')"
                  required
                />
              </div>
              <div class="mb-3">
                <label class="form-label">{{ t('wallet.adjustAmount') }}</label>
                <input v-model.number="adjustForm.amount" type="number" class="form-control" required />
              </div>
              <div class="mb-3">
                <label class="form-label">{{ t('common.reason') }}</label>
                <input v-model="adjustForm.reason" class="form-control" required />
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-ghost" @click="showAdjust = false">{{ t('common.cancel') }}</button>
              <button type="submit" class="btn btn-aurora" :disabled="saving">{{ t('wallet.apply') }}</button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="t('common.delete')"
      :message="confirmMsg"
      @confirm="runConfirm"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { settingsApi, walletApi, uploadsApi } from '@/api'
import { extractList, formatNumber, formatMoney, formatDate } from '@/composables/useUtils'
import { resolveAsset } from '@/utils/assets'
import { toast } from '@/composables/useToast'
import { askPrompt } from '@/composables/usePrompt'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const tabs = computed(() => [
  { id: 'packages', label: t('wallet.packages') },
  { id: 'withdrawPackages', label: t('wallet.withdrawPackages') },
  { id: 'transactions', label: t('wallet.transactions') },
  { id: 'withdraws', label: t('wallet.withdrawals') },
  { id: 'recharges', label: t('wallet.orders') },
])

const tab = ref('packages')
const rows = ref([])
const packages = ref([])
const withdrawPackages = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const showAdjust = ref(false)
const savingEconomy = ref(false)
const economy = reactive({
  diamondUsdRate: 0.00005,
  diamondCoinRate: 0.55,
  minWithdrawDiamonds: 10000,
  silverToCoinRate: 0.04,
  minSilverExchange: 4000,
  coinToSilverRate: 20,
})
const adjustForm = reactive({ userId: '', amount: 0, reason: '' })

const economyPreviewLine = computed(() => {
  const target = Math.max(1, Math.floor(Number(economy.minWithdrawDiamonds) || 0))
  const usd = Number(economy.diamondUsdRate) || 0
  const coins = Number(economy.diamondCoinRate) || 0
  const targetUsd = (target * usd).toFixed(2)
  const oneDiamondCoins = coins.toFixed(2)
  const silverMin = Math.max(1, Math.floor(Number(economy.minSilverExchange) || 0))
  const silverRate = Number(economy.silverToCoinRate) || 0
  const silverCoins = Math.floor(silverMin * silverRate)
  return t('wallet.economyPreview', {
    target,
    targetUsd,
    oneDiamondCoins,
    silverMin,
    silverCoins,
  })
})

const headers = computed(() => {
  if (tab.value === 'transactions') return [t('common.id'), t('common.user'), t('common.type'), t('common.amount'), t('common.status'), t('common.date')]
  if (tab.value === 'withdraws') return [t('common.id'), t('common.user'), t('common.amount'), t('wallet.payout'), t('common.status'), t('common.date'), t('common.actions')]
  return [t('common.id'), t('common.user'), t('common.amount'), t('common.coins'), t('wallet.provider'), t('common.status'), t('common.date')]
})

function shortId(id) {
  if (!id) return '—'
  const s = String(id)
  return s.length > 10 ? s.slice(0, 8) + '…' : s
}

function displayUser(r) {
  return r.userName || r.user?.displayName || r.user?.username || r.user?.name || '—'
}

function payoutAccount(r) {
  const details = r?.payoutDetails
  if (!details) return ''
  if (typeof details === 'string') return details
  return details.account || details.email || details.iban || details.wallet || details.address || JSON.stringify(details)
}

function switchTab(id) {
  tab.value = id
  load()
}

function addPackage() {
  const n = packages.value.length + 1
  packages.value.push({
    id: String(Date.now()),
    sku: `coins_new_${n}`,
    coins: 10000,
    bonusCoins: 0,
    priceUsd: 0.99,
    label: '10,000',
    popular: false,
  })
}

function addWithdrawPackage() {
  const n = withdrawPackages.value.length + 1
  const usd = n === 1 ? 1 : n === 2 ? 5 : 10
  withdrawPackages.value.push({
    id: String(Date.now()),
    usd,
    diamonds: Math.max(1, Math.floor(Number(economy.minWithdrawDiamonds) || 10000)),
    label: `$${usd}`,
  })
}

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
}

async function load() {
  loading.value = true
  error.value = ''
  if (tab.value === 'packages') {
    const result = await walletApi.packages()
    loading.value = false
    if (result.error) {
      error.value = result.error.message
      packages.value = []
      return
    }
    const data = result.data?.data ?? result.data
    packages.value = Array.isArray(data?.items) ? data.items : Array.isArray(data) ? data : []
    return
  }
  if (tab.value === 'withdrawPackages') {
    const result = await walletApi.withdrawPackages()
    loading.value = false
    if (result.error) {
      error.value = result.error.message
      withdrawPackages.value = []
      return
    }
    const data = result.data?.data ?? result.data
    withdrawPackages.value = Array.isArray(data?.items) ? data.items : Array.isArray(data) ? data : []
    return
  }
  let result
  if (tab.value === 'withdraws') result = await walletApi.withdraws({ limit: 50 })
  else if (tab.value === 'recharges') result = await walletApi.recharges({ limit: 50 })
  else result = await walletApi.transactions({ limit: 50 })
  loading.value = false
  if (result.error) {
    error.value = result.error.message
    rows.value = []
    return
  }
  rows.value = extractList(result.data)
}

async function loadEconomy() {
  const result = await settingsApi.get()
  if (result.error) return
  const data = result.data?.data ?? result.data
  const rows = Array.isArray(data) ? data : []
  const map = Object.fromEntries(rows.map((row) => [row.key, row.value]))
  const num = (key, fallback) => {
    const value = Number(map[key])
    return Number.isFinite(value) ? value : fallback
  }
  economy.diamondUsdRate = num('economy.diamondUsdRate', economy.diamondUsdRate)
  economy.diamondCoinRate = num('economy.diamondCoinRate', economy.diamondCoinRate)
  economy.minWithdrawDiamonds = num('economy.minWithdrawDiamonds', economy.minWithdrawDiamonds)
  economy.silverToCoinRate = num('economy.silverToCoinRate', economy.silverToCoinRate)
  economy.minSilverExchange = num('economy.minSilverExchange', economy.minSilverExchange)
  economy.coinToSilverRate = num('economy.coinToSilverRate', economy.coinToSilverRate)
}

async function saveEconomy() {
  savingEconomy.value = true
  const result = await settingsApi.update({
    'economy.diamondUsdRate': String(economy.diamondUsdRate),
    'economy.diamondCoinRate': String(economy.diamondCoinRate),
    'economy.minWithdrawDiamonds': String(Math.floor(economy.minWithdrawDiamonds)),
    'economy.silverToCoinRate': String(economy.silverToCoinRate),
    'economy.minSilverExchange': String(Math.floor(economy.minSilverExchange)),
    'economy.coinToSilverRate': String(Math.floor(economy.coinToSilverRate)),
  })
  savingEconomy.value = false
  if (result.error) {
    error.value = result.error.message
    toast().danger(result.error.message)
  } else {
    success.value = t('wallet.economySaved')
    toast().success(success.value)
  }
}

const confirmOpen = ref(false)
const confirmMsg = ref('')
const pendingAction = ref(null)

function askRemovePackage(idx) {
  pendingAction.value = { type: 'pkg', idx }
  confirmMsg.value = t('common.delete') + '؟'
  confirmOpen.value = true
}
function askRemoveWithdraw(idx) {
  pendingAction.value = { type: 'withdraw', idx }
  confirmMsg.value = t('common.delete') + '؟'
  confirmOpen.value = true
}
function runConfirm() {
  const a = pendingAction.value
  pendingAction.value = null
  if (!a) return
  if (a.type === 'pkg') packages.value.splice(a.idx, 1)
  else if (a.type === 'withdraw') withdrawPackages.value.splice(a.idx, 1)
}

async function savePackages() {
  saving.value = true
  const { error: err } = await walletApi.savePackages({ items: packages.value })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('wallet.packagesSaved')
    toast().success(success.value)
  }
}

async function saveWithdrawPackages() {
  saving.value = true
  const { error: err } = await walletApi.saveWithdrawPackages({ items: withdrawPackages.value })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('wallet.withdrawPackagesSaved')
    toast().success(success.value)
    await load()
  }
}

async function approve(r) {
  const { error: err } = await walletApi.approveWithdraw(r.id)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('wallet.withdrawPaid')
    toast().success(success.value)
    await load()
  }
}

async function reject(r) {
  const reason = await askPrompt({
    title: t('wallet.reject') || 'رفض',
    message: t('wallet.rejectReason'),
    defaultValue: t('wallet.defaultRejectReason'),
  })
  if (reason === null) return
  const { error: err } = await walletApi.rejectWithdraw(r.id, { reason, adminNote: reason })
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('wallet.withdrawRejected')
    toast().success(success.value)
    await load()
  }
}

async function adjust() {
  saving.value = true
  const { error: err } = await walletApi.adjust({
    userId: adjustForm.userId,
    coinsDelta: Number(adjustForm.amount) || 0,
    diamondsDelta: 0,
    amount: Number(adjustForm.amount) || 0,
    note: adjustForm.reason,
    reason: adjustForm.reason,
  })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  showAdjust.value = false
  success.value = t('wallet.balanceAdjusted')
  toast().success(success.value)
  await load()
}

onMounted(() => {
  load()
  loadEconomy()
})
</script>

<style scoped>
.pkg-thumb {
  width: 72px;
  height: 72px;
  object-fit: contain;
  background: var(--input-bg);
  border: 1px solid var(--border-color);
}
</style>
