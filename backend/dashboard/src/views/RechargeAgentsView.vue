<template>
  <div>
    <PageHeader
      :title="t('rechargeAgents.title')"
      subtitle="وكلاء البيع الداخلي فقط — بدون واتساب ودون طلبات انضمام مدفوعة"
    />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="row g-3 mb-4">
      <div class="col-md-3" v-for="stat in stats" :key="stat.label">
        <div class="glass p-3 h-100">
          <div class="text-secondary small">{{ stat.label }}</div>
          <div class="fs-3 fw-bold mt-1">{{ stat.value }}</div>
        </div>
      </div>
    </div>

    <div class="glass p-4 mb-4">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
          <h5 class="mb-1">أسعار الجملة (للمرجعية الإدارية)</h5>
          <p class="text-secondary small mb-0">الوكلاء يُعيَّنون يدوياً برصيد float — لا يوجد انضمام من التطبيق.</p>
        </div>
        <button class="btn btn-aurora" :disabled="saving" @click="savePricing">حفظ الأسعار</button>
      </div>
      <div class="row g-3">
        <div class="col-md-4">
          <label class="form-label">سعر الجملة لكل 100 عملة (USDT)</label>
          <input v-model.number="pricing.wholesalePer100CoinsUsdt" type="number" min="0" step="0.00000001" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">سعر البيع المقترح لكل 100 عملة</label>
          <input v-model.number="pricing.suggestedRetailPer100CoinsUsdt" type="number" min="0" step="0.00000001" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">مثال: تكلفة 100,000 عملة</label>
          <div class="form-control bg-transparent">{{ quoteExample }} USDT</div>
        </div>
      </div>
    </div>

    <div class="glass p-3 mb-4">
      <h5 class="mb-3">تعيين وكيل بيع داخلي</h5>
      <form class="row g-2 align-items-end" @submit.prevent="assign">
        <div class="col-lg-5">
          <label class="form-label">{{ t('rechargeAgents.userId') }}</label>
          <input v-model.trim="assignForm.userId" class="form-control" required placeholder="userId / UUID" />
        </div>
        <div class="col-lg-3">
          <label class="form-label">{{ t('rechargeAgents.initialFloat') }}</label>
          <input v-model.number="assignForm.floatCoins" type="number" min="0" class="form-control" />
        </div>
        <div class="col-lg-2">
          <label class="form-label">العمولة %</label>
          <input v-model.number="assignForm.commissionPercent" type="number" min="0" max="100" step=".01" class="form-control" />
        </div>
        <div class="col-auto">
          <button class="btn btn-aurora" :disabled="saving">تعيين الوكيل</button>
        </div>
      </form>
    </div>

    <div class="glass p-0 overflow-hidden">
      <div class="p-3 border-bottom"><h5 class="mb-0">وكلاء البيع الداخلي</h5></div>
      <div class="px-3 pt-2">
        <BulkActionBar
          :count="agents.length"
          :selected-count="agentSelectedCount"
          :all-selected="agentAllSelected"
          :some-selected="agentSomeSelected"
          :busy="bulkBusy"
          :actions="agentBulkActions"
          @toggle-all="agentToggleAll"
          @clear="agentClear"
          @action="onAgentBulkAction"
        />
      </div>
      <LoadingSpinner v-if="loading" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle mb-0">
          <thead>
            <tr>
              <th style="width:2.2rem"></th>
              <th>الوكيل</th>
              <th>الرصيد (float)</th>
              <th>مبيعات اليوم</th>
              <th>العمولة</th>
              <th>الحالة</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!agents.length">
              <td colspan="7" class="empty-state">لا يوجد وكلاء بيع داخلي — عيّن من النموذج أعلاه</td>
            </tr>
            <tr v-for="agent in agents" :key="agent.id">
              <td>
                <input
                  type="checkbox"
                  class="form-check-input"
                  :checked="agentIsSelected(agent.id)"
                  @change="agentToggle(agent.id)"
                />
              </td>
              <td>
                <div class="fw-semibold">{{ userName(agent) }}</div>
                <small class="text-muted">{{ agent.userId }}</small>
              </td>
              <td>{{ formatNumber(agent.floatCoins) }}</td>
              <td>{{ formatNumber(agent.dailySoldCoins) }}</td>
              <td>{{ ((agent.commissionBps || 0) / 100).toFixed(2) }}%</td>
              <td><StatusBadge :status="agent.status" /></td>
              <td>
                <div class="d-flex gap-2 justify-content-end flex-wrap">
                  <button class="btn btn-sm btn-outline-light" @click="adjustFloat(agent)">تعديل الرصيد</button>
                  <button
                    v-if="agent.status === 'active'"
                    class="btn btn-sm btn-outline-warning"
                    @click="setStatus(agent, 'suspended')"
                  >تعليق</button>
                  <button
                    v-else
                    class="btn btn-sm btn-outline-success"
                    @click="setStatus(agent, 'active')"
                  >تفعيل</button>
                  <button class="btn btn-sm btn-outline-danger" @click="removeAgent(agent)">حذف نهائي</button>
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
      @cancel="pendingAction = null"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rechargeAgentsApi } from '@/api'
import { extractList, formatNumber } from '@/composables/useUtils'
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
const agents = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const bulkBusy = ref(false)
const pricing = reactive({
  membershipFeeUsdt: 0,
  wholesalePer100CoinsUsdt: 0.01,
  suggestedRetailPer100CoinsUsdt: 0.015,
  minInitialCoins: 10000,
  maxInitialCoins: 10000000,
})
const assignForm = reactive({ userId: '', floatCoins: 0, commissionPercent: 0 })

const {
  selectedIds: agentSelectedIds,
  selectedCount: agentSelectedCount,
  allSelected: agentAllSelected,
  someSelected: agentSomeSelected,
  isSelected: agentIsSelected,
  toggle: agentToggle,
  clear: agentClear,
  toggleAll: agentToggleAll,
} = useBulkSelection(agents)

const agentBulkActions = computed(() => [
  { key: 'activate', label: t('bulk.activateSelected'), icon: 'bi-check2-circle', variant: 'btn-outline-success' },
  { key: 'suspend', label: t('bulk.suspendSelected'), icon: 'bi-pause', variant: 'btn-outline-warning' },
  { key: 'delete', label: t('bulk.deleteSelected'), icon: 'bi-trash', variant: 'btn-outline-danger' },
])

async function onAgentBulkAction(key) {
  const ids = agentSelectedIds.value
  if (!ids.length) return
  const messages = {
    activate: t('bulk.confirmApprove', { count: ids.length }),
    suspend: t('bulk.confirmSuspend', { count: ids.length }),
    delete: t('bulk.confirmDelete', { count: ids.length }),
  }
  confirmTitle.value = t('bulk.selectAll')
  confirmMsg.value = messages[key] || messages.delete
  pendingAction.value = async () => {
    bulkBusy.value = true
    const data = await runBulk({ resource: 'recharge-agents', action: key, ids, t })
    bulkBusy.value = false
    if (data) {
      agentClear()
      await load()
    }
  }
  confirmOpen.value = true
}

const stats = computed(() => [
  { label: 'الوكلاء النشطون', value: agents.value.filter((x) => x.status === 'active').length },
  {
    label: 'إجمالي أرصدة الوكلاء (float)',
    value: formatNumber(agents.value.reduce((n, x) => n + Number(x.floatCoins || 0), 0)),
  },
  {
    label: 'مبيعات اليوم (عملات)',
    value: formatNumber(agents.value.reduce((n, x) => n + Number(x.dailySoldCoins || 0), 0)),
  },
  {
    label: 'حد يومي الإجمالي',
    value: formatNumber(agents.value.reduce((n, x) => n + Number(x.dailyLimitCoins || 0), 0)),
  },
])
const quoteExample = computed(() =>
  money((100000 / 100) * Number(pricing.wholesalePer100CoinsUsdt || 0)),
)

function money(value) {
  return Number(value || 0).toLocaleString(undefined, { maximumFractionDigits: 8 })
}
function userName(item) {
  return item.user?.displayName || item.user?.username || item.userId
}
function unwrap(data) {
  return data?.data || data || {}
}

async function load() {
  loading.value = true
  error.value = ''
  const [agentsResult, pricingResult] = await Promise.all([
    rechargeAgentsApi.agents(),
    rechargeAgentsApi.pricing(),
  ])
  loading.value = false
  const failed = agentsResult.error || pricingResult.error
  if (failed) return (error.value = failed.message)
  agents.value = extractList(agentsResult.data)
  Object.assign(pricing, unwrap(pricingResult.data))
}

async function savePricing() {
  saving.value = true
  const result = await rechargeAgentsApi.updatePricing({ ...pricing })
  saving.value = false
  if (result.error) return (error.value = result.error.message)
  Object.assign(pricing, unwrap(result.data))
  success.value = 'تم حفظ الأسعار'
}

async function assign() {
  saving.value = true
  const result = await rechargeAgentsApi.assign({
    userId: assignForm.userId,
    floatCoins: Number(assignForm.floatCoins || 0),
    commissionBps: Math.round(Number(assignForm.commissionPercent || 0) * 100),
  })
  saving.value = false
  if (result.error) {
    error.value = result.error.message
    toast().danger(error.value)
    return
  }
  Object.assign(assignForm, { userId: '', floatCoins: 0, commissionPercent: 0 })
  success.value = 'تم تعيين وكيل البيع الداخلي'
  toast().success(success.value)
  await load()
}

async function setStatus(agent, status) {
  const noteRaw = await askPrompt({
    title: status === 'suspended' ? 'تعليق الوكيل' : 'تفعيل الوكيل',
    label: status === 'suspended' ? 'سبب التعليق' : 'ملاحظة التفعيل',
    defaultValue: '',
  })
  if (noteRaw === null) return
  const result = await rechargeAgentsApi.update(agent.id, { status, notes: noteRaw })
  if (result.error) {
    error.value = result.error.message
    toast().danger(error.value)
  } else {
    success.value = status === 'active' ? 'تم تفعيل الوكيل' : 'تم تعليق الوكيل'
    toast().success(success.value)
    await load()
  }
}

async function adjustFloat(agent) {
  const raw = await askPrompt({
    title: 'تعديل الرصيد',
    label: 'كمية الإضافة (+) أو الخصم (-)',
    defaultValue: '0',
    required: true,
  })
  if (raw === null) return
  const amount = Number(raw)
  if (!Number.isInteger(amount) || amount === 0) {
    error.value = 'الكمية غير صحيحة'
    toast().danger(error.value)
    return
  }
  let usdPaid
  if (amount > 0) {
    const usdRaw = await askPrompt({
      title: 'بونص الوكيل',
      label: 'كم دولار دُفع؟ (200→+12% · 500→+15% · 1000→+20%)',
      defaultValue: '',
    })
    if (usdRaw === null) return
    if (String(usdRaw).trim() !== '') {
      usdPaid = Number(usdRaw)
      if (!Number.isFinite(usdPaid) || usdPaid < 0) {
        error.value = 'مبلغ الدولار غير صحيح'
        toast().danger(error.value)
        return
      }
    }
  }
  const noteRaw = await askPrompt({
    title: 'تعديل الرصيد',
    label: 'سبب تعديل الرصيد',
    defaultValue: '',
  })
  if (noteRaw === null) return
  const payload = { amount, note: noteRaw }
  if (usdPaid !== undefined) payload.usdPaid = usdPaid
  const result = await rechargeAgentsApi.adjustFloat(agent.id, payload)
  if (result.error) {
    error.value = result.error.message
    toast().danger(error.value)
  } else {
    const data = result.data?.data ?? result.data
    const bonus = Number(data?.bonusCoins || 0)
    success.value =
      bonus > 0
        ? `تم التعديل · بونص +${data.bonusPercent}% (+${bonus.toLocaleString()} كوين)`
        : 'تم تعديل الرصيد'
    toast().success(success.value)
    await load()
  }
}

async function removeAgent(agent) {
  pendingAction.value = { type: 'removeAgent', payload: agent }
  confirmTitle.value = 'حذف نهائي'
  confirmMsg.value = `حذف الوكيل ${userName(agent)} نهائياً مع سجله؟ لا يمكن التراجع.`
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
  if (action.type === 'removeAgent') {
    const result = await rechargeAgentsApi.remove(action.payload.id)
    if (result.error) {
      error.value = result.error.message
      toast().danger(error.value)
    } else {
      success.value = 'تم حذف الوكيل نهائياً'
      toast().success(success.value)
      await load()
    }
  }
}

onMounted(load)
</script>
