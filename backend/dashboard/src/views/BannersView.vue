<template>
  <div>
    <PageHeader :title="splashOnly ? 'إدارة السبلاش' : t('banners.title')" :subtitle="splashOnly ? 'التحكم بإعلان بدء التشغيل الذي يصل للتطبيق مباشرة من API الحالي' : t('banners.subtitle')">
      <template #actions>
        <button v-if="!splashOnly" class="btn btn-outline-light btn-sm me-2" type="button" @click="seedDefaults">
          <i class="bi bi-magic me-1"></i> {{ t('banners.seed') }}
        </button>
        <button v-if="!splashOnly" class="btn btn-aurora btn-sm" type="button" @click="addRow">
          <i class="bi bi-plus-lg me-1"></i> {{ t('banners.add') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <section id="splash-management" class="widget-card p-3 mb-4">
      <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
        <div>
          <h3 class="h6 mb-1">إدارة السبلاش — إعلان بدء التشغيل</h3>
          <p class="form-text mb-0">تظهر مرة واحدة عند بدء تشغيل التطبيق بالكامل. إذا تعذّر تحميل الصورة، يتابع التطبيق تلقائيًا دون تعطيل الدخول.</p>
        </div>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="splashSaving || splashUploading" @click="saveSplashAd">
          {{ splashSaving ? 'جارٍ الحفظ…' : 'حفظ إعلان التشغيل' }}
        </button>
      </div>
      <div class="row g-3 align-items-start">
        <div class="col-12 col-lg-5">
          <div class="rounded overflow-hidden bg-light d-flex align-items-center justify-content-center" style="min-height:180px;max-height:360px">
            <img v-if="splashPreviewUrl" :src="splashPreviewUrl" alt="معاينة إعلان التشغيل" style="display:block;width:100%;max-height:360px;object-fit:contain" />
            <div v-else class="text-muted p-4 text-center"><i class="bi bi-image fs-1 d-block mb-2"></i>لم يتم اختيار صورة إعلان</div>
          </div>
          <label class="form-label small mt-3">صورة الإعلان (PNG / JPG / WebP)</label>
          <input ref="splashFileInput" type="file" accept="image/png,image/jpeg,image/webp" class="form-control form-control-sm" :disabled="splashUploading || splashSaving" @change="onSplashFile" />
          <div class="form-text">{{ splashUploading ? 'جارٍ رفع الصورة إلى التخزين…' : 'الصورة تُرفع إلى خدمة التخزين الحالية ويُحفظ رابطها في إعدادات التطبيق المركزية.' }}</div>
        </div>
        <div class="col-12 col-lg-7">
          <div class="form-check form-switch mb-3">
            <input id="splash-ad-enabled" v-model="splashEnabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="splash-ad-enabled">تفعيل إعلان التشغيل</label>
          </div>
          <label class="form-label small">مدة العرض بالثواني</label>
          <select v-model.number="splashDuration" class="form-select form-select-sm mb-3"><option :value="1">ثانية واحدة</option><option :value="2">ثانيتان</option><option :value="3">3 ثوانٍ</option><option :value="4">4 ثوانٍ</option><option :value="5">5 ثوانٍ</option></select>
          <label class="form-label small">نوع الوجهة عند الضغط على الصورة</label>
          <select v-model="splashTargetType" class="form-select form-select-sm mb-2">
            <option value="home">الرئيسية</option>
            <option value="messages">الرسائل</option>
            <option value="drama">الدراما</option>
            <option value="create_room">إنشاء غرفة</option>
            <option value="room">غرفة صوتية بواسطة ID</option>
            <option value="agency">وكالة بواسطة ID</option>
            <option value="person">شخص بواسطة ID</option>
            <option value="external">رابط خارجي</option>
          </select>
          <label v-if="['room', 'agency', 'person'].includes(splashTargetType)" class="form-label small">Target ID</label>
          <input v-if="['room', 'agency', 'person'].includes(splashTargetType)" v-model.trim="splashTargetId" type="text" inputmode="text" maxlength="128" class="form-control form-control-sm mb-3" :placeholder="splashTargetType === 'room' ? 'ID الغرفة' : splashTargetType === 'agency' ? 'ID الوكالة' : 'ID الشخص'" />
          <label v-if="splashTargetType === 'external'" class="form-label small">الرابط الخارجي</label>
          <input v-if="splashTargetType === 'external'" v-model.trim="splashTargetUrl" type="url" class="form-control form-control-sm mb-3" placeholder="https://example.com/page" />
          <p class="form-text">زر «تخطي» يظهر أسفل الإعلان. المدة الافتراضية 5 ثوانٍ. التعديلات تصل إلى التطبيق من إعدادات الخادم دون تحديث APK.</p>
          <div v-if="splashMessage" class="alert py-2 mt-2" :class="splashError ? 'alert-warning' : 'alert-success'" role="status">{{ splashMessage }}</div>
        </div>
      </div>
    </section>

    <section v-if="!splashOnly" class="widget-card p-3 mb-4">
      <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <div>
          <h3 class="h6 mb-1">بنر الملف الشخصي — قسم «أنا»</h3>
          <p class="form-text mb-0">هذا البنر مستقل تمامًا عن بنرات الصفحة الرئيسية. ارفع صورة واحفظها لتظهر في صفحة الملف الشخصي فقط.</p>
        </div>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="profileBannerSaving || profileBannerUploading" @click="saveProfileBanner">
          {{ profileBannerSaving ? 'جارٍ الحفظ…' : 'حفظ بنر الملف الشخصي' }}
        </button>
      </div>
      <div class="row g-3 align-items-start">
        <div class="col-12 col-lg-5">
          <div class="rounded overflow-hidden bg-light d-flex align-items-center justify-content-center" style="height:110px">
            <img v-if="profileBanner.imageUrl" :src="absUrl(profileBanner.imageUrl)" alt="معاينة بنر الملف الشخصي" style="display:block;width:100%;height:100%;object-fit:cover" />
            <div v-else class="text-muted p-3 text-center"><i class="bi bi-image fs-2 d-block mb-1"></i>لا توجد صورة لبنر الملف الشخصي</div>
          </div>
          <label class="form-label small mt-3">صورة البنر (PNG / JPG / WebP)</label>
          <input type="file" accept="image/png,image/jpeg,image/webp" class="form-control form-control-sm" :disabled="profileBannerUploading || profileBannerSaving" @change="onProfileBannerFile" />
          <div class="form-text">{{ profileBannerUploading ? 'جارٍ رفع الصورة…' : 'يتم حفظ الصورة في إعداد مستقل، ولا يغيّر بنرات الرئيسية.' }}</div>
        </div>
        <div class="col-12 col-lg-7">
          <label class="form-label small">عنوان اختياري</label>
          <input v-model.trim="profileBanner.title" class="form-control form-control-sm mb-3" maxlength="120" placeholder="بنر الملف الشخصي" />
          <button class="btn btn-outline-secondary btn-sm" type="button" :disabled="profileBannerSaving || profileBannerUploading" @click="profileBanner.imageUrl = ''">إزالة صورة البنر</button>
          <div v-if="profileBannerMessage" class="alert py-2 mt-3 mb-0" :class="profileBannerError ? 'alert-warning' : 'alert-success'" role="status">{{ profileBannerMessage }}</div>
        </div>
      </div>
    </section>

    <div v-if="!splashOnly" class="d-flex justify-content-between align-items-center mb-3">
      <h3 class="h6 mb-0">{{ t('banners.home') }}</h3>
      <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
        {{ saving ? t('app.saving') : t('banners.save') }}
      </button>
    </div>

    <LoadingSpinner v-if="!splashOnly && loading" />
    <template v-else-if="!splashOnly">
      <BulkActionBar
        :count="rows.length"
        :selected-count="selectedCount"
        :all-selected="allSelected"
        :some-selected="someSelected"
        :actions="bulkActions"
        @toggle-all="toggleAll"
        @clear="clear"
        @action="onBulkAction"
      />
      <div class="widget-grid">
        <div v-if="!rows.length" class="glass p-4 empty-state">{{ t('banners.empty') }}</div>
        <article v-for="(row, idx) in rows" :key="idx" class="widget-card position-relative" :class="{ 'is-selected': isSelected(String(idx)) }">
          <BulkCheck :checked="isSelected(String(idx))" @toggle="toggle(String(idx))" />
          <div class="widget-card-media banner-media">
          <img v-if="row.imageUrl" :src="absUrl(row.imageUrl)" alt="" />
          <i v-else class="bi bi-image text-muted" style="font-size: 2rem" />
        </div>
        <div class="widget-card-body">
          <div class="d-flex justify-content-between align-items-center mb-2">
            <h3 class="widget-card-title mb-0">{{ t('banners.bannerNumber', { number: idx + 1 }) }}</h3>
            <button class="btn btn-sm btn-outline-danger" type="button" @click="askRemove(idx)">
              {{ t('banners.remove') }}
            </button>
          </div>
          <div class="row g-2">
            <div class="col-12">
              <label class="form-label small">{{ t('banners.image') || t('common.image') || 'الصورة' }}</label>
              <input type="file" accept="image/*" class="form-control form-control-sm" @change="(e) => onFile(e, row)" />
              <div class="form-text">اختر صورة البنر — بدون لصق رابط</div>
              <div v-if="row.imageUrl" class="mt-2">
                <img :src="absUrl(row.imageUrl)" alt="" style="max-height: 72px; border-radius: 10px; object-fit: cover" />
              </div>
            </div>
            <div class="col-12">
              <label class="form-label small">{{ t('common.title') }}</label>
              <input v-model="row.title" class="form-control form-control-sm" />
            </div>
            <div class="col-12">
              <label class="form-label small">{{ t('common.link') }}</label>
              <select v-model="row.link" class="form-select form-select-sm mb-2">
                <option value="">— {{ t('banners.select') }} —</option>
                <option v-for="p in promoLinks" :key="p.value" :value="p.value">{{ p.label }}</option>
                <option value="ranking">{{ t('banners.ranking') }}</option>
                <option value="custom">{{ t('banners.custom') }}</option>
              </select>
              <input
                v-if="row.link === 'custom' || (row.link && !isKnownLink(row.link))"
                v-model="row.link"
                class="form-control form-control-sm"
                placeholder="https://api.adnova.bbs.tr/promos/…"
              />
            </div>
          </div>
        </div>
      </article>
      </div>
    </template>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="t('common.delete')"
      :message="confirmMsg"
      @confirm="doRemove"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { settingsApi, uploadsApi } from '@/api'
import api from '@/api/client'
import { toast } from '@/composables/useToast'
import { useBulkSelection } from '@/composables/useBulkSelection'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import BulkActionBar from '@/components/BulkActionBar.vue'
import BulkCheck from '@/components/BulkCheck.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()
const route = useRoute()
const splashOnly = computed(() => route.name === 'splash')

// Static promo assets may still live on the Adnova content host; uploads and API
// requests must use the exact API origin configured for this dashboard.
const ORIGIN = (import.meta.env.VITE_API_ORIGIN || window.location.origin).replace(/\/$/, '')
const API_ORIGIN = String(api.defaults.baseURL || '')
  .trim()
  .replace(/\/$/, '')
  .replace(/\/api\/v\d+$/i, '')
const PROMO = `${ORIGIN}/promos`

const promoLinks = computed(() => [
  { label: t('contests.promo.midyear'), value: `${PROMO}/midyear.html` },
  { label: t('contests.promo.championship'), value: `${PROMO}/championship.html` },
  { label: t('contests.promo.battle'), value: `${PROMO}/battle.html` },
  { label: t('contests.promo.elite'), value: `${PROMO}/elite-stars.html` },
  { label: t('contests.promo.agencies'), value: `${PROMO}/agencies-monthly.html` },
  { label: t('contests.promo.recharge'), value: `${PROMO}/recharge.html` },
  { label: 'VIP', value: `${PROMO}/vip.html` },
])

const known = new Set([...promoLinks.value.map((p) => p.value), 'ranking', 'wallet', 'recharge', 'vip', ''])

const profileBanner = ref({ imageUrl: '', title: '' })
const profileBannerSaving = ref(false)
const profileBannerUploading = ref(false)
const profileBannerMessage = ref('')
const profileBannerError = ref(false)
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const confirmOpen = ref(false)
const confirmMsg = ref('')
const pendingRemoveIdx = ref(-1)
const splashFileInput = ref(null)
const splashImageUrl = ref('')
const splashPreviewUrl = ref('')
const splashEnabled = ref(true)
const splashDuration = ref(5)
const splashTargetType = ref('home')
const splashTargetId = ref('')
const splashTargetUrl = ref('')
const splashSaving = ref(false)
const splashUploading = ref(false)
const splashMessage = ref('')
const splashError = ref(false)
let themeConfig = {}

function splashAssetAbsUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) {
    try {
      const parsed = new URL(url)
      if (/^\/uploads\//i.test(parsed.pathname)) return API_ORIGIN + parsed.pathname
    } catch {}
    return url
  }
  return API_ORIGIN + (url.startsWith('/') ? '' : '/') + url
}

function splashAbsUrl(url) {
  if (!url) return ''
  const absolute = splashAssetAbsUrl(url)
  const separator = absolute.includes('?') ? '&' : '?'
  return absolute + separator + 'preview=' + encodeURIComponent(String(themeConfig.version || 1) + '-' + String(themeConfig.updatedAt || ''))
}

function verifySplashImage(url) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    const timer = setTimeout(() => { image.src = ''; reject(new Error('انتهت مهلة التحقق من رابط الصورة')) }, 8000)
    image.onload = () => {
      clearTimeout(timer)
      if (image.naturalWidth > 0 && image.naturalHeight > 0) resolve(true)
      else reject(new Error('رابط الصورة لا يعرض صورة صالحة'))
    }
    image.onerror = () => {
      clearTimeout(timer)
      reject(new Error('تعذر الوصول إلى الصورة من عنوان API الحالي'))
    }
    image.src = url + (url.includes('?') ? '&' : '?') + 'verify=' + Date.now()
  })
}

async function onSplashFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!/^image\/(png|jpeg|webp)$/i.test(file.type)) {
    splashError.value = true
    splashMessage.value = 'اختر صورة PNG أو JPG أو WebP صالحة.'
    event.target.value = ''
    return
  }
  splashUploading.value = true
  splashMessage.value = ''
  try {
    const { data, error: err } = await uploadsApi.upload(file)
    if (err) throw new Error(err.message || 'فشل رفع الصورة')
    const url = data?.url || data?.data?.url || data?.path || data?.data?.path || ''
    if (!url) throw new Error('تم الرفع دون الحصول على رابط الصورة')
    const accessibleUrl = splashAssetAbsUrl(url)
    await verifySplashImage(accessibleUrl)
    // Persist the absolute URL that the Android app can request directly.
    // The upload API may return a relative path (with or without a leading slash).
    splashImageUrl.value = accessibleUrl
    splashPreviewUrl.value = URL.createObjectURL(file)
    splashError.value = false
    splashMessage.value = 'تم رفع الصورة والتحقق من إمكانية الوصول إليها. اضغط «حفظ إعلان التشغيل» للنشر.'
  } catch (err) {
    splashError.value = true
    splashMessage.value = err?.message || 'تعذّر رفع صورة الإعلان.'
  } finally {
    splashUploading.value = false
  }
}

async function saveSplashAd() {
  splashSaving.value = true
  splashMessage.value = ''
  splashError.value = false
  try {
    const duration = Math.max(1, Math.min(5, Math.floor(Number(splashDuration.value) || 5)))
    const next = { ...(themeConfig || {}) }
    const oldSplash = next.splash && typeof next.splash === 'object' ? next.splash : {}
    const items = Array.isArray(oldSplash.items) ? [...oldSplash.items] : []
    const current = items[0] && typeof items[0] === 'object' ? items[0] : {}
    const targetType = String(splashTargetType.value || 'home')
    const targetId = String(splashTargetId.value || '').trim()
    const targetUrl = String(splashTargetUrl.value || '').trim()
    const idTargets = ['room', 'agency', 'person']
    if (idTargets.includes(targetType) && !targetId) throw new Error('أدخل Target ID للوجهة المحددة')
    if (targetType === 'external' && !/^https?:\/\/.+/i.test(targetUrl)) throw new Error('أدخل رابطاً خارجياً صالحاً يبدأ بـ https:// أو http://')
    const target = targetType === 'external' ? targetUrl : targetType
    items[0] = {
      ...current,
      id: current.id || 'splash-ad-main',
      url: splashImageUrl.value || current.url || '',
      durationSeconds: duration,
      active: !!splashEnabled.value && !!(splashImageUrl.value || current.url),
      sortOrder: Number.isFinite(Number(current.sortOrder)) ? Number(current.sortOrder) : 0,
      targetType,
      targetId: idTargets.includes(targetType) ? targetId : '',
      targetUrl: targetType === 'external' ? targetUrl : '',
      target,
    }
    // This dashboard control manages one active startup ad. Keep any legacy items
    // in the existing config but deactivate them so an old image cannot rotate in.
    for (let i = 1; i < items.length; i += 1) items[i] = { ...items[i], active: false }
    next.splash = { ...oldSplash, enabled: !!splashEnabled.value && !!items[0].url, skipEnabled: true, items }
    next.version = (Number(next.version) || 1) + 1
    next.updatedAt = new Date().toISOString()
    const { error: err } = await settingsApi.update({ app_theme: JSON.stringify(next) })
    if (err) throw new Error(err.message || 'تعذّر حفظ إعدادات الإعلان')
    themeConfig = next
    splashDuration.value = duration
    splashPreviewUrl.value = splashAbsUrl(splashImageUrl.value)
    splashMessage.value = 'تم حفظ إعلان التشغيل ونشر إعداداته. سيجلب التطبيق الصورة الجديدة عند التشغيل التالي.'
  } catch (err) {
    splashError.value = true
    splashMessage.value = err?.message || 'تعذّر حفظ إعدادات الإعلان.'
  } finally {
    splashSaving.value = false
  }
}

const {
  selectedIds,
  selectedCount,
  allSelected,
  someSelected,
  isSelected,
  toggle,
  clear,
  toggleAll,
} = useBulkSelection(rows, (row) => String(rows.value.indexOf(row)))

const bulkActions = computed(() => [
  { key: 'delete', label: t('bulk.deleteSelected'), icon: 'bi-trash', variant: 'btn-outline-danger' },
])

function onBulkAction(key) {
  if (key !== 'delete') return
  const ids = selectedIds.value
  if (!ids.length) return
  pendingRemoveIdx.value = -2
  confirmMsg.value = t('bulk.confirmDelete', { count: ids.length })
  confirmOpen.value = true
}

function absUrl(u) {
  if (!u) return ''
  if (/^https?:\/\//i.test(u)) return u
  return `${ORIGIN}${u.startsWith('/') ? '' : '/'}${u}`
}

function isKnownLink(link) {
  return known.has(link) || promoLinks.value.some((p) => p.value === link)
}

function addRow() {
  rows.value.push({ imageUrl: '', title: '', link: `${PROMO}/midyear.html` })
}

function seedDefaults() {
  rows.value = [
    { imageUrl: `${ORIGIN}/banners/midyear.png`, title: 'احتفالية منتصف العام', link: `${PROMO}/midyear.html` },
    { imageUrl: `${ORIGIN}/banners/championship.png`, title: 'الطريق إلى البطولة', link: `${PROMO}/championship.html` },
    { imageUrl: `${ORIGIN}/banners/battle.png`, title: 'معركة الفرق', link: `${PROMO}/battle.html` },
    { imageUrl: `${ORIGIN}/banners/elite.png`, title: 'نخبة النجوم', link: `${PROMO}/elite-stars.html` },
    { imageUrl: `${ORIGIN}/banners/agencies.png`, title: 'الوكالات الوطنية', link: `${PROMO}/agencies-monthly.html` },
    { imageUrl: `${ORIGIN}/banners/recharge.png`, title: 'عرض الشحن المميز', link: `${PROMO}/recharge.html` },
  ]
  toast().info(t('banners.seed'))
}

function askRemove(idx) {
  pendingRemoveIdx.value = idx
  confirmMsg.value = `${t('banners.remove')} #${idx + 1}؟`
  confirmOpen.value = true
}

function doRemove() {
  const idx = pendingRemoveIdx.value
  pendingRemoveIdx.value = -1
  if (idx === -2) {
    const indices = selectedIds.value.map(Number).sort((a, b) => b - a)
    indices.forEach((i) => rows.value.splice(i, 1))
    clear()
    return
  }
  if (idx < 0) return
  rows.value.splice(idx, 1)
}

async function onFile(e, row) {
  const file = e.target.files?.[0]
  if (!file) return
  const { data, error: err } = await uploadsApi.upload(file)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const url = data?.url || data?.data?.url || ''
  row.imageUrl = absUrl(url)
  toast().success(t('app.success'))
}

async function onProfileBannerFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!/^image\/(png|jpeg|webp)$/i.test(file.type)) {
    profileBannerError.value = true
    profileBannerMessage.value = 'اختر صورة PNG أو JPG أو WebP صالحة.'
    event.target.value = ''
    return
  }
  profileBannerUploading.value = true
  profileBannerMessage.value = ''
  try {
    const { data, error: err } = await uploadsApi.upload(file)
    if (err) throw new Error(err.message || 'فشل رفع الصورة')
    const url = data?.url || data?.data?.url || data?.path || data?.data?.path || ''
    if (!url) throw new Error('تم الرفع دون الحصول على رابط الصورة')
    profileBanner.value.imageUrl = absUrl(url)
    profileBannerError.value = false
    profileBannerMessage.value = 'تم رفع الصورة. اضغط «حفظ بنر الملف الشخصي» لنشرها.'
  } catch (err) {
    profileBannerError.value = true
    profileBannerMessage.value = err?.message || 'تعذّر رفع صورة البنر.'
  } finally {
    profileBannerUploading.value = false
  }
}

async function saveProfileBanner() {
  profileBannerSaving.value = true
  profileBannerMessage.value = ''
  profileBannerError.value = false
  try {
    const payload = {
      profile_banner: JSON.stringify({
        imageUrl: absUrl(profileBanner.value.imageUrl || ''),
        title: String(profileBanner.value.title || '').trim(),
      }),
    }
    const { error: err } = await settingsApi.update(payload)
    if (err) throw new Error(err.message || 'تعذّر حفظ بنر الملف الشخصي')
    profileBannerMessage.value = profileBanner.value.imageUrl
      ? 'تم حفظ بنر الملف الشخصي بشكل مستقل عن بنرات الرئيسية.'
      : 'تم حفظ الإعداد. لن يظهر بنر في الملف الشخصي حتى ترفع صورة.'
  } catch (err) {
    profileBannerError.value = true
    profileBannerMessage.value = err?.message || 'تعذّر حفظ بنر الملف الشخصي.'
  } finally {
    profileBannerSaving.value = false
  }
}

function parseSetting(list, key, fallback) {
  const found = (Array.isArray(list) ? list : list?.items || []).find((s) => s.key === key)
  if (!found?.value) return fallback
  try {
    const parsed = JSON.parse(found.value)
    return Array.isArray(parsed) ? parsed : fallback
  } catch {
    return fallback
  }
}

async function load() {
  loading.value = true
  const { data, error: err } = await settingsApi.get()
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const settingsRows = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : (data?.items || data?.data?.items || []))
  rows.value = parseSetting(settingsRows, 'home_banners', [])
  const profileBannerRow = settingsRows.find((s) => s?.key === 'profile_banner')
  try {
    const parsedProfileBanner = profileBannerRow?.value ? JSON.parse(profileBannerRow.value) : {}
    profileBanner.value = {
      imageUrl: typeof parsedProfileBanner?.imageUrl === 'string' ? parsedProfileBanner.imageUrl : '',
      title: typeof parsedProfileBanner?.title === 'string' ? parsedProfileBanner.title : '',
    }
  } catch {
    profileBanner.value = { imageUrl: '', title: '' }
  }
  const themeRow = settingsRows.find((s) => s?.key === 'app_theme')
  try {
    themeConfig = themeRow?.value ? JSON.parse(themeRow.value) : {}
  } catch {
    themeConfig = {}
  }
  const splash = themeConfig?.splash && typeof themeConfig.splash === 'object' ? themeConfig.splash : {}
  const items = Array.isArray(splash.items) ? splash.items : []
  const item = items[0] && typeof items[0] === 'object' ? items[0] : {}
  splashImageUrl.value = String(item.url || '')
  splashPreviewUrl.value = splashAbsUrl(splashImageUrl.value)
  splashEnabled.value = splash.enabled !== false && item.active !== false
  splashDuration.value = Math.max(1, Math.min(5, Number(item.durationSeconds) || 5))
  const legacyTarget = String(item.target || item.targetUrl || 'home')
  const savedType = String(item.targetType || '')
  if (['home', 'messages', 'drama', 'create_room', 'room', 'agency', 'person'].includes(savedType)) {
    splashTargetType.value = savedType
    splashTargetId.value = String(item.targetId || '')
    splashTargetUrl.value = ''
  } else if (savedType === 'external' || /^https?:\/\//i.test(legacyTarget)) {
    splashTargetType.value = 'external'
    splashTargetId.value = ''
    splashTargetUrl.value = String(item.targetUrl || legacyTarget)
  } else if (['home', 'messages', 'drama', 'create_room'].includes(legacyTarget)) {
    splashTargetType.value = legacyTarget
    splashTargetId.value = ''
    splashTargetUrl.value = ''
  } else if (['room', 'agency', 'person'].includes(legacyTarget)) {
    splashTargetType.value = legacyTarget
    splashTargetId.value = String(item.targetId || '')
    splashTargetUrl.value = ''
  } else {
    splashTargetType.value = 'home'
    splashTargetId.value = ''
    splashTargetUrl.value = ''
  }
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    home_banners: JSON.stringify(
      rows.value
        .filter((r) => r.imageUrl)
        .map((r) => ({
          imageUrl: absUrl(r.imageUrl),
          title: r.title || '',
          link: r.link === 'custom' ? '' : r.link || '',
        })),
    ),
  }
  const { error: err } = await settingsApi.update(payload)
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('banners.saved')
    toast().success(t('banners.saved'))
  }
}

onMounted(load)
</script>

<style scoped>
.banner-media {
  aspect-ratio: 16 / 9;
}
.banner-media img {
  max-width: 100%;
  max-height: 100%;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.widget-card.is-selected {
  outline: 2px solid rgba(45, 212, 191, 0.65);
  outline-offset: 2px;
}
</style>
