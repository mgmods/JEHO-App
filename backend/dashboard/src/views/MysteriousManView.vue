<template>
  <div>
    <PageHeader title="الرجل الغامض" subtitle="إدارة الهوية الموحدة وتحديد مستويات VIP التي تتيح الميزة." />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <LoadingSpinner v-if="loading" />
    <template v-else>
      <section class="glass p-4 mb-3">
        <div class="d-flex align-items-start justify-content-between flex-wrap gap-3 mb-3">
          <div>
            <h3 class="h5 mb-1">تفعيل ميزة الرجل الغامض</h3>
            <p class="text-secondary mb-0">عند إيقافها لن يستطيع أي مستوى VIP استخدام الوضع. اختر مستوى واحداً أو أكثر للسماح بالميزة.</p>
          </div>
          <label class="form-check form-switch fs-5">
            <input v-model="form.enabled" class="form-check-input" type="checkbox" role="switch">
            <span class="form-check-label fs-6">{{ form.enabled ? 'الميزة مفعّلة' : 'الميزة متوقفة' }}</span>
          </label>
        </div>
        <div class="border rounded-3 p-3">
          <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-2">
            <h4 class="h6 mb-0">مستويات VIP المسموح لها</h4>
            <div class="d-flex gap-2">
              <button type="button" class="btn btn-sm btn-ghost" @click="selectAll">تحديد الكل</button>
              <button type="button" class="btn btn-sm btn-ghost" @click="form.eligibleVipLevels = []">إلغاء التحديد</button>
            </div>
          </div>
          <div class="row g-2">
            <div v-for="level in vipLevels" :key="level" class="col-6 col-md-3 col-xl-2">
              <label class="border rounded-3 p-3 d-flex align-items-center gap-2 h-100" :class="{ 'border-primary': form.eligibleVipLevels.includes(level) }">
                <input class="form-check-input mt-0" type="checkbox" :checked="form.eligibleVipLevels.includes(level)" @change="toggleLevel(level, $event.target.checked)">
                <span class="fw-semibold">VIP {{ level }}</span>
                <i v-if="form.eligibleVipLevels.includes(level)" class="bi bi-check-circle-fill text-success ms-auto"></i>
              </label>
            </div>
          </div>
          <p class="form-text mt-2 mb-0">المستويات المحددة: {{ form.eligibleVipLevels.length ? form.eligibleVipLevels.map(n => 'VIP ' + n).join('، ') : 'لم يتم تحديد أي مستوى' }}</p>
        </div>
      </section>

      <section class="glass p-4 mb-3">
        <h3 class="h5 mb-1">الهوية والدخولية الموحدة</h3>
        <p class="text-secondary mb-3">هذه الملفات موحّدة لكل من يفعّل الوضع، ولا تستبدل الصورة الأصلية المحفوظة في الحساب.</p>
        <div class="row g-3 mb-3">
          <div class="col-md-4">
            <label class="form-label">نوع الدخولية</label>
            <select v-model="form.entryMode" class="form-select">
              <option value="text">دخولية نصية</option>
              <option value="video">دخولية فيديو / حركة</option>
            </select>
          </div>
          <div v-if="form.entryMode === 'text'" class="col-md-8">
            <label class="form-label">نص الدخولية</label>
            <input v-model="form.entryText" maxlength="160" class="form-control" placeholder="دخل الرجل الغامض">
          </div>
        </div>
        <div class="row g-3">
          <div v-for="asset in assets" :key="asset.key" class="col-md-4">
            <div class="border rounded-3 p-3 h-100">
              <h4 class="h6">{{ asset.title }}</h4>
              <p class="small text-secondary">{{ asset.help }}</p>
              <div class="rounded-3 bg-body-tertiary d-flex align-items-center justify-content-center overflow-hidden mb-3" style="min-height:150px">
                <img v-if="form[asset.key] && isImageUrl(form[asset.key])" :src="previewUrl(form[asset.key])" alt="معاينة الملف" style="max-width:100%;max-height:180px;object-fit:contain">
                <video v-else-if="form[asset.key] && isVideoUrl(form[asset.key])" :src="previewUrl(form[asset.key])" controls muted loop style="max-width:100%;max-height:180px"></video>
                <a v-else-if="form[asset.key]" :href="previewUrl(form[asset.key])" target="_blank" rel="noopener">فتح الملف للمعاينة</a>
                <span v-else class="text-secondary small">لم يتم رفع ملف بعد</span>
              </div>
              <input type="file" class="form-control form-control-sm mb-2" :accept="asset.accept" @change="uploadAsset(asset, $event)">
              <button v-if="form[asset.key]" type="button" class="btn btn-link btn-sm text-danger" @click="form[asset.key] = ''">إزالة الملف</button>
            </div>
          </div>
        </div>
        <div class="alert alert-info small mt-3 mb-0">الصورة: PNG أو JPG أو WEBP. الدخولية والتأثير: MP4 أو WEBM أو MOV أو SVGA.</div>
      </section>
      <div class="d-flex justify-content-end">
        <button type="button" class="btn btn-aurora" :disabled="saving || !auth.can('settings', 'write')" @click="save">
          <span v-if="saving" class="spinner-border spinner-border-sm me-1"></span>حفظ إعدادات الرجل الغامض
        </button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { mysteriousManApi, uploadsApi, vipApi } from '@/api'
import { resolveAsset } from '@/utils/assets'
import { toast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const auth = useAuthStore()
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const vipLevels = ref([1, 2, 3, 4, 5, 6, 7])
const form = reactive({ enabled: false, eligibleVipLevels: [], avatarUrl: '', entryMode: 'video', entryText: 'دخل الرجل الغامض', entryEffectUrl: '', messageEffectUrl: '' })
const assets = [
  { key: 'avatarUrl', title: 'الصورة الشخصية الموحدة', help: 'تظهر بدلاً من الصورة الأصلية في مواضع الهوية المخفية.', accept: 'image/png,image/jpeg,image/webp' },
  { key: 'entryEffectUrl', title: 'دخولية الرجل الغامض', help: 'فيديو أو حركة موحدة عند دخول الروم.', accept: 'video/mp4,video/webm,video/quicktime,.svga' },
  { key: 'messageEffectUrl', title: 'تأثير الرسائل', help: 'يمكن رفع صورة ثابتة أو فيديو أو ملف SVGA.', accept: 'image/png,image/jpeg,image/gif,image/webp,video/mp4,video/webm,video/quicktime,.svga' },
]
function previewUrl(raw) {
  return resolveAsset(raw)
}
function isImageUrl(raw) { return /\.(png|jpe?g|gif|webp)(\?|$)/i.test(String(raw || '')) }
function isVideoUrl(raw) { return /\.(mp4|webm|mov)(\?|$)/i.test(String(raw || '')) }
function toggleLevel(level, checked) {
  const values = new Set(form.eligibleVipLevels)
  if (checked) values.add(level)
  else values.delete(level)
  form.eligibleVipLevels = [...values].sort((a, b) => a - b)
}
function selectAll() { form.eligibleVipLevels = [...vipLevels.value] }
async function load() {
  loading.value = true
  const [configResult, plansResult] = await Promise.all([mysteriousManApi.get(), vipApi.plans()])
  loading.value = false
  if (configResult.error) { error.value = configResult.error.message; toast().danger(error.value); return }
  const cfg = configResult.data?.data || configResult.data || {}
  Object.assign(form, {
    enabled: cfg.enabled === true,
    eligibleVipLevels: Array.isArray(cfg.eligibleVipLevels) ? cfg.eligibleVipLevels.map(Number).filter(n => Number.isInteger(n) && n >= 1 && n <= 7) : [],
    avatarUrl: String(cfg.avatarUrl || ''),
    entryMode: cfg.entryMode === 'text' ? 'text' : 'video',
    entryText: String(cfg.entryText || 'دخل الرجل الغامض'),
    entryEffectUrl: String(cfg.entryEffectUrl || ''),
    messageEffectUrl: String(cfg.messageEffectUrl || ''),
  })
  const plans = plansResult.data?.data || plansResult.data || []
  if (Array.isArray(plans) && plans.length) {
    const levels = plans.map(p => Number(p.level)).filter(n => Number.isInteger(n) && n >= 1 && n <= 7)
    if (levels.length) vipLevels.value = [...new Set(levels)].sort((a, b) => a - b)
  }
  form.eligibleVipLevels = form.eligibleVipLevels.filter(n => vipLevels.value.includes(n))
}
async function uploadAsset(asset, event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  const isAvatar = asset.key === 'avatarUrl'
  const isMessageEffect = asset.key === 'messageEffectUrl'
  const isImage = /\.(png|jpe?g|gif|webp)$/i.test(file.name)
  const isVideo = /\.(mp4|webm|mov|svga)$/i.test(file.name)
  const max = isAvatar || (isMessageEffect && isImage) ? 8 : 40
  if (file.size > max * 1024 * 1024) { toast().danger(`حجم الملف يجب ألا يتجاوز ${max} ميغابايت`); event.target.value = ''; return }
  const valid = isAvatar ? /\.(png|jpe?g|webp)$/i.test(file.name) : isMessageEffect ? (isImage || isVideo) : isVideo
  if (!valid) { toast().danger(isAvatar ? 'اختر صورة PNG أو JPG أو WEBP' : isMessageEffect ? 'اختر صورة PNG أو JPG أو GIF أو WEBP، أو فيديو MP4 أو WEBM أو MOV أو SVGA' : 'اختر MP4 أو WEBM أو MOV أو SVGA'); event.target.value = ''; return }
  const { data, error: err } = await uploadsApi.upload(file)
  event.target.value = ''
  if (err) { toast().danger(err.message); return }
  const uploaded = data?.data || data || {}
  const url = String(uploaded.url || uploaded.path || (uploaded.filename ? '/uploads/' + uploaded.filename : '')).trim()
  if (!url) { toast().danger('لم يصل رابط صالح من الخادم'); return }
  form[asset.key] = url
  toast().success('تم رفع الملف؛ اضغط حفظ لتطبيقه.')
}
async function save() {
  error.value = ''; success.value = ''
  if (!auth.can('settings', 'write')) { toast().danger('ليس لديك صلاحية تعديل إعدادات الرجل الغامض'); return }
  saving.value = true
  const { data, error: err } = await mysteriousManApi.update({ ...form })
  saving.value = false
  if (err) { error.value = err.message; toast().danger(err.message); return }
  const cfg = data?.data || data || {}
  Object.assign(form, cfg)
  success.value = 'تم حفظ إعدادات الرجل الغامض ومستويات VIP المختارة'
  toast().success(success.value)
}
onMounted(load)
</script>
