<template>
  <div class="paid-messages-page">
    <PageHeader title="الرسائل المدفوعة" subtitle="إعدادات التسعير والمتابعة والاسترداد وإحصائيات المحادثات" />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <section class="settings-card mb-3">
      <div class="d-flex align-items-center justify-content-between flex-wrap gap-3 mb-3">
        <div>
          <h3 class="settings-card-title mb-1">تفعيل النظام</h3>
          <p class="form-text mb-0">عند إيقافه لن تُخصم عملات للرسائل الجديدة.</p>
        </div>
        <label class="form-check form-switch fs-5 mb-0">
          <input v-model="form['paid_messages.enabled']" class="form-check-input" type="checkbox" role="switch">
          <span class="form-check-label">{{ form['paid_messages.enabled'] ? 'مفعّل' : 'متوقف' }}</span>
        </label>
      </div>
      <div class="row g-3">
        <div v-for="field in fields" :key="field.key" class="col-sm-6 col-lg-4">
          <label class="form-label">{{ field.label }}</label>
          <input v-model.number="form[field.key]" class="form-control" type="number" :min="0" :max="field.max || 10000000" :step="field.step || 1">
          <div v-if="field.hint" class="form-text">{{ field.hint }}</div>
        </div>
      </div>
      <div class="d-flex gap-2 mt-4 flex-wrap">
        <button class="btn btn-aurora" type="button" :disabled="loading || saving" @click="save">
          <span v-if="saving" class="spinner-border spinner-border-sm me-1" />
          حفظ الإعدادات
        </button>
        <button class="btn btn-outline-secondary" type="button" :disabled="loading || saving" @click="load">تحديث</button>
      </div>
      <p class="form-text mt-3 mb-0">يجب أن يكون مجموع نسب التطبيق والمضيف والوكيل 100%. نسب الأرباح المعروضة أدناه إحصائية تقديرية حاليًا.</p>
    </section>

    <section class="settings-card">
      <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3">
        <div>
          <h3 class="settings-card-title mb-1">الإحصائيات</h3>
          <p class="form-text mb-0">بيانات مسجلة من معاملات الرسائل المدفوعة والاستردادات.</p>
        </div>
        <button class="btn btn-outline-secondary btn-sm" type="button" :disabled="loadingStats" @click="loadStats">تحديث الإحصائيات</button>
      </div>
      <LoadingSpinner v-if="loadingStats" />
      <div v-else class="row g-3">
        <div v-for="item in statCards" :key="item.key" class="col-6 col-lg-3">
          <div class="stat-card h-100">
            <div class="small text-secondary">{{ item.label }}</div>
            <div class="fs-4 fw-bold mt-1">{{ format(stats[item.key]) }}</div>
          </div>
        </div>
      </div>
      <div v-if="stats?.earnings" class="row g-3 mt-1">
        <div class="col-12"><h5>توزيع تقديري للعملات الصافية</h5></div>
        <div class="col-sm-4"><div class="p-3 rounded border">التطبيق <strong class="d-block">{{ format(stats.earnings.app) }}</strong></div></div>
        <div class="col-sm-4"><div class="p-3 rounded border">المضيف <strong class="d-block">{{ format(stats.earnings.host) }}</strong></div></div>
        <div class="col-sm-4"><div class="p-3 rounded border">الوكيل <strong class="d-block">{{ format(stats.earnings.agent) }}</strong></div></div>
        <div class="col-12"><p class="form-text mb-0">{{ stats.earnings.note }}</p></div>
      </div>
      <p v-if="stats?.checkedAt" class="form-text mt-3 mb-0">آخر تحديث: {{ new Date(stats.checkedAt).toLocaleString() }}</p>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { paidMessagesApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const loading = ref(false)
const saving = ref(false)
const loadingStats = ref(false)
const error = ref('')
const success = ref('')
const stats = ref({})
const form = reactive({
  'paid_messages.enabled': false,
  'paid_messages.free_count': 3,
  'paid_messages.price_coins': 10,
  'paid_messages.one_way_discount_percent': 25,
  'paid_messages.price_step_coins': 0,
  'paid_messages.price_step_every': 10,
  'paid_messages.price_max_coins': 10000000,
  'paid_messages.refund_window_minutes': 30,
  'paid_messages.daily_limit': 0,
  'paid_messages.app_percent': 20,
  'paid_messages.host_percent': 70,
  'paid_messages.agent_percent': 10,
})
const fields = [
  { key: 'paid_messages.free_count', label: 'عدد الرسائل المجانية لكل حساب', max: 1000 },
  { key: 'paid_messages.price_coins', label: 'السعر الأساسي بالعملات' },
  { key: 'paid_messages.one_way_discount_percent', label: 'خصم المتابعة من طرف واحد (%)', max: 100, step: 0.1 },
  { key: 'paid_messages.price_step_coins', label: 'زيادة السعر لكل مستوى' },
  { key: 'paid_messages.price_step_every', label: 'كل كم رسالة مدفوعة يزيد السعر', max: 100000 },
  { key: 'paid_messages.price_max_coins', label: 'أقصى سعر للرسالة' },
  { key: 'paid_messages.refund_window_minutes', label: 'مهلة الرد والاسترداد (دقيقة)', max: 10080 },
  { key: 'paid_messages.daily_limit', label: 'الحد اليومي للرسائل المدفوعة', hint: '0 = بلا حد', max: 100000 },
  { key: 'paid_messages.app_percent', label: 'نسبة التطبيق (%)', max: 100, step: 0.1 },
  { key: 'paid_messages.host_percent', label: 'نسبة المضيف (%)', max: 100, step: 0.1 },
  { key: 'paid_messages.agent_percent', label: 'نسبة الوكيل (%)', max: 100, step: 0.1 },
]
const statCards = [
  { key: 'totalMessages', label: 'إجمالي الرسائل' },
  { key: 'freeMessages', label: 'الرسائل المجانية' },
  { key: 'paidMessages', label: 'الرسائل المدفوعة' },
  { key: 'coinsSpent', label: 'العملات المخصومة' },
  { key: 'coinsRefunded', label: 'العملات المستردة' },
  { key: 'replied', label: 'تم الرد عليها' },
  { key: 'pending', label: 'بانتظار الرد' },
  { key: 'expiredUnreplied', label: 'انتهت بلا رد' },
  { key: 'replyRatePercent', label: 'نسبة الرد (%)' },
]
const percentTotal = computed(() => Number(form['paid_messages.app_percent']) + Number(form['paid_messages.host_percent']) + Number(form['paid_messages.agent_percent']))
function format(value) { return Number(value || 0).toLocaleString() }

async function load() {
  loading.value = true; error.value = ''; success.value = ''
  try {
    const response = await paidMessagesApi.settings()
    const payload = response?.data?.data ?? response?.data ?? response
    const current = payload?.current || payload || {}
    for (const key of Object.keys(form)) {
      if (key === 'paid_messages.enabled') form[key] = ['true','1','yes','on'].includes(String(current[key] ?? form[key]).toLowerCase())
      else if (current[key] !== undefined) form[key] = Number(current[key])
    }
  } catch (e) { error.value = e?.message || 'تعذر تحميل إعدادات الرسائل المدفوعة' }
  finally { loading.value = false }
}
async function loadStats() {
  loadingStats.value = true
  try {
    const response = await paidMessagesApi.stats()
    stats.value = response?.data?.data ?? response?.data ?? response ?? {}
  } catch (e) { error.value = e?.message || 'تعذر تحميل الإحصائيات' }
  finally { loadingStats.value = false }
}
async function save() {
  error.value = ''; success.value = ''
  if (Math.abs(percentTotal.value - 100) > 0.001) {
    error.value = 'مجموع نسب التطبيق والمضيف والوكيل يجب أن يساوي 100%.'
    return
  }
  saving.value = true
  try {
    const payload = { ...form, 'paid_messages.enabled': form['paid_messages.enabled'] ? 'true' : 'false' }
    await paidMessagesApi.updateSettings(payload)
    success.value = 'تم حفظ الإعدادات.'
    await loadStats()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ الإعدادات' }
  finally { saving.value = false }
}
onMounted(() => { load(); loadStats() })
</script>
