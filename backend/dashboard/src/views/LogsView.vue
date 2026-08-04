<template>
  <div>
    <PageHeader :title="t('logs.title')" :subtitle="t('logs.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="cleaning" @click="clean({ olderThanDays: 30 })">
          <i class="bi bi-calendar-x me-1"></i> {{ t('system.cleanOld') }} (30d)
        </button>
        <button class="btn btn-outline-danger btn-sm" type="button" :disabled="cleaning" @click="clean({})">
          <i class="bi bi-trash me-1"></i> {{ t('system.cleanAll') }}
        </button>
        <button class="btn btn-ghost btn-sm" type="button" @click="load" :disabled="loading">
          <i class="bi bi-arrow-clockwise me-1"></i> {{ t('common.refresh') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />

    <div class="glass p-3 mb-3">
      <div class="row g-2 align-items-end">
        <div class="col-md-4">
          <label class="form-label">{{ t('common.search') }}</label>
          <input v-model="search" class="form-control" :placeholder="t('logs.searchPlaceholder')" @keyup.enter="page = 1; load()" />
        </div>
        <div class="col-md-3">
          <label class="form-label">{{ t('logs.level') }}</label>
          <select v-model="level" class="form-select" @change="page = 1; load()">
            <option value="">{{ t('common.all') }}</option>
            <option value="info">{{ t('logs.info') }}</option>
            <option value="warn">{{ t('logs.warn') }}</option>
            <option value="error">{{ t('logs.error') }}</option>
            <option value="security">{{ t('logs.security') }}</option>
          </select>
        </div>
        <div class="col-auto">
          <button class="btn btn-aurora" type="button" @click="page = 1; load()">{{ t('common.filter') }}</button>
        </div>
      </div>
    </div>

    <div class="glass p-0 overflow-hidden">
      <LoadingSpinner v-if="loading" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle">
          <thead>
            <tr>
              <th>{{ t('logs.time') }}</th>
              <th>{{ t('logs.level') }}</th>
              <th>{{ t('logs.action') }}</th>
              <th>{{ t('logs.actor') }}</th>
              <th>IP</th>
              <th>{{ t('logs.message') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!logs.length">
              <td colspan="6" class="empty-state">{{ t('logs.empty') }}</td>
            </tr>
            <tr v-for="log in logs" :key="log.id || log.createdAt + log.message">
              <td class="text-nowrap">{{ formatDate(log.createdAt || log.timestamp) }}</td>
              <td>
                <span class="badge badge-soft" :class="levelClass(log.level)">{{ (log.level || 'info').toUpperCase() }}</span>
              </td>
              <td>{{ log.action || log.event || '—' }}</td>
              <td>{{ log.actor || log.userName || log.adminId || t('logs.system') }}</td>
              <td>{{ log.ip || log.ipAddress || '—' }}</td>
              <td>
                <div class="text-truncate" style="max-width: 360px" :title="log.message || log.details">
                  {{ log.message || log.details || '—' }}
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="d-flex justify-content-between align-items-center p-3 border-top" style="border-color: var(--border-color) !important">
        <span class="small text-muted">{{ t('common.page', { page }) }}</span>
        <div class="btn-group">
          <button class="btn btn-sm btn-ghost" :disabled="page <= 1" @click="page--; load()">{{ t('common.previous') }}</button>
          <button class="btn btn-sm btn-ghost" :disabled="logs.length < limit" @click="page++; load()">{{ t('common.next') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { logsApi } from '@/api'
import { extractList, formatDate } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()

const logs = ref([])
const loading = ref(false)
const error = ref('')
const search = ref('')
const level = ref('')
const page = ref(1)
const limit = ref(40)
const cleaning = ref(false)

function levelClass(level) {
  const l = String(level || 'info').toLowerCase()
  if (l === 'error' || l === 'security') return 'badge-soft-danger'
  if (l === 'warn' || l === 'warning') return 'badge-soft-warning'
  return 'badge-soft-info'
}

async function clean(body) {
  const isAll = !body?.olderThanDays && !body?.resolvedOnly
  const msg = isAll ? t('system.confirmCleanAll') : t('system.confirmCleanOld')
  if (!window.confirm(msg)) return
  cleaning.value = true
  const { data, error: err } = await logsApi.cleanup(body || {})
  cleaning.value = false
  if (err) {
    toast().danger(err.message)
    return
  }
  toast().success(t('system.cleaned', { count: data?.deleted ?? 0 }))
  page.value = 1
  await load()
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await logsApi.list({
    page: page.value,
    limit: limit.value,
    search: search.value || undefined,
    level: level.value || undefined,
  })
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    logs.value = []
    return
  }
  logs.value = extractList(data)
}

onMounted(load)
</script>
