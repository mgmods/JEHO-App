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
      <div class="col-lg-6">
        <div class="glass p-4">
          <h3 class="h6 fw-semibold mb-3">{{ t('notifications.compose') }}</h3>
          <form @submit.prevent="send">
            <div class="mb-3">
              <label class="form-label">{{ t('common.title') }}</label>
              <input v-model="form.title" class="form-control" required maxlength="120" />
            </div>

            <div class="mb-3">
              <label class="form-label">{{ t('notifications.body') }}</label>
              <textarea
                v-model="form.body"
                class="form-control"
                rows="3"
                maxlength="800"
                :placeholder="t('notifications.bodyHint')"
              ></textarea>
              <div class="form-text">{{ t('notifications.bodyHint') }}</div>
            </div>

            <div class="mb-3">
              <div class="d-flex align-items-center justify-content-between mb-1">
                <label class="form-label mb-0">{{ t('notifications.htmlBody') }}</label>
                <button class="btn btn-ghost btn-sm" type="button" @click="insertHtmlSample">
                  {{ t('notifications.insertSample') }}
                </button>
              </div>
              <textarea
                v-model="form.html"
                class="form-control font-monospace"
                rows="8"
                dir="ltr"
                :placeholder="t('notifications.htmlPlaceholder')"
              ></textarea>
              <div class="form-text">{{ t('notifications.htmlHint') }}</div>
            </div>

            <div class="mb-3">
              <label class="form-label">{{ t('notifications.image') }}</label>
              <div class="d-flex gap-2 align-items-start flex-wrap">
                <input
                  class="form-control"
                  type="file"
                  accept="image/*"
                  :disabled="uploading"
                  @change="onImagePicked"
                />
                <button
                  v-if="form.imageUrl"
                  class="btn btn-ghost btn-sm"
                  type="button"
                  @click="form.imageUrl = ''"
                >
                  {{ t('common.remove') || 'Remove' }}
                </button>
              </div>
              <div v-if="uploading" class="form-text">{{ t('notifications.uploading') }}</div>
              <div v-if="form.imageUrl" class="mt-2">
                <img
                  :src="absoluteUrl(form.imageUrl)"
                  alt=""
                  class="rounded border"
                  style="max-width: 100%; max-height: 160px; object-fit: cover"
                />
                <div class="small text-muted text-truncate mt-1" dir="ltr">{{ form.imageUrl }}</div>
              </div>
              <div class="form-text">{{ t('notifications.imageHint') }}</div>
            </div>

            <div class="mb-3">
              <label class="form-label">{{ t('notifications.link') }}</label>
              <input
                v-model="form.link"
                class="form-control"
                dir="ltr"
                :placeholder="t('notifications.linkPlaceholder')"
              />
              <div class="form-text">{{ t('notifications.linkHint') }}</div>
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
            <button class="btn btn-aurora w-100" type="submit" :disabled="sending || uploading">
              <span v-if="sending" class="spinner-border spinner-border-sm me-2"></span>
              {{ t('notifications.send') }}
            </button>
          </form>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="glass p-4 mb-3">
          <h3 class="h6 fw-semibold mb-3">{{ t('notifications.preview') }}</h3>
          <div class="rounded-3 border p-3" style="background: #f7f7f8; min-height: 180px">
            <div class="fw-bold mb-2">{{ form.title || t('notifications.previewTitle') }}</div>
            <img
              v-if="form.imageUrl"
              :src="absoluteUrl(form.imageUrl)"
              alt=""
              class="rounded mb-2 w-100"
              style="max-height: 180px; object-fit: cover"
            />
            <div
              v-if="form.html"
              class="notif-html-preview small"
              dir="auto"
              v-html="safePreviewHtml"
            ></div>
            <div v-else class="text-muted small" style="white-space: pre-wrap">
              {{ form.body || t('notifications.previewEmpty') }}
            </div>
            <a
              v-if="form.link"
              class="btn btn-sm btn-aurora mt-3"
              :href="form.link"
              target="_blank"
              rel="noopener"
              >{{ t('notifications.openLink') }}</a
            >
          </div>
        </div>

        <div class="glass p-0 overflow-hidden">
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
                    <div class="d-flex gap-2 align-items-center">
                      <img
                        v-if="n.data?.imageUrl"
                        :src="absoluteUrl(n.data.imageUrl)"
                        alt=""
                        class="rounded"
                        style="width: 36px; height: 36px; object-fit: cover"
                      />
                      <div style="min-width: 0">
                        <div class="fw-medium">{{ n.title }}</div>
                        <div class="small text-muted text-truncate" style="max-width: 220px">
                          {{ n.body }}
                        </div>
                        <div v-if="n.data?.html || n.data?.imageUrl" class="small text-success">
                          {{ t('notifications.richBadge') }}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td>{{ n.data?.audience || n.audience || 'all' }}</td>
                  <td>{{ n.data?.channel || n.channel || '—' }}</td>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notificationsApi, uploadsApi } from '@/api'
import { extractList, formatDate } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()

const form = reactive({
  title: '',
  body: '',
  html: '',
  imageUrl: '',
  link: '',
  audience: 'all',
  userId: '',
  channel: 'both',
  type: 'system',
})
const history = ref([])
const loading = ref(false)
const sending = ref(false)
const uploading = ref(false)
const error = ref('')
const success = ref('')

const API_ORIGIN = (import.meta.env.VITE_API_BASE || '').replace(/\/api\/?$/, '') || ''

function absoluteUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  if (url.startsWith('//')) return `https:${url}`
  if (url.startsWith('/')) return `${API_ORIGIN}${url}`
  return `${API_ORIGIN}/${url}`
}

/** Basic client-side strip of script tags for live preview only (server sanitizes on send). */
const safePreviewHtml = computed(() => {
  const raw = form.html || ''
  return raw
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
    .replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
})

function insertHtmlSample() {
  form.html = `<p style="margin:0 0 8px"><b>🎉 عرض خاص اليوم</b></p>
<p style="margin:0 0 8px">ادخل إلى غرفة <b>Night Lounge</b> واربح جوائز VIP.</p>
<ul>
  <li>هدية مجانية عند الدخول</li>
  <li>خصم على العملات</li>
</ul>
<p style="margin:8px 0 0">اضغط الزر بالأسفل للانتقال 👇</p>`
  if (!form.body) {
    form.body = 'عرض خاص — ادخل الغرفة واربح جوائز VIP'
  }
  if (!form.title) form.title = 'عرض خاص 🎉'
}

async function onImagePicked(e) {
  const file = e?.target?.files?.[0]
  if (!file) return
  uploading.value = true
  error.value = ''
  const { data, error: err } = await uploadsApi.upload(file)
  uploading.value = false
  e.target.value = ''
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const payload = data?.data || data || {}
  const url = payload.url || payload.path || payload.fileUrl || ''
  if (!url) {
    error.value = t('notifications.uploadFailed')
    return
  }
  form.imageUrl = url
  toast().success(t('notifications.imageReady'))
}

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
  if (!form.title.trim()) return
  if (!form.body.trim() && !form.html.trim() && !form.imageUrl) {
    error.value = t('notifications.needContent')
    return
  }
  sending.value = true
  error.value = ''
  success.value = ''
  const payload = {
    title: form.title.trim(),
    body: form.body.trim() || form.title.trim(),
    message: form.body.trim() || form.title.trim(),
    html: form.html.trim() || undefined,
    imageUrl: form.imageUrl || undefined,
    link: form.link.trim() || undefined,
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
  form.html = ''
  form.imageUrl = ''
  form.link = ''
  form.userId = ''
  await loadHistory()
}

onMounted(loadHistory)
</script>

<style scoped>
.notif-html-preview :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}
.notif-html-preview :deep(a) {
  color: #0d6efd;
}
.notif-html-preview :deep(p) {
  margin-bottom: 0.4rem;
}
</style>
