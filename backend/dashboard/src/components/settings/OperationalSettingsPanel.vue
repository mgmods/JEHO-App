<template>
  <div>
    <AlertMessage v-if="error" :message="error" type="warning" class="mb-3" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" class="mb-3" @dismiss="success = ''" />

    <section class="settings-card mb-3">
      <h3 class="settings-card-title mb-1">اقتصاد المنصة والسحب</h3>
      <p class="form-text mb-3">إعدادات تشغيلية إضافية محفوظة في app_settings. هذه القيم لا تغيّر منطق Economy الحالي تلقائياً.</p>
      <div class="row g-3">
        <div class="col-md-4">
          <label class="form-label">نسبة المنصة (%)</label>
          <input v-model.number="form.platformCommissionPercent" type="number" min="0" max="100" step="0.5" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">هامش السحب (%)</label>
          <input v-model.number="form.withdrawalMarginPercent" type="number" min="0" max="100" step="0.5" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">الحد الأدنى للسحب (♦)</label>
          <input v-model.number="form.minWithdrawDiamonds" type="number" min="0" step="1000" class="form-control" />
        </div>
      </div>
      <div class="row g-3 mt-1">
        <div class="col-md-4">
          <label class="settings-switch">
            <div><div class="settings-switch-title">منع إرسال هدية للنفس</div><div class="settings-switch-hint">يمنع الداعم من إرسال هدية إلى حسابه.</div></div>
            <input v-model="form.selfGiftBlocked" class="form-check-input" type="checkbox" role="switch" />
          </label>
        </div>
        <div class="col-md-4">
          <label class="settings-switch">
            <div><div class="settings-switch-title">تفعيل سحب المضيف</div><div class="settings-switch-hint">السماح بطلبات سحب أرباح المضيف.</div></div>
            <input v-model="form.hostWithdrawalEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
        </div>
        <div class="col-md-4">
          <label class="settings-switch">
            <div><div class="settings-switch-title">تفعيل سحب الوكالة</div><div class="settings-switch-hint">السماح بطلبات سحب أرباح الوكالات.</div></div>
            <input v-model="form.agencyWithdrawalEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
        </div>
        <div class="col-md-4">
          <label class="settings-switch">
            <div><div class="settings-switch-title">تفعيل سحب المحفظة</div><div class="settings-switch-hint">السماح بسحب الرصيد من المحفظة.</div></div>
            <input v-model="form.walletWithdrawalEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
        </div>
      </div>
    </section>

    <section class="settings-card mb-3">
      <div class="d-flex justify-content-between align-items-center gap-2 mb-3">
        <div>
          <h3 class="settings-card-title mb-1">Twilio Verify SMS / OTP</h3>
          <p class="form-text mb-0">إعدادات Twilio محفوظة في إعدادات المنصة. لا تُفعّل OTP قبل اختبار بيانات Twilio.</p>
        </div>
        <span class="badge" :class="form.twilioEnabled ? 'bg-success-subtle text-success-emphasis' : 'bg-secondary-subtle text-secondary-emphasis'">
          {{ form.twilioEnabled ? 'مفعّل' : 'متوقف' }}
        </span>
      </div>

      <div class="row g-3">
        <div class="col-md-6">
          <label class="settings-switch">
            <div><div class="settings-switch-title">تفعيل Twilio Verify SMS</div></div>
            <input v-model="form.twilioEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
        </div>
        <div class="col-md-6">
          <label class="settings-switch">
            <div><div class="settings-switch-title">تفعيل OTP عبر Twilio</div></div>
            <input v-model="form.twilioOtpEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
        </div>
        <div class="col-md-6">
          <label class="form-label">Account SID</label>
          <input v-model="form.twilioAccountSid" type="text" class="form-control" dir="ltr" autocomplete="off" />
        </div>
        <div class="col-md-6">
          <label class="form-label">Auth Token</label>
          <input v-model="form.twilioAuthToken" :type="showTwilioSecrets ? 'text' : 'password'" class="form-control" dir="ltr" autocomplete="new-password" />
        </div>
        <div class="col-md-6">
          <label class="form-label">Verify Service SID</label>
          <input v-model="form.twilioVerifyServiceSid" type="text" class="form-control" dir="ltr" autocomplete="off" />
        </div>
        <div class="col-md-6">
          <label class="form-label">اسم التطبيق في Twilio</label>
          <input v-model="form.twilioAppName" type="text" class="form-control" />
        </div>
        <div class="col-md-6">
          <label class="form-label">مفتاح الدولة الافتراضي</label>
          <input v-model="form.twilioDefaultCountry" type="text" class="form-control" placeholder="+90" dir="ltr" />
        </div>
        <div class="col-md-6 d-flex align-items-end">
          <label class="settings-switch w-100">
            <div><div class="settings-switch-title">إظهار قيم Twilio</div><div class="settings-switch-hint">يؤثر على عرض Auth Token داخل هذه الصفحة فقط.</div></div>
            <input v-model="showTwilioSecrets" class="form-check-input" type="checkbox" role="switch" />
          </label>
        </div>
      </div>
      <div class="alert alert-warning mt-3 mb-0 small">تنبيه: هذه المرحلة تضيف لوحة الإعدادات والتخزين. تشغيل إرسال OTP فعلياً يحتاج ربط خدمة Twilio في Backend.</div>
    </section>

    <section class="settings-card mb-3">
      <h3 class="settings-card-title mb-3">الرسائل المدفوعة</h3>
      <div class="row g-3">
        <div class="col-md-6">
          <label class="settings-switch">
            <div><div class="settings-switch-title">تفعيل نظام الرسائل المدفوعة</div></div>
            <input v-model="form.paidMessagesEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
        </div>
        <div class="col-md-6">
          <label class="form-label">عدد الرسائل المجانية</label>
          <input v-model.number="form.freeMessagesCount" type="number" min="0" step="1" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">سعر الرسالة بالكوينز</label>
          <input v-model.number="form.messagePriceCoins" type="number" min="0" step="1" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">خصم/نسبة الرسالة (%)</label>
          <input v-model.number="form.messageDiscountPercent" type="number" min="0" max="100" step="0.5" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">مدة الرد/الاسترداد (ساعة)</label>
          <input v-model.number="form.messageRefundWindowHours" type="number" min="0" step="1" class="form-control" />
        </div>
      </div>
    </section>

    <section class="settings-card mb-3">
      <h3 class="settings-card-title mb-3">التسوية والأرباح</h3>
      <div class="row g-3">
        <div class="col-md-4">
          <label class="settings-switch">
            <div><div class="settings-switch-title">تفعيل التسوية</div></div>
            <input v-model="form.settlementEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
        </div>
        <div class="col-md-4">
          <label class="settings-switch">
            <div><div class="settings-switch-title">التسوية التلقائية</div></div>
            <input v-model="form.autoSettlementEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
        </div>
        <div class="col-md-4">
          <label class="form-label">دورة التسوية (ساعة)</label>
          <input v-model.number="form.settlementCycleHours" type="number" min="1" step="1" class="form-control" />
        </div>
      </div>
      <div class="alert alert-info mt-3 mb-0 small">إعدادات التسوية تُحفظ الآن كإعدادات تشغيلية، وسيتم ربطها بمنطق التسوية الفعلي في خطوة Backend التالية.</div>
    </section>

    <div class="d-flex justify-content-end gap-2">
      <button type="button" class="btn btn-aurora" :disabled="saving" @click="save">
        <span v-if="saving" class="spinner-border spinner-border-sm me-1" />
        حفظ الإعدادات
      </button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { settingsApi } from '@/api'
import AlertMessage from '@/components/AlertMessage.vue'
import { toast } from '@/composables/useToast'

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const showTwilioSecrets = ref(false)

const form = reactive({
  platformCommissionPercent: 0,
  withdrawalMarginPercent: 0,
  minWithdrawDiamonds: 200000,
  selfGiftBlocked: true,
  hostWithdrawalEnabled: true,
  agencyWithdrawalEnabled: true,
  walletWithdrawalEnabled: true,
  twilioEnabled: false,
  twilioOtpEnabled: false,
  twilioAccountSid: '',
  twilioAuthToken: '',
  twilioVerifyServiceSid: '',
  twilioAppName: 'JEHO CHAT',
  twilioDefaultCountry: '+90',
  paidMessagesEnabled: false,
  freeMessagesCount: 0,
  messagePriceCoins: 0,
  messageDiscountPercent: 0,
  messageRefundWindowHours: 0,
  settlementEnabled: false,
  autoSettlementEnabled: false,
  settlementCycleHours: 24,
})

const keys = {
  platformCommissionPercent: 'economy.platform_commission_percent',
  withdrawalMarginPercent: 'economy.withdrawal_margin_percent',
  minWithdrawDiamonds: 'economy.min_withdraw_diamonds',
  selfGiftBlocked: 'economy.self_gift_blocked',
  hostWithdrawalEnabled: 'withdrawal.host_enabled',
  agencyWithdrawalEnabled: 'withdrawal.agency_enabled',
  walletWithdrawalEnabled: 'withdrawal.wallet_enabled',
  twilioEnabled: 'twilio.verify.enabled',
  twilioOtpEnabled: 'twilio.otp.enabled',
  twilioAccountSid: 'twilio.account_sid',
  twilioAuthToken: 'twilio.auth_token',
  twilioVerifyServiceSid: 'twilio.verify_service_sid',
  twilioAppName: 'twilio.app_name',
  twilioDefaultCountry: 'twilio.default_country',
  paidMessagesEnabled: 'paid_messages.enabled',
  freeMessagesCount: 'paid_messages.free_count',
  messagePriceCoins: 'paid_messages.price_coins',
  messageDiscountPercent: 'paid_messages.discount_percent',
  messageRefundWindowHours: 'paid_messages.refund_window_hours',
  settlementEnabled: 'settlement.enabled',
  autoSettlementEnabled: 'settlement.auto_enabled',
  settlementCycleHours: 'settlement.cycle_hours',
}

const defaults = {
  platformCommissionPercent: 0, withdrawalMarginPercent: 0, minWithdrawDiamonds: 200000,
  selfGiftBlocked: true, hostWithdrawalEnabled: true, agencyWithdrawalEnabled: true, walletWithdrawalEnabled: true,
  twilioEnabled: false, twilioOtpEnabled: false, twilioAccountSid: '', twilioAuthToken: '',
  twilioVerifyServiceSid: '', twilioAppName: 'JEHO CHAT', twilioDefaultCountry: '+90',
  paidMessagesEnabled: false, freeMessagesCount: 0, messagePriceCoins: 0, messageDiscountPercent: 0,
  messageRefundWindowHours: 0, settlementEnabled: false, autoSettlementEnabled: false, settlementCycleHours: 24,
}

function bool(v, fallback) {
  if (v === undefined || v === null || v === '') return fallback
  if (typeof v === 'boolean') return v
  return /^(1|true|yes)$/i.test(String(v))
}
function num(v, fallback) {
  const n = Number(v)
  return Number.isFinite(n) ? n : fallback
}

async function load() {
  loading.value = true
  const { data, error: err } = await settingsApi.get()
  loading.value = false
  if (err) {
    error.value = err.message
    return
  }
  const raw = data?.settings || data?.data || data || []
  const map = {}
  if (Array.isArray(raw)) raw.forEach((row) => { if (row?.key != null) map[row.key] = row.value })
  else Object.assign(map, raw || {})
  for (const field of Object.keys(keys)) {
    const key = keys[field]
    if (typeof defaults[field] === 'boolean') form[field] = bool(map[key], defaults[field])
    else if (typeof defaults[field] === 'number') form[field] = num(map[key], defaults[field])
    else form[field] = map[key] ?? defaults[field]
  }
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {}
  for (const field of Object.keys(keys)) {
    const value = form[field]
    payload[keys[field]] = typeof value === 'boolean' ? String(value) : String(value ?? '')
  }
  const { error: err } = await settingsApi.update(payload)
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = 'تم حفظ الإعدادات التشغيلية'
  toast().success(success.value)
}

onMounted(load)
</script>
