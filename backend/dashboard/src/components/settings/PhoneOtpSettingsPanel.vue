<template>
  <section class="settings-card">
    <div class="d-flex flex-wrap justify-content-between align-items-start gap-3 mb-3">
      <div>
        <h3 class="settings-card-title mb-1">التحقق برقم الهاتف (SMS OTP)</h3>
        <p class="form-text mb-0">إدارة إرسال رموز التسجيل والتحقق من JEHO مباشرة من لوحة الإدارة. بيانات الاعتماد تُحفظ مشفّرة ولا تُعاد إلى المتصفح.</p>
      </div>
      <span class="badge" :class="form.enabled && form.credentialsConfigured ? 'text-bg-success' : 'text-bg-secondary'">
        {{ form.enabled && form.credentialsConfigured ? 'الإرسال مفعّل' : 'غير مفعّل' }}
      </span>
    </div>

    <div v-if="loading" class="py-4"><LoadingSpinner /></div>
    <template v-else>
      <div class="alert alert-info small">
        يلزم حساب Twilio Verify وخدمة Verify مهيّأة لإرسال SMS. لا ننشئ حساب مزوّد رسائل تلقائياً؛ هذه بيانات اعتماد الخدمة التي ستُستخدم للإرسال الفعلي.
      </div>

      <div class="border rounded-3 p-3 mb-3">
        <div class="form-check form-switch">
          <input id="phone-otp-enabled" v-model="form.enabled" class="form-check-input" type="checkbox" role="switch" />
          <label class="form-check-label fw-semibold" for="phone-otp-enabled">تفعيل إرسال رموز التحقق للتسجيل بالهاتف</label>
        </div>
        <div class="small text-secondary mt-1">لن يُفعّل النظام الإرسال قبل اكتمال بيانات المزود. عند التعطيل لن يُستخدم مسار OTP المحلي كبديل إنتاجي.</div>
      </div>

      <div class="row g-3">
        <div class="col-md-6">
          <label class="form-label">Twilio Account SID</label>
          <input v-model.trim="form.accountSid" class="form-control" dir="ltr" autocomplete="off" placeholder="ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" />
        </div>
        <div class="col-md-6">
          <label class="form-label">Twilio Verify Service SID</label>
          <input v-model.trim="form.verifyServiceSid" class="form-control" dir="ltr" autocomplete="off" placeholder="VAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" />
        </div>
        <div class="col-12">
          <label class="form-label">Twilio Auth Token</label>
          <input v-model="form.authToken" class="form-control" dir="ltr" type="password" autocomplete="new-password" :placeholder="form.authTokenConfigured ? 'مفتاح محفوظ ومشفّر — اتركه فارغاً للإبقاء عليه' : 'أدخل Auth Token'" />
          <div class="form-text">{{ form.authTokenConfigured ? 'يوجد مفتاح محفوظ. لن يظهر المفتاح الحالي لأسباب أمنية.' : 'لم يتم حفظ مفتاح حتى الآن.' }}</div>
        </div>
      </div>

      <div class="d-flex flex-wrap gap-2 mt-3">
        <button type="button" class="btn btn-aurora" :disabled="saving || !canWrite" @click="save">
          <span v-if="saving" class="spinner-border spinner-border-sm me-1"></span>
          {{ saving ? 'جارٍ الحفظ…' : 'حفظ إعدادات SMS' }}
        </button>
      </div>

      <hr class="my-4" />
      <h5 class="mb-2">اختبار وصول الرمز</h5>
      <p class="form-text">أدخل رقمك بصيغته الدولية مع +، ثم أرسل رسالة اختبار. لا نعرض رمز التحقق داخل لوحة الإدارة.</p>
      <div class="row g-2 align-items-end">
        <div class="col-md-8">
          <label class="form-label">رقم الهاتف للاختبار</label>
          <input v-model.trim="testPhone" class="form-control" dir="ltr" inputmode="tel" placeholder="+359..." />
        </div>
        <div class="col-md-4">
          <button type="button" class="btn btn-outline-primary w-100" :disabled="testing || !form.credentialsConfigured" @click="sendTest">
            <span v-if="testing" class="spinner-border spinner-border-sm me-1"></span>
            {{ testing ? 'جارٍ الإرسال…' : 'إرسال رمز تجريبي' }}
          </button>
        </div>
      </div>
      <div v-if="testMessage" class="alert mt-3 mb-0" :class="testOk ? 'alert-success' : 'alert-warning'" role="status">{{ testMessage }}</div>
      <div v-if="!canWrite" class="small text-warning mt-3">حسابك يملك صلاحية القراءة فقط لهذه الإعدادات.</div>
    </template>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { phoneOtpApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/composables/useToast'

const auth = useAuthStore()
const canWrite = auth.can('settings', 'write')
const loading = ref(true)
const saving = ref(false)
const testing = ref(false)
const testPhone = ref('')
const testMessage = ref('')
const testOk = ref(false)
const form = reactive({
  enabled: false,
  accountSid: '',
  verifyServiceSid: '',
  authToken: '',
  authTokenConfigured: false,
  credentialsConfigured: false,
})

async function load() {
  loading.value = true
  const { data, error } = await phoneOtpApi.get()
  loading.value = false
  if (error) {
    toast().danger(error.message || 'تعذّر تحميل إعدادات التحقق')
    return
  }
  const value = data?.data || data || {}
  Object.assign(form, {
    enabled: !!value.enabled,
    accountSid: value.accountSid || '',
    verifyServiceSid: value.verifyServiceSid || '',
    authToken: '',
    authTokenConfigured: !!value.authTokenConfigured,
    credentialsConfigured: !!value.credentialsConfigured,
  })
}

async function save() {
  if (!canWrite) return
  saving.value = true
  testMessage.value = ''
  const payload = {
    enabled: form.enabled,
    accountSid: form.accountSid,
    verifyServiceSid: form.verifyServiceSid,
  }
  if (form.authToken.trim()) payload.authToken = form.authToken.trim()
  const { data, error } = await phoneOtpApi.update(payload)
  saving.value = false
  if (error) {
    toast().danger(error.message || 'تعذّر حفظ إعدادات SMS')
    return
  }
  const value = data?.data || data || {}
  form.authToken = ''
  form.authTokenConfigured = !!value.authTokenConfigured
  form.credentialsConfigured = !!value.credentialsConfigured
  form.enabled = !!value.enabled
  toast().success(form.enabled ? 'تم حفظ إعدادات التحقق وتفعيلها' : 'تم حفظ إعدادات التحقق')
}

async function sendTest() {
  testing.value = true
  testMessage.value = ''
  const { data, error } = await phoneOtpApi.test(testPhone.value)
  testing.value = false
  if (error) {
    testOk.value = false
    testMessage.value = error.message || 'فشل إرسال رسالة الاختبار'
    toast().danger(testMessage.value)
    return
  }
  const value = data?.data || data || {}
  testOk.value = !!value.ok
  testMessage.value = value.message || 'تم إرسال طلب الاختبار.'
  toast().success(testMessage.value)
}

onMounted(load)
</script>
