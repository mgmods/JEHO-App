<template>
  <div>
    <PageHeader :title="t('reports.title')" :subtitle="t('reports.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" @click="load" :disabled="loading">{{ t('common.refresh') }}</button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

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
          </select>
        </div>
      </div>
    </div>

    <div class="glass p-0 overflow-hidden">
      <LoadingSpinner v-if="loading" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle">
          <thead>
            <tr>
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
              <td colspan="7" class="empty-state">{{ t('reports.empty') }}</td>
            </tr>
            <tr v-for="r in reports" :key="r.id">
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { reportsApi } from '@/api'
import { extractList, formatDate } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import { askPrompt } from '@/composables/usePrompt'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import StatusBadge from '@/components/StatusBadge.vue'

const { t } = useI18n()

const reports = ref([])
const loading = ref(false)
const error = ref('')
const success = ref('')
const status = ref('')
const type = ref('')

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

onMounted(load)
</script>
