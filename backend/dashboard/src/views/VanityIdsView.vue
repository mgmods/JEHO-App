<template>
  <div>
    <PageHeader
      title="الآي دي المميز"
      subtitle="أنشئ أرقاماً للبيع هنا — المستخدم يشتريها من التطبيق (ملف الشخصي → الآي دي المميز) بالكوينز ويستبدل آيديه الحالي"
    />
    <AlertMessage v-if="error" :message="error" type="danger" class="mb-3" />
    <AlertMessage v-if="ok" :message="ok" type="success" class="mb-3" />

    <div class="glass p-3 mb-3">
      <h5 class="mb-3">إضافة آي دي</h5>
      <form class="row g-2 align-items-end" @submit.prevent="create">
        <div class="col-md-4">
          <label class="form-label">الرقم (4-12 خانة)</label>
          <input v-model.trim="form.publicId" class="form-control" required pattern="\\d{4,12}" />
        </div>
        <div class="col-md-3">
          <label class="form-label">السعر (كوينز)</label>
          <input v-model.number="form.priceCoins" type="number" min="0" class="form-control" />
        </div>
        <div class="col-md-3">
          <button class="btn btn-aurora" type="submit" :disabled="saving">إضافة</button>
        </div>
      </form>
    </div>

    <div class="glass p-0">
      <LoadingSpinner v-if="loading" class="p-4" />
      <table v-else class="table align-middle mb-0">
        <thead>
          <tr><th>الآي دي</th><th>السعر</th><th>الحالة</th><th>المالك</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td class="fw-semibold font-monospace">{{ item.publicId }}</td>
            <td>{{ formatNumber(item.priceCoins) }}</td>
            <td><StatusBadge :status="item.status" /></td>
            <td class="small text-muted">{{ item.ownerUserId || '—' }}</td>
          </tr>
          <tr v-if="!items.length"><td colspan="4" class="empty-state">لا يوجد بعد</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
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
  saving.value = true
  ok.value = ''
  error.value = ''
  try {
    await api.post('/vanity-ids/admin', {
      publicId: form.publicId,
      priceCoins: form.priceCoins,
    })
    ok.value = 'تمت الإضافة'
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
