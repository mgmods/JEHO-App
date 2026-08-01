<template>
  <div>
    <PageHeader :title="t('agencies.title')" :subtitle="t('agencies.subtitle')">
      <template #actions>
        <button class="btn btn-aurora btn-sm" type="button" @click="openCreate">
          <i class="bi bi-plus-lg me-1"></i> {{ t('agencies.add') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="glass p-3 mb-3">
      <div class="row g-2 align-items-end">
        <div class="col-md-3">
          <label class="form-label">{{ t('agencies.createPrice') }}</label>
          <input v-model.number="pricing.createPriceCoins" type="number" min="0" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label">{{ t('agencies.defaultCommission') }}</label>
          <input v-model.number="pricing.defaultCommissionPercent" type="number" min="0" max="50" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label">{{ t('agencies.platformCut') }}</label>
          <input v-model.number="pricing.platformCutPercent" type="number" min="0" max="40" class="form-control" />
        </div>
        <div class="col-auto">
          <button class="btn btn-outline-light" type="button" @click="savePricing" :disabled="savingPricing">{{ t('agencies.savePricing') }}</button>
        </div>
      </div>
      <div class="small text-muted mt-2">
        {{ t('agencies.pricingHint') }}
        <span v-if="pricing.platformRevenueDiamonds != null" class="ms-2 text-warning">
          · {{ t('agencies.platformRevenue', { value: Number(pricing.platformRevenueDiamonds || 0).toLocaleString() }) }}
        </span>
      </div>
    </div>

    <!-- Pending paid applications — this is what the app means by «قيد المراجعة» -->
    <div class="glass p-0 overflow-hidden mb-3">
      <div class="p-3 border-bottom d-flex justify-content-between align-items-center">
        <h5 class="mb-0">{{ t('agencies.applications') }}</h5>
        <span class="badge bg-warning text-dark">{{ t('agencies.pendingApps', { count: pendingAppsCount }) }}</span>
      </div>
      <LoadingSpinner v-if="loadingApps" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle mb-0">
          <thead>
            <tr>
              <th>{{ t('agencies.applicant') }}</th>
              <th>{{ t('agencies.proposedName') }}</th>
              <th>{{ t('agencies.contactEmail') }}</th>
              <th>{{ t('agencies.hostsExpected') }}</th>
              <th>{{ t('common.status') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!applications.length">
              <td colspan="6" class="empty-state">{{ t('agencies.noApplications') }}</td>
            </tr>
            <tr v-for="app in applications" :key="app.id">
              <td>
                <div class="fw-medium">{{ applicantName(app) }}</div>
                <div class="small text-muted">{{ app.applicantId || app.applicant?.id || '—' }}</div>
              </td>
              <td>{{ app.proposedName || '—' }}</td>
              <td>
                <div>{{ app.contactEmail || '—' }}</div>
                <div class="small text-muted">{{ app.contactPhone || '' }}</div>
              </td>
              <td>{{ formatNumber(app.expectedHostCount || 0) }}</td>
              <td><StatusBadge :status="app.status || 'pending'" /></td>
              <td class="text-end">
                <div v-if="isPendingApp(app)" class="action-btns justify-content-end">
                  <button class="btn btn-sm btn-outline-success" type="button" @click="approveApplication(app)">
                    {{ t('agencies.approve') }}
                  </button>
                  <button class="btn btn-sm btn-outline-danger" type="button" @click="rejectApplication(app)">
                    {{ t('rechargeAgents.reject') }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="glass p-3 mb-3">
      <div class="row g-2 align-items-end">
        <div class="col-md-5">
          <label class="form-label">{{ t('common.search') }}</label>
          <input v-model="search" class="form-control" :placeholder="t('agencies.searchPlaceholder')" @keyup.enter="load()" />
        </div>
        <div class="col-md-3">
          <label class="form-label">{{ t('common.status') }}</label>
          <select v-model="status" class="form-select" @change="load()">
            <option value="">{{ t('common.all') }}</option>
            <option value="pending">{{ t('common.pending') }}</option>
            <option value="active">{{ t('common.active') }}</option>
            <option value="suspended">{{ t('common.suspended') }}</option>
          </select>
        </div>
        <div class="col-auto">
          <button class="btn btn-aurora" type="button" @click="load()">{{ t('common.filter') }}</button>
        </div>
      </div>
    </div>

    <div class="glass p-0 overflow-hidden">
      <LoadingSpinner v-if="loading" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle">
          <thead>
            <tr>
              <th>{{ t('common.agency') }}</th>
              <th>{{ t('common.owner') }}</th>
              <th>{{ t('agencies.hosts') }}</th>
              <th>{{ t('agencies.commission') }}</th>
              <th>{{ t('agencies.activationCode') }}</th>
              <th>{{ t('common.status') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!agencies.length">
              <td colspan="7" class="empty-state">{{ t('agencies.noAgencies') }}</td>
            </tr>
            <tr v-for="a in agencies" :key="a.id">
              <td>
                <div class="fw-medium">{{ a.name }}</div>
                <div class="small text-muted">{{ t('common.diamonds') }}: {{ formatNumber(a.totalDiamonds || 0) }}</div>
              </td>
              <td>{{ a.ownerName || a.owner?.displayName || a.owner?.username || a.ownerId || '—' }}</td>
              <td>{{ formatNumber(a.hostsCount ?? a.membersCount ?? a.memberCount ?? 0) }}</td>
              <td>{{ a.commission ?? a.commissionRate ?? a.commissionPercent ?? '—' }}%</td>
              <td>
                <div v-if="a.activationCode" class="d-flex align-items-center gap-1 flex-wrap">
                  <code class="font-monospace">{{ a.activationCode }}</code>
                  <button class="btn btn-sm btn-ghost" type="button" :title="t('agencies.copyCode')" @click="copyCode(a.activationCode)">
                    <i class="bi bi-clipboard"></i>
                  </button>
                  <button class="btn btn-sm btn-ghost" type="button" :title="t('agencies.regenerateCode')" @click="regenCode(a)">
                    <i class="bi bi-arrow-repeat"></i>
                  </button>
                </div>
                <span v-else class="text-muted">—</span>
              </td>
              <td><StatusBadge :status="a.status || 'pending'" /></td>
              <td class="text-end">
                <div class="action-btns justify-content-end">
                  <button
                    v-if="(a.status || '').toLowerCase() === 'pending' || (a.status || '').toLowerCase() === 'suspended'"
                    class="btn btn-sm btn-outline-success"
                    type="button"
                    @click="approve(a)"
                  >{{ (a.status || '').toLowerCase() === 'suspended' ? t('agencies.reactivate') : t('agencies.approve') }}</button>
                  <button
                    v-if="(a.status || '').toLowerCase() === 'active' || (a.status || '').toLowerCase() === 'pending'"
                    class="btn btn-sm btn-outline-warning"
                    type="button"
                    @click="suspend(a)"
                  >{{ t('agencies.suspend') }}</button>
                  <button class="btn btn-sm btn-outline-danger" type="button" @click="removeAgency(a)" :title="t('app.delete')">
                    <i class="bi bi-trash"></i>
                  </button>
                  <button class="btn btn-sm btn-ghost" type="button" @click="openMembers(a)" :title="t('common.members')">
                    <i class="bi bi-people"></i>
                  </button>
                  <button class="btn btn-sm btn-ghost" type="button" @click="openEdit(a)"><i class="bi bi-pencil"></i></button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="showModal" class="modal fade show d-block" tabindex="-1" style="background: rgba(0,0,0,.45)">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content glass-modal">
          <div class="modal-header">
            <h5 class="modal-title">{{ form.id ? t('agencies.editAgency') : t('agencies.newAgency') }}</h5>
            <button type="button" class="btn-close" @click="showModal = false"></button>
          </div>
          <form @submit.prevent="save">
            <div class="modal-body">
              <div class="mb-3">
                <label class="form-label">{{ t('common.name') }}</label>
                <input v-model="form.name" class="form-control" required />
              </div>
              <div class="mb-3">
                <label class="form-label">{{ t('agencies.contactEmail') }}</label>
                <input v-model="form.contactEmail" type="email" class="form-control" />
              </div>
              <div class="mb-3">
                <label class="form-label">{{ t('agencies.commission') }} %</label>
                <input v-model.number="form.commission" type="number" min="0" max="100" class="form-control" />
              </div>
              <div class="mb-3">
                <label class="form-label">{{ t('agencies.ownerId') }}</label>
                <input v-model="form.ownerId" class="form-control" />
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-ghost" @click="showModal = false">{{ t('common.cancel') }}</button>
              <button type="submit" class="btn btn-aurora" :disabled="saving">{{ t('common.save') }}</button>
            </div>
          </form>
        </div>
      </div>
    </div>
    <div v-if="showMembers" class="modal fade show d-block" tabindex="-1" style="background: rgba(0,0,0,.45)">
      <div class="modal-dialog modal-dialog-centered modal-lg">
        <div class="modal-content glass-modal">
          <div class="modal-header">
            <h5 class="modal-title">{{ t('common.members') }} · {{ membersAgency?.name || '' }}</h5>
            <button type="button" class="btn-close" @click="showMembers = false"></button>
          </div>
          <div class="modal-body">
            <LoadingSpinner v-if="loadingMembers" />
            <div v-else class="table-responsive">
              <table class="table table-glass table-sm align-middle mb-0">
                <thead>
                  <tr>
                    <th>{{ t('common.user') }}</th>
                    <th>{{ t('common.role') }}</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="!members.length">
                    <td colspan="3" class="empty-state">{{ t('agencies.noMembers') }}</td>
                  </tr>
                  <tr v-for="m in members" :key="m.userId">
                    <td>
                      <div class="fw-medium">{{ m.displayName || m.username || m.userId }}</div>
                      <div class="small text-muted">{{ m.userId }}</div>
                    </td>
                    <td>{{ m.role || '—' }}</td>
                    <td class="text-end">
                      <button
                        v-if="(m.role || '').toLowerCase() !== 'owner'"
                        class="btn btn-sm btn-outline-danger"
                        type="button"
                        @click="kickMember(m)"
                      >{{ t('agencies.kick') }}</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-ghost" @click="showMembers = false">{{ t('app.close') }}</button>
          </div>
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
import { agenciesApi, settingsApi } from '@/api'
import { extractList, formatNumber } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import { askPrompt } from '@/composables/usePrompt'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const agencies = ref([])
const applications = ref([])
const loading = ref(false)
const loadingApps = ref(false)
const saving = ref(false)
const savingPricing = ref(false)
const error = ref('')
const success = ref('')
const search = ref('')
const status = ref('')
const showModal = ref(false)
const showMembers = ref(false)
const loadingMembers = ref(false)
const membersAgency = ref(null)
const members = ref([])
const form = reactive({ id: null, name: '', contactEmail: '', commission: 20, ownerId: '' })
const pricing = reactive({ createPriceCoins: 50000, defaultCommissionPercent: 10, platformCutPercent: 20, platformRevenueDiamonds: 0 })

const pendingAppsCount = computed(() =>
  applications.value.filter((a) => isPendingApp(a)).length,
)

function isPendingApp(app) {
  return String(app?.status || '').toLowerCase() === 'pending'
}

function applicantName(app) {
  return (
    app?.applicant?.displayName
    || app?.applicant?.username
    || app?.applicantName
    || app?.contactEmail
    || '—'
  )
}

async function loadPricing() {
  const { data } = await settingsApi.get()
  const rows = Array.isArray(data) ? data : (data?.data || [])
  const map = {}
  if (Array.isArray(rows)) {
    for (const r of rows) {
      if (r?.key) map[r.key] = r.value
    }
  }
  pricing.createPriceCoins = Number(map.agency_create_price_coins ?? pricing.createPriceCoins)
  pricing.defaultCommissionPercent = Number(map.agency_default_commission_percent ?? pricing.defaultCommissionPercent)
  pricing.platformCutPercent = Number(map.agency_platform_cut_percent ?? pricing.platformCutPercent)
  pricing.platformRevenueDiamonds = Number(map.platform_gift_revenue_diamonds ?? pricing.platformRevenueDiamonds)
}

async function loadApplications() {
  loadingApps.value = true
  const { data, error: err } = await agenciesApi.applications({ limit: 50, status: 'pending' })
  loadingApps.value = false
  if (err) {
    error.value = err.message
    applications.value = []
    return
  }
  applications.value = extractList(data)
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await agenciesApi.list({
    search: search.value || undefined,
    status: status.value || undefined,
    limit: 50,
  })
  loading.value = false
  if (err) {
    error.value = err.message
    agencies.value = []
    return
  }
  agencies.value = extractList(data)
}

async function approveApplication(app) {
  const { data, error: err } = await agenciesApi.approveApplication(app.id, {})
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const code = data?.agency?.activationCode || data?.data?.agency?.activationCode
  success.value = code
    ? `${t('agencies.applicationApproved')} — ${t('agencies.activationCode')}: ${code}`
    : t('agencies.applicationApproved')
  toast().success(success.value)
  await Promise.all([loadApplications(), load()])
}

async function rejectApplication(app) {
  const note = await askPrompt({
    title: t('common.reject') || 'رفض',
    message: t('agencies.rejectNote'),
    defaultValue: t('agencies.defaultRejectNote'),
  })
  if (note === null) return
  const { error: err } = await agenciesApi.rejectApplication(app.id, { reviewNote: note })
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('agencies.applicationRejected')
    toast().success(success.value)
    await loadApplications()
  }
}

async function copyCode(code) {
  try {
    await navigator.clipboard.writeText(String(code))
    success.value = t('agencies.codeCopied')
    toast().success(success.value)
  } catch {
    error.value = 'Clipboard failed'
    toast().danger(error.value)
  }
}

async function regenCode(a) {
  if (!a?.id) return
  const { data, error: err } = await agenciesApi.regenerateActivationCode(a.id)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const code = data?.activationCode || data?.data?.activationCode
  success.value = code
    ? `${t('agencies.codeRegenerated')}: ${code}`
    : t('agencies.codeRegenerated')
  toast().success(success.value)
  await load()
}

function openCreate() {
  Object.assign(form, { id: null, name: '', contactEmail: '', commission: 20, ownerId: '' })
  showModal.value = true
}

function openEdit(a) {
  Object.assign(form, {
    id: a.id,
    name: a.name || '',
    contactEmail: a.contactEmail || a.email || '',
    commission: a.commission ?? a.commissionRate ?? 20,
    ownerId: a.ownerId || '',
  })
  showModal.value = true
}

async function save() {
  saving.value = true
  const payload = {
    name: form.name,
    contactEmail: form.contactEmail,
    commission: form.commission,
    commissionRate: form.commission,
    ownerId: form.ownerId || undefined,
  }
  const result = form.id
    ? await agenciesApi.update(form.id, payload)
    : await agenciesApi.create(payload)
  saving.value = false
  if (result.error) {
    error.value = result.error.message
    toast().danger(result.error.message)
    return
  }
  showModal.value = false
  success.value = t('agencies.saved')
  toast().success(success.value)
  await load()
}

async function approve(a) {
  const { error: err } = await agenciesApi.approve(a.id)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = (a.status || '').toLowerCase() === 'suspended'
      ? t('agencies.reactivated')
      : t('agencies.approved')
    toast().success(success.value)
    await load()
  }
}

async function suspend(a) {
  const reason = await askPrompt({
    title: t('agencies.suspend') || 'تعليق',
    message: t('agencies.suspendReason'),
    defaultValue: t('agencies.defaultSuspendReason'),
  })
  if (reason === null) return
  const { error: err } = await agenciesApi.suspend(a.id, { reason })
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('agencies.suspended')
    toast().success(success.value)
    await load()
  }
}

async function removeAgency(a) {
  pendingAction.value = { type: 'deleteAgency', payload: a }
  confirmTitle.value = t('common.delete')
  confirmMsg.value = t('agencies.confirmDelete', { name: a.name || a.id })
  confirmOpen.value = true
}

async function openMembers(a) {
  membersAgency.value = a
  members.value = []
  showMembers.value = true
  loadingMembers.value = true
  error.value = ''
  const { data, error: err } = await agenciesApi.get(a.id)
  loadingMembers.value = false
  if (err) {
    error.value = err.message
    return
  }
  const row = data?.data || data || {}
  members.value = Array.isArray(row.members) ? row.members : []
}

async function kickMember(m) {
  if (!membersAgency.value?.id || !m?.userId) return
  pendingAction.value = { type: 'kick', payload: m }
  confirmTitle.value = t('common.delete')
  confirmMsg.value = t('agencies.confirmKick', { name: m.displayName || m.userId })
  confirmOpen.value = true
}

const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
const pendingAction = ref(null)

async function runConfirm() {
  const action = pendingAction.value
  pendingAction.value = null
  if (!action) return
  if (action.type === 'deleteAgency') {
    const { error: err } = await agenciesApi.delete(action.payload.id)
    if (err) {
      error.value = err.message
      toast().danger(err.message)
    } else {
      success.value = t('agencies.deleted')
      toast().success(success.value)
      await load()
    }
  } else if (action.type === 'kick') {
    const m = action.payload
    const { error: err } = await agenciesApi.removeMember(membersAgency.value.id, m.userId)
    if (err) {
      error.value = err.message
      toast().danger(err.message)
    } else {
      success.value = t('agencies.memberRemoved')
      toast().success(success.value)
      await openMembers(membersAgency.value)
      await load()
    }
  }
}

async function savePricing() {
  savingPricing.value = true
  error.value = ''
  const { error: err } = await settingsApi.update({
    agency_create_price_coins: String(pricing.createPriceCoins),
    agency_default_commission_percent: String(pricing.defaultCommissionPercent),
    agency_platform_cut_percent: String(pricing.platformCutPercent),
  })
  savingPricing.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('agencies.pricingSaved')
    toast().success(success.value)
  }
}

onMounted(async () => {
  await loadPricing()
  await Promise.all([load(), loadApplications()])
})
</script>
