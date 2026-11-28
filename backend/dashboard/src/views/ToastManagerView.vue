<template>
  <div class="toast-manager-page">
    <PageHeader title="إدارة التوستات والمؤثرات" subtitle="ارفع التصاميم من الداشبورد، والتطبيق يشغّل الحركة محلياً على أجهزة المستخدمين." />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <section class="settings-card mb-3">
      <div class="toast-tabs" role="tablist" aria-label="أنواع التوستات">
        <button v-for="tab in tabs" :key="tab.key" type="button" class="toast-tab" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">{{ tab.label }}</button>
      </div>
      <label class="form-check form-switch mt-3 d-inline-flex align-items-center gap-2">
        <input class="form-check-input" type="checkbox" :checked="activeTab === 'luckyButton' ? form.luckyButton.enabled : currentStyle.enabled !== false" :disabled="!canWriteSettings" @change="activeTab === 'luckyButton' ? (form.luckyButton.enabled = $event.target.checked) : (currentStyle.enabled = $event.target.checked)" />
        <span class="form-check-label fw-semibold">تشغيل هذا النوع من التوست بشكل مستقل</span>
      </label>
      <section class="position-control mt-3" aria-label="حدود إعلان الربح العالي">
        <h3 class="settings-card-title">متى نعتبر الربح كبيراً ونعلنه بشكل عام؟</h3>
        <p class="small text-secondary">اختر الحد بنفسك. إذا وصل الربح إلى عدد X المحدد أو إلى عدد العملات المحدد، يظهر التوست العلوي العام بالإضافة إلى التوست الوسطي على جهاز الفائز فقط. ضع 0 لتعطيل أحد الشرطين؛ يجب إبقاء شرط واحد على الأقل فعالاً.</p>
        <div class="row g-3">
          <div class="col-md-6">
            <label class="form-label">أقل مضاعف X للإعلان العام</label>
            <input v-model.number="form.highWinMinMultiplier" class="form-control" type="number" min="0" max="1000000" step="1" :disabled="!canWriteSettings" />
          </div>
          <div class="col-md-6">
            <label class="form-label">أقل ربح بالعملات للإعلان العام</label>
            <input v-model.number="form.highWinMinCoins" class="form-control" type="number" min="0" max="1000000000" step="1" :disabled="!canWriteSettings" />
          </div>
        </div>
        <div class="small text-secondary mt-2">الافتراضي الحالي: ×10 أو 1,000 عملة. التغيير يُطبّق على السيرفر والتطبيق معاً بعد الحفظ.</div>
      </section>
      <div v-if="activeTab !== 'luckyButton'" class="room-preview-shell mt-3 mb-3">
        <div class="room-preview-heading">
          <div><strong>معاينة الروم المباشرة</strong><div class="small text-secondary">اسحب التوست كاملاً إلى مكانه على شاشة الروم، أو اسحب الصورة والاسم والنص والعداد كلٌّ على حدة.</div></div>
          <span class="room-preview-live"><i></i> معاينة</span>
        </div>
        <div class="room-preview-stage-wrap">
          <div ref="roomPreviewRef" class="room-preview-screen"
               @pointermove="moveDrag" @pointerup="stopDrag" @pointercancel="stopDrag" @lostpointercapture="stopDrag">
            <div class="room-preview-cover"></div>
            <div class="room-preview-top">
              <div class="room-preview-roomline"><span>JEHO ROOM</span><span>● 128</span><span>رقم الغرفة 1024</span></div>
              <div class="room-preview-roomname">غرفة السهر والأصدقاء</div>
              <div class="room-preview-host"><span class="room-preview-host-avatar">👑</span><span>المضيف · JEHO</span><span class="room-preview-tag">VIP</span></div>
            </div>
            <div class="room-preview-seats">
              <div v-for="(seat, index) in ['👤','🧑🏻','👩🏻','🧔🏻','👩🏼','🧑🏽','👨🏻','👩🏽']" :key="index" class="room-preview-seat">
                <span>{{ seat }}</span><small>{{ ['المضيف','عضو 2','عضو 3','عضو 4','عضو 5','عضو 6','عضو 7','عضو 8'][index] }}</small>
              </div>
            </div>
            <div class="room-preview-chat">
              <div class="room-preview-chatline"><b>JEHO</b> أهلاً وسهلاً بالجميع ❤️</div>
              <div class="room-preview-chatline"><b>مستخدم</b> نورتوا الروم 🌹</div>
              <div class="room-preview-chatline"><b>عضو مميز</b> مساء الخير ✨</div>
            </div>
            <div class="room-preview-toolbar"><span>الجميع</span><span>دردشة</span><span>هدية</span></div>
            <div class="room-preview-input">اكتب رسالة... <span>🎁　🎙</span></div>
            <div ref="toastCanvasRef" class="toast-canvas" :style="canvasStyle" @pointerdown.stop="startDrag('toast', $event)">
              <img v-if="currentStyle.backgroundUrl" class="toast-canvas-image" :src="resolveAsset(currentStyle.backgroundUrl)" alt="تصميم التوست" draggable="false" />
              <div v-if="activeTab !== 'luckyWinHighRoom'" class="preview-avatar drag-item" :style="avatarPreviewStyle" @pointerdown.stop="startDrag('avatar', $event)">🧑</div>
              <strong class="preview-title drag-item" :style="titlePreviewStyle" @pointerdown.stop="startDrag('title', $event)">{{ activeTab === 'giftSend' ? 'اسم المرسل' : activeTab === 'luckyReturn' ? 'مردود الهدية' : 'الفائز المحظوظ' }}</strong>
              <span class="preview-body drag-item" :style="bodyPreviewStyle" @pointerdown.stop="startDrag('body', $event)">{{ activeTab === 'giftSend' ? 'أرسل هدية' : activeTab === 'luckyReturn' ? 'مردود جزئي · +250 كوين' : (activeTab.startsWith('luckyWinHigh') ? 'ربح عالي · +12,500 كوين' : 'مبروك الربح · +250 كوين') }}</span>
              <span class="preview-count drag-item" :style="countPreviewStyle" @pointerdown.stop="startDrag('count', $event)">{{ activeTab === 'giftSend' ? 'X77' : (activeTab.startsWith('luckyWinHigh') ? '×25' : '×3') }}</span>
              <div v-if="currentStyle.shineEnabled" class="preview-shine"></div>
            </div>
          </div>
        </div>
        <div class="small text-secondary mt-2">الإحداثيات الأفقية والعمودية محسوبة من مساحة شاشة الروم نفسها، وليس من إطار المعاينة الخارجي.</div>
      </div>
      <div v-else class="toast-preview-wrap mt-3 mb-3">
        <div class="button-preview-wrap">
          <div class="button-preview" :class="{ pulse: form.luckyButton.pulseEnabled }">
            <img v-if="form.luckyButton.iconUrl" :src="resolveAsset(form.luckyButton.iconUrl)" alt="أيقونة زر المحظوظ" />
            <span v-else>💎</span>
          </div>
          <span>معاينة الزر المنبثق بعد اختيار هدية المحظوظ</span>
        </div>
      </div>
      <div v-if="activeTab !== 'luckyButton'" class="mb-3">
        <label class="form-label">{{ activeTab === 'giftSend' ? 'خلفية توست إرسال الهدايا والعداد X' : activeTab === 'luckyReturn' ? 'خلفية توست الهدايا المردودة' : activeTab === 'luckyWinNormal' ? 'خلفية توست الربح العادي' : activeTab === 'luckyWinHighRoom' ? 'خلفية توست الربح العالي داخل الروم (مستقلة)' : 'خلفية توست الربح العالي العام' }}</label>
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
          <p class="small text-secondary">اسحب الصورة والاسم والمردود وعداد X مباشرة داخل المعاينة في كل نوع توست، بما فيها أرباح المحظوظ العادية والعالية. كل عنصر مستقل؛ احفظ التعديلات لتبقى مواضعها محفوظة وتُطبّق على التطبيق.</p>
          <div class="position-control">
            <label class="form-label">عرض التوست: {{ currentStyle.widthDp }} dp</label>
            <input v-model.number="currentStyle.widthDp" class="form-range" type="range" min="220" max="360" step="5" />
            <label class="form-label">ارتفاع التوست: {{ currentStyle.heightDp }} dp</label>
            <input v-model.number="currentStyle.heightDp" class="form-range" type="range" min="60" max="180" step="2" />
            <p class="small text-secondary mb-0">يُحفظ الحجم لكل نوع توست ويُطبّق في المعاينة والتطبيق.</p>
          </div>
          <div class="position-control">
            <label class="form-label">مكان التوست أفقيًا على الشاشة: {{ currentStyle.placementX }}%</label>
            <input v-model.number="currentStyle.placementX" class="form-range" type="range" min="0" max="100" step="1" />
            <label class="form-label">مكان التوست عموديًا على الشاشة: {{ currentStyle.placementY }}%</label>
            <input v-model.number="currentStyle.placementY" class="form-range" type="range" min="0" max="100" step="1" />
            <p class="small text-secondary mb-0">اسحب التوست كاملاً داخل معاينة الشاشة لتحديد مكانه. يُحفظ الموضع مستقلاً لكل نوع ويُطبّق على أجهزة المستخدمين.</p>
          </div>
          <div class="position-control">
            <label class="form-label">حجم صورة المستخدم: {{ currentStyle.avatarSizeDp }} dp</label>
            <input v-model.number="currentStyle.avatarSizeDp" class="form-range" type="range" min="20" max="76" step="2" />
          </div>
          <div class="position-control">
            <label class="form-label">حجم اسم المرسل/الفائز: {{ currentStyle.titleSizeSp }} sp</label>
            <input v-model.number="currentStyle.titleSizeSp" class="form-range" type="range" min="8" max="30" step="1" />
            <label class="form-label">حجم كتابة «أرسل هدية»/المردود: {{ currentStyle.bodySizeSp }} sp</label>
            <input v-model.number="currentStyle.bodySizeSp" class="form-range" type="range" min="8" max="30" step="1" />
            <label class="form-label">حجم عداد X: {{ currentStyle.countSizeSp }} sp</label>
            <input v-model.number="currentStyle.countSizeSp" class="form-range" type="range" min="8" max="30" step="1" />
          </div>
          <div class="position-control">
            <label class="form-label">الموضع الأفقي لاسم المرسل/الفائز: {{ currentStyle.textX }}%</label>
            <input v-model.number="currentStyle.textX" class="form-range" type="range" min="0" max="100" step="1" />
            <label class="form-label">الموضع الأفقي لكتابة «أرسل هدية»/المردود: {{ currentStyle.bodyX }}%</label>
            <input v-model.number="currentStyle.bodyX" class="form-range" type="range" min="0" max="100" step="1" />
            <label class="form-label">الموضع الأفقي لعداد X: {{ currentStyle.countX }}%</label>
            <input v-model.number="currentStyle.countX" class="form-range" type="range" min="0" max="100" step="1" />
          </div>
          <div class="position-control">
            <label class="form-label">ارتفاع كتابة «أرسل هدية»/المردود: {{ currentStyle.bodyY }}%</label>
            <input v-model.number="currentStyle.bodyY" class="form-range" type="range" min="0" max="100" step="1" />
            <label class="form-label">ارتفاع اسم المرسل/الفائز: {{ currentStyle.textY }}%</label>
            <input v-model.number="currentStyle.textY" class="form-range" type="range" min="0" max="100" step="1" />
            <label class="form-label">ارتفاع عداد X: {{ currentStyle.countY }}%</label>
            <input v-model.number="currentStyle.countY" class="form-range" type="range" min="0" max="100" step="1" />
          </div>
          <div class="position-control small text-secondary">
            اسحب الصورة والاسم والمردود وعداد X إلى أي مكان داخل التوست. موضع كل عنصر محفوظ بشكل مستقل كنسبة مئوية، ويُستخدم نفسه في معاينة الداشبورد والتوست الفعلي بالتطبيق.
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
          <label class="settings-switch mt-2">
            <div><div class="settings-switch-title">توهج إطار التوست</div><div class="settings-switch-hint">يتحكم بتوهج التوست عند إرسال الهدية أو ظهور ربح المحظوظ.</div></div>
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
  { key: 'giftSend', label: 'إرسال الهدايا' },
  { key: 'luckyWinNormal', label: 'أرباح المحظوظ العادية' },
  { key: 'luckyReturn', label: 'الهدايا المردودة' },
  { key: 'luckyWinHigh', label: 'الأرباح العالية — التوست العام' },
  { key: 'luckyWinHighRoom', label: 'الأرباح العالية — داخل الروم' },
  { key: 'luckyButton', label: 'زر هدية المحظوظ' },
]
const activeTab = ref('luckyWinNormal')
const form = reactive({
  giftSend: { enabled: true, backgroundUrl: '', shineEnabled: true, frameGlowEnabled: false, durationSeconds: 3, avatarX: 8, avatarY: 50, textX: 25, textY: 50, bodyX: 25, bodyY: 67, countX: 88, countY: 50, widthDp: 340, heightDp: 100, avatarSizeDp: 44, titleSizeSp: 13, bodySizeSp: 12, countSizeSp: 12, placementX: 50, placementY: 16 },
  luckyWinNormal: { enabled: true, backgroundUrl: '', shineEnabled: true, frameGlowEnabled: false, durationSeconds: 4, avatarX: 8, avatarY: 50, textX: 25, textY: 42, bodyX: 25, bodyY: 62, countX: 88, countY: 50, widthDp: 340, heightDp: 100, avatarSizeDp: 44, titleSizeSp: 13, bodySizeSp: 12, countSizeSp: 12, placementX: 50, placementY: 16 },
  luckyReturn: { enabled: true, backgroundUrl: '', shineEnabled: true, frameGlowEnabled: false, durationSeconds: 3, avatarX: 8, avatarY: 50, textX: 25, textY: 42, bodyX: 25, bodyY: 62, countX: 88, countY: 50, widthDp: 340, heightDp: 100, avatarSizeDp: 44, titleSizeSp: 13, bodySizeSp: 12, countSizeSp: 12, placementX: 50, placementY: 32 },
  luckyWinHigh: { enabled: true, backgroundUrl: '', shineEnabled: true, frameGlowEnabled: false, durationSeconds: 5, avatarX: 8, avatarY: 50, textX: 25, textY: 42, bodyX: 25, bodyY: 62, countX: 88, countY: 50, widthDp: 340, heightDp: 100, avatarSizeDp: 44, titleSizeSp: 13, bodySizeSp: 12, countSizeSp: 12, placementX: 50, placementY: 16 },
  luckyWinHighRoom: { enabled: true, backgroundUrl: '', shineEnabled: true, frameGlowEnabled: false, durationSeconds: 5, avatarX: 8, avatarY: 50, textX: 25, textY: 42, bodyX: 25, bodyY: 62, countX: 88, countY: 50, widthDp: 340, heightDp: 100, avatarSizeDp: 44, titleSizeSp: 13, bodySizeSp: 12, countSizeSp: 12, placementX: 50, placementY: 50 },
  luckyButton: { enabled: true, iconUrl: '', durationSeconds: 5, pulseEnabled: true },
  highWinMinMultiplier: 10,
  highWinMinCoins: 1000,
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
  boxShadow: 'none',
  position: 'absolute',
  left: (Number(currentStyle.value.placementX ?? 50)) + '%',
  top: (Number(currentStyle.value.placementY ?? 12)) + '%',
  transform: 'translate(-50%, -50%)',
}))
const avatarPreviewStyle = computed(() => ({ left: currentStyle.value.avatarX + '%', top: currentStyle.value.avatarY + '%', width: currentStyle.value.avatarSizeDp + 'px', height: currentStyle.value.avatarSizeDp + 'px' }))
const titlePreviewStyle = computed(() => ({ left: (activeTab.value === 'luckyWinHighRoom' ? 8 : currentStyle.value.textX) + '%', top: currentStyle.value.textY + '%', fontSize: (Number(currentStyle.value.titleSizeSp) || 13) + 'px' }))
const bodyPreviewStyle = computed(() => ({ left: (activeTab.value === 'luckyWinHighRoom' ? 8 : currentStyle.value.bodyX) + '%', top: currentStyle.value.bodyY + '%', fontSize: (Number(currentStyle.value.bodySizeSp) || 12) + 'px' }))
const countPreviewStyle = computed(() => ({ left: currentStyle.value.countX + '%', top: currentStyle.value.countY + '%', fontSize: (Number(currentStyle.value.countSizeSp) || 12) + 'px' }))
const roomPreviewRef = ref(null)
const toastCanvasRef = ref(null)
const dragTarget = ref('')
function startDrag(target, event) {
  if (activeTab.value === 'luckyButton') return
  dragTarget.value = target
  try { roomPreviewRef.value?.setPointerCapture(event.pointerId) } catch {}
}
function moveDrag(event) {
  if (!dragTarget.value) return
  const rect = (dragTarget.value === 'toast' ? roomPreviewRef.value : toastCanvasRef.value)?.getBoundingClientRect()
  if (!rect || !rect.width || !rect.height) return
  const x = Math.max(0, Math.min(100, ((event.clientX - rect.left) / rect.width) * 100))
  const y = Math.max(0, Math.min(100, ((event.clientY - rect.top) / rect.height) * 100))
  const s = currentStyle.value
  if (dragTarget.value === 'toast') { s.placementX = Math.round(x); s.placementY = Math.round(y) }
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
  for (const key of ['giftSend', 'luckyWinNormal', 'luckyReturn', 'luckyWinHigh', 'luckyWinHighRoom']) {
    const incoming = cfg[key] || {}
    Object.assign(form[key], {
      enabled: incoming.enabled !== false,
      backgroundUrl: String(incoming.backgroundUrl || ''),
      shineEnabled: incoming.shineEnabled !== false,
      frameGlowEnabled: incoming.frameGlowEnabled !== false,
      durationSeconds: Math.max(1, Math.min(12, Number(incoming.durationSeconds) || (key === 'giftSend' ? 3 : key === 'luckyReturn' ? 3 : key === 'luckyWinNormal' ? 4 : 5))),
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
      titleSizeSp: Math.max(8, Math.min(30, Number(incoming.titleSizeSp) || 13)),
      bodySizeSp: Math.max(8, Math.min(30, Number(incoming.bodySizeSp) || 12)),
      countSizeSp: Math.max(8, Math.min(30, Number(incoming.countSizeSp) || 12)),
      placementX: Math.max(0, Math.min(100, Number.isFinite(Number(incoming.placementX)) ? Number(incoming.placementX) : 50)),
      placementY: Math.max(0, Math.min(100, Number.isFinite(Number(incoming.placementY)) ? Number(incoming.placementY) : (key === 'luckyReturn' ? 32 : key === 'luckyWinHighRoom' ? 50 : 16))),
    })
  }
  form.highWinMinMultiplier = Math.max(0, Math.floor(Number.isFinite(Number(cfg.highWinMinMultiplier)) ? Number(cfg.highWinMinMultiplier) : 10))
  form.highWinMinCoins = Math.max(0, Math.floor(Number.isFinite(Number(cfg.highWinMinCoins)) ? Number(cfg.highWinMinCoins) : 1000))
  Object.assign(form.luckyButton, {
    enabled: cfg.luckyButton?.enabled !== false,
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
  }
  toast().success('تم رفع الخلفية. يمكنك ضبط الحجم وتحريك العناصر ثم الحفظ.')
}
async function save() {
  if (!canWriteSettings.value) { toast().danger('لا تملك صلاحية تعديل إعدادات الداشبورد'); return }
  saving.value = true
  error.value = ''
  success.value = ''
  const highWinMinMultiplier = Math.max(0, Math.floor(Number(form.highWinMinMultiplier) || 0))
  const highWinMinCoins = Math.max(0, Math.floor(Number(form.highWinMinCoins) || 0))
  if (highWinMinMultiplier <= 0 && highWinMinCoins <= 0) {
    saving.value = false
    error.value = 'حدد حد X أو حد الربح بالعملات على الأقل حتى يعمل إعلان الربح العالي.'
    toast().danger(error.value)
    return
  }
  const payload = {
    // Bump the public asset version so clients bypass image caches after every save.
    version: Math.max(1, Number(form.version) || 1) + 1,
    highWinMinMultiplier,
    highWinMinCoins,
    giftSend: { ...form.giftSend },
    luckyWinNormal: { ...form.luckyWinNormal },
    luckyReturn: { ...form.luckyReturn },
    luckyWinHigh: { ...form.luckyWinHigh },
    luckyWinHighRoom: { ...form.luckyWinHighRoom },
    luckyButton: { ...form.luckyButton },
  }
  const { error: saveError } = await toastManagerApi.save(payload)
  if (saveError) {
    saving.value = false
    error.value = saveError.message
    toast().danger(saveError.message)
    return
  }

  // Verify persistence from a fresh GET instead of trusting the PATCH response.
  // This catches a backend that accepted a save but serves stale/default settings on reload.
  const { data: readBack, error: readError } = await toastManagerApi.get()
  saving.value = false
  if (readError) {
    error.value = 'تم إرسال الحفظ، لكن تعذّر التحقق من الإعدادات بعد الحفظ: ' + readError.message
    toast().danger(error.value)
    return
  }
  const cfg = readBack?.data || readBack || {}
  if (!cfg.giftSend || !cfg.luckyWinNormal || !cfg.luckyReturn || !cfg.luckyWinHigh || !cfg.luckyWinHighRoom || !cfg.luckyButton) {
    error.value = 'الخادم لم يُرجع إعدادات التوست المحفوظة كاملة؛ لم نعرض نجاحاً غير مؤكّد.'
    toast().danger(error.value)
    return
  }
  if (Number(cfg.highWinMinMultiplier) !== highWinMinMultiplier || Number(cfg.highWinMinCoins) !== highWinMinCoins) {
    error.value = 'لم يتم تأكيد حفظ حدود الربح العالي من قاعدة البيانات.'
    toast().danger(error.value)
    return
  }
  const fieldsToVerify = [
    'enabled', 'avatarX', 'avatarY', 'textX', 'textY', 'bodyX', 'bodyY', 'countX', 'countY',
    'widthDp', 'heightDp', 'avatarSizeDp', 'titleSizeSp', 'bodySizeSp', 'countSizeSp',
    'durationSeconds', 'shineEnabled', 'frameGlowEnabled', 'backgroundUrl', 'placementX', 'placementY',
  ]
  for (const key of ['giftSend', 'luckyWinNormal', 'luckyReturn', 'luckyWinHigh', 'luckyWinHighRoom']) {
    for (const field of fieldsToVerify) {
      const expected = payload[key]?.[field]
      const actual = cfg[key]?.[field]
      if (expected !== undefined && String(actual ?? '') !== String(expected ?? '')) {
        error.value = `لم يتم تأكيد حفظ ${key}.${field} من قاعدة البيانات. أعد المحاولة بعد التأكد من اتصال الخادم.`
        toast().danger(error.value)
        return
      }
    }
  }

  Object.assign(form.giftSend, cfg.giftSend)
  Object.assign(form.luckyWinNormal, cfg.luckyWinNormal)
  Object.assign(form.luckyReturn, cfg.luckyReturn)
  Object.assign(form.luckyWinHigh, cfg.luckyWinHigh)
  Object.assign(form.luckyWinHighRoom, cfg.luckyWinHighRoom)
  Object.assign(form.luckyButton, cfg.luckyButton)
  form.highWinMinMultiplier = Number(cfg.highWinMinMultiplier)
  form.highWinMinCoins = Number(cfg.highWinMinCoins)
  form.version = Number(cfg.version) || form.version
  success.value = 'تم الحفظ والتحقق من القيم بقراءة جديدة من الخادم. الإعدادات التي لم تُحفظ ستظهر كخطأ ولن تُعرض رسالة نجاح.'
  toast().success(success.value)
}
onMounted(load)
</script>

<style scoped>
.toast-tabs{display:flex;flex-wrap:wrap;gap:8px}
.toast-tab{border:1px solid rgba(255,211,111,.28);border-radius:10px;padding:9px 13px;background:rgba(20,18,31,.55);color:inherit;font-size:.92rem}
.toast-tab.active{border-color:#f5ca69;background:linear-gradient(110deg,rgba(125,22,54,.8),rgba(90,43,103,.7));box-shadow:0 0 12px rgba(255,191,64,.18)}
.toast-preview-wrap{position:relative;width:100%;min-height:140px;padding:0;border-radius:16px;background:linear-gradient(135deg,#191a25,#2b2232);overflow:hidden;touch-action:none}
.room-preview-shell{padding:14px;border:1px solid rgba(255,211,111,.2);border-radius:16px;background:linear-gradient(145deg,rgba(18,20,35,.96),rgba(35,19,43,.96))}
.room-preview-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}
.room-preview-live{display:inline-flex;align-items:center;gap:7px;border:1px solid rgba(72,211,151,.3);border-radius:20px;padding:5px 10px;color:#8ee4bd;font-size:12px;white-space:nowrap}
.room-preview-live i{width:7px;height:7px;border-radius:50%;background:#48d397;box-shadow:0 0 8px #48d397}
.room-preview-stage-wrap{display:flex;justify-content:center;overflow:auto;padding:8px 0}
.room-preview-screen{position:relative;isolation:isolate;width:min(100%,360px);height:640px;flex:0 0 auto;overflow:hidden;border:5px solid #11131c;border-radius:28px;background:radial-gradient(ellipse at 50% 35%,rgba(107,73,125,.55),transparent 55%),linear-gradient(180deg,#30233e 0%,#171c32 48%,#101522 100%);box-shadow:0 18px 45px rgba(0,0,0,.42);touch-action:none}
.room-preview-cover{position:absolute;inset:0;background:linear-gradient(145deg,rgba(255,255,255,.035),transparent 35%),radial-gradient(ellipse at 50% 72%,rgba(198,140,64,.13),transparent 44%);pointer-events:none}
.room-preview-top{position:absolute;top:0;left:0;right:0;padding:16px 14px 12px;background:linear-gradient(180deg,rgba(8,10,20,.84),rgba(8,10,20,.18))}
.room-preview-roomline{display:flex;justify-content:space-between;gap:5px;color:#e8d8ae;font-size:9px;white-space:nowrap}
.room-preview-roomname{margin-top:13px;text-align:center;color:#fff;font-size:16px;font-weight:800;text-shadow:0 2px 8px #000}
.room-preview-host{display:flex;justify-content:center;align-items:center;gap:7px;margin-top:12px;color:#f8e7c2;font-size:11px}
.room-preview-host-avatar{display:grid;place-items:center;width:25px;height:25px;border:1px solid #d6b76c;border-radius:50%;background:#5b3b48}
.room-preview-tag{padding:2px 7px;border-radius:8px;background:linear-gradient(90deg,#c59b44,#f4df9b);color:#271c17;font-size:9px;font-weight:900}
.room-preview-seats{position:absolute;top:155px;left:10px;right:10px;display:grid;grid-template-columns:repeat(4,1fr);gap:22px 10px}
.room-preview-seat{display:flex;align-items:center;flex-direction:column;gap:5px;color:#f5e9d1;font-size:24px}
.room-preview-seat>span{display:grid;place-items:center;width:49px;height:49px;border:2px solid rgba(227,190,115,.8);border-radius:50%;background:linear-gradient(145deg,#59445d,#1e2940);box-shadow:0 0 12px rgba(230,185,101,.18)}
.room-preview-seat small{max-width:62px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#ddd4e8;font-size:8px}
.room-preview-chat{position:absolute;left:12px;right:12px;bottom:110px;display:flex;flex-direction:column;gap:6px}
.room-preview-chatline{width:max-content;max-width:100%;padding:5px 9px;border-radius:9px;background:rgba(5,8,18,.48);color:#f5f0e9;font-size:10px}
.room-preview-chatline b{color:#f2d18a;margin-inline-end:5px}
.room-preview-toolbar{position:absolute;left:12px;right:12px;bottom:57px;display:flex;justify-content:space-around;padding:9px 5px;border:1px solid rgba(226,190,120,.2);border-radius:14px;background:rgba(12,15,28,.85);color:#f4dfb4;font-size:12px;font-weight:700}
.room-preview-input{position:absolute;left:12px;right:12px;bottom:12px;padding:9px 12px;border-radius:20px;background:rgba(255,255,255,.1);color:#bdb9c7;font-size:10px}
.room-preview-input span{float:right;color:#f4d28d}
.toast-canvas{position:absolute;max-width:calc(100% - 12px);min-width:220px;margin:0;border:0;border-radius:0;background:transparent;overflow:visible;color:#fff;touch-action:none;isolation:isolate;z-index:5}
.toast-canvas-image{position:absolute;inset:0;width:100%;height:100%;object-fit:fill;pointer-events:none;user-select:none}
.drag-item{touch-action:none;cursor:grab;user-select:none}
.drag-item:active{cursor:grabbing}
.preview-avatar{position:absolute;transform:translate(-50%,-50%);display:grid;place-items:center;border:0;border-radius:0;background:transparent;font-size:22px;z-index:2}
.preview-title,.preview-body{position:absolute;transform:translate(0,-50%);max-width:68%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;text-shadow:0 1px 4px #160711;z-index:2;color:#fff;font-size:12px;font-weight:800}
.preview-count{position:absolute;transform:translate(-50%,-50%);padding:0;border:0;border-radius:0;background:transparent;font-weight:800;color:#ffe6a4;z-index:2;font-size:12px}
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
