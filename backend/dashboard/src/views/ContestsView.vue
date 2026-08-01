<template>
  <div>
    <PageHeader
      :title="t('contests.title')"
      :subtitle="t('contests.subtitle')"
    >
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" @click="reload" :disabled="loading">
          <i class="bi bi-arrow-clockwise me-1"></i> {{ t('common.refresh') }}
        </button>
        <button
          class="btn btn-outline-warning btn-sm"
          type="button"
          :disabled="loading || !hasOpenContests"
          @click="endAllContests"
        >
          <i class="bi bi-stop-circle me-1"></i> {{ t('contests.endAll') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" @click="openCreate">
          <i class="bi bi-plus-lg me-1"></i> {{ t('contests.newContest') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="glass overflow-hidden mb-3">
      <img :src="banner" alt="Weekly contest" class="w-100 contest-banner" @error="onBannerError" />
    </div>

    <div v-if="showEditor" class="glass p-3 mb-3">
      <h3 class="h6 mb-3">
        {{ editingId ? t('contests.editContest') : t('contests.createContest') }}
      </h3>
      <div class="row g-2">
        <div class="col-md-6">
          <label class="form-label small">{{ t('common.title') }}</label>
          <input v-model="form.title" class="form-control form-control-sm" :placeholder="t('contests.titlePlaceholder')" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('contests.category') }}</label>
          <select v-model="form.category" class="form-select form-select-sm">
            <option value="rich">{{ t('contests.rich') }}</option>
            <option value="gifts">{{ t('gifts.title') }}</option>
            <option value="popular">{{ t('contests.popular') }}</option>
          </select>
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('contests.durationUnit') }}</label>
          <select v-model="form.durationUnit" class="form-select form-control-sm" :disabled="!!editingId">
            <option value="hours">{{ t('contests.hours') }}</option>
            <option value="days">{{ t('contests.days') }}</option>
          </select>
        </div>
        <div class="col-md-3">
          <label class="form-label small">
            {{ form.durationUnit === 'hours' ? t('contests.durationHours') : t('contests.durationDays') }}
          </label>
          <input
            v-model.number="form.durationValue"
            type="number"
            min="1"
            class="form-control form-control-sm"
            :disabled="!!editingId"
          />
        </div>
        <div v-if="!editingId" class="col-md-3 d-flex align-items-end">
          <button class="btn btn-outline-secondary btn-sm w-100" type="button" @click="apply24hPreset">
            {{ t('contests.preset24h') }}
          </button>
        </div>
        <div v-if="editingId" class="col-md-3">
          <label class="form-label small">{{ t('contests.status') }}</label>
          <select v-model="form.status" class="form-select form-select-sm">
            <option value="upcoming">{{ t('contests.statusUpcoming') }}</option>
            <option value="active">{{ t('contests.statusActive') }}</option>
            <option value="ended">{{ t('contests.statusEnded') }}</option>
          </select>
        </div>
        <div class="col-md-12">
          <label class="form-label small">{{ t('common.description') }}</label>
          <input v-model="form.description" class="form-control form-control-sm" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('common.scope') }}</label>
          <select v-model="form.scope" class="form-select form-select-sm">
            <option value="global">{{ t('common.global') }}</option>
            <option value="room">{{ t('common.room') }}</option>
            <option value="agency">{{ t('common.agency') }}</option>
          </select>
        </div>
        <div v-if="form.scope === 'room'" class="col-md-9">
          <label class="form-label small">{{ t('common.room') }} {{ t('common.id') }}</label>
          <input v-model="form.roomId" class="form-control form-control-sm" />
        </div>
        <div v-if="form.scope === 'agency'" class="col-md-9">
          <label class="form-label small">{{ t('common.agency') }} {{ t('common.id') }}</label>
          <input v-model="form.agencyId" class="form-control form-control-sm" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('contests.entryFee') }}</label>
          <input v-model.number="form.entryFeeCoins" type="number" min="0" class="form-control form-control-sm" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('contests.prizeCoins') }}</label>
          <input v-model.number="form.prizeCoins" type="number" min="0" class="form-control form-control-sm" />
        </div>
        <div class="col-md-6">
          <label class="form-label small">{{ t('contests.prizeLabel') }}</label>
          <input v-model="form.prizeLabel" class="form-control form-control-sm" :placeholder="t('contests.prizePlaceholder')" />
        </div>
        <div v-if="editingId" class="col-md-6">
          <label class="form-label small">{{ t('contests.from') }} (start)</label>
          <input v-model="form.startAt" type="datetime-local" class="form-control form-control-sm" />
        </div>
        <div v-if="editingId" class="col-md-6">
          <label class="form-label small">End</label>
          <input v-model="form.endAt" type="datetime-local" class="form-control form-control-sm" />
        </div>
      </div>
      <div class="mt-3 d-flex gap-2">
        <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="saveContest">
          {{ t('common.save') }}
        </button>
        <button class="btn btn-ghost btn-sm" type="button" @click="closeEditor">{{ t('common.cancel') }}</button>
      </div>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else class="widget-grid mb-4">
      <div v-if="!contests.length" class="glass p-4 empty-state text-center">{{ t('contests.empty') }}</div>
      <article v-for="c in contests" :key="c.id" class="widget-card">
        <div class="widget-card-body">
          <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
            <h3 class="widget-card-title mb-0">{{ c.title }}</h3>
            <span class="badge" :class="statusClass(c.status)">{{ statusLabel(c.status) }}</span>
          </div>
          <p class="widget-card-meta mb-2">{{ c.description || '—' }}</p>
          <ul class="small mb-3 ps-3">
            <li>{{ t('contests.category') }}: {{ c.category }}</li>
            <li>{{ t('common.scope') }}: {{ c.scope || 'global' }}{{ c.roomId ? ` · ${c.roomId}` : '' }}{{ c.agencyId ? ` · ${c.agencyId}` : '' }}</li>
            <li>{{ t('contests.entryFee') }}: {{ formatNumber(c.entryFeeCoins || 0) }} {{ t('common.coins') }}</li>
            <li>{{ t('contests.prizeLabel') }}: {{ c.prizeLabel || formatNumber(c.prizeCoins || 0) + ' ' + t('common.coins') }}</li>
            <li>{{ t('contests.entrants') }}: {{ formatNumber(c.entrantsCount || 0) }}</li>
            <li>{{ t('contests.from') }} {{ fmtDate(c.startAt) }} → {{ fmtDate(c.endAt) }}</li>
          </ul>
          <div class="action-btns">
            <button class="btn btn-sm btn-aurora" type="button" @click="openDetail(c)">{{ t('contests.ranking') }}</button>
            <button class="btn btn-sm btn-outline-light" type="button" @click="openEdit(c)">{{ t('contests.edit') }}</button>
            <button
              v-if="c.status !== 'ended'"
              class="btn btn-sm btn-outline-warning"
              type="button"
              @click="endContest(c)"
            >
              {{ t('contests.end') }}
            </button>
            <button class="btn btn-sm btn-outline-danger" type="button" @click="deleteContest(c)">
              {{ t('contests.delete') }}
            </button>
          </div>
        </div>
      </article>
    </div>

    <div class="glass p-3 mb-4">
      <h3 class="h6 mb-3">
        {{ t('contests.ranking') }}
        <span v-if="selected" class="text-muted">· {{ selected.title }}</span>
      </h3>
      <LoadingSpinner v-if="loadingRank" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle mb-0">
          <thead>
            <tr>
              <th>#</th>
              <th>{{ t('common.user') }}</th>
              <th>{{ t('contests.score') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!ranking.length">
              <td colspan="3" class="empty-state">{{ t('contests.selectContest') }}</td>
            </tr>
            <tr v-for="row in ranking" :key="row.userId || row.rank">
              <td>{{ row.rank }}</td>
              <td>{{ row.displayName || row.username || row.userId || '—' }}</td>
              <td>{{ formatNumber(row.score ?? 0) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="glass p-3">
      <h3 class="h6 mb-3">{{ t('contests.promoPages') }}</h3>
      <div class="row g-2">
        <div v-for="e in events" :key="e.url" class="col-md-6">
          <button
            type="button"
            class="w-100 d-flex align-items-center justify-content-between border rounded p-3 text-start text-reset"
            style="border-color:rgba(255,255,255,.1);background:transparent"
            @click="openPromo(e.url)"
          >
            <span>{{ e.title }}</span>
            <i class="bi bi-box-arrow-up-right opacity-50"></i>
          </button>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { resolveAsset } from '@/utils/assets'
import { formatNumber, extractList } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import { contestsApi } from '@/api'

const { t, locale } = useI18n()

const ORIGIN = (import.meta.env.VITE_API_ORIGIN || 'https://api.adnova.bbs.tr').replace(/\/$/, '')
const banner = ref(resolveAsset('/assets/badges/contest_weekly.png'))
const error = ref('')
const success = ref('')
const contests = ref([])
const ranking = ref([])
const selected = ref(null)
const loading = ref(false)
const loadingRank = ref(false)
const saving = ref(false)
const showEditor = ref(false)
const editingId = ref(null)

const form = reactive({
  title: '',
  description: '',
  category: 'rich',
  durationUnit: 'hours',
  durationValue: 24,
  days: 7,
  entryFeeCoins: 0,
  prizeCoins: 5000,
  prizeLabel: '',
  scope: 'global',
  roomId: '',
  agencyId: '',
  status: 'active',
  startAt: '',
  endAt: '',
})

const events = computed(() => [
  { title: t('contests.promo.midyear'), url: `${ORIGIN}/promos/midyear.html` },
  { title: t('contests.promo.championship'), url: `${ORIGIN}/promos/championship.html` },
  { title: t('contests.promo.battle'), url: `${ORIGIN}/promos/battle.html` },
  { title: t('contests.promo.elite'), url: `${ORIGIN}/promos/elite-stars.html` },
  { title: t('contests.promo.agencies'), url: `${ORIGIN}/promos/agencies-monthly.html` },
  { title: t('contests.promo.recharge'), url: `${ORIGIN}/promos/recharge.html` },
  { title: t('contests.promo.vip'), url: `${ORIGIN}/promos/vip.html` },
])

const hasOpenContests = computed(() =>
  contests.value.some((c) => c && c.status !== 'ended'),
)

function statusClass(s) {
  if (s === 'active') return 'text-bg-success'
  if (s === 'upcoming') return 'text-bg-info'
  return 'text-bg-secondary'
}

function statusLabel(s) {
  if (s === 'active') return t('contests.statusActive')
  if (s === 'upcoming') return t('contests.statusUpcoming')
  if (s === 'ended') return t('contests.statusEnded')
  return s || '—'
}

function fmtDate(v) {
  if (!v) return '—'
  try {
    return new Date(v).toLocaleDateString(locale.value)
  } catch {
    return String(v)
  }
}

function toLocalInput(v) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function openPromo(url) {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

function onBannerError() {
  banner.value = `${ORIGIN}/assets/badges/contest_weekly.png`
}

function resetForm() {
  form.title = ''
  form.description = ''
  form.category = 'rich'
  form.durationUnit = 'hours'
  form.durationValue = 24
  form.days = 7
  form.entryFeeCoins = 0
  form.prizeCoins = 5000
  form.prizeLabel = ''
  form.scope = 'global'
  form.roomId = ''
  form.agencyId = ''
  form.status = 'active'
  form.startAt = ''
  form.endAt = ''
}

function apply24hPreset() {
  form.durationUnit = 'hours'
  form.durationValue = 24
  form.prizeCoins = 5000
  if (!form.title.trim()) form.title = t('contests.preset24hTitle')
  if (!form.prizeLabel.trim()) form.prizeLabel = t('contests.preset24hPrize')
}

function openCreate() {
  editingId.value = null
  resetForm()
  showEditor.value = true
}

function openEdit(c) {
  if (!c) return
  editingId.value = c.id
  form.title = c.title || ''
  form.description = c.description || ''
  form.category = c.category || 'rich'
  form.durationUnit = 'hours'
  form.durationValue = 24
  form.days = 7
  form.entryFeeCoins = Number(c.entryFeeCoins || 0)
  form.prizeCoins = Number(c.prizeCoins || 0)
  form.prizeLabel = c.prizeLabel || ''
  form.scope = c.scope || 'global'
  form.roomId = c.roomId || ''
  form.agencyId = c.agencyId || ''
  form.status = c.status || 'active'
  form.startAt = toLocalInput(c.startAt)
  form.endAt = toLocalInput(c.endAt)
  showEditor.value = true
}

function closeEditor() {
  showEditor.value = false
  editingId.value = null
  resetForm()
}

function validateForm() {
  if (!form.title.trim()) {
    error.value = t('contests.titleRequired')
    return false
  }
  if (form.scope === 'room' && !form.roomId.trim()) {
    error.value = t('contests.roomRequired')
    return false
  }
  if (form.scope === 'agency' && !form.agencyId.trim()) {
    error.value = t('contests.agencyRequired')
    return false
  }
  return true
}

async function loadContests() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await contestsApi.list()
  loading.value = false
  if (err) {
    error.value = err.message
    contests.value = []
    return
  }
  const payload = data?.data ?? data ?? {}
  contests.value = Array.isArray(payload.items)
    ? payload.items
    : Array.isArray(payload)
      ? payload
      : extractList(payload)
}

async function openDetail(c) {
  selected.value = c
  loadingRank.value = true
  const { data, error: err } = await contestsApi.get(c.id)
  loadingRank.value = false
  if (err) {
    error.value = err.message
    ranking.value = []
    return
  }
  const payload = data?.data ?? data
  ranking.value = payload?.leaderboard || []
}

async function saveContest() {
  if (!validateForm()) return
  saving.value = true
  error.value = ''
  const payload = {
    title: form.title.trim(),
    description: form.description,
    category: form.category,
    entryFeeCoins: form.entryFeeCoins,
    prizeCoins: form.prizeCoins,
    prizeLabel: form.prizeLabel,
    scope: form.scope,
    roomId: form.scope === 'room' ? form.roomId.trim() : undefined,
    agencyId: form.scope === 'agency' ? form.agencyId.trim() : undefined,
  }
  let err
  if (editingId.value) {
    payload.status = form.status
    if (form.startAt) payload.startAt = new Date(form.startAt).toISOString()
    if (form.endAt) payload.endAt = new Date(form.endAt).toISOString()
    ;({ error: err } = await contestsApi.update(editingId.value, payload))
  } else {
    if (form.durationUnit === 'hours') {
      payload.hours = Math.max(1, Number(form.durationValue) || 24)
    } else {
      payload.days = Math.max(1, Number(form.durationValue) || 7)
    }
    ;({ error: err } = await contestsApi.create(payload))
  }
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = editingId.value ? t('contests.updated') : t('contests.created')
  toast().success(success.value)
  closeEditor()
  await loadContests()
}

const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
const pendingAction = ref(null)

function endContest(c) {
  pendingAction.value = { type: 'end', payload: c }
  confirmTitle.value = t('contests.endAll')
  confirmMsg.value = t('contests.confirmEnd', { title: c.title })
  confirmOpen.value = true
}

function endAllContests() {
  pendingAction.value = { type: 'endAll' }
  confirmTitle.value = t('contests.endAll')
  confirmMsg.value = t('contests.confirmEndAll')
  confirmOpen.value = true
}

function deleteContest(c) {
  pendingAction.value = { type: 'delete', payload: c }
  confirmTitle.value = t('common.delete')
  confirmMsg.value = t('contests.confirmDelete', { title: c.title })
  confirmOpen.value = true
}

async function runConfirm() {
  const action = pendingAction.value
  pendingAction.value = null
  if (!action) return
  if (action.type === 'end') await doEndContest(action.payload)
  else if (action.type === 'endAll') await doEndAllContests()
  else if (action.type === 'delete') await doDeleteContest(action.payload)
}

async function doEndContest(c) {
  const { error: err } = await contestsApi.end(c.id)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = t('contests.ended')
  toast().success(success.value)
  await loadContests()
}

async function doEndAllContests() {
  const { data, error: err } = await contestsApi.endAll()
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const payload = data?.data ?? data ?? {}
  const count = Number(payload.ended || 0)
  success.value = t('contests.endedAll', { count })
  toast().success(success.value)
  await loadContests()
}

async function doDeleteContest(c) {
  const { error: err } = await contestsApi.remove(c.id)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = t('contests.deleted')
  toast().success(success.value)
  if (selected.value?.id === c.id) {
    selected.value = null
    ranking.value = []
  }
  if (editingId.value === c.id) closeEditor()
  await loadContests()
}

async function reload() {
  await loadContests()
  if (selected.value) await openDetail(selected.value)
}

onMounted(loadContests)
</script>

<style scoped>
.contest-banner {
  max-height: 220px;
  object-fit: cover;
  display: block;
}
</style>
