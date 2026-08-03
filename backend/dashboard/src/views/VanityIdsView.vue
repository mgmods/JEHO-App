<template>
  <div>
    <PageHeader
      title="الآي دي المميز"
      subtitle="أنشئ أرقاماً للبيع — المستخدم يشتريها من التطبيق لمدة 30 يوماً. التجديد قبل الانتهاء بنصف السعر."
    />
    <AlertMessage v-if="error" :message="error" type="danger" class="mb-3" />
    <AlertMessage v-if="ok" :message="ok" type="success" class="mb-3" />

    <div class="glass p-3 mb-3">
      <div class="small text-muted mb-3">
        مدة الاشتراك في التطبيق: <strong>30 يوماً</strong> · التجديد قبل الانتهاء = نصف السعر · بعد الانتهاء = السعر الكامل
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

    <div class="glass p-0">
      <LoadingSpinner v-if="loading" class="p-4" />
      <table v-else class="table align-middle mb-0">
        <thead>
          <tr>
            <th>الآي دي</th>
            <th>السعر</th>
            <th>تجديد (½)</th>
            <th>الحالة</th>
            <th>ينتهي</th>
            <th>المالك</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td class="fw-semibold font-monospace">{{ item.publicId }}</td>
            <td>{{ formatNumber(item.priceCoins) }} كوينز</td>
            <td class="text-muted">{{ formatNumber(Math.ceil((item.priceCoins || 0) / 2)) }}</td>
            <td><StatusBadge :status="item.status" /></td>
            <td class="small text-muted">{{ formatExpiry(item.expiresAt) }}</td>
            <td class="small text-muted font-monospace">{{ item.ownerUserId || '—' }}</td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="6" class="empty-state">لا يوجد بعد — أضف آي دي أعلاه ليظهر في التطبيق</td>
          </tr>
        </tbody>
      </table>
    </div>
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
import StatusBadge from '@/components/StatusBadge.vue'

const items = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const ok = ref('')
const form = reactive({ publicId: '', priceCoins: 10000 })

const renewHint = computed(() =>
  formatNumber(Math.ceil(Math.max(0, Number(form.priceCoins) || 0) / 2)),
)

function formatExpiry(v) {
  if (!v) return '—'
  try {
    return new Date(v).toLocaleString('ar')
  } catch {
    return String(v)
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get('/vanity-ids/admin/all')
    items.value = Array.isArray(data) ? data : data?.data || data?.items || []
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

onMounted(load)
</script>
