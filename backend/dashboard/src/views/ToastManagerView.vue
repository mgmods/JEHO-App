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
        <div v-if="activeTab !== 'luckyButton'" class="toast-preview" :style="previewStyle"
             @pointermove="movePreviewElement" @pointerup="endPreviewDrag" @pointercancel="endPreviewDrag">
          <div class="preview-avatar draggable" :style="avatarPreviewStyle" data-preview-element="avatar"
               @pointerdown="startPreviewDrag($event, 'avatar')">{{ activeTab === 'giftSend' ? '🎁' : '🧑' }}</div>
          <strong class="preview-name draggable" :style="namePreviewStyle" data-preview-element="name"
                  @pointerdown="startPreviewDrag($event, 'name')">{{ activeTab === 'giftSend' ? 'اسم المرسل' : 'الفائز المحظوظ' }}</strong>
          <span class="preview-reward draggable" :style="rewardPreviewStyle" data-preview-element="reward"
                @pointerdown="startPreviewDrag($event, 'reward')">{{ activeTab === 'giftSend' ? 'أرسل هدية' : (activeTab === 'luckyWinHigh' ? 'ربح عالي · +12,500 كوين' : 'مبروك الربح · +250 كوين') }}</span>
          <span class="preview-count draggable" :style="multiplierPreviewStyle" data-preview-element="multiplier"
                @pointerdown="startPreviewDrag($event, 'multiplier')">{{ activeTab === 'giftSend' ? 'X77' : (activeTab === 'luckyWinHigh' ? '×25' : '×3') }}</span>
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
          <h3 class="settings-card-title">تحريك عناصر التوست</h3>
          <p class="small text-secondary">اسحب الصورة أو الاسم أو X أو المردود مباشرة داخل المعاينة، ثم اضغط «حفظ وتطبيق». ويمكنك ضبط كل عنصر بدقة من القياسات أدناه. المواضع محفوظة كنسب مئوية لتناسب أحجام الشاشات المختلفة.</p>
          <div class="position-control">
            <strong class="d-block mb-2">صورة الفائز / الهدية</strong>
            <label class="form-label">أفقيًا: {{ currentStyle.avatarX }}%</label>
            <input v-model.number="currentStyle.avatarX" class="form-range" type="range" min="0" max="100" step="1" />
            <label class="form-label">عموديًا: {{ currentStyle.avatarY }}%</label>
            <input v-model.number="currentStyle.avatarY" class="form-range" type="range" min="0" max="100" step="1" />
          </div>
          <div class="position-control">
            <strong class="d-block mb-2">اسم المستخدم</strong>
            <label class="form-label">أفقيًا: {{ currentStyle.nameX }}%</label>
            <input v-model.number="currentStyle.nameX" class="form-range" type="range" min="0" max="100" step="1" />
            <label class="form-label">عموديًا: {{ currentStyle.nameY }}%</label>
            <input v-model.number="currentStyle.nameY" class="form-range" type="range" min="0" max="100" step="1" />
          </div>
          <div class="position-control">
            <strong class="d-block mb-2">المردود / قيمة الربح</strong>
            <label class="form-label">أفقيًا: {{ currentStyle.rewardX }}%</label>
            <input v-model.number="currentStyle.rewardX" class="form-range" type="range" min="0" max="100" step="1" />
            <label class="form-label">عموديًا: {{ currentStyle.rewardY }}%</label>
            <input v-model.number="currentStyle.rewardY" class="form-range" type="range" min="0" max="100" step="1" />
          </div>
          <div class="position-control">
            <strong class="d-block mb-2">عداد X / المضاعف</strong>
            <label class="form-label">أفقيًا: {{ currentStyle.multiplierX }}%</label>
            <input v-model.number="currentStyle.multiplierX" class="form-range" type="range" min="0" max="100" step="1" />
            <label class="form-label">عموديًا: {{ currentStyle.multiplierY }}%</label>
            <input v-model.number="currentStyle.multiplierY" class="form-range" type="range" min="0" max="100" step="1" />
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
  giftSend: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: true, durationSeconds: 3, avatarX: 8, avatarY: 50, nameX: 25, nameY: 35, rewardX: 25, rewardY: 65, multiplierX: 88, multiplierY: 50, textX: 25, textY: 50 },
  luckyWinNormal: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: true, durationSeconds: 4, avatarX: 8, avatarY: 50, nameX: 25, nameY: 35, rewardX: 25, rewardY: 65, multiplierX: 88, multiplierY: 50, textX: 25, textY: 50 },
  luckyWinHigh: { backgroundUrl: '', shineEnabled: true, frameGlowEnabled: true, durationSeconds: 5, avatarX: 8, avatarY: 50, nameX: 25, nameY: 35, rewardX: 25, rewardY: 65, multiplierX: 88, multiplierY: 50, textX: 25, textY: 50 },
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
const previewStyle = computed(() => {
  const url = resolveStyleUrl(currentStyle.value)
  return {
    backgroundImage: url ? 'url("' + url.replace(/"/g, '%22') + '")' : 'linear-gradient(100deg,#351126,#98264a 55%,#34204e)',
    boxShadow: currentStyle.value.frameGlowEnabled ? '0 0 0 1px rgba(255,211,111,.9),0 0 18px rgba(255,191,64,.45)' : '0 0 0 1px rgba(255,211,111,.35)',
  }
})
const avatarPreviewStyle = computed(() => ({ left: currentStyle.value.avatarX + '%', top: currentStyle.value.avatarY + '%' }))
const namePreviewStyle = computed(() => ({ left: currentStyle.value.nameX + '%', top: currentStyle.value.nameY + '%' }))
const rewardPreviewStyle = computed(() => ({ left: currentStyle.value.rewardX + '%', top: currentStyle.value.rewardY + '%' }))
const multiplierPreviewStyle = computed(() => ({ left: currentStyle.value.multiplierX + '%', top: currentStyle.value.multiplierY + '%' }))
const dragElement = ref('')
function startPreviewDrag(event, element) {
  if (activeTab.value === 'luckyButton') return
  event.preventDefault()
  event.stopPropagation()
  dragElement.value = element
  try { event.currentTarget.setPointerCapture(event.pointerId) } catch {}
  movePreviewElement(event)
}
function movePreviewElement(event) {
  if (!dragElement.value) return
  const rect = event.currentTarget.getBoundingClientRect()
  if (!rect.width || !rect.height) return
  const x = Math.max(0, Math.min(100, Math.round((event.clientX - rect.left) / rect.width * 100)))
  const y = Math.max(0, Math.min(100, Math.round((event.clientY - rect.top) / rect.height * 100)))
  const xKey = dragElement.value + 'X'
  const yKey = dragElement.value + 'Y'
  currentStyle.value[xKey] = x
  currentStyle.value[yKey] = y
}
function endPreviewDrag() { dragElement.value = '' }

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
      nameX: Number.isFinite(Number(incoming.nameX)) ? Number(incoming.nameX) : Number.isFinite(Number(incoming.textX)) ? Number(incoming.textX) : 25,
      nameY: Number.isFinite(Number(incoming.nameY)) ? Number(incoming.nameY) : 35,
      rewardX: Number.isFinite(Number(incoming.rewardX)) ? Number(incoming.rewardX) : Number.isFinite(Number(incoming.textX)) ? Number(incoming.textX) : 25,
      rewardY: Number.isFinite(Number(incoming.rewardY)) ? Number(incoming.rewardY) : 65,
      multiplierX: Number.isFinite(Number(incoming.multiplierX)) ? Number(incoming.multiplierX) : 88,
      multiplierY: Number.isFinite(Number(incoming.multiplierY)) ? Number(incoming.multiplierY) : 50,
      textX: Number.isFinite(Number(incoming.textX)) ? Number(incoming.textX) : 25,
      textY: Number.isFinite(Number(incoming.textY)) ? Number(incoming.textY) : 50,
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
  else currentStyle.value.backgroundUrl = url
  toast().success('تم رفع الصورة. اضغط حفظ وتطبيق لاعتمادها.')
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
.toast-preview-wrap{padding:24px 10px;border-radius:16px;background:linear-gradient(135deg,#191a25,#2b2232);overflow:hidden}
.toast-preview{position:relative;height:124px;border:1px solid rgba(255,213,122,.9);border-radius:13px;background-size:cover;background-position:center;overflow:hidden;color:#fff;touch-action:none;user-select:none}
.draggable{position:absolute;z-index:2;cursor:grab;touch-action:none;user-select:none;outline:1px dashed rgba(255,225,150,.65);outline-offset:3px}
.draggable:active{cursor:grabbing;outline-color:#fff}
.preview-avatar{transform:translate(-50%,-50%);display:grid;place-items:center;width:40px;height:40px;border:2px solid #f9d77e;border-radius:50%;background:rgba(35,15,28,.82);font-size:22px}
.preview-name,.preview-reward{transform:translateY(-50%);max-width:58%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;text-shadow:0 1px 4px #160711;font-size:12px}
.preview-name{font-size:13px;font-weight:800}
.preview-count{transform:translate(-50%,-50%);padding:5px 7px;border:1px solid #ffe3a0;border-radius:10px;background:linear-gradient(#8f183b,#3a102d);font-weight:800;color:#ffe6a4;white-space:nowrap}
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
