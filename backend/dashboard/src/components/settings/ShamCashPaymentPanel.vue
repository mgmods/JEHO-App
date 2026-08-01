<template>
  <div class="mt-4">
    <AlertMessage v-if="error" :message="error" type="warning" class="mb-3" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" class="mb-3" @dismiss="success = ''" />
    <LoadingSpinner v-if="loading" />

    <form v-else class="row g-3" @submit.prevent="save">
      <div class="col-12">
        <div class="settings-card">
          <h3 class="settings-card-title">شام كاش — سوريا</h3>
          <p class="text-secondary small mb-2">
            بدون API. ضع <strong>رقم حساب شام كاش</strong> (مثل
            <code dir="ltr">2c5e893402a9cd76d27d28b4b92605aa</code>)
            واسم صاحب الحساب — يظهر للمستخدم QR + الاسم مثل تطبيق شام كاش.
            بعد التحويل يرسل إثبات على واتساب، ثم توافق الطلب من
            <strong>المحفظة → طلبات الشحن</strong>.
          </p>
          <div class="small mb-0">
            حالة التطبيق:
            <span class="fw-medium">
              {{ form.accountId && String(form.accountId).replace(/\s/g, '').length >= 8
                ? 'شاشة QR جاهزة'
                : 'توست قريباً (لا يوجد رقم حساب)' }}
            </span>
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="settings-card h-100">
          <h3 class="settings-card-title">حساب شام كاش (للـ QR)</h3>
          <div class="mb-3">
            <label class="form-label">اسم صاحب الحساب (يظهر تحت الـ QR)</label>
            <input
              v-model="form.accountName"
              class="form-control"
              placeholder="علي محمود الخليل"
            />
          </div>
          <div class="mb-3">
            <label class="form-label">رقم حساب شام كاش / المعرّف</label>
            <input
              v-model="form.accountId"
              class="form-control font-monospace"
              dir="ltr"
              placeholder="2c5e893402a9cd76d27d28b4b92605aa"
              autocomplete="off"
            />
            <div class="form-text">
              هذا هو الرقم الذي يظهر كباركود في التطبيق (ليس رقم الهاتف).
            </div>
          </div>
          <div class="mb-0">
            <label class="form-label">اسم الطريقة في قائمة الدفع</label>
            <input
              v-model="form.displayName"
              class="form-control"
              placeholder="شام كاش"
            />
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="settings-card h-100">
          <h3 class="settings-card-title">واتساب لإرسال الإثبات</h3>
          <div class="mb-3">
            <label class="form-label">رقم واتساب (مع مفتاح الدولة)</label>
            <input
              v-model="form.whatsapp"
              class="form-control font-monospace"
              dir="ltr"
              placeholder="9639XXXXXXXX"
              autocomplete="off"
            />
          </div>
          <div class="mb-0">
            <label class="form-label">تعليمات للمستخدم</label>
            <textarea
              v-model="form.instructions"
              class="form-control"
              rows="5"
              placeholder="حوّل عبر شام كاش ثم أرسل صورة الإثبات على واتساب…"
            />
          </div>
        </div>
      </div>

      <div class="col-12">
        <button class="btn btn-aurora" type="submit" :disabled="saving">
          <span v-if="saving" class="spinner-border spinner-border-sm me-2"></span>
          حفظ شام كاش
        </button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { paymentSettingsApi } from '@/api'
import { toast } from '@/composables/useToast'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

const form = reactive({
  enabled: true,
  whatsapp: '',
  displayName: 'شام كاش',
  accountName: '',
  accountId: '',
  instructions: '',
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await paymentSettingsApi.getShamCash()
    form.enabled = data?.enabled !== false
    form.whatsapp = data?.whatsapp || ''
    form.displayName = data?.displayName || 'شام كاش'
    form.accountName = data?.accountName || ''
    form.accountId = data?.accountId || ''
    form.instructions = data?.instructions || ''
  } catch (e) {
    error.value = e?.message || 'تعذر تحميل إعدادات شام كاش'
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const accountId = String(form.accountId || '').trim().replace(/\s+/g, '')
    if (accountId.length < 8) {
      throw new Error('أدخل رقم حساب شام كاش (المعرّف) — بدون رقم حساب يظهر للمستخدم «قريباً»')
    }
    const data = await paymentSettingsApi.updateShamCash({
      enabled: true,
      whatsapp: String(form.whatsapp || '').trim(),
      displayName: String(form.displayName || '').trim() || 'شام كاش',
      accountName: String(form.accountName || '').trim() || 'شام كاش',
      accountId,
      instructions: String(form.instructions || '').trim(),
    })
    form.whatsapp = data?.whatsapp || ''
    form.displayName = data?.displayName || 'شام كاش'
    form.accountName = data?.accountName || ''
    form.accountId = data?.accountId || ''
    form.instructions = data?.instructions || ''
    success.value = 'تم حفظ إعدادات شام كاش'
    toast().success(success.value)
  } catch (e) {
    error.value = e?.message || 'تعذر الحفظ'
    toast().danger(error.value)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
