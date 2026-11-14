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
    <template v-else>
      <BulkActionBar
        :count="packages.length"
        :selected-count="selectedCount"
        :all-selected="allSelected"
        :some-selected="someSelected"
        :actions="bulkActions"
        @toggle-all="toggleAll"
        @clear="clear"
        @action="onBulkAction"
      />
      <div class="pkg-grid">
        <div v-if="!packages.length" class="glass p-4 empty-state">{{ t('coinPackages.empty') }}</div>
        <article
          v-for="(p, idx) in packages"
          :key="p.id || idx"
          class="pkg-card glass"
          :class="{ 'is-selected': isSelected(p.id || String(idx)), 'pkg-card--popular': p.popular }"
        >
          <BulkCheck :checked="isSelected(p.id || String(idx))" @toggle="toggle(p.id || String(idx))" />

          <div class="pkg-card__head">
            <div class="pkg-card__amount">
              <span class="pkg-card__coins">{{ formatNumber(Number(p.coins) || 0) }}</span>
              <span class="pkg-card__unit">{{ t('common.coins') }}</span>
            </div>
            <div v-if="Number(p.bonusCoins) > 0" class="pkg-card__bonus">
              +{{ formatNumber(Number(p.bonusCoins) || 0) }} {{ t('common.bonus') }}
            </div>
            <div class="pkg-card__price">${{ Number(p.priceUsd || 0).toFixed(2) }}</div>
          </div>

          <div class="pkg-card__fields">
            <div class="d-flex justify-content-between align-items-center gap-2 mb-2">
              <input
                v-model="p.label"
                class="form-control form-control-sm"
                :placeholder="t('coinPackages.labelPlaceholder')"
              />
              <div class="form-check m-0 text-nowrap">
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
                <input v-model="p.sku" class="form-control" dir="ltr" />
              </div>
            </div>
            <button class="btn btn-sm btn-outline-danger mt-3 w-100" type="button" @click="askRemovePackage(idx)">
              {{ t('coinPackages.delete') }}
            </button>
          </div>
        </article>
      </div>
    </template>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="t('common.delete')"
      :message="confirmMsg"
      @confirm="doRemovePackage"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '@/api'
import { formatNumber } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import { useBulkSelection } from '@/composables/useBulkSelection'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import BulkActionBar from '@/components/BulkActionBar.vue'
import BulkCheck from '@/components/BulkCheck.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const packages = ref([])

const {
  selectedIds,
  selectedCount,
  allSelected,
  someSelected,
  isSelected,
  toggle,
  clear,
  toggleAll,
} = useBulkSelection(packages, (row) => {
  const idx = packages.value.indexOf(row)
  return String(row.id || idx)
})

const bulkActions = computed(() => [
  { key: 'delete', label: t('bulk.deleteSelected'), icon: 'bi-trash', variant: 'btn-outline-danger' },
])

function onBulkAction(key) {
  if (key !== 'delete') return
  const ids = selectedIds.value
  if (!ids.length) return
  pendingRemoveIdx.value = -2
  confirmMsg.value = t('bulk.confirmDelete', { count: ids.length })
  confirmOpen.value = true
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
  if (idx === -2) {
    const ids = new Set(selectedIds.value)
    packages.value = packages.value.filter((p, i) => !ids.has(String(p.id || i)))
    clear()
    return
  }
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
  border: 1px solid rgba(255, 200, 80, 0.22);
}
.pkg-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}
.pkg-card {
  position: relative;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(18, 20, 28, 0.72);
}
.pkg-card.is-selected {
  outline: 2px solid rgba(45, 212, 191, 0.65);
  outline-offset: 2px;
}
.pkg-card--popular {
  border-color: rgba(234, 179, 8, 0.4);
}
.pkg-card__head {
  padding: 18px 16px 14px;
  text-align: center;
  background:
    linear-gradient(180deg, rgba(234, 179, 8, 0.12), transparent 70%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.pkg-card__amount {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.pkg-card__coins {
  font-family: var(--al-display, ui-sans-serif, system-ui);
  font-weight: 800;
  font-size: 1.75rem;
  line-height: 1.1;
  color: #fde68a;
  letter-spacing: -0.02em;
}
.pkg-card__unit {
  font-size: 0.75rem;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
.pkg-card__bonus {
  margin-top: 8px;
  display: inline-block;
  font-size: 0.78rem;
  font-weight: 700;
  color: #86efac;
  background: rgba(34, 197, 94, 0.14);
  padding: 3px 10px;
  border-radius: 999px;
}
.pkg-card__price {
  margin-top: 10px;
  font-size: 1.05rem;
  font-weight: 700;
  color: #e2e8f0;
}
.pkg-card__fields {
  padding: 14px 16px 16px;
}
</style>
