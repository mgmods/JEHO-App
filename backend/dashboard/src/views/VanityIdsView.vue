<template>
  <div>
    <PageHeader
      title="الآي دي المميز"
      subtitle="أنشئ أرقاماً للبيع · عدّل السعر · أوقف / فعّل · اسحب من المستخدم · احذف. الاشتراك في التطبيق 30 يوماً والتجديد نصف السعر."
    >
      <template #actions>
        <button class="btn btn-outline-light btn-sm" type="button" :disabled="loading" @click="load">
          <i class="bi bi-arrow-clockwise me-1"></i> تحديث
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="danger" class="mb-3" @dismiss="error = ''" />
    <AlertMessage v-if="ok" :message="ok" type="success" class="mb-3" @dismiss="ok = ''" />

    <div class="glass p-3 mb-3">
      <div class="small text-muted mb-3">
        مدة الاشتراك: <strong>30 يوماً</strong> · التجديد قبل الانتهاء = نصف السعر · بعد الانتهاء = السعر الكامل
      </div>
      <h5 class="mb-3">إضافة آي دي</h5>
      <form class="row g-2 align-items-end" @submit.prevent="create">
        <div class="col-md-4">
          <label class="form-label">الرقم (3–12 خانة رقمية)</label>
          <input
            v-model.trim="form.publicId"
            class="form-control font-monospace"
            required
            pattern="[0-9]{3,12}"
            inputmode="numeric"
            maxlength="12"
            placeholder="مثال: 8888"
          />
        </div>
        <div class="col-md-3">
          <label class="form-label">السعر (كوينز)</label>
          <input v-model.number="form.priceCoins" type="number" min="0" class="form-control" />
          <div class="form-text">تجديد قبل الانتهاء: {{ renewHint }} كوينز</div>
        </div>
        <div class="col-md-3">
          <button class="btn btn-aurora" type="submit" :disabled="saving">إضافة للبيع</button>
        </div>
      </form>
    </div>

    <div class="d-flex flex-wrap gap-2 align-items-center mb-3">
      <label class="form-label mb-0 small text-muted">تصفية الحالة</label>
      <select v-model="filterStatus" class="form-select form-select-sm" style="max-width: 180px">
        <option value="">الكل</option>
        <option value="available">متاح</option>
        <option value="owned">مملوك</option>
        <option value="reserved">محجوز</option>
        <option value="disabled">متوقف</option>
      </select>
      <span class="small text-muted ms-auto">{{ filtered.length }} / {{ items.length }}</span>
    </div>

    <LoadingSpinner v-if="loading" class="p-4" />
    <div v-else class="widget-grid">
      <article v-for="item in filtered" :key="item.id" class="widget-card vanity-card">
        <div class="widget-card-body">
          <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
            <div class="vanity-id">{{ item.publicId }}</div>
            <span class="badge" :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span>
          </div>
          <div class="vanity-price-row mb-2">
            <input
              v-model.number="item._priceEdit"
              type="number"
              min="0"
              class="form-control form-control-sm"
            />
            <button
              class="btn btn-sm btn-aurora"
              type="button"
              :disabled="busyId === item.id || item._priceEdit === item.priceCoins"
              @click="savePrice(item)"
            >
              حفظ السعر
            </button>
          </div>
          <div class="small text-muted mb-1">
            تجديد ½: {{ formatNumber(Math.ceil((Number(item._priceEdit ?? item.priceCoins) || 0) / 2)) }}
          </div>
          <div class="small text-muted mb-1">ينتهي: {{ formatExpiry(item.expiresAt) }}</div>
          <div class="small text-muted mb-3">المالك: {{ shortId(item.ownerUserId) }}</div>
          <div class="d-flex flex-wrap gap-1 justify-content-end">
            <button
              v-if="item.status === 'owned' || item.status === 'reserved'"
              class="btn btn-sm btn-outline-warning"
              type="button"
              :disabled="busyId === item.id"
              @click="ask('release', item)"
            >
              سحب
            </button>
            <button
              v-if="item.status !== 'disabled'"
              class="btn btn-sm btn-ghost"
              type="button"
              :disabled="busyId === item.id"
              @click="ask('disable', item)"
            >
              إيقاف
            </button>
            <button
              v-else
              class="btn btn-sm btn-outline-success"
              type="button"
              :disabled="busyId === item.id"
              @click="enable(item)"
            >
              تفعيل
            </button>
            <button
              class="btn btn-sm btn-outline-danger"
              type="button"
              :disabled="busyId === item.id"
              @click="ask('delete', item)"
            >
              حذف
            </button>
          </div>
        </div>
      </article>
      <div v-if="!filtered.length" class="glass p-4 empty-state">
        {{ items.length ? 'لا نتائج لهذه التصفية' : 'لا يوجد بعد — أضف آي دي أعلاه' }}
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
import api from '@/api/client'
import { formatNumber } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const items = ref([])
const loading = ref(false)
const saving = ref(false)
const busyId = ref('')
const error = ref('')
const ok = ref('')
const filterStatus = ref('')
const form = reactive({ publicId: '', priceCoins: 10000 })

const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
const confirmAction = ref('')
const confirmItem = ref(null)

const renewHint = computed(() =>
  formatNumber(Math.ceil(Math.max(0, Number(form.priceCoins) || 0) / 2)),
)

const filtered = computed(() => {
  const st = filterStatus.value
  if (!st) return items.value
  return items.value.filter((i) => i.status === st)
})

function formatExpiry(v) {
  if (!v) return '—'
  try {
    return new Date(v).toLocaleString('ar')
  } catch {
    return String(v)
  }
}

function shortId(id) {
  if (!id) return '—'
  const s = String(id)
  return s.length > 10 ? `${s.slice(0, 8)}…` : s
}

function statusLabel(s) {
  const map = {
    available: 'متاح',
    owned: 'مملوك',
    reserved: 'محجوز',
    disabled: 'متوقف',
  }
  return map[s] || s || '—'
}

function statusClass(s) {
  const map = {
    available: 'text-bg-success',
    owned: 'text-bg-primary',
    reserved: 'text-bg-warning',
    disabled: 'text-bg-secondary',
  }
  return map[s] || 'text-bg-light'
}

function withPriceEdit(list) {
  return (list || []).map((i) => ({
    ...i,
    _priceEdit: Number(i.priceCoins) || 0,
  }))
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get('/vanity-ids/admin/all')
    const raw = Array.isArray(data) ? data : data?.data || data?.items || []
    items.value = withPriceEdit(raw)
  } catch (e) {
    error.value = e?.message || 'تعذر التحميل'
    toast().danger(error.value)
  }
  loading.value = false
}

async function create() {
  const id = String(form.publicId || '').trim()
  if (!/^[0-9]{3,12}$/.test(id)) {
    error.value = 'الآي دي يجب أن يكون أرقاماً فقط من 3 إلى 12 خانة'
    toast().danger(error.value)
    return
  }
  saving.value = true
  ok.value = ''
  error.value = ''
  try {
    await api.post('/vanity-ids/admin', {
      publicId: id,
      priceCoins: form.priceCoins,
    })
    ok.value = `تمت إضافة ${id} بسعر ${formatNumber(form.priceCoins)} كوينز`
    toast().success(ok.value)
    form.publicId = ''
    await load()
  } catch (e) {
    error.value = e?.message || 'فشل الحفظ'
    toast().danger(error.value)
  }
  saving.value = false
}

async function savePrice(item) {
  const price = Math.max(0, Math.floor(Number(item._priceEdit) || 0))
  busyId.value = item.id
  error.value = ''
  try {
    await api.patch(`/vanity-ids/admin/${item.id}`, { priceCoins: price })
    item.priceCoins = price
    item._priceEdit = price
    ok.value = `تم تحديث سعر ${item.publicId}`
    toast().success(ok.value)
  } catch (e) {
    error.value = e?.message || 'فشل تحديث السعر'
    toast().danger(error.value)
  }
  busyId.value = ''
}

function ask(action, item) {
  confirmItem.value = item
  confirmAction.value = action
  if (action === 'delete') {
    confirmTitle.value = 'حذف الآي دي'
    confirmMsg.value = item.status === 'owned' || item.status === 'reserved'
      ? `حذف ${item.publicId}؟ سيتم سحبه من المستخدم أولاً ثم حذفه نهائياً.`
      : `حذف ${item.publicId} نهائياً؟`
  } else if (action === 'disable') {
    confirmTitle.value = 'إيقاف الآي دي'
    confirmMsg.value = item.status === 'owned' || item.status === 'reserved'
      ? `إيقاف ${item.publicId}؟ سيُسحب من المستخدم ويُخفى من المتجر.`
      : `إيقاف ${item.publicId} وإخفاؤه من متجر التطبيق؟`
  } else if (action === 'release') {
    confirmTitle.value = 'سحب من المستخدم'
    confirmMsg.value = `سحب ${item.publicId} من المالك وإعادته للبيع؟ سيُستعاد الآي دي السابق للمستخدم.`
  }
  confirmOpen.value = true
}

async function runConfirm() {
  const item = confirmItem.value
  const action = confirmAction.value
  if (!item || !action) return
  busyId.value = item.id
  error.value = ''
  try {
    if (action === 'delete') {
      await api.delete(`/vanity-ids/admin/${item.id}`)
      ok.value = `تم حذف ${item.publicId}`
    } else if (action === 'disable') {
      await api.post(`/vanity-ids/admin/${item.id}/disable`)
      ok.value = `تم إيقاف ${item.publicId}`
    } else if (action === 'release') {
      await api.post(`/vanity-ids/admin/${item.id}/release`)
      ok.value = `تم سحب ${item.publicId}`
    }
    toast().success(ok.value)
    await load()
  } catch (e) {
    error.value = e?.message || 'فشلت العملية'
    toast().danger(error.value)
  }
  busyId.value = ''
  confirmItem.value = null
  confirmAction.value = ''
}

async function enable(item) {
  busyId.value = item.id
  error.value = ''
  try {
    await api.post(`/vanity-ids/admin/${item.id}/enable`)
    ok.value = `تم تفعيل ${item.publicId} للبيع`
    toast().success(ok.value)
    await load()
  } catch (e) {
    error.value = e?.message || 'فشل التفعيل'
    toast().danger(error.value)
  }
  busyId.value = ''
}

onMounted(load)
</script>

<style scoped>
.vanity-id {
  font-size: 1.45rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #fff;
  font-variant-numeric: tabular-nums lining-nums;
  text-shadow: 0 0 20px rgba(167, 139, 250, 0.35);
}
.vanity-price-row {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
.vanity-card {
  min-height: 100%;
}
</style>

