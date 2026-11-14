<template>
  <div>
    <PageHeader :title="t('genderVerifications.title')" :subtitle="t('genderVerifications.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="loading" @click="load">
          {{ t('common.refresh') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div v-if="!featureEnabled" class="glass p-4">
      <h3 class="h6 fw-semibold mb-2">{{ t('genderVerifications.disabledTitle') }}</h3>
      <p class="text-secondary mb-3">{{ t('genderVerifications.disabledBody') }}</p>
      <div class="d-flex gap-2 flex-wrap">
        <button class="btn btn-primary btn-sm" type="button" :disabled="toggling" @click="enableFeature">
          تشغيل التحقق من المضيفات
        </button>
        <RouterLink class="btn btn-ghost btn-sm" :to="{ name: 'settings' }">{{ t('nav.settings') }}</RouterLink>
      </div>
    </div>

    <template v-else>
      <div class="glass p-3 mb-3 d-flex flex-wrap gap-2 align-items-center justify-content-between">
        <div class="form-check form-switch mb-0">
          <input id="gvFeature" class="form-check-input" type="checkbox" checked @change="disableFeature" />
          <label class="form-check-label" for="gvFeature">التحقق من المضيفات مفعّل (إيقاف يخفيه من التطبيق)</label>
        </div>
        <div class="row g-2 align-items-end flex-grow-1" style="max-width: 280px">
          <div class="col">
            <label class="form-label">{{ t('common.status') }}</label>
            <select v-model="status" class="form-select" @change="load()">
              <option value="">{{ t('common.all') }}</option>
              <option value="pending">{{ t('common.pending') }}</option>
              <option value="approved">{{ t('common.approved') }}</option>
              <option value="rejected">{{ t('common.rejected') }}</option>
            </select>
          </div>
        </div>
      </div>

      <BulkActionBar
        :count="rows.length"
        :selected-count="selectedCount"
        :all-selected="allSelected"
        :some-selected="someSelected"
        :busy="bulkBusy"
        :actions="bulkActions"
        @toggle-all="toggleAll"
        @clear="clear"
        @action="onBulkAction"
      />

      <div class="glass p-0 overflow-hidden">
        <LoadingSpinner v-if="loading" />
        <div v-else class="table-responsive">
          <table class="table table-glass table-hover align-middle">
            <thead>
              <tr>
                <th style="width:2.2rem"></th>
                <th>{{ t('common.user') }}</th>
                <th>{{ t('genderVerifications.selfie') }}</th>
                <th>{{ t('genderVerifications.liveness') }}</th>
                <th>{{ t('common.status') }}</th>
                <th>{{ t('common.date') }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!rows.length">
                <td colspan="7" class="empty-state">{{ t('genderVerifications.empty') }}</td>
              </tr>
              <tr v-for="row in rows" :key="row.id">
                <td>
                  <input
                    type="checkbox"
                    class="form-check-input"
                    :checked="isSelected(row.id)"
                    @change="toggle(row.id)"
                  />
                </td>
                <td>
                  <div class="fw-semibold">{{ row.user?.displayName || row.user?.username || '—' }}</div>
                  <div class="small text-muted">{{ row.user?.username || shortId(row.userId) }}</div>
                </td>
                <td>
                  <a v-if="row.selfieUrl" :href="resolveAsset(row.selfieUrl)" target="_blank" rel="noopener">
                    <img
                      class="rounded"
                      :src="resolveAsset(row.selfieUrl)"
                      alt=""
                      style="width: 56px; height: 56px; object-fit: cover"
                    />
                  </a>
                  <span v-else>—</span>
                </td>
                <td>
                  <div>{{ row.livenessPassed ? '✓' : '—' }}</div>
                  <div class="small text-muted" v-if="row.livenessScore != null">
                    {{ Number(row.livenessScore).toFixed(2) }}
                  </div>
                </td>
                <td><StatusBadge :status="row.status || 'pending'" /></td>
                <td>{{ formatDate(row.createdAt) }}</td>
                <td class="text-end">
                  <div class="action-btns justify-content-end">
                    <button
                      v-if="String(row.status || '').toLowerCase() === 'pending'"
                      class="btn btn-sm btn-outline-success"
                      type="button"
                      @click="approve(row)"
                    >
                      {{ t('genderVerifications.approve') }}
                    </button>
                    <button
                      v-if="['pending', 'approved'].includes(String(row.status || '').toLowerCase())"
                      class="btn btn-sm btn-outline-danger"
                      type="button"
                      @click="reject(row)"
                    >
                      {{ t('genderVerifications.reject') }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="confirmTitle"
      :message="confirmMsg"
      @confirm="runConfirm"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { genderVerificationsApi, settingsApi } from '@/api'
import { extractList, formatDate } from '@/composables/useUtils'
import { resolveAsset } from '@/utils/assets'
import { toast } from '@/composables/useToast'
import { askPrompt } from '@/composables/usePrompt'
import { useBulkSelection } from '@/composables/useBulkSelection'
import { runBulk } from '@/composables/useBulk'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import BulkActionBar from '@/components/BulkActionBar.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const rows = ref([])
const loading = ref(false)
const error = ref('')
const success = ref('')
const status = ref('pending')
const featureEnabled = ref(false)
const toggling = ref(false)
const bulkBusy = ref(false)

const {
  selectedIds,
  selectedCount,
  allSelected,
  someSelected,
  isSelected,
  toggle,
  clear,
  toggleAll,
} = useBulkSelection(rows)

const bulkActions = computed(() => [
  { key: 'approve', label: t('bulk.approveSelected'), icon: 'bi-check2', variant: 'btn-outline-success' },
  { key: 'reject', label: t('bulk.rejectSelected'), icon: 'bi-x-lg', variant: 'btn-outline-danger' },
])

async function onBulkAction(key) {
  const ids = selectedIds.value
  if (!ids.length) return
  confirmTitle.value = t('bulk.selectAll')
  confirmMsg.value = key === 'approve'
    ? t('bulk.confirmApprove', { count: ids.length })
    : t('bulk.confirmReject', { count: ids.length })
  pendingAction.value = async () => {
    bulkBusy.value = true
    const data = await runBulk({
      resource: 'gender-verifications',
      action: key,
      ids,
      reason: key === 'reject' ? t('wallet.defaultRejectReason') : undefined,
      t,
    })
    bulkBusy.value = false
    if (data) {
      clear()
      await load()
    }
  }
  confirmOpen.value = true
}

function shortId(id) {
  if (!id) return '—'
  const s = String(id)
  return s.length > 12 ? s.slice(0, 8) + '…' : s
}

function readFeatureFlag(raw) {
  const map = {}
  if (Array.isArray(raw)) {
    for (const row of raw) {
      if (row?.key != null) map[row.key] = row.value
    }
  } else if (raw && typeof raw === 'object') {
    Object.assign(map, raw)
  }
  const v = map['features.female_only_voice_hosts'] ?? map.femaleOnlyVoiceHosts
  if (v === undefined || v === null || v === '') return false
  if (typeof v === 'boolean') return v
  const s = String(v).toLowerCase()
  return s === '1' || s === 'true' || s === 'yes'
}

async function refreshFeatureFlag() {
  const { data } = await settingsApi.get()
  featureEnabled.value = readFeatureFlag(data?.data ?? data)
}

async function setFeature(enabled) {
  toggling.value = true
  error.value = ''
  const { error: err } = await settingsApi.update({
    'features.female_only_voice_hosts': String(!!enabled),
  })
  toggling.value = false
  if (err) {
    error.value = err.message
    return
  }
  success.value = enabled ? 'تم تشغيل التحقق' : 'تم إيقاف التحقق — لن يظهر في التطبيق'
  toast().success(success.value)
  await load()
}

async function enableFeature() {
  await setFeature(true)
}

const confirmOpen = ref(false)
const confirmTitle = ref('إيقاف التحقق')
const confirmMsg = ref('إيقاف التحقق من المضيفات؟ لن يظهر فحص الهوية في التطبيق.')
const pendingAction = ref(null)

function disableFeature(e) {
  if (e?.target) e.target.checked = true
  pendingAction.value = null
  confirmTitle.value = 'إيقاف التحقق'
  confirmMsg.value = 'إيقاف التحقق من المضيفات؟ لن يظهر فحص الهوية في التطبيق.'
  confirmOpen.value = true
}

async function runConfirm() {
  const action = pendingAction.value
  pendingAction.value = null
  if (typeof action === 'function') {
    await action()
    return
  }
  await doDisableFeature()
}

async function doDisableFeature() {
  await setFeature(false)
}

async function load() {
  loading.value = true
  error.value = ''
  await refreshFeatureFlag()
  if (!featureEnabled.value) {
    rows.value = []
    loading.value = false
    return
  }
  const { data, error: err } = await genderVerificationsApi.list({
    status: status.value || undefined,
  })
  loading.value = false
  if (err) {
    error.value = err.message
    rows.value = []
    return
  }
  rows.value = extractList(data?.data ?? data)
}

async function approve(row) {
  const { error: err } = await genderVerificationsApi.approve(row.id, {})
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = t('genderVerifications.approve')
  toast().success(success.value)
  load()
}

async function reject(row) {
  const note = await askPrompt({
    title: t('genderVerifications.reject'),
    message: t('wallet.rejectReason'),
    defaultValue: t('wallet.defaultRejectReason'),
  })
  if (note === null) return
  const { error: err } = await genderVerificationsApi.reject(row.id, { note })
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = t('genderVerifications.reject')
  toast().success(success.value)
  load()
}

onMounted(load)
</script>
