<template>
  <div>
    <PageHeader :title="t('users.title')" :subtitle="t('users.subtitle')">
      <template #actions>
        <button
          class="btn btn-outline-warning btn-sm me-2"
          type="button"
          :disabled="loading || resettingAll"
          @click="resetAllBaseline"
        >
          {{ resettingAll ? t('users.resettingAll') : t('users.resetAllBaseline') }}
        </button>
        <button class="btn btn-ghost btn-sm" type="button" @click="load" :disabled="loading">
          <i class="bi bi-arrow-clockwise me-1"></i> {{ t('common.refresh') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />

    <div class="glass p-3 mb-3">
      <div class="row g-2 align-items-end">
        <div class="col-md-6 col-lg-4">
          <label class="form-label">{{ t('common.search') }}</label>
          <input v-model="search" class="form-control" :placeholder="t('users.searchPlaceholder')" @keyup.enter="page = 1; load()" />
        </div>
        <div class="col-md-3 col-lg-2">
          <label class="form-label">{{ t('common.status') }}</label>
          <select v-model="status" class="form-select" @change="page = 1; load()">
            <option value="">{{ t('common.all') }}</option>
            <option value="active">{{ t('common.active') }}</option>
            <option value="banned">{{ t('common.banned') }}</option>
            <option value="deleted">{{ t('users.deleted') }}</option>
          </select>
        </div>
        <div class="col-auto">
          <button class="btn btn-aurora" type="button" @click="page = 1; load()">{{ t('common.search') }}</button>
        </div>
      </div>
    </div>

    <div class="glass p-0 overflow-hidden">
      <LoadingSpinner v-if="loading" />
      <div v-else class="p-3">
        <div v-if="!users.length" class="empty-state p-5 text-center">{{ t('users.empty') }}</div>
        <div v-else class="widget-grid users-grid">
          <UserCard v-for="u in users" :key="u.id" :user="u" @ban="banUser" @unban="unbanUser" @delete="deleteUser" />
        </div>
      </div>
      <div class="d-flex justify-content-between align-items-center p-3 border-top" style="border-color: var(--border-color) !important">
        <span class="small text-muted">{{ total }} {{ t('common.total') }}</span>
        <div class="btn-group">
          <button class="btn btn-sm btn-ghost" :disabled="page <= 1" @click="page--; load()">{{ t('common.previous') }}</button>
          <button class="btn btn-sm btn-ghost disabled">{{ page }} / {{ totalPages }}</button>
          <button class="btn btn-sm btn-ghost" :disabled="page >= totalPages" @click="page++; load()">{{ t('common.next') }}</button>
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
import { usersApi } from '@/api'
import { extractList } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import { askPrompt } from '@/composables/usePrompt'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import UserCard from '@/components/UserCard.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const users = ref([])
const loading = ref(false)
const resettingAll = ref(false)
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
  const { data, error: err } = await usersApi.list({
    page: page.value,
    limit: limit.value,
    search: search.value || undefined,
    status: status.value || undefined,
  })
  loading.value = false
  if (err) {
    error.value = err.message
    users.value = []
    return
  }
  users.value = extractList(data)
  total.value = data?.meta?.total ?? data?.total ?? users.value.length
}

const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
const pendingAction = ref(null)

function resetAllBaseline() {
  pendingAction.value = { type: 'resetAll' }
  confirmTitle.value = t('users.resetAllBaseline')
  confirmMsg.value = t('users.confirmResetAll')
  confirmOpen.value = true
}

function deleteUser(u) {
  const label = u.name || u.displayName || u.email || u.id
  pendingAction.value = { type: 'delete', payload: u }
  confirmTitle.value = t('common.delete')
  confirmMsg.value = t('users.confirmDeleteNamed', { name: label })
  confirmOpen.value = true
}

async function runConfirm() {
  const action = pendingAction.value
  pendingAction.value = null
  if (!action) return
  if (action.type === 'resetAll') {
    const typed = await askPrompt({
      title: t('users.resetAllBaseline'),
      message: t('users.resetAllTypeConfirm'),
      placeholder: 'RESET',
      required: true,
    })
    if (typed !== 'RESET') return
    resettingAll.value = true
    error.value = ''
    const { data, error: err } = await usersApi.resetAllBaseline()
    resettingAll.value = false
    if (err) {
      error.value = err.message
      toast().danger(err.message)
      return
    }
    toast().success(t('users.resetAllDone', { count: data?.resetCount ?? 0 }))
    await load()
    return
  }
  if (action.type === 'delete') {
    const { error: err } = await usersApi.delete(action.payload.id)
    if (err) {
      error.value = err.message
      toast().danger(err.message)
      return
    }
    toast().success(t('app.success'))
    await load()
  }
}

async function banUser(u) {
  const reason = await askPrompt({
    title: t('users.ban'),
    message: t('users.banReasonFor', { name: u.name || u.email || u.id }),
    defaultValue: t('users.defaultBanReason'),
  })
  if (reason === null) return
  const { error: err } = await usersApi.ban(u.id, { reason })
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  toast().success(t('app.success'))
  await load()
}

async function unbanUser(u) {
  const { error: err } = await usersApi.unban(u.id)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  toast().success(t('app.success'))
  await load()
}

onMounted(load)
</script>
