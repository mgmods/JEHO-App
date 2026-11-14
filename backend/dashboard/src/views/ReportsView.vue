<template>
  <div>
    <PageHeader :title="route.name === 'moments' ? 'إدارة اللحظات' : t('reports.title')" :subtitle="route.name === 'moments' ? 'مراجعة وحذف منشورات اللحظات' : t('reports.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" @click="refreshAll" :disabled="loading || momentsLoading">{{ t('common.refresh') }}</button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="glass p-3 mb-3">
      <div class="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
        <div><h5 class="mb-1">إدارة اللحظات</h5><div class="small text-muted">إدارة المنشورات وحذف لحظة أو جميع اللحظات مع التعليقات والإعجابات.</div></div>
        <div class="d-flex gap-2">
          <button class="btn btn-sm btn-outline-secondary" type="button" @click="loadMoments" :disabled="momentsLoading">تحديث القائمة</button>
          <button class="btn btn-sm btn-outline-danger" type="button" @click="confirmDeleteAllMoments" :disabled="momentsLoading || !moments.length">حذف جميع اللحظات</button>
        </div>
      </div>
      <LoadingSpinner v-if="momentsLoading" />
      <div v-else-if="!moments.length" class="empty-state">لا توجد لحظات منشورة.</div>
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle">
          <thead><tr><th>المنشور</th><th>الناشر</th><th>الخصوصية</th><th>التاريخ</th><th></th></tr></thead>
          <tbody><tr v-for="m in moments" :key="m.id">
            <td style="min-width:220px;max-width:420px"><div v-if="m.body" class="small text-break">{{ m.body }}</div><a v-if="m.imageUrl" :href="m.imageUrl" target="_blank" rel="noopener noreferrer" class="small">عرض الصورة</a><div class="small text-muted">{{ shortId(m.id) }}</div></td>
            <td>{{ m.displayName || m.username || 'مستخدم محذوف' }}</td><td>{{ m.visibility || '—' }}</td><td>{{ formatDate(m.createdAt) }}</td>
            <td class="text-end"><button class="btn btn-sm btn-outline-danger" type="button" @click="confirmDeleteMoment(m)">حذف</button></td>
          </tr></tbody>
        </table>
      </div>
      <div class="small text-muted mt-2">تُعرض أحدث 100 لحظة. حذف المنشور يزيله من واجهات جميع المستخدمين.</div>
    </div>

    <div class="glass p-3 mb-3">
      <div class="row g-2 align-items-end">
        <div class="col-md-3">
          <label class="form-label">{{ t('common.status') }}</label>
          <select v-model="status" class="form-select" @change="load()">
            <option value="">{{ t('common.all') }}</option>
            <option value="pending">{{ t('common.pending') }}</option>
            <option value="reviewing">{{ t('common.reviewing') }}</option>
            <option value="resolved">{{ t('common.resolved') }}</option>
            <option value="dismissed">{{ t('common.dismissed') }}</option>
          </select>
        </div>
        <div class="col-md-4">
          <label class="form-label">{{ t('common.type') }}</label>
          <select v-model="type" class="form-select" @change="load()">
            <option value="">{{ t('reports.allTypes') }}</option>
            <option value="user">{{ t('common.user') }}</option>
            <option value="room">{{ t('common.room') }}</option>
            <option value="live">{{ t('dashboard.liveRooms') }}</option>
            <option value="message">{{ t('logs.message') }}</option>
            <option value="gift">{{ t('gifts.title') }}</option>
            <option value="moment">اللحظات</option>
          </select>
        </div>
      </div>
    </div>

    <BulkActionBar
      :count="reports.length"
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
              <th>{{ t('reports.report') }}</th>
              <th>{{ t('reports.target') }}</th>
              <th>{{ t('reports.reporter') }}</th>
              <th>{{ t('common.reason') }}</th>
              <th>{{ t('common.status') }}</th>
              <th>{{ t('common.date') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!reports.length">
              <td colspan="8" class="empty-state">{{ t('reports.empty') }}</td>
            </tr>
            <tr v-for="r in reports" :key="r.id">
              <td>
                <input
                  type="checkbox"
                  class="form-check-input"
                  :checked="isSelected(r.id)"
                  @change="toggle(r.id)"
                />
              </td>
              <td class="small text-muted">{{ shortId(r.id) }}</td>
              <td>
                <div>{{ r.targetType || r.type || '—' }}</div>
                <div class="small text-muted">{{ shortId(r.targetId || r.reportedUserId) }}</div>
              </td>
              <td>{{ r.reporterName || r.reporter?.displayName || r.reporter?.username || '—' }}</td>
              <td>
                <div>{{ r.reason || r.category || '—' }}</div>
                <div class="small text-muted" v-if="r.description">{{ r.description }}</div>
              </td>
              <td><StatusBadge :status="r.status || 'pending'" /></td>
              <td>{{ formatDate(r.createdAt) }}</td>
              <td class="text-end">
                <div
                  class="action-btns justify-content-end"
                  v-if="!['resolved', 'dismissed', 'rejected'].includes((r.status || '').toLowerCase())"
                >
                  <button class="btn btn-sm btn-outline-success" type="button" @click="resolve(r)">{{ t('reports.resolve') }}</button>
                  <button class="btn btn-sm btn-outline-secondary" type="button" @click="dismiss(r)">{{ t('reports.dismiss') }}</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
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
import { useRoute } from 'vue-router'
import { reportsApi, momentsAdminApi } from '@/api'
import { extractList, formatDate } from '@/composables/useUtils'
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
const route = useRoute()

const reports = ref([])
const moments = ref([])
const momentsLoading = ref(false)
const loading = ref(false)
const error = ref('')
const success = ref('')
const status = ref('')
const type = ref('')
const bulkBusy = ref(false)
const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
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
} = useBulkSelection(reports)

const bulkActions = computed(() => [
  { key: 'resolve', label: t('bulk.resolveSelected'), icon: 'bi-check2', variant: 'btn-outline-success' },
  { key: 'dismiss', label: t('bulk.dismissSelected'), icon: 'bi-x-lg', variant: 'btn-outline-secondary' },
])

async function onBulkAction(key) {
  const ids = selectedIds.value
  if (!ids.length) return
  confirmTitle.value = t('bulk.selectAll')
  confirmMsg.value = key === 'resolve'
    ? `${t('bulk.resolveSelected')} (${ids.length})?`
    : `${t('bulk.dismissSelected')} (${ids.length})?`
  pendingAction.value = async () => {
    bulkBusy.value = true
    const data = await runBulk({
      resource: 'reports',
      action: key,
      ids,
      note: key === 'resolve' ? t('reports.defaultResolution') : t('reports.defaultDismissReason'),
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

async function runConfirm() {
  const action = pendingAction.value
  pendingAction.value = null
  if (typeof action === 'function') await action()
}

function shortId(id) {
  if (!id) return '—'
  const s = String(id)
  return s.length > 12 ? s.slice(0, 8) + '…' : s
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await reportsApi.list({
    status: status.value || undefined,
    type: type.value || undefined,
    limit: 50,
  })
  loading.value = false
  if (err) {
    error.value = err.message
    reports.value = []
    return
  }
  reports.value = extractList(data)
}

async function loadMoments() {
  momentsLoading.value = true
  const { data, error: err } = await momentsAdminApi.list({ limit: 100 })
  momentsLoading.value = false
  if (err) { toast().danger(err.message || 'تعذر تحميل اللحظات'); return }
  moments.value = extractList(data)
}
async function refreshAll() { await Promise.all([load(), loadMoments()]) }
function confirmDeleteMoment(moment) {
  confirmTitle.value = 'حذف اللحظة'
  confirmMsg.value = 'هل تريد حذف هذه اللحظة نهائياً من جميع المستخدمين؟'
  pendingAction.value = async () => {
    const { error: err } = await momentsAdminApi.remove(moment.id)
    if (err) { error.value = err.message; toast().danger(err.message) }
    else { success.value = 'تم حذف اللحظة من التطبيق'; toast().success(success.value); await loadMoments() }
  }
  confirmOpen.value = true
}
function confirmDeleteAllMoments() {
  confirmTitle.value = 'حذف جميع اللحظات'
  confirmMsg.value = 'تحذير: سيتم حذف جميع اللحظات المنشورة مع التعليقات والإعجابات نهائياً. هل تريد المتابعة؟'
  pendingAction.value = async () => {
    momentsLoading.value = true
    const { error: err } = await momentsAdminApi.removeAll()
    momentsLoading.value = false
    if (err) { error.value = err.message; toast().danger(err.message) }
    else { success.value = 'تم حذف جميع اللحظات'; toast().success(success.value); moments.value = []; await load() }
  }
  confirmOpen.value = true
}

async function resolve(r) {
  const note = await askPrompt({
    title: t('reports.resolve'),
    message: t('reports.resolutionNote'),
    defaultValue: t('reports.defaultResolution'),
  })
  if (note === null) return
  const { error: err } = await reportsApi.resolve(r.id, { note, adminNote: note, action: 'resolved' })
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('reports.resolved')
    toast().success(success.value)
    await load()
  }
}

async function dismiss(r) {
  const note = await askPrompt({
    title: t('reports.dismiss'),
    message: t('reports.dismissReason'),
    defaultValue: t('reports.defaultDismissReason'),
  })
  if (note === null) return
  const { error: err } = await reportsApi.dismiss(r.id, { note, adminNote: note })
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('reports.dismissed')
    toast().success(success.value)
    await load()
  }
}

onMounted(refreshAll)
</script>
