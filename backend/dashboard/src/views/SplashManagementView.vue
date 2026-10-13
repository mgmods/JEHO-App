<template>
  <div>
    <PageHeader title="إدارة السبلاش" subtitle="تحكم بإعلان بدء التشغيل من خلال إعدادات الخادم الحالية." />
    <AlertMessage v-if="message" :message="message" :type="error ? 'warning' : 'success'" @dismiss="message = ''" />
    <LoadingSpinner v-if="loading" />
    <section v-else class="widget-card p-3 p-lg-4">
      <div class="row g-4">
        <div class="col-12 col-lg-5">
          <h3 class="h6 mb-3">معاينة صورة الإعلان</h3>
          <div class="splash-preview rounded overflow-hidden d-flex align-items-center justify-content-center">
            <img v-if="previewUrl" :src="previewUrl" alt="معاينة السبلاش" />
            <div v-else class="text-muted text-center p-4"><i class="bi bi-image fs-1 d-block mb-2"></i>اختر صورة لمعاينتها</div>
            <span class="preview-skip">تخطي</span>
          </div>
          <label class="form-label small mt-3">الصورة (PNG / JPG / WebP)</label>
          <input class="form-control" type="file" accept="image/png,image/jpeg,image/webp" :disabled="uploading || saving" @change="uploadImage" />
          <p class="form-text">{{ uploading ? 'جارٍ رفع الصورة والتحقق من الوصول إليها…' : 'لا يتم اعتماد الصورة قبل التحقق من رابطها عبر الخادم الحالي.' }}</p>
        </div>
        <div class="col-12 col-lg-7">
          <div class="form-check form-switch mb-4">
            <input id="splash-enabled" v-model="enabled" class="form-check-input" type="checkbox" />
            <label for="splash-enabled" class="form-check-label">تفعيل شاشة السبلاش الإعلانية</label>
          </div>
          <label class="form-label small">مدة العرض</label>
          <select v-model.number="duration" class="form-select mb-3">
            <option v-for="seconds in [1,2,3,4,5]" :key="seconds" :value="seconds">{{ seconds }} {{ seconds === 1 ? 'ثانية' : 'ثوانٍ' }}</option>
          </select>
          <label class="form-label small">الوجهة عند الضغط على الصورة</label>
          <select v-model="targetType" class="form-select mb-3">
            <option value="home">الرئيسية</option><option value="messages">الرسائل</option><option value="drama">الدراما</option>
            <option value="create_room">إنشاء غرفة</option><option value="room">روم بواسطة الآيدي</option>
            <option value="agency">وكالة بواسطة الآيدي</option><option value="user">شخص / ملف شخصي بواسطة الآيدي</option>
            <option value="external">رابط خارجي</option>
          </select>
          <template v-if="['room','agency','user'].includes(targetType)">
            <label class="form-label small">الآيدي العام</label>
            <input v-model.trim="targetId" class="form-control mb-3" maxlength="64" placeholder="آيدي الروم أو الوكالة أو الشخص" />
            <p class="form-text">يتم حل الآيدي عبر API، وإذا لم يكن صالحًا يعود التطبيق للرئيسية دون تعطل.</p>
          </template>
          <template v-if="targetType === 'external'">
            <label class="form-label small">الرابط الخارجي</label>
            <input v-model.trim="targetUrl" type="url" class="form-control mb-3" placeholder="https://example.com" />
          </template>
          <div class="d-flex flex-wrap gap-2 mt-4">
            <button class="btn btn-aurora" type="button" :disabled="saving || uploading" @click="save">{{ saving ? 'جارٍ الحفظ…' : 'حفظ ونشر الإعدادات' }}</button>
            <button class="btn btn-outline-light" type="button" :disabled="loading || saving || uploading" @click="load">إعادة تحميل الإعدادات</button>
          </div>
          <p class="form-text mt-3">تظهر السبلاش عند بدء تشغيل التطبيق فقط. إذا تعذر تحميل الإعدادات أو الصورة، يتابع التطبيق إلى الرئيسية.</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { settingsApi, uploadsApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const apiUrl = (import.meta.env.VITE_API_URL || 'https://jeho-own-backend.mgs.blitz.cloud/api/v1').replace(/\/$/, '')
const origin = apiUrl.replace(/\/api\/v\d+$/i, '')
const loading = ref(false), uploading = ref(false), saving = ref(false)
const enabled = ref(false), duration = ref(5), imageUrl = ref(''), previewUrl = ref('')
const targetType = ref('home'), targetId = ref(''), targetUrl = ref('')
const message = ref(''), error = ref(false)
let theme = {}

function abs(url) {
  const value = String(url || '').trim()
  return !value ? '' : (/^https?:\/\//i.test(value) ? value : origin + (value.startsWith('/') ? value : '/' + value))
}
function versioned(url) {
  const value = abs(url)
  return value ? value + (value.includes('?') ? '&' : '?') + 'v=' + encodeURIComponent(String(theme.version || 1) + '-' + String(theme.updatedAt || '') + '-' + Date.now()) : ''
}
async function verifyImage(url) {
  const value = abs(url)
  if (!value) throw new Error('لم يرجع الخادم رابطًا للصورة')
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 10000)
  try {
    const response = await fetch(value + (value.includes('?') ? '&' : '?') + 'verify=' + Date.now(), { cache: 'no-store', signal: controller.signal })
    if (!response.ok) throw new Error('الصورة غير متاحة من الخادم (HTTP ' + response.status + ')')
    if (!(response.headers.get('content-type') || '').toLowerCase().startsWith('image/')) throw new Error('الرابط لا يعيد ملف صورة صالحًا')
    if (response.body) await response.body.cancel()
  } finally { clearTimeout(timer) }
}
function applySettings(data) {
  const rows = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : (data?.items || data?.data?.items || []))
  const row = rows.find(x => x?.key === 'app_theme')
  try { theme = row?.value ? JSON.parse(row.value) : {} } catch { theme = {} }
  const splash = theme?.splash || {}, items = Array.isArray(splash.items) ? splash.items : [], item = items[0] || {}
  imageUrl.value = String(item.url || '')
  previewUrl.value = versioned(imageUrl.value)
  enabled.value = splash.enabled !== false && item.active !== false && !!item.url
  duration.value = Math.max(1, Math.min(5, Number(item.durationSeconds) || 5))
  const type = String(item.targetType || ''), legacy = String(item.target || item.targetUrl || 'home')
  if (['room','agency','user'].includes(type)) { targetType.value = type; targetId.value = String(item.targetId || ''); targetUrl.value = '' }
  else if (type === 'external' || /^https?:\/\//i.test(legacy)) { targetType.value = 'external'; targetUrl.value = String(item.targetUrl || legacy); targetId.value = '' }
  else { targetType.value = ['home','messages','drama','create_room'].includes(legacy) ? legacy : 'home'; targetId.value = ''; targetUrl.value = '' }
}
async function load() {
  loading.value = true; message.value = ''
  try { const { data, error: err } = await settingsApi.get(); if (err) throw new Error(err.message || 'تعذّر تحميل الإعدادات'); applySettings(data) }
  catch (e) { error.value = true; message.value = e?.message || 'تعذّر تحميل إعدادات السبلاش' }
  finally { loading.value = false }
}
async function uploadImage(event) {
  const file = event.target.files?.[0]; if (!file) return
  if (!/^image\/(png|jpeg|webp)$/i.test(file.type)) { error.value = true; message.value = 'اختر صورة PNG أو JPG أو WebP.'; event.target.value = ''; return }
  uploading.value = true; message.value = ''
  try {
    const { data, error: err } = await uploadsApi.upload(file)
    if (err) throw new Error(err.message || 'فشل رفع الصورة')
    const url = data?.url || data?.data?.url || data?.path || data?.data?.path || ''
    if (!url) throw new Error('لم يرجع الخادم رابط الصورة')
    await verifyImage(url)
    imageUrl.value = url; previewUrl.value = URL.createObjectURL(file)
    error.value = false; message.value = 'تم رفع الصورة والتحقق من إمكانية الوصول إليها. اضغط حفظ ونشر.'
  } catch (e) { error.value = true; message.value = e?.message || 'تعذّر رفع الصورة أو التحقق منها' }
  finally { uploading.value = false }
}
async function save() {
  saving.value = true; message.value = ''
  try {
    if (enabled.value && !imageUrl.value) throw new Error('ارفع صورة صالحة قبل تفعيل السبلاش')
    if (['room','agency','user'].includes(targetType.value) && !targetId.value) throw new Error('أدخل الآيدي المطلوب')
    if (targetType.value === 'external' && !/^https?:\/\//i.test(targetUrl.value)) throw new Error('أدخل رابطًا يبدأ بـ http:// أو https://')
    if (imageUrl.value) await verifyImage(imageUrl.value)
    const next = { ...(theme || {}) }, old = next.splash && typeof next.splash === 'object' ? next.splash : {}
    const items = Array.isArray(old.items) ? [...old.items] : [], current = items[0] || {}
    items[0] = { ...current, id: current.id || 'splash-ad-main', url: imageUrl.value,
      durationSeconds: Math.max(1, Math.min(5, Number(duration.value) || 5)),
      active: !!enabled.value && !!imageUrl.value, sortOrder: 0,
      target: targetType.value === 'external' ? targetUrl.value : targetType.value,
      targetType: targetType.value, targetId: ['room','agency','user'].includes(targetType.value) ? targetId.value : '',
      targetUrl: targetType.value === 'external' ? targetUrl.value : '' }
    for (let i = 1; i < items.length; i++) items[i] = { ...items[i], active: false }
    next.splash = { ...old, enabled: !!enabled.value && !!imageUrl.value, skipEnabled: true, items }
    next.version = (Number(next.version) || 1) + 1; next.updatedAt = new Date().toISOString()
    const { error: err } = await settingsApi.update({ app_theme: JSON.stringify(next) })
    if (err) throw new Error(err.message || 'تعذّر حفظ إعدادات السبلاش')
    theme = next; previewUrl.value = versioned(imageUrl.value); error.value = false
    message.value = 'تم حفظ ونشر إعدادات السبلاش. سيجلب التطبيق الإعدادات الجديدة عند بدء التشغيل التالي.'
  } catch (e) { error.value = true; message.value = e?.message || 'تعذّر حفظ إعدادات السبلاش' }
  finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.splash-preview { position: relative; min-height: 260px; max-height: 460px; aspect-ratio: 9 / 16; background: #101522; }
.splash-preview img { width: 100%; height: 100%; max-height: 460px; object-fit: contain; }
.preview-skip { position: absolute; bottom: 14px; left: 14px; padding: 6px 12px; border-radius: 8px; color: #fff; background: rgba(0,0,0,.72); font-size: .85rem; }
</style>
