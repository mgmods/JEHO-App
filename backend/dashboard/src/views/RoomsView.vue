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
      <h5 class="mb-1">إنشاء روم رسمي تابع للمنصة</h5>
      <p class="small text-muted mb-3">رومات خدمة العملاء والإدارة تبقى مثبتة أعلى قائمة الرومات. أدخل ID حساب المسؤول الأساسي التقني للروم.</p>
      <form class="row g-2 align-items-end" @submit.prevent="createPlatformRoom">
        <div class="col-md-3">
          <label class="form-label">اسم الروم</label>
          <input v-model.trim="newPlatformRoom.title" class="form-control" maxlength="128" required placeholder="مثلاً: خدمة العملاء" />
        </div>
        <div class="col-md-3">
          <label class="form-label">ID المسؤول الأساسي</label>
          <input v-model.trim="newPlatformRoom.hostPublicId" class="form-control" required inputmode="numeric" placeholder="ID المستخدم" />
        </div>
        <div class="col-md-4">
          <label class="form-label">وصف الروم (اختياري)</label>
          <input v-model.trim="newPlatformRoom.description" class="form-control" placeholder="روم رسمي تابع لإدارة المنصة" />
        </div>
        <div class="col-md-2">
          <button class="btn btn-aurora w-100" type="submit" :disabled="creatingPlatformRoom">
            {{ creatingPlatformRoom ? 'جارٍ الإنشاء…' : 'إنشاء وتثبيت' }}
          </button>
        </div>
      </form>
    </div>

    <div class="glass p-3 mb-3">
      <h5 class="mb-1">إدارة مشرفي الرومات الرسمية</h5>
      <p class="small text-muted mb-3">اختر رومًا رسميًا وأضف أو أزل المشرفين باستخدام ID الحساب. تُمنح صلاحيات إدارة الروم ومقاعد المايك والدعوات والإشراف.</p>
      <div class="row g-2 align-items-end">
        <div class="col-md-4">
          <label class="form-label">الروم الرسمي</label>
          <select v-model="staffRoomId" class="form-select" @change="loadPlatformStaff" :disabled="!platformRooms.length">
            <option value="">اختر الروم</option>
            <option v-for="r in platformRooms" :key="r.id" :value="r.id">{{ r.title || r.name }} · {{ r.id }}</option>
          </select>
        </div>
        <div class="col-md-4">
          <label class="form-label">ID المستخدم لإضافته كمشرف</label>
          <input v-model.trim="staffPublicId" class="form-control" inputmode="numeric" placeholder="ID الحساب" />
        </div>
        <div class="col-md-2">
          <button class="btn btn-aurora w-100" type="button" :disabled="!staffRoomId || !staffPublicId || staffBusy" @click="addPlatformStaff">إضافة مشرف</button>
        </div>
        <div class="col-md-2">
          <button class="btn btn-ghost w-100" type="button" :disabled="!staffRoomId || staffBusy" @click="loadPlatformStaff">تحديث القائمة</button>
        </div>
      </div>
      <div v-if="staffLoading" class="small text-muted mt-3">جارٍ تحميل المشرفين…</div>
      <div v-else-if="staffRoomId && !platformStaff.length" class="small text-muted mt-3">لا يوجد مشرفون مضافون لهذا الروم بعد.</div>
      <div v-else-if="platformStaff.length" class="table-responsive mt-3">
        <table class="table table-sm align-middle mb-0">
          <thead><tr><th>الاسم</th><th>ID</th><th>الدور</th><th></th></tr></thead>
          <tbody>
            <tr v-for="person in platformStaff" :key="person.userId">
              <td>{{ person.displayName || 'مستخدم' }}</td>
              <td>{{ person.publicId }}</td>
              <td>{{ person.role === 'admin' ? 'إدارة الروم' : 'مشرف' }}</td>
              <td class="text-end"><button class="btn btn-sm btn-outline-danger" type="button" :disabled="staffBusy" @click="removePlatformStaff(person.publicId)">إزالة</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

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

    <BulkActionBar
      :count="rooms.length"
      :selected-count="selectedCount"
      :all-selected="allSelected"
      :some-selected="someSelected"
      :busy="bulkBusy"
      :actions="bulkActions"
      @toggle-all="toggleAll"
      @clear="clear"
      @action="onBulkAction"
    />

    <LoadingSpinner v-if="loading" />
    <div v-else-if="!rooms.length" class="glass empty-state p-5 text-center">{{ t('rooms.empty') }}</div>
    <div v-else class="widget-grid rooms-grid">
      <RoomCard
        v-for="r in rooms"
        :key="r.id"
        :room="r"
        :selected="isSelected(r.id)"
        @toggle-select="(row) => toggle(row.id)"
        @close="closeRoom"
        @delete="removeRoom"
        @edit="editRoom"
        @forceEnd="forceEndRoom"
        @toggleSupport="toggleSupport"
      />
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
import { useBulkSelection } from '@/composables/useBulkSelection'
import { runBulk } from '@/composables/useBulk'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import RoomCard from '@/components/RoomCard.vue'
import BulkActionBar from '@/components/BulkActionBar.vue'
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
const bulkBusy = ref(false)
const newPlatformRoom = ref({ title: '', hostPublicId: '', description: '' })
const creatingPlatformRoom = ref(false)
const staffRoomId = ref('')
const staffPublicId = ref('')
const platformStaff = ref([])
const staffLoading = ref(false)
const staffBusy = ref(false)
const platformRooms = computed(() => rooms.value.filter(r =>
  String(r.roomKind || '').toLowerCase() === 'support' || r.isSupport === true
))

const {
  selectedIds,
  selectedCount,
  allSelected,
  someSelected,
  isSelected,
  toggle,
  clear,
  toggleAll,
} = useBulkSelection(rooms)

const bulkActions = computed(() => [
  { key: 'close', label: t('bulk.closeSelected'), icon: 'bi-door-closed', variant: 'btn-outline-warning' },
  { key: 'force-end', label: t('bulk.forceEndSelected'), icon: 'bi-broadcast', variant: 'btn-outline-danger' },
  { key: 'delete', label: t('bulk.deleteSelected'), icon: 'bi-trash', variant: 'btn-outline-danger' },
])

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / limit.value) || 1))

async function onBulkAction(key) {
  const ids = selectedIds.value
  if (!ids.length) return
  const messages = {
    close: t('bulk.confirmClose', { count: ids.length }),
    'force-end': t('bulk.confirmForceEnd', { count: ids.length }),
    delete: t('bulk.confirmDelete', { count: ids.length }),
  }
  confirmTitle.value = t('bulk.selectAll')
  confirmMsg.value = messages[key] || messages.delete
  pendingAction.value = async () => {
    bulkBusy.value = true
    const data = await runBulk({
      resource: 'rooms',
      action: key,
      ids,
      force: key === 'delete',
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

async function createPlatformRoom() {
  if (creatingPlatformRoom.value) return
  creatingPlatformRoom.value = true
  const { data, error: err } = await roomsApi.createPlatform({ ...newPlatformRoom.value })
  creatingPlatformRoom.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  toast().success('تم إنشاء الروم الرسمي وتثبيته أعلى الرومات')
  newPlatformRoom.value = { title: '', hostPublicId: '', description: '' }
  await load()
  const created = data?.id ? data : rooms.value.find(r => r.title === newPlatformRoom.value.title)
  if (created?.id) staffRoomId.value = created.id
  else if (platformRooms.value.length) staffRoomId.value = platformRooms.value[0].id
  await loadPlatformStaff()
}

async function loadPlatformStaff() {
  if (!staffRoomId.value) { platformStaff.value = []; return }
  staffLoading.value = true
  const { data, error: err } = await roomsApi.platformStaff(staffRoomId.value)
  staffLoading.value = false
  if (err) {
    error.value = err.message
    platformStaff.value = []
    return
  }
  platformStaff.value = Array.isArray(data) ? data : Array.isArray(data?.data) ? data.data : []
}

async function addPlatformStaff() {
  if (!staffRoomId.value || !staffPublicId.value || staffBusy.value) return
  staffBusy.value = true
  const { error: err } = await roomsApi.addPlatformStaff(staffRoomId.value, staffPublicId.value)
  staffBusy.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  toast().success('تم تعيين المستخدم إدارةً للروم')
  staffPublicId.value = ''
  await loadPlatformStaff()
}

async function removePlatformStaff(publicId) {
  if (!staffRoomId.value || !publicId || staffBusy.value) return
  if (!window.confirm(`إزالة المستخدم صاحب ID ${publicId} من إدارة هذا الروم؟`)) return
  staffBusy.value = true
  const { error: err } = await roomsApi.removePlatformStaff(staffRoomId.value, publicId)
  staffBusy.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  toast().success('تمت إزالة المشرف من الروم')
  await loadPlatformStaff()
}

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
  if (!staffRoomId.value && platformRooms.value.length) {
    staffRoomId.value = platformRooms.value[0].id
    await loadPlatformStaff()
  }
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

function toggleSupport(r) {
  const enable = !(r.roomKind === 'support' || r.isSupport)
  pendingAction.value = { type: 'support', payload: r, enable }
  confirmTitle.value = enable ? 'ترقية لخدمة عملاء' : 'إلغاء خدمة عملاء'
  confirmMsg.value = enable
    ? `ترقية الروم «${r.name || r.title || r.id}» إلى روم خدمة عملاء رسمي؟ سيظهر أعلى القائمة في التطبيق.`
    : `إلغاء ترقية «${r.name || r.title || r.id}» وإعادته روماً عادياً؟`
  confirmOpen.value = true
}

function removeRoom(r) {
  const kind = String(r.roomKind || '').toLowerCase()
  const isAgency =
    kind === 'agency' || (!kind && !!(r.agencyId || r.isPersistent))
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
  // Bulk actions store a callback, not { type, payload }
  if (typeof action === 'function') {
    try {
      await action()
    } catch (e) {
      const msg = e?.message || String(e)
      error.value = msg
      toast().danger(msg)
    }
    return
  }
  const r = action.payload
  let err
  if (action.type === 'close') {
    // force-end first so live sessions really stop (personal + agency)
    ;({ error: err } = await roomsApi.forceEnd(r.id, { reason: 'Admin close' }))
    if (err && !(r.roomKind === 'agency' || r.agencyId || r.isPersistent)) {
      ;({ error: err } = await roomsApi.close(r.id))
    }
  } else if (action.type === 'forceEnd') {
    ;({ error: err } = await roomsApi.forceEnd(r.id, { reason: 'Admin force end' }))
  } else if (action.type === 'support') {
    ;({ error: err } = await roomsApi.setCustomerService(r.id, !!action.enable))
  } else if (action.type === 'delete') {
    // force end then delete
    await roomsApi.forceEnd(r.id, { reason: 'Admin delete' })
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
