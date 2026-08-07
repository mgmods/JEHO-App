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
        <div class="col-md-2">
          <label class="form-label d-block">{{ t('agencies.createFree') }}</label>
          <div class="form-check form-switch mt-2">
            <input
              id="agencyCreateFree"
              v-model="pricing.createFree"
              class="form-check-input"
              type="checkbox"
              @change="onCreateFreeToggle"
            />
            <label class="form-check-label small" for="agencyCreateFree">
              {{ pricing.createFree ? t('agencies.createFreeOn') : t('agencies.createFreeOff') }}
            </label>
          </div>
        </div>
        <div class="col-md-2">
          <label class="form-label">{{ t('agencies.createPrice') }}</label>
          <input
            v-model.number="pricing.createPriceCoins"
            type="number"
            :min="pricing.createFree ? 0 : 1000"
            class="form-control"
            :disabled="pricing.createFree"
          />
          <div v-if="pricing.createFree" class="form-text small">
            {{ t('agencies.createFreePriceHint') }}
          </div>
        </div>
        <div class="col-md-2">
          <label class="form-label">{{ t('agencies.hostShare') }}</label>
          <input
            v-model.number="pricing.hostSharePercent"
            type="number"
            min="0"
            max="100"
            class="form-control"
            @input="onShareInput('host')"
          />
        </div>
        <div class="col-md-2">
          <label class="form-label">{{ t('agencies.defaultCommission') }}</label>
          <input
            v-model.number="pricing.defaultCommissionPercent"
            type="number"
            min="0"
            max="100"
            class="form-control"
            @input="onShareInput('agency')"
          />
        </div>
        <div class="col-md-2">
          <label class="form-label">{{ t('agencies.platformCut') }}</label>
          <input
            v-model.number="pricing.platformCutPercent"
            type="number"
            min="0"
            max="100"
            class="form-control"
            @input="onShareInput('platform')"
          />
          <div class="form-text small">{{ t('agencies.platformCutAutoHint') }}</div>
        </div>
        <div class="col-md-2">
          <label class="form-label d-block">{{ t('agencies.autoApprove') }}</label>
          <div class="form-check form-switch mt-2">
            <input
              id="agencyAutoApprove"
              v-model="pricing.autoApproveAfterPayment"
              class="form-check-input"
              type="checkbox"
            />
            <label class="form-check-label small" for="agencyAutoApprove">
              {{ pricing.autoApproveAfterPayment ? t('agencies.autoApproveOn') : t('agencies.autoApproveOff') }}
            </label>
          </div>
        </div>
        <div class="col-auto">
          <button class="btn btn-outline-light" type="button" @click="savePricing" :disabled="savingPricing">
            {{ t('agencies.savePricing') }}
          </button>
        </div>
      </div>
      <div class="small text-muted mt-2">
        {{ t('agencies.pricingHint') }}
        <span
          class="ms-2"
          :class="sharesSum === 100 ? 'text-success' : 'text-danger'"
        >
          · {{ t('agencies.sharesSum', { value: sharesSum }) }}
        </span>
        <span v-if="sharesSum !== 100" class="ms-2 text-danger">
          · {{ t('agencies.sharesMustSum100') }}
        </span>
        <button
          type="button"
          class="btn btn-link btn-sm p-0 ms-2 align-baseline"
          @click="applySafeSharePreset"
        >
          {{ t('agencies.applySafePreset') }}
        </button>
        <span v-if="pricing.platformRevenueDiamonds != null" class="ms-2 text-warning">
          · {{ t('agencies.platformRevenue', { value: Number(pricing.platformRevenueDiamonds || 0).toLocaleString() }) }}
        </span>
      </div>
      <div class="row g-2 mt-2">
        <div class="col-md-12">
          <div
            class="rounded border px-3 py-2 small"
            :class="economySim.profitable ? 'border-success bg-success bg-opacity-10' : 'border-danger bg-danger bg-opacity-10'"
          >
            <div class="fw-semibold mb-1">{{ t('agencies.economyTitle') }}</div>
            <div>{{ t('agencies.economyFlow') }}</div>
            <div class="mt-1">
              {{ t('agencies.economyOn1m', {
                mint: economySim.mint.toLocaleString(),
                host: economySim.hostD.toLocaleString(),
                agency: economySim.agencyD.toLocaleString(),
                platform: economySim.platformD.toLocaleString(),
                hostUsd: economySim.hostUsd,
                agencyUsd: economySim.agencyUsd,
                liability: economySim.liabilityUsd,
                revenue: economySim.packageUsd,
                profit: economySim.profitUsd,
              }) }}
            </div>
            <div class="mt-1 fw-semibold" :class="economySim.profitable ? 'text-success' : 'text-danger'">
              {{ economySim.profitable ? t('agencies.economyProfitOk') : t('agencies.economyLossWarn') }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Pending paid applications — this is what the app means by «قيد المراجعة» -->
    <div class="glass p-0 overflow-hidden mb-3">
      <div class="p-3 border-bottom d-flex justify-content-between align-items-center">
        <h5 class="mb-0">{{ t('agencies.applications') }}</h5>
        <span class="badge bg-warning text-dark">{{ t('agencies.pendingApps', { count: pendingAppsCount }) }}</span>
      </div>
      <div class="px-3 pt-2">
        <BulkActionBar
          :count="applications.length"
          :selected-count="appSelectedCount"
          :all-selected="appAllSelected"
          :some-selected="appSomeSelected"
          :busy="bulkBusy"
          :actions="appBulkActions"
          @toggle-all="appToggleAll"
          @clear="appClear"
          @action="onAppBulkAction"
        />
      </div>
      <LoadingSpinner v-if="loadingApps" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle mb-0">
          <thead>
            <tr>
              <th style="width:2.2rem"></th>
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
              <td colspan="7" class="empty-state">{{ t('agencies.noApplications') }}</td>
            </tr>
            <tr v-for="app in applications" :key="app.id">
              <td>
                <input
                  type="checkbox"
                  class="form-check-input"
                  :checked="appIsSelected(app.id)"
                  @change="appToggle(app.id)"
                />
              </td>
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
      <div class="px-3 pt-2">
        <BulkActionBar
          :count="agencies.length"
          :selected-count="agencySelectedCount"
          :all-selected="agencyAllSelected"
          :some-selected="agencySomeSelected"
          :busy="bulkBusy"
          :actions="agencyBulkActions"
          @toggle-all="agencyToggleAll"
          @clear="agencyClear"
          @action="onAgencyBulkAction"
        />
      </div>
      <LoadingSpinner v-if="loading" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle">
          <thead>
            <tr>
              <th style="width:2.2rem"></th>
              <th>{{ t('common.agency') }}</th>
              <th>{{ t('agencies.publicId') }}</th>
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
              <td colspan="9" class="empty-state">{{ t('agencies.noAgencies') }}</td>
            </tr>
            <tr v-for="a in agencies" :key="a.id">
              <td>
                <input
                  type="checkbox"
                  class="form-check-input"
                  :checked="agencyIsSelected(a.id)"
                  @change="agencyToggle(a.id)"
                />
              </td>
              <td>
                <div class="fw-medium d-flex align-items-center gap-1">
                  <span>{{ a.name }}</span>
                  <i
                    v-if="a.isVerified"
                    class="bi bi-patch-check-fill text-info"
                    :title="t('agencies.verified')"
                  ></i>
                </div>
                <div class="small text-muted">{{ t('common.diamonds') }}: {{ formatNumber(a.totalDiamonds || 0) }}</div>
              </td>
              <td>
                <code v-if="a.publicId" class="font-monospace">{{ a.publicId }}</code>
                <span v-else class="text-muted">—</span>
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
              <div class="mb-3" v-if="form.id">
                <label class="form-label">{{ t('agencies.publicId') }}</label>
                <input
                  v-model="form.publicId"
                  class="form-control font-monospace"
                  maxlength="12"
                  :placeholder="t('agencies.publicIdHint')"
                />
                <div class="form-text">{{ t('agencies.publicIdAdminOnly') }}</div>
              </div>
              <div class="form-check form-switch mb-3" v-if="form.id">
                <input id="agencyVerified" v-model="form.isVerified" class="form-check-input" type="checkbox" />
                <label class="form-check-label" for="agencyVerified">{{ t('agencies.verified') }}</label>
              </div>
              <div class="mb-3" v-if="form.id">
                <label class="form-label">{{ t('agencies.exclusiveFrameCode') }}</label>
                <input v-model="form.exclusiveFrameCode" class="form-control font-monospace" placeholder="frame_mikoo_235_monthly_a_agency" />
              </div>
              <div class="mb-3" v-if="form.id">
                <label class="form-label">{{ t('agencies.exclusiveRoomCardCode') }}</label>
                <input v-model="form.exclusiveRoomCardCode" class="form-control font-monospace" />
              </div>
              <div class="mb-3" v-if="form.id">
                <label class="form-label">{{ t('agencies.exclusiveFrameUrl') }}</label>
                <input v-model="form.exclusiveFrameUrl" class="form-control" placeholder="/assets/cosmetics/frames/..." />
              </div>
              <div class="alert alert-info small py-2" v-if="form.id">
                {{ t('agencies.exclusiveHint') }}
              </div>
            </div>
            <div class="modal-footer flex-wrap gap-2">
              <button type="button" class="btn btn-ghost" @click="showModal = false">{{ t('common.cancel') }}</button>
              <button
                v-if="form.id"
                type="button"
                class="btn btn-outline-warning"
                :disabled="grantingExclusives"
                @click="grantExclusives"
              >
                {{ t('agencies.grantExclusives') }}
              </button>
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
import { useBulkSelection } from '@/composables/useBulkSelection'
import { runBulk } from '@/composables/useBulk'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import BulkActionBar from '@/components/BulkActionBar.vue'

const { t } = useI18n()

const agencies = ref([])
const applications = ref([])
const loading = ref(false)
const loadingApps = ref(false)
const saving = ref(false)
const savingPricing = ref(false)
const bulkBusy = ref(false)
const error = ref('')
const success = ref('')
const search = ref('')
const status = ref('')
const showModal = ref(false)
const showMembers = ref(false)
const loadingMembers = ref(false)
const membersAgency = ref(null)
const members = ref([])
const form = reactive({
  id: null,
  name: '',
  contactEmail: '',
  commission: 20,
  ownerId: '',
  publicId: '',
  isVerified: false,
  exclusiveFrameCode: '',
  exclusiveRoomCardCode: '',
  exclusiveFrameUrl: '',
})
const grantingExclusives = ref(false)
const pricing = reactive({
  createPriceCoins: 50000,
  createFree: false,
  /** Owner-safe preset: host 45 / agency 15 / platform 40 (must sum 100). */
  defaultCommissionPercent: 15,
  platformCutPercent: 40,
  hostSharePercent: 45,
  autoApproveAfterPayment: false,
  platformRevenueDiamonds: 0,
  /** Coin → diamond mint ratio (fixed product rule). */
  giftMintRatio: 0.35,
  diamondUsdRate: 0.0000666667,
  /** Example paid 1M coin package USD (Play catalog). */
  packageUsdFor1m: 99.99,
})

const {
  selectedIds: appSelectedIds,
  selectedCount: appSelectedCount,
  allSelected: appAllSelected,
  someSelected: appSomeSelected,
  isSelected: appIsSelected,
  toggle: appToggle,
  clear: appClear,
  toggleAll: appToggleAll,
} = useBulkSelection(applications)

const {
  selectedIds: agencySelectedIds,
  selectedCount: agencySelectedCount,
  allSelected: agencyAllSelected,
  someSelected: agencySomeSelected,
  isSelected: agencyIsSelected,
  toggle: agencyToggle,
  clear: agencyClear,
  toggleAll: agencyToggleAll,
} = useBulkSelection(agencies)

const appBulkActions = computed(() => [
  { key: 'approve', label: t('bulk.approveSelected'), icon: 'bi-check2', variant: 'btn-outline-success' },
  { key: 'reject', label: t('bulk.rejectSelected'), icon: 'bi-x-lg', variant: 'btn-outline-danger' },
])
const agencyBulkActions = computed(() => [
  { key: 'approve', label: t('bulk.approveSelected'), icon: 'bi-check2', variant: 'btn-outline-success' },
  { key: 'suspend', label: t('bulk.suspendSelected'), icon: 'bi-pause', variant: 'btn-outline-warning' },
  { key: 'delete', label: t('bulk.deleteSelected'), icon: 'bi-trash', variant: 'btn-outline-danger' },
])

async function onAppBulkAction(key) {
  const ids = appSelectedIds.value
  if (!ids.length) return
  confirmTitle.value = t('bulk.selectAll')
  confirmMsg.value = key === 'approve'
    ? t('bulk.confirmApprove', { count: ids.length })
    : t('bulk.confirmReject', { count: ids.length })
  pendingAction.value = async () => {
    bulkBusy.value = true
    const data = await runBulk({
      resource: 'agency-applications',
      action: key,
      ids,
      reason: key === 'reject' ? t('agencies.defaultRejectNote') : undefined,
      t,
    })
    bulkBusy.value = false
    if (data) {
      appClear()
      await Promise.all([loadApplications(), load()])
    }
  }
  confirmOpen.value = true
}

async function onAgencyBulkAction(key) {
  const ids = agencySelectedIds.value
  if (!ids.length) return
  const messages = {
    approve: t('bulk.confirmApprove', { count: ids.length }),
    suspend: t('bulk.confirmSuspend', { count: ids.length }),
    delete: t('bulk.confirmDelete', { count: ids.length }),
  }
  confirmTitle.value = t('bulk.selectAll')
  confirmMsg.value = messages[key] || messages.delete
  pendingAction.value = async () => {
    bulkBusy.value = true
    const data = await runBulk({
      resource: 'agencies',
      action: key,
      ids,
      reason: key === 'suspend' ? t('agencies.defaultSuspendReason') : undefined,
      t,
    })
    bulkBusy.value = false
    if (data) {
      agencyClear()
      await load()
    }
  }
  confirmOpen.value = true
}

const sharesSum = computed(() =>
  Number(pricing.defaultCommissionPercent || 0)
  + Number(pricing.platformCutPercent || 0)
  + Number(pricing.hostSharePercent || 0),
)

function clampPct(n, max = 100) {
  const v = Number(n)
  if (!Number.isFinite(v)) return 0
  return Math.max(0, Math.min(max, Math.round(v)))
}

function clampPricingFields() {
  pricing.defaultCommissionPercent = clampPct(pricing.defaultCommissionPercent, 100)
  pricing.platformCutPercent = clampPct(pricing.platformCutPercent, 100)
  pricing.hostSharePercent = clampPct(pricing.hostSharePercent, 100)
}

/** Keep the three shares as a real 100% pie (platform auto-fills remainder). */
function onShareInput(which) {
  clampPricingFields()
  const host = Number(pricing.hostSharePercent || 0)
  const agency = Number(pricing.defaultCommissionPercent || 0)
  const platform = Number(pricing.platformCutPercent || 0)
  if (which === 'host' || which === 'agency') {
    const cashable = host + agency
    if (cashable > 100) {
      if (which === 'host') {
        pricing.hostSharePercent = Math.max(0, 100 - agency)
      } else {
        pricing.defaultCommissionPercent = Math.max(0, 100 - host)
      }
    }
    pricing.platformCutPercent = Math.max(
      0,
      100 - Number(pricing.hostSharePercent || 0) - Number(pricing.defaultCommissionPercent || 0),
    )
  } else {
    // Platform edited: shrink cashable proportionally if needed.
    const rest = Math.max(0, 100 - platform)
    const cash = host + agency
    if (cash <= 0) {
      pricing.hostSharePercent = 0
      pricing.defaultCommissionPercent = 0
    } else if (cash !== rest) {
      const scale = rest / cash
      pricing.hostSharePercent = clampPct(Math.floor(host * scale), 100)
      pricing.defaultCommissionPercent = Math.max(
        0,
        rest - Number(pricing.hostSharePercent || 0),
      )
    }
    pricing.platformCutPercent = clampPct(
      100 - Number(pricing.hostSharePercent || 0) - Number(pricing.defaultCommissionPercent || 0),
      100,
    )
  }
}

function applySafeSharePreset() {
  pricing.hostSharePercent = 45
  pricing.defaultCommissionPercent = 15
  pricing.platformCutPercent = 40
}

/** Simulation: paid 1M coins gifted in agency room with current splits. */
const economySim = computed(() => {
  const coins = 1_000_000
  const mintR = Math.min(1, Math.max(0, Number(pricing.giftMintRatio) || 0.35))
  const rate = Math.max(0, Number(pricing.diamondUsdRate) || 0.00005)
  const packageUsd = Math.max(0, Number(pricing.packageUsdFor1m) || 99.99)
  const h = Number(pricing.hostSharePercent || 0)
  const a = Number(pricing.defaultCommissionPercent || 0)
  const p = Number(pricing.platformCutPercent || 0)
  const sum = h + a + p || 1
  const hn = (h * 100) / sum
  const an = (a * 100) / sum
  const pn = Math.max(0, 100 - hn - an)
  const mint = Math.floor(coins * mintR)
  const hostD = Math.floor((mint * hn) / 100)
  const agencyD = Math.floor((mint * an) / 100)
  const platformD = Math.max(0, mint - hostD - agencyD)
  const hostUsd = Number((hostD * rate).toFixed(2))
  const agencyUsd = Number((agencyD * rate).toFixed(2))
  const liabilityUsd = Number(((hostD + agencyD) * rate).toFixed(2))
  const profitUsd = Number((packageUsd - liabilityUsd).toFixed(2))
  return {
    mint,
    hostD,
    agencyD,
    platformD,
    hostUsd,
    agencyUsd,
    liabilityUsd,
    packageUsd,
    profitUsd,
    profitable: profitUsd > 0,
  }
})

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
  const freeRaw = String(map.agency_create_free ?? 'false').toLowerCase()
  // Legacy: price 0 was used to mean free — migrate into the free toggle.
  pricing.createFree =
    freeRaw === 'true' || freeRaw === '1' || freeRaw === 'yes'
    || Number(pricing.createPriceCoins) === 0
  if (pricing.createFree) {
    pricing.createPriceCoins = 0
  } else if (!Number.isFinite(pricing.createPriceCoins) || pricing.createPriceCoins < 1000) {
    pricing.createPriceCoins = 1000
  }
  pricing.defaultCommissionPercent = Number(map.agency_default_commission_percent ?? pricing.defaultCommissionPercent)
  pricing.platformCutPercent = Number(map.agency_platform_cut_percent ?? pricing.platformCutPercent)
  pricing.platformRevenueDiamonds = Number(map.platform_gift_revenue_diamonds ?? pricing.platformRevenueDiamonds)
  const rateRaw = Number(map['economy.diamondUsdRate'])
  if (Number.isFinite(rateRaw) && rateRaw > 0) pricing.diamondUsdRate = rateRaw
  const autoRaw = String(map.agency_auto_approve_after_payment ?? 'false').toLowerCase()
  pricing.autoApproveAfterPayment = autoRaw === 'true' || autoRaw === '1' || autoRaw === 'yes'
  const hostRaw = map.agency_host_share_percent
  pricing.hostSharePercent = hostRaw != null && hostRaw !== ''
    ? Number(hostRaw)
    : Math.max(0, 100 - Number(pricing.defaultCommissionPercent || 0) - Number(pricing.platformCutPercent || 0))
  clampPricingFields()
  // Force a clean pie for display (legacy independent shares may not sum 100).
  if (sharesSum.value !== 100) {
    onShareInput('host')
  }
}

function onCreateFreeToggle() {
  if (pricing.createFree) {
    pricing.createPriceCoins = 0
  } else if (!Number.isFinite(pricing.createPriceCoins) || pricing.createPriceCoins < 1000) {
    pricing.createPriceCoins = 50000
  }
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
  Object.assign(form, {
    id: null,
    name: '',
    contactEmail: '',
    commission: 20,
    ownerId: '',
    publicId: '',
    isVerified: false,
    exclusiveFrameCode: '',
    exclusiveRoomCardCode: '',
    exclusiveFrameUrl: '',
  })
  showModal.value = true
}

function openEdit(a) {
  Object.assign(form, {
    id: a.id,
    name: a.name || '',
    contactEmail: a.contactEmail || a.email || '',
    commission: a.commission ?? a.commissionRate ?? a.commissionPercent ?? 20,
    ownerId: a.ownerId || '',
    publicId: a.publicId || '',
    isVerified: !!a.isVerified,
    exclusiveFrameCode: a.exclusiveFrameCode || '',
    exclusiveRoomCardCode: a.exclusiveRoomCardCode || '',
    exclusiveFrameUrl: a.exclusiveFrameUrl || '',
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
  if (form.id) {
    payload.publicId = form.publicId || null
    payload.isVerified = !!form.isVerified
    payload.exclusiveFrameCode = form.exclusiveFrameCode || null
    payload.exclusiveRoomCardCode = form.exclusiveRoomCardCode || null
    payload.exclusiveFrameUrl = form.exclusiveFrameUrl || null
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

async function grantExclusives() {
  if (!form.id) return
  grantingExclusives.value = true
  error.value = ''
  const { data, error: err } = await agenciesApi.grantExclusives(form.id, {
    frameCode: form.exclusiveFrameCode || undefined,
    roomCardCode: form.exclusiveRoomCardCode || undefined,
    days: 90,
    includeManagers: true,
    includeHosts: false,
  })
  grantingExclusives.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const ok = data?.okCount ?? data?.data?.okCount ?? 0
  success.value = t('agencies.exclusivesGranted', { count: ok })
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
  if (typeof action === 'function') {
    await action()
    return
  }
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
  clampPricingFields()
  if (sharesSum.value !== 100) {
    onShareInput('host')
  }
  if (sharesSum.value !== 100) {
    savingPricing.value = false
    error.value = t('agencies.sharesMustSum100')
    toast().danger(error.value)
    return
  }
  // Guard: cashable (host+agency) too high still usually profitable at Play prices,
  // but platform share under 25% is marked unsafe for operator messaging.
  const platform = Number(pricing.platformCutPercent || 0)
  if (platform < 25) {
    const ok = window.confirm(t('agencies.lowPlatformConfirm', { value: platform }))
    if (!ok) {
      savingPricing.value = false
      return
    }
  }
  const createFree = !!pricing.createFree
  let coins = Math.floor(Number(pricing.createPriceCoins) || 0)
  if (createFree) {
    coins = 0
  } else {
    coins = Math.max(1000, coins > 0 ? coins : 1000)
    pricing.createPriceCoins = coins
  }
  const { error: err } = await settingsApi.update({
    agency_create_free: createFree ? 'true' : 'false',
    agency_create_price_coins: String(coins),
    agency_default_commission_percent: String(pricing.defaultCommissionPercent),
    agency_platform_cut_percent: String(pricing.platformCutPercent),
    agency_host_share_percent: String(pricing.hostSharePercent),
    agency_auto_approve_after_payment: pricing.autoApproveAfterPayment ? 'true' : 'false',
  })
  savingPricing.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('agencies.pricingSaved')
    toast().success(success.value)
    await loadPricing()
  }
}

onMounted(async () => {
  await loadPricing()
  await Promise.all([load(), loadApplications()])
})
</script>
