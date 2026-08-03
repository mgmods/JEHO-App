<template>
  <div>
    <PageHeader :title="t('rechargeAgents.title')" subtitle="إدارة طلبات الوكلاء، أسعار USDT، دليل التواصل (واتساب/تيليجرام)" />
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
          <h5 class="mb-1">التسعير والدفع بـ USDT</h5>
          <p class="text-secondary small mb-0">يُحسب السعر دائماً في السيرفر ولا يمكن للوكيل تغييره.</p>
        </div>
        <button class="btn btn-aurora" :disabled="saving" @click="savePricing">حفظ الأسعار</button>
      </div>
      <div class="row g-3">
        <div class="col-md-4">
          <label class="form-label">رسوم عضوية الوكيل (USDT)</label>
          <input v-model.number="pricing.membershipFeeUsdt" type="number" min="0" step="0.00000001" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">سعر الجملة لكل 100 عملة (USDT)</label>
          <input v-model.number="pricing.wholesalePer100CoinsUsdt" type="number" min="0" step="0.00000001" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">سعر البيع المقترح لكل 100 عملة</label>
          <input v-model.number="pricing.suggestedRetailPer100CoinsUsdt" type="number" min="0" step="0.00000001" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">أقل رصيد افتتاحي</label>
          <input v-model.number="pricing.minInitialCoins" type="number" min="1" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">أعلى رصيد افتتاحي</label>
          <input v-model.number="pricing.maxInitialCoins" type="number" min="1" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">مثال: تكلفة 100,000 عملة</label>
          <div class="form-control bg-transparent">{{ quoteExample }} USDT + رسوم العضوية</div>
        </div>
      </div>
    </div>

    <div class="glass p-4 mb-4">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
          <h5 class="mb-1">دليل التواصل مع وكلاء الشحن</h5>
          <p class="text-secondary small mb-0">يظهر في التطبيق عند اختيار «تواصل مع وكيل شحن» — واتساب / تيليجرام حسب الدولة.</p>
        </div>
        <button class="btn btn-aurora" type="button" @click="startContactCreate">إضافة وكيل تواصل</button>
      </div>
      <div v-if="editingContact" class="border rounded-3 p-3 mb-3">
        <div class="row g-2">
          <div class="col-md-3">
            <label class="form-label">الاسم الظاهر</label>
            <input v-model.trim="contactForm.displayName" class="form-control" required />
          </div>
          <div class="col-md-3">
            <label class="form-label">الدولة / المنطقة</label>
            <select v-model="contactForm.country" class="form-select" required>
              <option disabled value="">اختر الدولة</option>
              <option v-for="c in countryOptions" :key="c.name" :value="c.name">
                {{ c.flag }} {{ c.name }}
              </option>
            </select>
          </div>
          <div class="col-md-3">
            <label class="form-label">واتساب</label>
            <input v-model.trim="contactForm.whatsapp" class="form-control" placeholder="9639…" />
          </div>
          <div class="col-md-3">
            <label class="form-label">تيليجرام</label>
            <input v-model.trim="contactForm.telegram" class="form-control" placeholder="@username أو رقم" />
          </div>
          <div class="col-md-6">
            <label class="form-label">ملاحظات (اختياري)</label>
            <input v-model.trim="contactForm.notes" class="form-control" />
          </div>
          <div class="col-md-2">
            <label class="form-label">الترتيب</label>
            <input v-model.number="contactForm.sortOrder" type="number" class="form-control" />
          </div>
          <div class="col-md-2 form-check form-switch mt-4">
            <input id="contactActive" v-model="contactForm.isActive" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="contactActive">نشط</label>
          </div>
          <div class="col-12 d-flex gap-2">
            <button class="btn btn-aurora" type="button" :disabled="saving" @click="saveContact">حفظ</button>
            <button class="btn btn-ghost" type="button" @click="editingContact = false">إلغاء</button>
          </div>
        </div>
      </div>
      <div class="table-responsive">
        <table class="table table-glass table-hover align-middle mb-0">
          <thead><tr>
            <th>الاسم</th><th>الدولة</th><th>واتساب</th><th>تيليجرام</th><th>الحالة</th><th></th>
          </tr></thead>
          <tbody>
            <tr v-if="!contacts.length"><td colspan="6" class="empty-state">لا يوجد وكلاء تواصل بعد — أضفهم ليظهروا في التطبيق</td></tr>
            <tr v-for="item in contacts" :key="item.id">
              <td class="fw-semibold">{{ item.displayName }}</td>
              <td><CountryFlag :country="item.country" show-label /></td>
              <td class="font-monospace">{{ item.whatsapp || '—' }}</td>
              <td class="font-monospace">{{ item.telegram || '—' }}</td>
              <td><StatusBadge :status="item.isActive ? 'active' : 'suspended'" /></td>
              <td>
                <div class="d-flex gap-2 justify-content-end">
                  <button class="btn btn-sm btn-outline-info" @click="editContact(item)">تعديل</button>
                  <button class="btn btn-sm btn-outline-danger" @click="removeContact(item)">حذف</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="glass p-3 mb-4">
      <h5 class="mb-3">تعيين وكيل يدوياً (بيع داخلي)</h5>
      <form class="row g-2 align-items-end" @submit.prevent="assign">
        <div class="col-lg-5">
          <label class="form-label">{{ t('rechargeAgents.userId') }}</label>
          <input v-model.trim="assignForm.userId" class="form-control" required />
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

    <div class="glass p-0 overflow-hidden mb-4">
      <div class="p-3 border-bottom d-flex justify-content-between">
        <h5 class="mb-0">طلبات الانضمام المدفوعة</h5>
        <span class="badge bg-warning text-dark">{{ pendingCount }} بانتظار المراجعة</span>
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
      <LoadingSpinner v-if="loading" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle mb-0">
          <thead><tr>
            <th style="width:2.2rem"></th>
            <th>المستخدم</th><th>التواصل</th><th>الرصيد المطلوب</th><th>المبلغ</th>
            <th>الدفع</th><th>الحالة</th><th></th>
          </tr></thead>
          <tbody>
            <tr v-if="!applications.length"><td colspan="8" class="empty-state">لا توجد طلبات</td></tr>
            <tr v-for="item in applications" :key="item.id">
              <td>
                <input
                  type="checkbox"
                  class="form-check-input"
                  :checked="appIsSelected(item.id)"
                  @change="appToggle(item.id)"
                />
              </td>
              <td>
                <div class="fw-semibold">{{ userName(item) }}</div>
                <small class="text-muted">{{ item.user?.username }} · {{ item.userId }}</small>
              </td>
              <td>{{ item.contact || '—' }}<br><small class="text-muted">{{ item.region || '—' }}</small></td>
              <td>{{ formatNumber(item.requestedCoins || 0) }}</td>
              <td>
                <strong>{{ money(item.totalPaidUsdt) }} USDT</strong>
                <div class="small text-muted">رسوم {{ money(item.membershipFeeUsdt) }} + رصيد {{ money(item.stockCostUsdt) }}</div>
              </td>
              <td>
                <span class="badge bg-dark">{{ item.paymentNetwork || '—' }}</span>
                <div class="small font-monospace text-break" style="max-width:180px">{{ item.paymentReference || '—' }}</div>
              </td>
              <td><StatusBadge :status="item.status" /></td>
              <td>
                <div v-if="item.status === 'pending'" class="d-flex gap-2">
                  <button class="btn btn-sm btn-success" @click="approve(item)">تحقق وقبول</button>
                  <button class="btn btn-sm btn-outline-danger" @click="reject(item)">رفض</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="glass p-0 overflow-hidden">
      <div class="p-3 border-bottom"><h5 class="mb-0">الوكلاء (بيع داخلي + ظهور اختياري في الدليل)</h5></div>
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
          <thead><tr>
            <th style="width:2.2rem"></th>
            <th>الوكيل</th><th>الرصيد</th><th>الدولة / تواصل</th><th>مبيعات اليوم</th>
            <th>العمولة</th><th>الحالة</th><th></th>
          </tr></thead>
          <tbody>
            <tr v-if="!agents.length"><td colspan="8" class="empty-state">لا يوجد وكلاء</td></tr>
            <tr v-for="agent in agents" :key="agent.id">
              <td>
                <input
                  type="checkbox"
                  class="form-check-input"
                  :checked="agentIsSelected(agent.id)"
                  @change="agentToggle(agent.id)"
                />
              </td>
              <td><div class="fw-semibold">{{ userName(agent) }}</div><small class="text-muted">{{ agent.userId }}</small></td>
              <td>{{ formatNumber(agent.floatCoins) }}</td>
              <td>
                <div><CountryFlag :country="agent.country" show-label /></div>
                <small class="text-muted">WA {{ agent.whatsapp || '—' }} · TG {{ agent.telegram || '—' }}</small>
                <div v-if="agent.listedInDirectory" class="badge bg-info text-dark mt-1">في الدليل</div>
              </td>
              <td>{{ formatNumber(agent.dailySoldCoins) }}</td>
              <td>{{ ((agent.commissionBps || 0) / 100).toFixed(2) }}%</td>
              <td><StatusBadge :status="agent.status" /></td>
              <td>
                <div class="d-flex gap-2 justify-content-end flex-wrap">
                  <button class="btn btn-sm btn-outline-info" @click="editAgentDirectory(agent)">دليل التواصل</button>
                  <button class="btn btn-sm btn-outline-light" @click="adjustFloat(agent)">تعديل الرصيد</button>
                  <button v-if="agent.status === 'active'" class="btn btn-sm btn-outline-warning" @click="setStatus(agent, 'suspended')">تعليق</button>
                  <button v-else class="btn btn-sm btn-outline-success" @click="setStatus(agent, 'active')">تفعيل</button>
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
      @cancel="onConfirmCancel"
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
import CountryFlag from '@/components/CountryFlag.vue'
import BulkActionBar from '@/components/BulkActionBar.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()
const applications = ref([])
const agents = ref([])
const contacts = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const editingContact = ref(false)
const bulkBusy = ref(false)
const pricing = reactive({
  membershipFeeUsdt: 25,
  wholesalePer100CoinsUsdt: 0.01,
  suggestedRetailPer100CoinsUsdt: 0.015,
  minInitialCoins: 10000,
  maxInitialCoins: 10000000,
})
const assignForm = reactive({ userId: '', floatCoins: 0, commissionPercent: 0 })
const contactForm = reactive({
  id: null,
  displayName: '',
  country: '',
  whatsapp: '',
  telegram: '',
  notes: '',
  isActive: true,
  sortOrder: 0,
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
  selectedIds: agentSelectedIds,
  selectedCount: agentSelectedCount,
  allSelected: agentAllSelected,
  someSelected: agentSomeSelected,
  isSelected: agentIsSelected,
  toggle: agentToggle,
  clear: agentClear,
  toggleAll: agentToggleAll,
} = useBulkSelection(agents)

const appBulkActions = computed(() => [
  { key: 'approve', label: t('bulk.approveSelected'), icon: 'bi-check2', variant: 'btn-outline-success' },
  { key: 'reject', label: t('bulk.rejectSelected'), icon: 'bi-x-lg', variant: 'btn-outline-danger' },
])

const agentBulkActions = computed(() => [
  { key: 'activate', label: t('bulk.activateSelected'), icon: 'bi-check2-circle', variant: 'btn-outline-success' },
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
      resource: 'recharge-agent-applications',
      action: key,
      ids,
      reason: key === 'reject' ? 'مرفوض من لوحة التحكم' : undefined,
      t,
    })
    bulkBusy.value = false
    if (data) {
      appClear()
      await load()
    }
  }
  confirmOpen.value = true
}

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

const pendingCount = computed(() => applications.value.filter(x => x.status === 'pending').length)
const stats = computed(() => [
  { label: 'الوكلاء النشطون', value: agents.value.filter(x => x.status === 'active').length },
  { label: 'طلبات قيد المراجعة', value: pendingCount.value },
  { label: 'إجمالي أرصدة الوكلاء (float)', value: formatNumber(agents.value.reduce((n, x) => n + Number(x.floatCoins || 0), 0)) },
  { label: 'مبيعات اليوم (عملات)', value: formatNumber(agents.value.reduce((n, x) => n + Number(x.dailySoldCoins || 0), 0)) },
  { label: 'حد يومي إجمالي', value: formatNumber(agents.value.reduce((n, x) => n + Number(x.dailyLimitCoins || 0), 0)) },
  { label: 'وكلاء التواصل', value: contacts.value.filter(x => x.isActive).length },
])
const quoteExample = computed(() => money((100000 / 100) * Number(pricing.wholesalePer100CoinsUsdt || 0)))

const countryOptions = [
  { name: 'سوريا', flag: '🇸🇾' },
  { name: 'تركيا', flag: '🇹🇷' },
  { name: 'السعودية', flag: '🇸🇦' },
  { name: 'الإمارات', flag: '🇦🇪' },
  { name: 'مصر', flag: '🇪🇬' },
  { name: 'الأردن', flag: '🇯🇴' },
  { name: 'لبنان', flag: '🇱🇧' },
  { name: 'العراق', flag: '🇮🇶' },
  { name: 'الكويت', flag: '🇰🇼' },
  { name: 'قطر', flag: '🇶🇦' },
  { name: 'البحرين', flag: '🇧🇭' },
  { name: 'عمان', flag: '🇴🇲' },
  { name: 'اليمن', flag: '🇾🇪' },
  { name: 'فلسطين', flag: '🇵🇸' },
  { name: 'ليبيا', flag: '🇱🇾' },
  { name: 'تونس', flag: '🇹🇳' },
  { name: 'الجزائر', flag: '🇩🇿' },
  { name: 'المغرب', flag: '🇲🇦' },
  { name: 'السودان', flag: '🇸🇩' },
  { name: 'ألمانيا', flag: '🇩🇪' },
  { name: 'فرنسا', flag: '🇫🇷' },
  { name: 'هولندا', flag: '🇳🇱' },
  { name: 'السويد', flag: '🇸🇪' },
  { name: 'بريطانيا', flag: '🇬🇧' },
  { name: 'الولايات المتحدة', flag: '🇺🇸' },
  { name: 'كندا', flag: '🇨🇦' },
  { name: 'أخرى', flag: '🌍' },
]

function countryLabel(country) {
  if (!country) return '🌍 —'
  const hit = countryOptions.find((c) => c.name === country || country.includes(c.name))
  return hit ? `${hit.flag} ${hit.name}` : `🌍 ${country}`
}

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
  const [appsResult, agentsResult, pricingResult, contactsResult] = await Promise.all([
    rechargeAgentsApi.applications(),
    rechargeAgentsApi.agents(),
    rechargeAgentsApi.pricing(),
    rechargeAgentsApi.contacts(),
  ])
  loading.value = false
  const failed = appsResult.error || agentsResult.error || pricingResult.error
  if (failed) return (error.value = failed.message)
  applications.value = extractList(appsResult.data)
  agents.value = extractList(agentsResult.data)
  contacts.value = contactsResult.error ? [] : extractList(contactsResult.data)
  Object.assign(pricing, unwrap(pricingResult.data))
  if (contactsResult.error) {
    error.value = 'تعذر تحميل دليل التواصل: ' + contactsResult.error.message
  }
}

async function savePricing() {
  saving.value = true
  const result = await rechargeAgentsApi.updatePricing({ ...pricing })
  saving.value = false
  if (result.error) return (error.value = result.error.message)
  Object.assign(pricing, unwrap(result.data))
  success.value = 'تم حفظ أسعار الوكلاء'
}

function startContactCreate() {
  Object.assign(contactForm, {
    id: null, displayName: '', country: '', whatsapp: '', telegram: '', notes: '', isActive: true, sortOrder: 0,
  })
  editingContact.value = true
}

function editContact(item) {
  Object.assign(contactForm, {
    id: item.id,
    displayName: item.displayName || '',
    country: item.country || '',
    whatsapp: item.whatsapp || '',
    telegram: item.telegram || '',
    notes: item.notes || '',
    isActive: item.isActive !== false,
    sortOrder: Number(item.sortOrder || 0),
  })
  editingContact.value = true
}

async function saveContact() {
  if (!contactForm.displayName || !contactForm.country) {
    return (error.value = 'الاسم والدولة مطلوبان')
  }
  if (!contactForm.whatsapp && !contactForm.telegram) {
    return (error.value = 'أدخل واتساب أو تيليجرام على الأقل')
  }
  saving.value = true
  const result = await rechargeAgentsApi.upsertContact({ ...contactForm })
  saving.value = false
  if (result.error) return (error.value = result.error.message)
  editingContact.value = false
  success.value = 'تم حفظ وكيل التواصل'
  await load()
}

async function removeContact(item) {
  pendingAction.value = { type: 'removeContact', payload: item }
  confirmTitle.value = 'حذف'
  confirmMsg.value = `حذف ${item.displayName} من الدليل؟`
  confirmOpen.value = true
}

async function editAgentDirectory(agent) {
  const country = await askPrompt({
    title: 'دليل التواصل',
    label: 'الدولة / المنطقة',
    defaultValue: agent.country || '',
  })
  if (country === null) return
  const whatsapp = await askPrompt({
    title: 'دليل التواصل',
    label: 'واتساب (رقم مع مفتاح الدولة)',
    defaultValue: agent.whatsapp || '',
  })
  if (whatsapp === null) return
  const telegram = await askPrompt({
    title: 'دليل التواصل',
    label: 'تيليجرام (@user أو رقم)',
    defaultValue: agent.telegram || '',
  })
  if (telegram === null) return
  pendingAction.value = {
    type: 'listAgent',
    payload: {
      agent,
      country: country.trim() || null,
      whatsapp: whatsapp.trim() || null,
      telegram: telegram.trim() || null,
    },
  }
  confirmTitle.value = 'دليل التواصل'
  confirmMsg.value = 'إظهار هذا الوكيل في دليل التطبيق؟'
  confirmOpen.value = true
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
  success.value = 'تم تعيين الوكيل'
  toast().success(success.value)
  await load()
}

async function approve(item) {
  pendingAction.value = { type: 'approve', payload: item }
  confirmTitle.value = 'تأكيد الدفعة'
  confirmMsg.value = `هل تحققت من دفعة ${money(item.totalPaidUsdt)} USDT؟`
  confirmOpen.value = true
}

async function reject(item) {
  const note = await askPrompt({ title: 'رفض الطلب', label: 'سبب الرفض', defaultValue: '' })
  if (note === null) return
  const result = await rechargeAgentsApi.reject(item.id, { note })
  if (result.error) {
    error.value = result.error.message
    toast().danger(error.value)
  } else {
    success.value = 'تم رفض الطلب'
    toast().success(success.value)
    await load()
  }
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
    success.value = bonus > 0
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
  if (action.type === 'removeContact') {
    const result = await rechargeAgentsApi.removeContact(action.payload.id)
    if (result.error) {
      error.value = result.error.message
      toast().danger(error.value)
    } else {
      success.value = 'تم الحذف'
      toast().success(success.value)
      await load()
    }
  } else if (action.type === 'listAgent') {
    const { agent, country, whatsapp, telegram } = action.payload
    const result = await rechargeAgentsApi.update(agent.id, {
      country,
      whatsapp,
      telegram,
      listedInDirectory: true,
    })
    if (result.error) {
      error.value = result.error.message
      toast().danger(error.value)
    } else {
      success.value = 'تم تحديث بيانات الدليل'
      toast().success(success.value)
      await load()
    }
  } else if (action.type === 'approve') {
    const item = action.payload
    const note = await askPrompt({
      title: 'تأكيد الدفعة',
      label: 'ملاحظة الموافقة',
      defaultValue: 'تم التحقق من دفعة USDT',
    })
    if (note === null) return
    const result = await rechargeAgentsApi.approve(item.id, {
      note,
      floatCoins: Number(item.requestedCoins || 0),
    })
    if (result.error) {
      error.value = result.error.message
      toast().danger(error.value)
    } else {
      success.value = 'تم قبول الطلب وتفعيل رصيد الوكيل'
      toast().success(success.value)
      await load()
    }
  } else if (action.type === 'removeAgent') {
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

async function onConfirmCancel() {
  const action = pendingAction.value
  if (action?.type !== 'listAgent') {
    pendingAction.value = null
    return
  }
  pendingAction.value = null
  const { agent, country, whatsapp, telegram } = action.payload
  const result = await rechargeAgentsApi.update(agent.id, {
    country,
    whatsapp,
    telegram,
    listedInDirectory: false,
  })
  if (result.error) {
    error.value = result.error.message
    toast().danger(error.value)
  } else {
    success.value = 'تم تحديث بيانات الدليل'
    toast().success(success.value)
    await load()
  }
}

onMounted(load)
</script>
