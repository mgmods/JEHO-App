<template>
  <div>
    <PageHeader :title="t('banners.title')" :subtitle="t('banners.subtitle')">
      <template #actions>
        <button class="btn btn-outline-light btn-sm me-2" type="button" @click="seedDefaults">
          <i class="bi bi-magic me-1"></i> {{ t('banners.seed') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" @click="addRow">
          <i class="bi bi-plus-lg me-1"></i> {{ t('banners.add') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <section class="widget-card p-3 mb-4">
      <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
        <div>
          <h3 class="h6 mb-1">إدارة السبلاش</h3>
          <p class="form-text mb-0">تظهر عند بدء تشغيل التطبيق فقط. إذا تعذّر تحميل صورة صالحة، يتابع التطبيق تلقائيًا إلى الرئيسية دون تعليق.</p>
        </div>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="splashSaving || splashUploading" @click="saveSplashAd">
          {{ splashSaving ? 'جارٍ الحفظ…' : 'حفظ إعلان التشغيل' }}
        </button>
      </div>
      <div class="row g-3 align-items-start">
        <div class="col-12 col-lg-5">
          <div class="rounded overflow-hidden bg-light d-flex align-items-center justify-content-center" style="min-height:180px;max-height:360px">
            <img v-if="splashImageUrl" :src="splashPreviewUrl" alt="معاينة إعلان التشغيل" style="display:block;width:100%;max-height:360px;object-fit:contain" />
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
          <select v-model.number="splashDuration" class="form-select form-select-sm mb-3">
            <option :value="1">ثانية واحدة</option>
            <option :value="2">ثانيتان</option>
            <option :value="3">3 ثوانٍ</option>
            <option :value="4">4 ثوانٍ</option>
            <option :value="5">5 ثوانٍ</option>
          </select>
          <label class="form-label small">الوجهة عند الضغط على الصورة</label>
          <select v-model="splashTarget" class="form-select form-select-sm mb-2">
            <option value="home">الرئيسية</option>
            <option value="messages">الرسائل</option>
            <option value="drama">الدراما</option>
            <option value="create_room">إنشاء غرفة</option>
            <option value="room_id">فتح روم بواسطة ID</option>
            <option value="agency_id">فتح وكالة بواسطة ID</option>
            <option value="user_id">فتح حساب شخص بواسطة ID</option>
            <option value="custom">رابط خارجي</option>
          </select>
          <input v-if="['room_id', 'agency_id', 'user_id'].includes(splashTarget)" v-model.trim="splashTargetId" type="text" inputmode="text" class="form-control form-control-sm mb-3" :placeholder="splashTarget === 'room_id' ? 'ID الروم (مثال: 123456)' : splashTarget === 'agency_id' ? 'ID الوكالة' : 'ID الشخص'" />
          <input v-if="splashTarget === 'custom'" v-model.trim="splashTargetUrl" type="url" class="form-control form-control-sm mb-3" placeholder="https://example.com/page" />
          <p class="form-text">زر «تخطي» يظهر أسفل اليسار. المدة من ثانية إلى 5 ثوانٍ فقط. تُنشر الإعدادات من الخادم دون إصدار APK جديد.</p>
          <div v-if="splashMessage" class="alert py-2 mt-2" :class="splashError ? 'alert-warning' : 'alert-success'" role="status">{{ splashMessage }}</div>
        </div>
      </div>
    </section>

    <div class="d-flex justify-content-between align-items-center mb-3">
      <h3 class="h6 mb-0">{{ t('banners.home') }}</h3>
      <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
        {{ saving ? t('app.saving') : t('banners.save') }}
      </button>
    </div>

    <LoadingSpinner v-if="loading" />
    <template v-else>
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
import { useI18n } from 'vue-i18n'
import { settingsApi, uploadsApi } from '@/api'
import apiClient from '@/api/client'
import { toast } from '@/composables/useToast'
import { useBulkSelection } from '@/composables/useBulkSelection'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import BulkActionBar from '@/components/BulkActionBar.vue'
import BulkCheck from '@/components/BulkCheck.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const ORIGIN = (import.meta.env.VITE_API_ORIGIN || apiClient.defaults.baseURL || '').replace(/\/api\/v\d+\/?$/i, '').replace(/\/$/, '')
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
const splashTarget = ref('home')
const splashTargetUrl = ref('')
const splashTargetId = ref('')
const splashSaving = ref(false)
const splashUploading = ref(false)
const splashMessage = ref('')
const splashError = ref(false)
let themeConfig = {}

function splashAbsUrl(url) {
  if (!url) return ''
  const absolute = absUrl(url)
  const separator = absolute.includes('?') ? '&' : '?'
  return absolute + separator + 'preview=' + encodeURIComponent(String(themeConfig.version || 1) + '-' + String(themeConfig.updatedAt || ''))
}

function probeSplashImage(url, timeoutMs = 12000) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    const timer = window.setTimeout(() => {
      image.onload = null
      image.onerror = null
      reject(new Error('رابط الصورة لم يستجب خلال المهلة المحددة. لم يتم اعتماد الصورة.'))
    }, timeoutMs)
    image.onload = () => {
      window.clearTimeout(timer)
      if (image.naturalWidth > 0 && image.naturalHeight > 0) resolve(true)
      else reject(new Error('الخادم أعاد ملفاً ليس صورة صالحة.'))
    }
    image.onerror = () => {
      window.clearTimeout(timer)
      reject(new Error('تعذر فتح الصورة من رابط الخادم. تحقق من التخزين وصلاحيات الوصول.'))
    }
    const absolute = absUrl(url)
    image.src = absolute + (absolute.includes('?') ? '&' : '?') + 'splash_probe=' + Date.now()
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
    // Do not report a successful upload until the public URL is actually readable.
    await probeSplashImage(url)
    splashImageUrl.value = url
    splashPreviewUrl.value = URL.createObjectURL(file)
    splashError.value = false
    splashMessage.value = 'تم رفع الصورة والتحقق من إمكانية فتحها من الخادم. اضغط «حفظ إعلان التشغيل» للنشر.'
  } catch (err) {
    splashError.value = true
    splashMessage.value = err?.message || 'تعذّر رفع صورة الإعلان.'
  } finally {
    splashUploading.value = false
    event.target.value = ''
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
    let target = splashTarget.value
    if (splashTarget.value === 'custom') {
      target = String(splashTargetUrl.value || '').trim()
      if (!/^https?:\/\//i.test(target)) throw new Error('الرابط الخارجي يجب أن يبدأ بـ https:// أو http://')
    } else if (['room_id', 'agency_id', 'user_id'].includes(splashTarget.value)) {
      const id = String(splashTargetId.value || '').trim()
      if (!id || !/^[A-Za-z0-9_-]{3,64}$/.test(id)) throw new Error('أدخل ID صالحاً للوجهة المختارة.')
      target = `${splashTarget.value.replace('_id', '')}:${id}`
    } else if (!['home', 'messages', 'drama', 'create_room'].includes(target)) {
      throw new Error('اختر وجهة صالحة.')
    }
    items[0] = {
      ...current,
      id: current.id || 'splash-ad-main',
      url: splashImageUrl.value || current.url || '',
      durationSeconds: duration,
      active: !!splashEnabled.value && !!(splashImageUrl.value || current.url),
      sortOrder: Number.isFinite(Number(current.sortOrder)) ? Number(current.sortOrder) : 0,
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
    // Read back the public mobile config so we know the saved settings reached the same API.
    const readBack = await apiClient.get('/config/theme', { params: { splash_verify: Date.now() } })
    const published = readBack.data?.data || readBack.data
    const publishedItem = published?.splash?.items?.[0]
    if (!publishedItem || String(publishedItem.url || '').split('?')[0].split('/').pop() !== String(items[0].url || '').split('?')[0].split('/').pop()) {
      throw new Error('تم إرسال الحفظ لكن تعذّر تأكيد إعداد السبلاش من واجهة التطبيق العامة.')
    }
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
  const target = String(item.target || item.targetUrl || 'home')
  const idTarget = target.match(/^(room|agency|user):(.+)$/i)
  if (idTarget) {
    splashTarget.value = idTarget[1].toLowerCase() + '_id'
    splashTargetId.value = idTarget[2]
    splashTargetUrl.value = ''
  } else if (['home', 'messages', 'drama', 'create_room'].includes(target)) {
    splashTarget.value = target
    splashTargetId.value = ''
    splashTargetUrl.value = ''
  } else if (/^https?:\/\//i.test(target)) {
    splashTarget.value = 'custom'
    splashTargetUrl.value = target
    splashTargetId.value = ''
  } else {
    splashTarget.value = 'home'
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
