<template>
  <div>
    <PageHeader :title="t('notifications.title')" :subtitle="t('notifications.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" @click="loadHistory" :disabled="loading">
          {{ t('notifications.refreshHistory') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="row g-3">
      <div class="col-lg-5">
        <div class="glass p-4">
          <h3 class="h6 fw-semibold mb-3">{{ t('notifications.compose') }}</h3>
          <form @submit.prevent="send">
            <div class="mb-3">
              <label class="form-label">{{ t('common.title') }}</label>
              <input v-model="form.title" class="form-control" required maxlength="120" />
            </div>
            <div class="mb-3">
              <label class="form-label">{{ t('notifications.body') }}</label>
              <textarea v-model="form.body" class="form-control" rows="4" required maxlength="500"></textarea>
            </div>
            <div class="mb-3">
              <label class="form-label">{{ t('notifications.audience') }}</label>
              <select v-model="form.audience" class="form-select">
                <option value="all">{{ t('notifications.allUsers') }}</option>
                <option value="hosts">{{ t('notifications.hostsOnly') }}</option>
                <option value="vip">{{ t('notifications.vipMembers') }}</option>
                <option value="user">{{ t('notifications.specificUser') }}</option>
              </select>
            </div>
            <div v-if="form.audience === 'user'" class="mb-3">
              <label class="form-label">{{ t('notifications.targetUserId') }}</label>
              <input
                v-model="form.userId"
                class="form-control"
                required
                :placeholder="t('notifications.targetUserPlaceholder')"
              />
              <div class="form-text">{{ t('notifications.targetUserHint') }}</div>
            </div>
            <div class="mb-3">
              <label class="form-label">{{ t('notifications.channel') }}</label>
              <select v-model="form.channel" class="form-select">
                <option value="push">{{ t('notifications.push') }}</option>
                <option value="in_app">{{ t('notifications.inApp') }}</option>
                <option value="both">{{ t('notifications.both') }}</option>
              </select>
            </div>
            <div class="mb-3">
              <label class="form-label">{{ t('common.type') }}</label>
              <select v-model="form.type" class="form-select">
                <option value="system">{{ t('notifications.system') }}</option>
                <option value="agency">{{ t('notifications.agency') }}</option>
                <option value="vip">VIP</option>
                <option value="wallet">{{ t('wallet.title') }}</option>
              </select>
            </div>
            <button class="btn btn-aurora w-100" type="submit" :disabled="sending">
              <span v-if="sending" class="spinner-border spinner-border-sm me-2"></span>
              {{ t('notifications.send') }}
            </button>
          </form>
        </div>
      </div>
      <div class="col-lg-7">
        <div class="glass p-0 overflow-hidden h-100">
          <div class="p-3 border-bottom" style="border-color: var(--border-color) !important">
            <h3 class="h6 mb-0">{{ t('notifications.recent') }}</h3>
          </div>
          <LoadingSpinner v-if="loading" />
          <div v-else class="table-responsive">
            <table class="table table-glass table-hover align-middle mb-0">
              <thead>
                <tr>
                  <th>{{ t('common.title') }}</th>
                  <th>{{ t('notifications.audience') }}</th>
                  <th>{{ t('notifications.channel') }}</th>
                  <th>{{ t('common.date') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!history.length">
                  <td colspan="4" class="empty-state">{{ t('notifications.empty') }}</td>
                </tr>
                <tr v-for="n in history" :key="n.id">
                  <td>
                    <div class="fw-medium">{{ n.title }}</div>
                    <div class="small text-muted text-truncate" style="max-width: 240px">{{ n.body }}</div>
                  </td>
                  <td>{{ n.audience || 'all' }}</td>
                  <td>{{ n.channel || '—' }}</td>
                  <td>{{ formatDate(n.createdAt || n.sentAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notificationsApi } from '@/api'
import { extractList, formatDate } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()

const form = reactive({
  title: '',
  body: '',
  audience: 'all',
  userId: '',
  channel: 'both',
  type: 'system',
})
const history = ref([])
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const success = ref('')

async function loadHistory() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await notificationsApi.list({ limit: 30 })
  loading.value = false
  if (err) {
    error.value = err.message
    history.value = []
    return
  }
  history.value = extractList(data)
}

async function send() {
  sending.value = true
  error.value = ''
  success.value = ''
  const payload = {
    title: form.title,
    body: form.body,
    message: form.body,
    audience: form.audience,
    channel: form.channel,
    type: form.type,
    userId: form.audience === 'user' ? form.userId : undefined,
  }
  const { data, error: err } = await notificationsApi.send(payload)
  sending.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const info = data?.data || data || {}
  success.value = t('notifications.sent', {
    targets: info.targets ?? '—',
    push: info.pushed ?? '—',
    saved: info.saved ?? '—',
  })
  toast().success(success.value)
  form.title = ''
  form.body = ''
  form.userId = ''
  await loadHistory()
}

onMounted(loadHistory)
</script>
