<template>
  <div class="toast-manager-page">
    <PageHeader title="إدارة التوستات والمؤثرات" subtitle="ارفع التصاميم من الداشبورد، والتطبيق يشغّل الحركة محلياً على أجهزة المستخدمين." />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <section class="settings-card mb-3">
      <div class="toast-tabs" role="tablist" aria-label="أنواع التوستات">
        <button v-for="tab in tabs" :key="tab.key" type="button" class="toast-tab" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">{{ tab.label }}</button>
      </div>
      <div class="toast-preview-wrap mt-3 mb-3">
        <div v-if="activeTab !== 'luckyButton'" class="toast-canvas" :style="canvasStyle"
             @pointermove="moveDrag" @pointerup="stopDrag" @pointercancel="stopDrag">
          <img v-if="currentStyle.backgroundUrl" class="toast-canvas-image" :src="resolveAsset(currentStyle.backgroundUrl)" alt="تصميم التوست" draggable="false" />
          <div class="preview-avatar drag-item" :style="avatarPreviewStyle" @pointerdown.stop="startDrag('avatar', $event)">🧑</div>
          <strong class="preview-title drag-item" :style="titlePreviewStyle" @pointerdown.stop="startDrag('title', $event)">{{ activeTab === 'giftSend' ? 'اسم المرسل' : 'الفائز المحظوظ' }}</strong>
          <span class="preview-body drag-item" :style="bodyPreviewStyle" @pointerdown.stop="startDrag('body', $event)">{{ activeTab === 'giftSend' ? 'أرسل هدية' : (activeTab === 'luckyWinHigh' ? 'ربح عالي · +12,500 كوين' : 'مبروك الربح · +250 كوين') }}</span>
          <span v-if="activeTab === 'giftSend'" class="preview-count drag-item" :style="countPreviewStyle" @pointerdown.stop="startDrag('count', $event)">X77</span>
          <div v-if="currentStyle.shineEnabled" class="preview-shine"></div>
        </div>
        <div v-else class="button-preview-wrap">
          <div class="button-preview" :class="{ pulse: form.luckyButton.pulseEnabled }">
            <img v-if="form.luckyButton.iconUrl" :src="resolveAsset(form.luckyButton.iconUrl)" alt="أيقونة زر المحظوظ" />
            <span v-else>💎</span>
          </div>
          <span>معاينة الزر المنبثق بعد اختيار هدية المحظوظ</span>
        </div>
      </div>
      <div v-if="activeTab !== 'luckyButton'" class="mb-3">
        <label class="form-label">{{ activeTab === 'giftSend' ? 'خلفية توست إرسال الهدايا والعداد X' : activeTab === 'luckyWinNormal' ? 'خلفية توست الربح العادي' : 'خلفية توست الربح العالي والمردود العالي' }}</label>
        <input class="form-control" type="file" accept="image/png,image/jpeg,image/webp" :disabled="uploading || !canWriteSettings" @change="uploadAsset" />
        <div class="form-text">PNG أو JPG أو WEBP حتى 8 ميغابايت. ارفع صورة التوست كاملة كخلفية، ثم اضبط مكان صورة الفائز والكتابة من المعاينة.</div>
        <div v-if="currentStyle.backgroundUrl" class="mt-2 d-flex align-items-center gap-2">
          <a :href="resolveAsset(currentStyle.backgroundUrl)" target="_blank" rel="noopener">فتح الصورة المرفوعة</a>
          <button type="button" class="btn btn-outline-danger btn-sm" @click="currentStyle.backgroundUrl = ''">إزالة</button>
        </div>
      </div>
      <div v-else class="mb-3">
        <label class="form-label">أيقونة الزر المنبثق</label>
        <input class="form-control" type="file" accept="image/png,image/jpeg,image/webp" :disabled="uploading || !canWriteSettings" @change="uploadAsset" />
        <div class="form-text">يفضّل PNG بخلفية شفافة وأبعاد مربعة. الأيقونة تُحمّل مرة واحدة وتُعرض محلياً على جهاز المستخدم.</div>
        <div v-if="form.luckyButton.iconUrl" class="mt-2 d-flex align-items-center gap-2">
          <a :href="resolveAsset(form.luckyButton.iconUrl)" target="_blank" rel="noopener">فتح الأيقونة الحالية</a>
          <button type="button" class="btn btn-outline-danger btn-sm" @click="form.luckyButton.iconUrl = ''">إزالة الأيقونة</button>
        </div>
      </div>
    </section>

    <div class="row g-3" v-if="activeTab !== 'luckyButton'">
      <div class="col-xl-7">
        <section class="settings-card h-100">
          <h3 class="settings-card-title">موضع الصورة والكتابة</h3>
          <p class="small text-secondary">اسحب الصورة والكتابة والعداد مباشرة داخل المعاينة. كل عنصر مستقل، ويمكن ترتيب النص بجانب الصورة أو فوقه/تحته.</p>
          <div class="position-control">
            <label class="form-label">عرض التوست: {{ currentStyle.widthDp }} dp</label>
            <input v-model.number="currentStyle.widthDp" class="form-range" type="range" min="220" max="360" step="5" />
            <label class="form-label">ارتفاع التوست: {{ currentStyle.heightDp }} dp</label>
            <input v-model.number="currentStyle.heightDp" class="form-range" type="range" min="60" max="180" step="2" />
          </div>
          <div class="position-control">
            <label class="form-label">حجم صورة المستخدم: {{ currentStyle.avatarSizeDp }} dp</label>
            <input v-model.number="currentStyle.avatarSizeDp" class="form-range" type="range" min="20" max="76" step="2" />
          </div>
          <div class="position-control small text-secondary">
            اسحب كل عنصر داخل الصورة لتحديد مكانه. تغييرات المواضع محفوظة كنسب مئوية وتظهر في المعاينة.
          </div>
        </section>
      </div>
      <div class="col-xl-5">
        <section class="settings-card">
          <h3 class="settings-card-title">الحركة ومدة الظهور</h3>
          <label class="settings-switch">
            <div><div class="settings-switch-title">لمعة ذهبية متحركة</div><div class="settings-switch-hint">تُرسم محلياً على الجهاز، بدون طلبات متكررة للخادم.</div></div>
            <input v-model="currentStyle.shineEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
          <label class="settings-switch">
            <div><div class="settings-switch-title">توهج إطار التوست</div><div class="settings-switch-hint">توهج خفيف حول الإطار.</div></div>
            <input v-model="currentStyle.frameGlowEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
          <div class="mt-3">
            <label class="form-label">مدة ظهور التوست: {{ currentStyle.durationSeconds }} ثانية</label>
            <input v-model.number="currentStyle.durationSeconds" class="form-range" type="range" min="1" max="12" step="1" />
            <div class="d-flex justify-content-between small text-secondary"><span>1 ث</span><span>6 ث</span><span>12 ث</span></div>
          </div>
        </section>
      </div>
    </div>

    <div v-else class="row g-3">
      <div class="col-xl-7">
        <section class="settings-card">
          <h3 class="settings-card-title">حركة الزر المنبثق</h3>
          <label class="settings-switch">
            <div><div class="settings-switch-title">تكبير وتصغير مع مؤثرات صغيرة</div><div class="settings-switch-hint">نبضة قصيرة ولمعات متناثرة عند الضغط، على جهاز المستخدم فقط.</div></div>
            <input v-model="form.luckyButton.pulseEnabled" class="form-check-input" type="checkbox" role="switch" />
          </label>
          <div class="mt-3">
            <label class="form-label">مدة ظهور الزر: {{ form.luckyButton.durationSeconds }} ثانية</label>
            <input v-model.number="form.luckyButton.durationSeconds" class="form-range" type="range" min="1" max="12" step="1" />
          </div>
        </section>
      </div>
      <div class="col-xl-5">
        <section class="settings-card">
          <h3 class="settings-card-title">تأثير الضغط</h3>
          <ul class="small text-secondary mb-0 ps-3">
            <li>يكبر الزر ثم يعود لحجمه بانسيابية.</li>
            <li>تظهر نقاط ضوء صغيرة وتتلاشى تلقائياً.</li>
            <li>لا يتم إرسال أي تحديث للخادم لكل إطار حركة؛ الإعدادات فقط تُقرأ وتُحفظ.</li>
          </ul>
        </section>
      </div>
    </div>

    <div class="d-flex flex-wrap gap-2 mt-3">
      <button class="btn btn-aurora" type="button" :disabled="saving || loading || !canWriteSettings" @click="save">
        <span v-if="saving" class="spinner-border spinner-border-sm me-1"></span>حفظ وتطبيق كل الإعدادات
      </button>
      <button class="btn btn-outline-secondary" type="button" :disabled="loading || saving" @click="load">إعادة تحميل الإعدادات</button>
    </div>
    <section class="settings-card mt-3">
      <h3 class="settings-card-title">ملاحظات الأداء</h3>
      <ul class="small text-secondary mb-0 ps-3">
        <li>الصور تُرفع مرة واحدة وتُحمّل على أجهزة المستخدمين عند الحاجة.</li>
        <li>اللمعة، التوهج، تحريك العناصر، والنبض تُنفّذ محلياً ولا ترسل أحداث حركة إلى Blitz.</li>
        <li>لا يتغيّر منطق إرسال الهدايا أو خصم العملات أو ZEGO أو الصوت أو المقاعد.</li>
      </ul>
    </section>
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
const tabs = [
  { key: 'giftSend', label: 'إرسال الهدايا والعداد X' },
  { key: 'luckyWinNormal', label: 'أرباح المحظوظ العادية' },
  { key: 'luckyWinHigh', label: 'الأرباح العالية' },
  { key: 'luckyButton', label: 'زر هدية المحظوظ' },
]
const activeTab = ref('giftSend')
const form = reactive({
  giftSend: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: true, durationSeconds: 3, avatarX: 8, avatarY: 50, textX: 25, textY: 50, bodyX: 25, bodyY: 67, countX: 88, countY: 50, widthDp: 340, heightDp: 100, avatarSizeDp: 44 },
  luckyWinNormal: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: true, durationSeconds: 4, avatarX: 8, avatarY: 50, textX: 25, textY: 42, bodyX: 25, bodyY: 62, countX: 88, countY: 50, widthDp: 340, heightDp: 100, avatarSizeDp: 44 },
  luckyWinHigh: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: true, durationSeconds: 5, avatarX: 8, avatarY: 50, textX: 25, textY: 42, bodyX: 25, bodyY: 62, countX: 88, countY: 50, widthDp: 340, heightDp: 100, avatarSizeDp: 44, bodyOffsetY: 17 },
  luckyButton: { iconUrl: '', durationSeconds: 5, pulseEnabled: true },
  version: 1,
})
const currentStyle = computed(() => activeTab.value === 'luckyButton' ? form.luckyWinNormal : form[activeTab.value])
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const error = ref('')
const success = ref('')
const resolveStyleUrl = (style) => resolveAsset(style?.backgroundUrl)
const canvasStyle = computed(() => ({
  width: Math.max(220, Math.min(360, Number(currentStyle.value.widthDp) || 340)) + 'px',
  height: Math.max(60, Math.min(180, Number(currentStyle.value.heightDp) || 100)) + 'px',
  boxShadow: currentStyle.value.frameGlowEnabled ? '0 0 0 1px rgba(255,211,111,.9),0 0 18px rgba(255,191,64,.45)' : '0 0 0 1px rgba(255,211,111,.35)',
}))
const avatarPreviewStyle = computed(() => ({ left: currentStyle.value.avatarX + '%', top: currentStyle.value.avatarY + '%', width: currentStyle.value.avatarSizeDp + 'px', height: currentStyle.value.avatarSizeDp + 'px' }))
const titlePreviewStyle = computed(() => ({ left: currentStyle.value.textX + '%', top: currentStyle.value.textY + '%' }))
const bodyPreviewStyle = computed(() => ({ left: currentStyle.value.bodyX + '%', top: currentStyle.value.bodyY + '%' }))
const countPreviewStyle = computed(() => ({ left: currentStyle.value.countX + '%', top: currentStyle.value.countY + '%' }))
const dragTarget = ref('')
function startDrag(target, event) {
  if (activeTab.value === 'luckyButton') return
  dragTarget.value = target
  try { event.currentTarget.setPointerCapture(event.pointerId) } catch {}
}
function moveDrag(event) {
  if (!dragTarget.value) return
  const rect = event.currentTarget.getBoundingClientRect()
  if (!rect.width || !rect.height) return
  const x = Math.max(0, Math.min(100, ((event.clientX - rect.left) / rect.width) * 100))
  const y = Math.max(0, Math.min(100, ((event.clientY - rect.top) / rect.height) * 100))
  const s = currentStyle.value
  if (dragTarget.value === 'avatar') { s.avatarX = Math.round(x); s.avatarY = Math.round(y) }
  if (dragTarget.value === 'title') { s.textX = Math.round(x); s.textY = Math.round(y) }
  if (dragTarget.value === 'body') { s.bodyX = Math.round(x); s.bodyY = Math.round(y) }
  if (dragTarget.value === 'count') { s.countX = Math.round(x); s.countY = Math.round(y) }
}
function stopDrag() { dragTarget.value = '' }

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await toastManagerApi.get()
  loading.value = false
  if (err) { error.value = err.message; return }
  const cfg = data?.data || data || {}
  const gift = cfg.giftSend || cfg
  for (const key of ['giftSend', 'luckyWinNormal', 'luckyWinHigh']) {
    const incoming = cfg[key] || {}
    Object.assign(form[key], {
      backgroundUrl: String(incoming.backgroundUrl || ''),
      shineEnabled: incoming.shineEnabled !== false,
      frameGlowEnabled: incoming.frameGlowEnabled !== false,
      durationSeconds: Math.max(1, Math.min(12, Number(incoming.durationSeconds) || (key === 'giftSend' ? 3 : key === 'luckyWinNormal' ? 4 : 5))),
      avatarX: Number.isFinite(Number(incoming.avatarX)) ? Number(incoming.avatarX) : 8,
      avatarY: Number.isFinite(Number(incoming.avatarY)) ? Number(incoming.avatarY) : 50,
      textX: Number.isFinite(Number(incoming.textX)) ? Number(incoming.textX) : 25,
      textY: Number.isFinite(Number(incoming.textY)) ? Number(incoming.textY) : 50,
      bodyX: Number.isFinite(Number(incoming.bodyX)) ? Number(incoming.bodyX) : 25,
      bodyY: Number.isFinite(Number(incoming.bodyY)) ? Number(incoming.bodyY) : 67,
      countX: Number.isFinite(Number(incoming.countX)) ? Number(incoming.countX) : 88,
      countY: Number.isFinite(Number(incoming.countY)) ? Number(incoming.countY) : 50,
      widthDp: Math.max(220, Math.min(360, Number(incoming.widthDp) || 340)),
      heightDp: Math.max(60, Math.min(180, Number(incoming.heightDp) || 100)),
      avatarSizeDp: Math.max(20, Math.min(76, Number(incoming.avatarSizeDp) || 44)),
    })
  }
  Object.assign(form.luckyButton, {
    iconUrl: String(cfg.luckyButton?.iconUrl || ''),
    durationSeconds: Math.max(1, Math.min(12, Number(cfg.luckyButton?.durationSeconds) || 5)),
    pulseEnabled: cfg.luckyButton?.pulseEnabled !== false,
  })
  form.version = Number(cfg.version) || 1
}
async function uploadAsset(event) {
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
  if (activeTab.value === 'luckyButton') form.luckyButton.iconUrl = url
  else {
    currentStyle.value.backgroundUrl = url
    // Match the canvas to the uploaded artwork's aspect ratio instead of stretching it to screen dimensions.
    const artwork = new Image()
    artwork.onload = () => {
      if (artwork.naturalWidth > 0 && artwork.naturalHeight > 0) {
        currentStyle.value.widthDp = 340
        currentStyle.value.heightDp = Math.max(60, Math.min(180, Math.round(340 * artwork.naturalHeight / artwork.naturalWidth)))
      }
    }
    artwork.src = resolveAsset(url)
  }
  toast().success('تم رفع الصورة. ضُبطت المعاينة على نسبة أبعاد التصميم؛ يمكنك تعديل الحجم وسحب العناصر ثم الحفظ.')
}
async function save() {
  if (!canWriteSettings.value) { toast().danger('لا تملك صلاحية تعديل إعدادات الداشبورد'); return }
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    giftSend: { ...form.giftSend },
    luckyWinNormal: { ...form.luckyWinNormal },
    luckyWinHigh: { ...form.luckyWinHigh },
    luckyButton: { ...form.luckyButton },
  }
  const { data, error: err } = await toastManagerApi.save(payload)
  saving.value = false
  if (err) { error.value = err.message; toast().danger(err.message); return }
  const cfg = data?.data || data || {}
  Object.assign(form.giftSend, cfg.giftSend || {})
  Object.assign(form.luckyWinNormal, cfg.luckyWinNormal || {})
  Object.assign(form.luckyWinHigh, cfg.luckyWinHigh || {})
  Object.assign(form.luckyButton, cfg.luckyButton || {})
  form.version = Number(cfg.version) || form.version
  success.value = 'تم حفظ جميع الإعدادات. ستُنفّذ الحركة محلياً على الأجهزة التي تدعم هذه النسخة.'
  toast().success(success.value)
}
onMounted(load)
</script>

<style scoped>
.toast-tabs{display:flex;flex-wrap:wrap;gap:8px}
.toast-tab{border:1px solid rgba(255,211,111,.28);border-radius:10px;padding:9px 13px;background:rgba(20,18,31,.55);color:inherit;font-size:.92rem}
.toast-tab.active{border-color:#f5ca69;background:linear-gradient(110deg,rgba(125,22,54,.8),rgba(90,43,103,.7));box-shadow:0 0 12px rgba(255,191,64,.18)}
.toast-preview-wrap{padding:18px 10px;border-radius:16px;background:linear-gradient(135deg,#191a25,#2b2232);overflow:auto}
.toast-canvas{position:relative;max-width:100%;min-width:220px;margin:0 auto;border:1px solid rgba(255,213,122,.9);border-radius:10px;background:linear-gradient(100deg,#351126,#98264a 55%,#34204e);overflow:hidden;color:#fff;touch-action:none;isolation:isolate}
.toast-canvas-image{position:absolute;inset:0;width:100%;height:100%;object-fit:fill;pointer-events:none;user-select:none}
.drag-item{touch-action:none;cursor:grab;user-select:none}
.drag-item:active{cursor:grabbing}
.preview-avatar{position:absolute;transform:translate(-50%,-50%);display:grid;place-items:center;border:2px solid #f9d77e;border-radius:50%;background:rgba(35,15,28,.82);font-size:22px;z-index:2}
.preview-title,.preview-body{position:absolute;transform:translate(0,-50%);max-width:68%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;text-shadow:0 1px 4px #160711;z-index:2;color:#fff;font-size:12px;font-weight:800}
.preview-count{position:absolute;transform:translate(-50%,-50%);padding:5px 7px;border:1px solid #ffe3a0;border-radius:8px;background:linear-gradient(#8f183b,#3a102d);font-weight:800;color:#ffe6a4;z-index:2;font-size:12px}
.preview-shine{position:absolute;top:-20%;bottom:-20%;left:-30%;width:34px;transform:skewX(-18deg);background:linear-gradient(90deg,transparent,rgba(255,255,255,.68),transparent);animation:toast-shine 2.4s linear infinite;pointer-events:none}
.button-preview-wrap{display:flex;min-height:140px;align-items:center;justify-content:center;gap:18px;flex-wrap:wrap;color:#b9b4c6}
.button-preview{width:82px;height:82px;border-radius:50%;display:grid;place-items:center;border:3px solid #f8d77d;background:radial-gradient(circle at 30% 25%,#ffcf79,#c62c56 48%,#3c0d37);box-shadow:0 0 20px rgba(255,190,65,.45);font-size:34px;overflow:hidden}
.button-preview img{width:100%;height:100%;object-fit:contain}
.button-preview.pulse{animation:button-pulse 1.2s ease-in-out infinite}
.position-control{padding:12px 0;border-bottom:1px solid rgba(140,140,160,.18)}
.position-control:last-child{border-bottom:0}
@keyframes toast-shine{to{left:120%}}
@keyframes button-pulse{0%,100%{transform:scale(1)}50%{transform:scale(1.07)}}
</style>
