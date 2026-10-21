<template>
  <div class="toast-manager-page">
    <PageHeader title="إدارة توستات الهدايا" subtitle="ارفع الخلفية مرة واحدة، واترك اللمعة وحركة الإطار للتطبيق." />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <div class="row g-3">
      <div class="col-xl-7">
        <section class="settings-card h-100">
          <h3 class="settings-card-title">تصميم التوست</h3>
          <p class="text-secondary small">النوع المحدد: إرسال الهدايا والعداد X. استخدم خلفية أفقية خفيفة، واترك مساحة للصورة والنص والعداد.</p>
          <div class="toast-preview-wrap mb-3">
            <div class="toast-preview" :style="previewStyle">
              <div class="toast-preview-avatar">🎁</div>
              <div class="toast-preview-copy"><strong>اسم المرسل</strong><span>أرسل هدية</span></div>
              <span class="toast-preview-count">X77</span>
              <div v-if="form.shineEnabled" class="toast-preview-shine"></div>
            </div>
          </div>
          <div class="mb-3">
            <label class="form-label">صورة خلفية التوست</label>
            <input ref="fileInput" class="form-control" type="file" accept="image/png,image/jpeg,image/webp" :disabled="uploading || !canWriteSettings" @change="uploadBackground" />
            <div class="form-text">PNG أو JPG أو WEBP، بحد أقصى 8 ميغابايت. يفضّل تصميم أفقي بنسبة 3:1.</div>
          </div>
          <div v-if="form.backgroundUrl" class="d-flex align-items-center gap-2 mb-3">
            <a :href="resolveAsset(form.backgroundUrl)" target="_blank" rel="noopener">فتح الخلفية الحالية</a>
            <button class="btn btn-outline-danger btn-sm" type="button" @click="form.backgroundUrl = ''">إزالة الخلفية</button>
          </div>
          <div class="d-flex flex-wrap gap-2">
            <button class="btn btn-aurora" type="button" :disabled="saving || loading || !canWriteSettings" @click="save">
              <span v-if="saving" class="spinner-border spinner-border-sm me-1"></span>
              حفظ وتطبيق
            </button>
            <button class="btn btn-outline-secondary" type="button" :disabled="loading || saving" @click="load">إعادة تحميل</button>
          </div>
        </section>
      </div>
      <div class="col-xl-5">
        <section class="settings-card mb-3">
          <h3 class="settings-card-title">الحركة والمؤثرات</h3>
          <label class="settings-switch">
            <div><div class="settings-switch-title">لمعة ذهبية متحركة</div><div class="settings-switch-hint">تمر اللمعة فوق التوست بشكل خفيف، دون تعطيل الضغط.</div></div>
            <input v-model="form.shineEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
          <label class="settings-switch">
            <div><div class="settings-switch-title">توهج إطار التوست</div><div class="settings-switch-hint">توهج بسيط على الحواف مع الحركة، دون مؤثرات ثقيلة.</div></div>
            <input v-model="form.frameGlowEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
          <div class="mt-3">
            <label class="form-label">مدة الظهور: {{ form.durationSeconds }} ثوانٍ</label>
            <input v-model.number="form.durationSeconds" class="form-range" type="range" min="2" max="8" step="1" />
            <div class="d-flex justify-content-between small text-secondary"><span>2 ث</span><span>5 ث</span><span>8 ث</span></div>
          </div>
        </section>
        <section class="settings-card">
          <h3 class="settings-card-title">ملاحظات التطبيق</h3>
          <ul class="small text-secondary mb-0 ps-3">
            <li>العداد يعرض العدد الحقيقي المختار مثل X1 وX7 وX77 وX177.</li>
            <li>تغيير الخلفية لا يحتاج تحديث APK بعد وصول إعدادات الداشبورد للتطبيق.</li>
            <li>هذا القسم لا يغيّر إرسال الهدايا أو خصم العملات أو الصوت أو ZEGO أو المقاعد.</li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { toastManagerApi, uploadsApi } from '@/api'
import { resolveAsset } from '@/utils/assets'
import { toast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'

const auth = useAuthStore()
const canWriteSettings = computed(() => auth.can('settings', 'write'))
const form = reactive({ backgroundUrl: '', shineEnabled: true, frameGlowEnabled: true, durationSeconds: 3, version: 1 })
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const error = ref('')
const success = ref('')
const fileInput = ref(null)
const previewStyle = computed(() => {
  const url = resolveAsset(form.backgroundUrl)
  return {
    backgroundImage: url ? 'linear-gradient(90deg, rgba(30,7,18,.78), rgba(50,12,25,.18)), url("' + url.replace(/"/g, '%22') + '")' : 'linear-gradient(100deg, #4b1024, #9d2343 54%, #3c194d)',
    boxShadow: form.frameGlowEnabled ? '0 0 0 1px rgba(255,211,111,.85), 0 0 18px rgba(255,191,64,.42)' : '0 0 0 1px rgba(255,211,111,.35)',
  }
})
async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await toastManagerApi.get()
  loading.value = false
  if (err) { error.value = err.message; return }
  const cfg = data?.data || data || {}
  Object.assign(form, {
    backgroundUrl: String(cfg.backgroundUrl || ''),
    shineEnabled: cfg.shineEnabled !== false,
    frameGlowEnabled: cfg.frameGlowEnabled !== false,
    durationSeconds: Math.max(2, Math.min(8, Number(cfg.durationSeconds) || 3)),
    version: Number(cfg.version) || 1,
  })
}
async function uploadBackground(event) {
  if (!canWriteSettings.value) { toast().danger('لا تملك صلاحية تعديل إعدادات الداشبورد'); return }
  const file = event?.target?.files?.[0]
  if (!file) return
  if (file.size > 8 * 1024 * 1024 || !/\.(png|jpe?g|webp)$/i.test(file.name)) {
    toast().danger('اختر صورة PNG أو JPG أو WEBP بحجم لا يتجاوز 8 ميغابايت')
    event.target.value = ''
    return
  }
  uploading.value = true
  const { data, error: err } = await uploadsApi.upload(file)
  uploading.value = false
  event.target.value = ''
  if (err) { toast().danger(err.message); return }
  const uploaded = data?.data || data || {}
  const url = String(uploaded.url || uploaded.path || (uploaded.filename ? '/uploads/' + uploaded.filename : '')).trim()
  if (!url) { toast().danger('تم الرفع لكن لم يصل رابط الصورة من الخادم'); return }
  form.backgroundUrl = url
  toast().success('تم رفع الخلفية. اضغط حفظ وتطبيق لاعتمادها.')
}
async function save() {
  if (!canWriteSettings.value) { toast().danger('لا تملك صلاحية تعديل إعدادات الداشبورد'); return }
  saving.value = true
  error.value = ''
  success.value = ''
  const { data, error: err } = await toastManagerApi.save({
    backgroundUrl: form.backgroundUrl,
    shineEnabled: form.shineEnabled,
    frameGlowEnabled: form.frameGlowEnabled,
    durationSeconds: form.durationSeconds,
  })
  saving.value = false
  if (err) { error.value = err.message; toast().danger(err.message); return }
  Object.assign(form, data?.data || data || {})
  success.value = 'تم حفظ إعدادات التوست. ستستخدمها الإصدارات التي تدعم إدارة التوستات عن بُعد.'
  toast().success(success.value)
}
onMounted(load)
</script>

<style scoped>
.toast-preview-wrap{padding:24px 10px;border-radius:16px;background:linear-gradient(135deg,#191a25,#2b2232);overflow:hidden}
.toast-preview{position:relative;display:flex;align-items:center;gap:10px;min-height:76px;padding:10px 12px;border:1px solid rgba(255,213,122,.9);border-radius:13px;background-size:cover;background-position:center;overflow:hidden;color:#fff}
.toast-preview-avatar{display:grid;place-items:center;flex:0 0 48px;height:48px;border:2px solid #f9d77e;border-radius:12px;background:rgba(35,15,28,.82);font-size:24px}
.toast-preview-copy{display:flex;flex:1;min-width:0;flex-direction:column;gap:4px;text-shadow:0 1px 4px #160711}
.toast-preview-copy strong,.toast-preview-copy span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.toast-preview-count{flex:0 0 auto;padding:7px 9px;border:1px solid #ffe3a0;border-radius:10px;background:linear-gradient(#8f183b,#3a102d);font-weight:800;color:#ffe6a4}
.toast-preview-shine{position:absolute;top:-20%;bottom:-20%;left:-30%;width:30px;transform:skewX(-18deg);background:linear-gradient(90deg,transparent,rgba(255,255,255,.6),transparent);animation:toast-shine 2.4s linear infinite;pointer-events:none}
@keyframes toast-shine{to{left:120%}}
</style>
