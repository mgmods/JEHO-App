<template>
  <div class="mt-4">
    <AlertMessage v-if="error" :message="error" type="warning" class="mb-3" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" class="mb-3" @dismiss="success = ''" />
    <AlertMessage
      v-if="testMessage"
      :message="testMessage"
      :type="testOk ? 'success' : 'warning'"
      class="mb-3"
      @dismiss="testMessage = ''"
    />
    <LoadingSpinner v-if="loading" />

    <form v-else class="row g-3" @submit.prevent="save">
      <div class="col-12">
        <div class="settings-card">
          <h3 class="settings-card-title">Fourthwall — الدفع بالبطاقة</h3>
          <p class="text-secondary small mb-2">
            من هنا تدخل بيانات المطور (API User / Password / Storefront Token / Webhook Secret).
            التطبيق يأخذ سعر الباقة وعدد الكوينز تلقائياً وينشئ منتج الدفع عند الحاجة.
          </p>
          <div class="small mb-0">
            <div>
              <span class="text-secondary">Webhook URL:</span>
              <code class="ms-1 user-select-all" dir="ltr">{{ masked.webhookUrl }}</code>
            </div>
            <div class="mt-1">
              الحالة:
              <span class="fw-medium">{{ masked.configured ? 'جاهز' : 'غير مكتمل' }}</span>
              · Shop:
              <span class="fw-medium">{{ masked.shopDomain || '—' }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="settings-card h-100">
          <h3 class="settings-card-title">بيانات Open API</h3>
          <div class="mb-3">
            <label class="form-label">API User (email)</label>
            <input
              v-model="form.apiUser"
              class="form-control font-monospace"
              dir="ltr"
              autocomplete="off"
              :placeholder="masked.apiUserConfigured ? `…${masked.apiUserHint || ''}` : 'fw_api_…@fourthwall.com'"
            />
          </div>
          <div class="mb-3">
            <label class="form-label">API Password</label>
            <input
              v-model="form.apiPassword"
              type="password"
              class="form-control font-monospace"
              dir="ltr"
              autocomplete="off"
              :placeholder="masked.apiPasswordConfigured ? 'اتركه فارغ للإبقاء' : ''"
            />
            <div class="form-check mt-2">
              <input id="fwClearPass" v-model="form.clearApiPassword" class="form-check-input" type="checkbox" />
              <label class="form-check-label" for="fwClearPass">مسح كلمة السر</label>
            </div>
          </div>
          <div class="mb-0">
            <label class="form-label">Storefront Token (ptkn_…)</label>
            <input
              v-model="form.storefrontToken"
              type="password"
              class="form-control font-monospace"
              dir="ltr"
              autocomplete="off"
              :placeholder="masked.storefrontTokenConfigured ? 'اتركه فارغ للإبقاء' : ''"
            />
            <div class="form-check mt-2">
              <input id="fwClearTok" v-model="form.clearStorefrontToken" class="form-check-input" type="checkbox" />
              <label class="form-check-label" for="fwClearTok">مسح التوكين</label>
            </div>
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="settings-card h-100">
          <h3 class="settings-card-title">المتجر والـ Webhook</h3>
          <div class="mb-3">
            <label class="form-label">Shop domain</label>
            <input
              v-model="form.shopDomain"
              class="form-control font-monospace"
              dir="ltr"
              placeholder="dijitalplus-shop.fourthwall.com"
            />
          </div>
          <div class="mb-0">
            <label class="form-label">Webhook Secret</label>
            <input
              v-model="form.webhookSecret"
              type="password"
              class="form-control font-monospace"
              dir="ltr"
              autocomplete="off"
              :placeholder="masked.webhookSecretConfigured ? 'اتركه فارغ للإبقاء' : 'من صفحة Webhooks'"
            />
            <div class="form-check mt-2">
              <input id="fwClearWh" v-model="form.clearWebhookSecret" class="form-check-input" type="checkbox" />
              <label class="form-check-label" for="fwClearWh">مسح السر</label>
            </div>
            <div class="form-text">
              انسخه من Fourthwall → Settings → For developers → Webhooks بعد Create webhook
            </div>
          </div>
        </div>
      </div>

      <div class="col-12 d-flex gap-2 flex-wrap">
        <button class="btn btn-aurora" type="submit" :disabled="saving">
          <span v-if="saving" class="spinner-border spinner-border-sm me-2"></span>
          حفظ Fourthwall
        </button>
        <button class="btn btn-ghost" type="button" :disabled="testing" @click="runTest">
          <span v-if="testing" class="spinner-border spinner-border-sm me-2"></span>
          اختبار الاتصال
        </button>
        <button class="btn btn-ghost" type="button" :disabled="syncing" @click="runSync">
          <span v-if="syncing" class="spinner-border spinner-border-sm me-2"></span>
          مزامنة باقات الشحن
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
const testing = ref(false)
const syncing = ref(false)
const error = ref('')
const success = ref('')
const testMessage = ref('')
const testOk = ref(false)

const masked = reactive({
  configured: false,
  apiUserConfigured: false,
  apiPasswordConfigured: false,
  storefrontTokenConfigured: false,
  webhookSecretConfigured: false,
  apiUserHint: null,
  shopDomain: '',
  webhookUrl: '',
  variantMap: {},
})

const form = reactive({
  apiUser: '',
  apiPassword: '',
  storefrontToken: '',
  shopDomain: '',
  webhookSecret: '',
  clearApiPassword: false,
  clearStorefrontToken: false,
  clearWebhookSecret: false,
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await paymentSettingsApi.getFourthwall()
    Object.assign(masked, data || {})
    form.shopDomain = data?.shopDomain || ''
    form.apiUser = ''
    form.apiPassword = ''
    form.storefrontToken = ''
    form.webhookSecret = ''
    form.clearApiPassword = false
    form.clearStorefrontToken = false
    form.clearWebhookSecret = false
  } catch (e) {
    error.value = e?.message || 'تعذر تحميل إعدادات Fourthwall'
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const payload = {
      apiUser: form.apiUser || undefined,
      apiPassword: form.apiPassword || undefined,
      storefrontToken: form.storefrontToken || undefined,
      shopDomain: form.shopDomain || undefined,
      webhookSecret: form.webhookSecret || undefined,
      clearApiPassword: form.clearApiPassword || undefined,
      clearStorefrontToken: form.clearStorefrontToken || undefined,
      clearWebhookSecret: form.clearWebhookSecret || undefined,
    }
    const data = await paymentSettingsApi.updateFourthwall(payload)
    Object.assign(masked, data || {})
    success.value = 'تم حفظ إعدادات Fourthwall'
    toast.success(success.value)
    form.apiPassword = ''
    form.storefrontToken = ''
    form.webhookSecret = ''
  } catch (e) {
    error.value = e?.message || 'فشل الحفظ'
  } finally {
    saving.value = false
  }
}

async function runTest() {
  testing.value = true
  testMessage.value = ''
  try {
    const data = await paymentSettingsApi.testFourthwall()
    testOk.value = !!data?.ok
    testMessage.value = data?.ok
      ? `متصل: ${data.name || ''} · ${data.publicDomain || ''}`
      : 'فشل الاختبار'
  } catch (e) {
    testOk.value = false
    testMessage.value = e?.message || 'فشل الاختبار'
  } finally {
    testing.value = false
  }
}

async function runSync() {
  syncing.value = true
  error.value = ''
  success.value = ''
  try {
    const data = await paymentSettingsApi.syncFourthwallPackages()
    const items = data?.items || []
    const ok = items.filter((i) => i.ok).length
    const bad = items.length - ok
    success.value = `مزامنة الباقات: نجح ${ok} · فشل ${bad}`
    toast.success(success.value)
    await load()
  } catch (e) {
    error.value = e?.message || 'فشلت المزامنة'
  } finally {
    syncing.value = false
  }
}

onMounted(load)
</script>
