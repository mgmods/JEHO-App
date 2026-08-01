<template>
  <div>
    <PageHeader :title="t('rooms.title')" :subtitle="t('rooms.subtitle')">
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
          <input v-model="search" class="form-control" :placeholder="t('rooms.searchPlaceholder')" @keyup.enter="page = 1; load()" />
        </div>
        <div class="col-md-3">
          <label class="form-label">{{ t('common.status') }}</label>
          <select v-model="status" class="form-select" @change="page = 1; load()">
            <option value="">{{ t('rooms.openDefault') }}</option>
            <option value="live">{{ t('dashboard.liveRooms') }}</option>
            <option value="active">{{ t('commonStatus.open') }}</option>
            <option value="locked">{{ t('commonStatus.locked') }}</option>
            <option value="closed">{{ t('commonStatus.closed') }}</option>
            <option value="all">{{ t('rooms.allIncludingClosed') }}</option>
          </select>
        </div>
        <div class="col-auto">
          <button class="btn btn-aurora" type="button" @click="page = 1; load()">{{ t('common.filter') }}</button>
        </div>
      </div>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="!rooms.length" class="glass empty-state p-5 text-center">{{ t('rooms.empty') }}</div>
    <div v-else class="widget-grid rooms-grid">
      <RoomCard v-for="r in rooms" :key="r.id" :room="r" @close="closeRoom" @delete="removeRoom" @edit="editRoom" @forceEnd="forceEndRoom" />
    </div>

    <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 p-3 mt-2">
      <span class="small text-muted">
        {{ total }} {{ t('common.total') }} · {{ limit }} لكل صفحة · صفحة {{ page }} من {{ totalPages }}
      </span>
      <div class="btn-group">
        <button class="btn btn-sm btn-ghost" :disabled="page <= 1 || loading" @click="page--; load()">{{ t('common.previous') }}</button>
        <button class="btn btn-sm btn-ghost disabled">{{ page }} / {{ totalPages }}</button>
        <button class="btn btn-sm btn-ghost" :disabled="page >= totalPages || loading" @click="page++; load()">{{ t('common.next') }}</button>
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
import { roomsApi } from '@/api'
import { extractList } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import { askPrompt } from '@/composables/usePrompt'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import RoomCard from '@/components/RoomCard.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const rooms = ref([])
const loading = ref(false)
const error = ref('')
const search = ref('')
const status = ref('')
const page = ref(1)
const limit = ref(20)
const total = ref(0)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / limit.value) || 1))

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await roomsApi.list({
    page: page.value,
    limit: limit.value,
    search: search.value || undefined,
    status: status.value || undefined,
  })
  loading.value = false
  if (err) {
    error.value = err.message
    rooms.value = []
    return
  }
  rooms.value = extractList(data)
  total.value = Number(data?.meta?.total ?? data?.total ?? rooms.value.length)
}

const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
const pendingAction = ref(null)

function closeRoom(r) {
  pendingAction.value = { type: 'close', payload: r }
  confirmTitle.value = t('common.confirm')
  confirmMsg.value = t('rooms.confirmClose', { name: r.name || r.title || r.id })
  confirmOpen.value = true
}

function forceEndRoom(r) {
  pendingAction.value = { type: 'forceEnd', payload: r }
  confirmTitle.value = t('common.confirm')
  confirmMsg.value = t('rooms.confirmForceEnd', { name: r.name || r.title || r.id })
  confirmOpen.value = true
}

function removeRoom(r) {
  const isAgency = r.isPersistent || r.roomKind === 'agency' || r.agencyId
  const msg = isAgency
    ? t('rooms.confirmDeleteAgencyRoom', { name: r.name || r.title || r.id })
    : t('rooms.confirmDelete', { name: r.name || r.title || r.id })
  pendingAction.value = { type: 'delete', payload: r, force: !!isAgency }
  confirmTitle.value = t('common.delete')
  confirmMsg.value = msg
  confirmOpen.value = true
}

async function runConfirm() {
  const action = pendingAction.value
  pendingAction.value = null
  if (!action) return
  const r = action.payload
  let err
  if (action.type === 'close') {
    ;({ error: err } = await roomsApi.close(r.id))
  } else if (action.type === 'forceEnd') {
    ;({ error: err } = await roomsApi.forceEnd(r.id, { reason: 'Admin force end' }))
  } else if (action.type === 'delete') {
    ;({ error: err } = await roomsApi.delete(r.id, { force: !!action.force }))
  }
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    toast().success(t('app.success'))
    await load()
  }
}

async function editRoom(r) {
  const mode = await askPrompt({
    title: t('rooms.access'),
    message: t('rooms.accessPrompt', { name: r.title || r.name }),
    defaultValue: r.accessMode || 'free',
    placeholder: 'free | paid | permanent',
  })
  if (mode == null) return
  const normalized = String(mode).trim().toLowerCase()
  if (!['free', 'paid', 'permanent'].includes(normalized)) {
    error.value = t('rooms.invalidAccess')
    toast().danger(error.value)
    return
  }
  const feeRaw = await askPrompt({
    title: t('rooms.access'),
    message: t('rooms.feePrompt'),
    defaultValue: String(r.entryFeeCoins || 0),
  })
  if (feeRaw == null) return
  const entryFeeCoins = Math.max(0, Number(feeRaw) || 0)
  const { error: err } = await roomsApi.update(r.id, {
    accessMode: normalized,
    entryFeeCoins,
  })
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
