<template>
  <div class="staff-identity-page">
    <PageHeader title="هوية الرتب الإدارية" subtitle="ارفع إطار وشارة وبطاقة الاسم وبطاقة الرسائل والدخولية الخاصة بكل رتبة. تُحفظ على الخادم وتُعرض لموظفي الرتبة فقط، ولا تُضاف إلى المتجر." />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <div v-if="loading" class="identity-loading">جارٍ تحميل إعدادات الهوية المحفوظة…</div>
    <template v-else>
      <div class="identity-note"><strong>تنبيه:</strong> هذه أصول إدارية خاصة وليست عناصر متجر. اختر صورة لكل رتبة واضغط «حفظ كل الإعدادات»؛ بعد تحديث الصفحة تُحمّل القيم المحفوظة من الخادم.</div>
      <section v-for="role in roles" :key="role.key" class="identity-card">
        <header class="identity-header">
          <div><span class="identity-dot" :style="{ background: form[role.key].accentColor }"></span><h2>{{ role.label }}</h2><p>{{ role.description }}</p></div>
          <label class="color-picker">لون الهوية <input v-model="form[role.key].accentColor" type="color" /></label>
        </header>
        <label class="form-label">اسم الرتبة الظاهر</label>
        <input v-model.trim="form[role.key].label" class="form-control mb-3" maxlength="32" :placeholder="role.label" />
        <div class="asset-grid">
          <div v-for="asset in assets" :key="asset.key" class="asset-field">
            <label class="form-label">{{ asset.label }}</label>
            <div v-if="form[role.key][asset.key]" class="asset-preview">
              <img :src="resolveAsset(form[role.key][asset.key])" :alt="asset.label" />
              <button type="button" class="btn btn-sm btn-outline-danger" @click="form[role.key][asset.key] = ''">إزالة</button>
            </div>
            <div v-else class="asset-empty">لم تُرفع صورة بعد</div>
            <input class="form-control" type="file" accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml" :disabled="uploadingKey === role.key + '.' + asset.key" @change="uploadAsset(role.key, asset.key, $event)" />
            <small v-if="uploadingKey === role.key + '.' + asset.key" class="text-muted">جارٍ رفع الملف…</small>
            <input v-model.trim="form[role.key][asset.key]" class="form-control form-control-sm mt-2" placeholder="أو ضع رابط الصورة" />
          </div>
        </div>
      </section>
      <div class="identity-actions">
        <span class="text-muted">آخر حفظ ناجح يبقى محفوظًا بعد تحديث الصفحة.</span>
        <button type="button" class="btn btn-aurora" :disabled="saving || !!uploadingKey" @click="save">{{ saving ? 'جارٍ الحفظ…' : 'حفظ كل الإعدادات' }}</button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { staffIdentityApi, uploadsApi } from '@/api'
import { resolveAsset } from '@/utils/assets'

const roles = [
  { key: 'super', label: 'السوبر أدمن', description: 'هوية حصرية للسوبر أدمن' },
  { key: 'admin', label: 'الأدمن', description: 'هوية حصرية للأدمن' },
  { key: 'manager', label: 'المانجر', description: 'هوية حصرية للمانجر' },
]
const assets = [
  { key: 'frameUrl', label: 'إطار الصورة الشخصية' },
  { key: 'badgeUrl', label: 'شارة الرتبة' },
  { key: 'nameplateUrl', label: 'بطاقة الاسم' },
  { key: 'messageCardUrl', label: 'بطاقة الرسائل' },
  { key: 'entryEffectUrl', label: 'الدخولية / مؤثر الدخول' },
]
const blankRole = (label, accentColor) => ({ label, accentColor, badgeUrl: '', frameUrl: '', nameplateUrl: '', messageCardUrl: '', entryEffectUrl: '' })
const form = reactive({
  super: blankRole('Super Admin', '#D8B35B'),
  admin: blankRole('Admin', '#9AA8C7'),
  manager: blankRole('Manager', '#37A889'),
})
const loading = ref(true)
const saving = ref(false)
const uploadingKey = ref('')
const error = ref('')
const success = ref('')

function unwrap(result) {
  if (result?.error) throw new Error(result.error.message || 'تعذر الاتصال بالخادم.')
  return result?.data?.data ?? result?.data ?? result ?? {}
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = unwrap(await staffIdentityApi.get())
    for (const role of roles) Object.assign(form[role.key], blankRole(role.label, form[role.key].accentColor), data?.[role.key] || {})
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر تحميل هوية الرتب.'
  } finally { loading.value = false }
}
async function uploadAsset(roleKey, assetKey, event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { error.value = 'اختر ملف صورة صالحًا.'; event.target.value = ''; return }
  const key = roleKey + '.' + assetKey
  uploadingKey.value = key
  error.value = ''
  success.value = ''
  try {
    const payload = unwrap(await uploadsApi.upload(file))
    if (!payload.url) throw new Error('لم يُرجع الخادم رابط الملف المرفوع.')
    form[roleKey][assetKey] = payload.url
    success.value = 'تم رفع الملف. اضغط «حفظ كل الإعدادات» لتثبيته لهذه الرتبة.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر رفع الملف.'
  } finally { uploadingKey.value = ''; event.target.value = '' }
}
async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const payload = {}
    for (const role of roles) {
      const item = form[role.key]
      payload[role.key] = { label: item.label, accentColor: item.accentColor, ...Object.fromEntries(assets.map(a => [a.key, item[a.key] || ''])) }
    }
    const saved = unwrap(await staffIdentityApi.update(payload))
    for (const role of roles) Object.assign(form[role.key], saved?.[role.key] || payload[role.key])
    // Read back from server to verify persistence rather than trusting the save response.
    const verify = unwrap(await staffIdentityApi.get())
    for (const role of roles) Object.assign(form[role.key], verify?.[role.key] || {})
    success.value = 'تم حفظ إعدادات الرتب والتحقق من قراءتها مجددًا من الخادم.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ إعدادات الهوية.'
  } finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.staff-identity-page{max-width:1280px;margin:0 auto}.identity-note{padding:14px 16px;border:1px solid #d8c99b;background:#fff9e8;color:#6f5718;border-radius:14px;margin-bottom:18px}.identity-card{background:var(--surface, #fff);border:1px solid var(--border, #e4e7ec);border-radius:18px;padding:20px;margin-bottom:18px;box-shadow:0 8px 28px rgba(25,32,55,.05)}.identity-header{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;margin-bottom:18px}.identity-header h2{display:inline-block;font-size:1.15rem;margin:0 0 4px 9px}.identity-header p{color:#778094;margin:4px 0 0}.identity-dot{display:inline-block;width:12px;height:12px;border-radius:50%}.color-picker{display:flex;align-items:center;gap:8px;font-size:.85rem}.color-picker input{width:42px;height:34px;border:0;background:transparent}.asset-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px}.asset-field{padding:12px;border:1px solid var(--border, #e4e7ec);border-radius:14px;min-width:0}.asset-preview{height:110px;display:flex;align-items:center;justify-content:center;gap:10px;background:#f6f7fb;border-radius:10px;margin-bottom:10px;padding:8px}.asset-preview img{max-width:100%;max-height:92px;object-fit:contain}.asset-empty{height:110px;display:grid;place-items:center;background:#f6f7fb;color:#9299a8;border-radius:10px;margin-bottom:10px;font-size:.85rem}.identity-actions{position:sticky;bottom:10px;display:flex;justify-content:space-between;align-items:center;gap:12px;padding:14px;background:rgba(255,255,255,.96);border:1px solid #e4e7ec;border-radius:16px;box-shadow:0 8px 28px rgba(25,32,55,.12);z-index:3}.identity-loading{padding:28px;text-align:center;color:#778094}@media(max-width:640px){.identity-header,.identity-actions{flex-direction:column;align-items:stretch}.identity-card{padding:14px}}
</style>
