<template>
  <div>
    <PageHeader title="أيقونات قسم أنا" subtitle="ارفع أيقونة مخصصة لكل زر في الملف الشخصي، ثم احفظ لتظهر في التطبيق.">
      <template #actions>
        <button class="btn btn-outline-light btn-sm me-2" type="button" :disabled="saving" @click="clearAll">استعادة الأيقونات الافتراضية</button>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
          {{ saving ? 'جارٍ الحفظ…' : 'حفظ التغييرات' }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <p class="text-secondary small mb-3">الأيقونات التي تُطبّق على جميع المستخدمين: VIP، المستوى، المتجر، الغرفة، الوكالة، فعالياتي، فتح وكالة، مركز المهام، وكيل الشحن، الأدوات. إعدادات هذه الأيقونات مشتركة مع أيقونات التبويبات، ولن تتأثر عند حفظ أيقونات التبويبات. ترك الأيقونة فارغة يعيد أيقونة التطبيق الافتراضية.</p>

    <LoadingSpinner v-if="loading" />
    <div v-else class="profile-icons-grid">
      <article v-for="item in actions" :key="item.key" class="glass p-3 profile-icon-card">
        <h3 class="h6 mb-3">{{ item.label }}</h3>
        <div class="icon-preview mb-3">
          <img v-if="form[item.key]" :src="absUrl(form[item.key])" :alt="item.label" />
          <span v-else class="text-muted small">الأيقونة الافتراضية</span>
        </div>
        <label class="form-label small">رفع أيقونة</label>
        <input type="file" accept="image/png,image/jpeg,image/jpg,image/webp,image/svg+xml,.png,.jpg,.jpeg,.webp,.svg"
          class="form-control form-control-sm" @change="(e) => onFile(e, item.key)" />
        <button v-if="form[item.key]" class="btn btn-link btn-sm text-danger px-0 mt-2" type="button" @click="form[item.key] = ''">إزالة الأيقونة المخصصة</button>
      </article>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { settingsApi, uploadsApi } from '@/api'
import { resolveAsset } from '@/utils/assets'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const ORIGIN = (import.meta.env.VITE_API_ORIGIN || window.location.origin).replace(/\/$/, '')
const actions = [
  { key: 'vip', label: 'VIP' },
  { key: 'level', label: 'المستوى' },
  { key: 'store', label: 'المتجر' },
  { key: 'room', label: 'الغرفة' },
  { key: 'agencyCenter', label: 'الوكالة' },
  { key: 'events', label: 'فعالياتي' },
  { key: 'agency', label: 'فتح وكالة' },
  { key: 'tasks', label: 'مركز المهام' },
  { key: 'rechargeAgent', label: 'وكيل الشحن' },
  { key: 'tools', label: 'الأدوات' },
  { key: 'settings', label: 'الإعدادات' },
  { key: 'support', label: 'الدعم' },
]
const form = reactive(Object.fromEntries(actions.map(x => [x.key, ''])))
const baseConfig = ref({ version: 1 })
const version = ref(1)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

function absUrl(value) {
  return resolveAsset(value)
}
function storedUrl(value) {
  if (!value) return ''
  if (/^https?:\/\//i.test(value)) {
    try {
      const parsed = new URL(value)
      if (parsed.pathname.startsWith('/uploads/')) return parsed.pathname + parsed.search
    } catch {}
  }
  return value
}
function parseSetting(list) {
  const rows = Array.isArray(list) ? list : list?.items || []
  const row = rows.find(x => x.key === 'app_nav_icons')
  if (!row?.value) return null
  try { return typeof row.value === 'string' ? JSON.parse(row.value) : row.value } catch { return null }
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
  const cfg = parseSetting(data)
  version.value = Math.max(1, Number(cfg?.version) || 1)
  baseConfig.value = cfg && typeof cfg === 'object' ? cfg : { version: 1 }
  const profileActions = cfg?.profileActions && typeof cfg.profileActions === 'object' ? cfg.profileActions : {}
  for (const item of actions) {
    const key = item.key === 'rechargeAgent' ? 'recharge' : item.key
    form[item.key] = typeof profileActions[key] === 'string' ? profileActions[key] : ''
  }
}
async function onFile(event, key) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  const valid = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'].includes(file.type)
    || /\.(png|jpe?g|webp|svg)$/i.test(file.name || '')
  if (!valid) {
    error.value = 'صيغة الملف غير مدعومة. استخدم PNG أو JPG أو WEBP أو SVG.'
    toast().danger(error.value)
    return
  }
  const { data, error: err } = await uploadsApi.upload(file)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const url = storedUrl(data?.path || data?.url || data?.data?.path || data?.data?.url || '')
  if (!url) {
    error.value = 'تعذر الحصول على رابط الأيقونة بعد الرفع.'
    toast().danger(error.value)
    return
  }
  form[key] = url
  toast().success('تم رفع الأيقونة')
}
function clearAll() {
  for (const item of actions) form[item.key] = ''
}
async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const profileActions = { ...(baseConfig.value.profileActions || {}) }
  for (const item of actions) {
    const key = item.key === 'rechargeAgent' ? 'recharge' : item.key
    profileActions[key] = form[item.key]
  }
  const body = { ...baseConfig.value, version: version.value + 1, profileActions }
  const { error: err } = await settingsApi.update({ app_nav_icons: JSON.stringify(body) })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = 'تم حفظ أيقونات قسم أنا. افتح التطبيق أو حدّث صفحة الملف الشخصي لرؤية التغييرات.'
  toast().success(success.value)
  await load()
}
onMounted(load)
</script>

<style scoped>
.profile-icons-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}
.profile-icon-card { border-radius: 14px; }
.icon-preview {
  width: 92px;
  height: 92px;
  border-radius: 16px;
  background: rgba(255,255,255,.06);
  border: 1px solid rgba(255,255,255,.1);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.icon-preview img { width: 72px; height: 72px; object-fit: contain; }
</style>
