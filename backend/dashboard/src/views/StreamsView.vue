<template>
  <div>
    <PageHeader :title="t('streams.title')" :subtitle="t('streams.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" @click="load" :disabled="loading">
          <i class="bi bi-arrow-clockwise me-1"></i> {{ t('common.refresh') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />

    <div class="glass p-3 mb-3">
      <div class="row g-2 align-items-end">
        <div class="col-md-5">
          <label class="form-label">{{ t('common.search') }}</label>
          <input v-model="search" class="form-control" :placeholder="t('streams.searchPlaceholder')" @keyup.enter="page = 1; load()" />
        </div>
        <div class="col-md-3">
          <label class="form-label">{{ t('common.status') }}</label>
          <select v-model="status" class="form-select" @change="page = 1; load()">
            <option value="">{{ t('streams.liveDefault') }}</option>
            <option value="live">{{ t('commonStatus.live') }}</option>
            <option value="ended">{{ t('commonStatus.ended') }}</option>
          </select>
        </div>
        <div class="col-auto">
          <button class="btn btn-aurora" type="button" @click="page = 1; load()">{{ t('common.filter') }}</button>
        </div>
      </div>
    </div>

    <BulkActionBar
      :count="streams.length"
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
      <div v-else class="p-3">
        <div v-if="!streams.length" class="empty-state p-5 text-center">{{ t('streams.empty') }}</div>
        <div v-else class="widget-grid rooms-grid">
          <StreamCard
            v-for="s in streams"
            :key="s.id"
            :stream="s"
            :selected="isSelected(s.id)"
            @toggle-select="(row) => toggle(row.id)"
            @forceEnd="askForceEnd"
          />
        </div>
      </div>
      <div class="d-flex justify-content-between align-items-center p-3 border-top" style="border-color: var(--border-color) !important">
        <span class="small text-muted">{{ total }} {{ t('common.total') }}</span>
        <div class="btn-group">
          <button class="btn btn-sm btn-ghost" :disabled="page <= 1" @click="page--; load()">{{ t('common.previous') }}</button>
          <button class="btn btn-sm btn-ghost disabled">{{ page }}</button>
          <button class="btn btn-sm btn-ghost" :disabled="streams.length < limit" @click="page++; load()">{{ t('common.next') }}</button>
        </div>
      </div>
    </div>

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
import { streamsApi } from '@/api'
import { extractList } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import { useBulkSelection } from '@/composables/useBulkSelection'
import { runBulk } from '@/composables/useBulk'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import StreamCard from '@/components/StreamCard.vue'
import BulkActionBar from '@/components/BulkActionBar.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const streams = ref([])
const loading = ref(false)
const error = ref('')
const search = ref('')
const status = ref('')
const page = ref(1)
const limit = ref(20)
const total = ref(0)
const bulkBusy = ref(false)
const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
const pendingStream = ref(null)
const pendingAction = ref(null)

const {
  selectedIds,
  selectedCount,
  allSelected,
  someSelected,
  isSelected,
  toggle,
  clear,
  toggleAll,
} = useBulkSelection(streams)

const bulkActions = computed(() => [
  { key: 'force-end', label: t('bulk.forceEndSelected'), icon: 'bi-broadcast', variant: 'btn-outline-danger' },
])

async function onBulkAction(key) {
  const ids = selectedIds.value
  if (!ids.length) return
  confirmTitle.value = t('bulk.selectAll')
  confirmMsg.value = t('bulk.confirmForceEnd', { count: ids.length })
  pendingAction.value = async () => {
    bulkBusy.value = true
    const data = await runBulk({ resource: 'streams', action: key, ids, t })
    bulkBusy.value = false
    if (data) {
      clear()
      await load()
    }
  }
  confirmOpen.value = true
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await streamsApi.list({
    page: page.value,
    limit: limit.value,
    search: search.value || undefined,
    status: status.value || undefined,
  })
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    streams.value = []
    return
  }
  streams.value = extractList(data)
  total.value = data?.meta?.total ?? data?.total ?? streams.value.length
}

function askForceEnd(s) {
  pendingStream.value = s
  pendingAction.value = null
  confirmTitle.value = t('common.confirm')
  confirmMsg.value = t('streams.confirmEnd', { title: s.title || s.id })
  confirmOpen.value = true
}

async function runConfirm() {
  const action = pendingAction.value
  pendingAction.value = null
  if (typeof action === 'function') {
    await action()
    return
  }
  await doForceEnd()
}

async function doForceEnd() {
  const s = pendingStream.value
  pendingStream.value = null
  if (!s?.id) return
  const { error: err } = await streamsApi.forceEnd(s.id, { reason: 'Admin force end' })
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    toast().success(t('app.success'))
    await load()
  }
}

onMounted(load)
</script>
