<template>
  <div class="staff-identity-page">
    <PageHeader title="هوية الرتب الإدارية" subtitle="صمّم شارة وإطار كل رتبة، ثم أهدِ الشارة مباشرةً إلى حساب عبر ID." />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <div v-if="loading" class="loading">جارٍ تحميل هويات الرتب…</div>
    <template v-else>
      <div class="intro"><strong>كل رتبة لها هويتها الخاصة.</strong> ارفع صورة الشارة وصورة الإطار، أو استخدم رابطًا مباشرًا. الصور المتحركة GIF وWebP مدعومة، والإطار الثابت يحصل على حركة إضاءة ناعمة في المعاينة.</div>

      <section v-for="role in roles" :key="role.key" class="role-card" :style="{ '--role-accent': form[role.key].accentColor }">
        <header class="role-head">
          <div class="role-heading">
            <span class="role-symbol"><i class="bi bi-award-fill"></i></span>
            <div><h2>{{ role.label }}</h2><p>{{ role.description }}</p></div>
          </div>
          <label class="color-control">لون الرتبة <input v-model="form[role.key].accentColor" type="color" /></label>
        </header>

        <div class="role-body">
          <div class="assets">
            <div class="asset-panel badge-panel">
              <div class="asset-title"><i class="bi bi-patch-check-fill"></i> صورة الشارة</div>
              <div class="badge-preview" :class="{ empty: !form[role.key].badgeUrl }">
                <img v-if="form[role.key].badgeUrl" :src="resolveAsset(form[role.key].badgeUrl)" :alt="'شارة ' + role.label" />
                <span v-else>لا توجد صورة شارة</span>
              </div>
              <label class="upload-button">رفع صورة الشارة
                <input type="file" accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml" :disabled="uploadingKey === role.key + '.badgeUrl'" @change="uploadAsset(role.key, 'badgeUrl', $event)" />
              </label>
              <input v-model.trim="form[role.key].badgeUrl" class="url-input" placeholder="أو الصق رابط صورة الشارة https://…" />
            </div>

            <div class="asset-panel frame-panel">
              <div class="asset-title"><i class="bi bi-circle-square"></i> إطار الصورة الشخصية</div>
              <div class="frame-preview" :class="{ empty: !form[role.key].frameUrl }">
                <div class="avatar-placeholder"><i class="bi bi-person-fill"></i></div>
                <img v-if="form[role.key].frameUrl" class="frame-art" :src="resolveAsset(form[role.key].frameUrl)" :alt="'إطار ' + role.label" />
                <span v-else>ارفع إطارًا لمعاينته</span>
              </div>
              <label class="upload-button frame-upload">رفع الإطار
                <input type="file" accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml" :disabled="uploadingKey === role.key + '.frameUrl'" @change="uploadAsset(role.key, 'frameUrl', $event)" />
              </label>
              <input v-model.trim="form[role.key].frameUrl" class="url-input" placeholder="أو الصق رابط الإطار https://…" />
              <small class="hint">الإطار الثابت يظهر بحركة نبض وإضاءة ناعمة، والمتحرك يحتفظ بحركته الأصلية.</small>
            </div>
          </div>

          <div class="role-settings">
            <label class="field-label">اسم الرتبة الظاهر</label>
            <input v-model.trim="form[role.key].label" class="text-input" maxlength="32" :placeholder="role.label" />
            <div class="preview-strip">
              <span>معاينة الشارة كما تظهر في التطبيق</span>
              <div class="mini-identity">
                <img v-if="form[role.key].badgeUrl" class="mini-role-badge" :src="resolveAsset(form[role.key].badgeUrl)" :alt="'شارة ' + (form[role.key].label || role.label)" />
                <b v-else>{{ form[role.key].label || role.label }}</b>
              </div>
            </div>
          </div>
        </div>

        <div class="gift-panel">
          <div class="gift-title"><i class="bi bi-gift-fill"></i><div><strong>إهداء شارة هذه الرتبة</strong><small>تُمنح الشارة للحساب المحدد وتصل إليه رسالة إشعار.</small></div></div>
          <div class="gift-controls">
            <label class="gift-id-field"><span>معرّف المستخدم ID</span><input v-model.trim="recipients[role.key]" class="text-input" inputmode="text" placeholder="أدخل ID الحساب" /></label>
            <label class="gift-duration"><span>مدة الشارة</span><select v-model.number="durations[role.key]" class="text-input"><option :value="0">دائمة</option><option :value="1">يوم واحد</option><option :value="7">7 أيام</option><option :value="30">30 يومًا</option><option :value="90">90 يومًا</option><option :value="365">سنة</option></select></label>
            <button class="gift-button" type="button" :disabled="busyKey === role.key || saving || !form[role.key].badgeUrl" @click="giftRole(role.key)">{{ busyKey === role.key ? 'جارٍ الإهداء…' : 'إهداء الشارة عبر ID' }}</button>
          </div>
          <small v-if="!form[role.key].badgeUrl" class="gift-hint">ارفع صورة الشارة واحفظ الإعدادات أولًا لتفعيل الإهداء.</small>
          <small v-else-if="form[role.key].badgeId" class="linked-state"><i class="bi bi-check-circle-fill"></i> الشارة مرتبطة بنظام المنح الفعلي.</small>
        </div>
      </section>

      <div class="save-bar">
        <span v-if="uploadingKey">جارٍ رفع الملف…</span>
        <span v-else>يتم حفظ الصور والروابط لكل رتبة بشكل مستقل.</span>
        <button type="button" class="save-button" :disabled="saving || !!uploadingKey" @click="save">{{ saving ? 'جارٍ الحفظ والتحقق…' : 'حفظ كل هويات الرتب' }}</button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { staffIdentityApi, uploadsApi, medalsApi } from '@/api'
import { resolveAsset } from '@/utils/assets'

const roles = [
  { key: 'super', label: 'السوبر أدمن', description: 'هوية السوبر أدمن' },
  { key: 'admin', label: 'الأدمن', description: 'هوية الأدمن' },
  { key: 'manager', label: 'المدير / المانجر', description: 'هوية المدير' },
  { key: 'rechargeAgent', label: 'وكيل الشحن', description: 'هوية وكيل الشحن' },
  { key: 'agencyLeader', label: 'قائد الوكالة', description: 'هوية قائد الوكالة' },
  { key: 'host', label: 'المضيف', description: 'هوية المضيف' },
]
const blankRole = (label, accentColor) => ({
  label, accentColor, badgeUrl: '', frameUrl: '', badgeId: '',
  nameplateUrl: '', messageCardUrl: '', entryEffectUrl: '',
})
const form = reactive({
  super: blankRole('Super Admin', '#F5C542'),
  admin: blankRole('Admin', '#60A5FA'),
  manager: blankRole('Manager', '#34D399'),
  rechargeAgent: blankRole('وكيل شحن', '#A78BFA'),
  agencyLeader: blankRole('قائد الوكالة', '#F472B6'),
  host: blankRole('مضيف', '#FB923C'),
})
const recipients = reactive(Object.fromEntries(roles.map(r => [r.key, ''])))
const durations = reactive(Object.fromEntries(roles.map(r => [r.key, 0])))
const loading = ref(true)
const saving = ref(false)
const busyKey = ref('')
const uploadingKey = ref('')
const error = ref('')
const success = ref('')

function unwrap(result) {
  if (result?.error) throw new Error(result.error.message || 'تعذر الاتصال بالخادم.')
  return result?.data?.data ?? result?.data ?? result ?? {}
}
function imageIsAnimated(url) {
  return /\.(gif|webp)(?:[?#].*)?$/i.test(String(url || ''))
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = unwrap(await staffIdentityApi.get())
    for (const role of roles) Object.assign(form[role.key], blankRole(role.label, form[role.key].accentColor), data?.[role.key] || {})
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر تحميل هويات الرتب.'
  } finally { loading.value = false }
}
async function uploadAsset(roleKey, assetKey, event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { error.value = 'اختر ملف صورة صالحًا.'; event.target.value = ''; return }
  if (file.size > 15 * 1024 * 1024) { error.value = 'حجم الصورة يجب ألا يتجاوز 15 ميغابايت.'; event.target.value = ''; return }
  const key = roleKey + '.' + assetKey
  uploadingKey.value = key
  error.value = ''
  success.value = ''
  try {
    const payload = unwrap(await uploadsApi.upload(file))
    if (!payload.url && !payload.path) throw new Error('لم يُرجع الخادم رابط الملف المرفوع.')
    form[roleKey][assetKey] = payload.url || payload.path
    success.value = 'تم رفع الملف. احفظ الإعدادات لتثبيته.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر رفع الملف.'
  } finally { uploadingKey.value = ''; event.target.value = '' }
}
async function syncBadge(roleKey) {
  const item = form[roleKey]
  if (!String(item.badgeUrl || '').trim()) throw new Error('ارفع صورة الشارة أولًا.')
  const payload = {
    name: String(item.label || roles.find(r => r.key === roleKey)?.label || roleKey).trim().slice(0, 80),
    description: 'شارة رتبة إدارية — ' + roleKey,
    imageUrl: String(item.badgeUrl).trim(),
    category: 'display_rank',
    awardType: 'manual',
    isActive: true,
    isAnimated: imageIsAnimated(item.badgeUrl),
    sortOrder: 0,
  }
  if (item.badgeId) {
    const saved = unwrap(await medalsApi.update(item.badgeId, payload))
    return saved?.id || item.badgeId
  }
  const created = unwrap(await medalsApi.create(payload))
  const id = created?.id || created?.badge?.id
  if (!id) throw new Error('تم إنشاء الشارة لكن لم يصل معرّفها من الخادم.')
  item.badgeId = id
  return id
}
async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    // Sync each configured role with the real badge catalogue used by ID gifting.
    for (const role of roles) {
      if (String(form[role.key].badgeUrl || '').trim()) {
        form[role.key].badgeId = await syncBadge(role.key)
      }
    }
    const payload = {}
    for (const role of roles) {
      const item = form[role.key]
      payload[role.key] = {
        label: item.label,
        accentColor: item.accentColor,
        badgeUrl: item.badgeUrl || '',
        frameUrl: item.frameUrl || '',
        badgeId: item.badgeId || '',
        nameplateUrl: item.nameplateUrl || '',
        messageCardUrl: item.messageCardUrl || '',
        entryEffectUrl: item.entryEffectUrl || '',
      }
    }
    unwrap(await staffIdentityApi.update(payload))
    const verify = unwrap(await staffIdentityApi.get())
    for (const role of roles) Object.assign(form[role.key], verify?.[role.key] || {})
    success.value = 'تم حفظ هويات الرتب والتحقق من قراءتها مجددًا من الخادم.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ هويات الرتب.'
  } finally { saving.value = false }
}
async function giftRole(roleKey) {
  const item = form[roleKey]
  const userId = String(recipients[roleKey] || '').trim()
  if (!userId) { error.value = 'أدخل ID المستخدم أولًا.'; return }
  if (!item.badgeUrl) { error.value = 'ارفع صورة الشارة أولًا.'; return }
  busyKey.value = roleKey
  error.value = ''
  success.value = ''
  try {
    if (!item.badgeId) await save()
    if (!item.badgeId) throw new Error('لم يتم تجهيز سجل الشارة. احفظ الهوية ثم أعد المحاولة.')
    const result = unwrap(await medalsApi.grant(item.badgeId, { userId, durationDays: Number(durations[roleKey]) || 0 }))
    const target = result?.user?.displayName || result?.user?.username || result?.user?.publicId || userId
    success.value = 'تم إهداء شارة «' + item.label + '» إلى ' + target + ' بنجاح.'
    recipients[roleKey] = ''
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر إهداء الشارة. تحقق من ID المستخدم.'
  } finally { busyKey.value = '' }
}
onMounted(load)
</script>

<style scoped>
.staff-identity-page{max-width:1320px;margin:0 auto;color:#182235}
.intro{padding:16px 18px;border:1px solid #b9d9ff;border-right:5px solid #2477d4;background:linear-gradient(110deg,#eff7ff,#fff);color:#173a61;border-radius:14px;margin:0 0 20px;line-height:1.8}
.role-card{--role-accent:#2477d4;background:#fff;border:1px solid #d7e0ec;border-top:5px solid var(--role-accent);border-radius:18px;padding:20px;margin:0 0 20px;box-shadow:0 9px 28px rgba(27,48,78,.08)}
.role-head{display:flex;justify-content:space-between;align-items:center;gap:16px;padding-bottom:15px;border-bottom:1px solid #e4eaf2}
.role-heading{display:flex;align-items:center;gap:12px}.role-heading h2{margin:0;color:#15243a;font-size:1.18rem;font-weight:900}.role-heading p{margin:4px 0 0;color:#64748b;font-size:.88rem}
.role-symbol{width:46px;height:46px;display:grid;place-items:center;border-radius:14px;background:var(--role-accent);color:#fff;font-size:1.3rem;box-shadow:0 5px 14px color-mix(in srgb,var(--role-accent) 30%,transparent)}
.color-control{display:flex;align-items:center;gap:9px;color:#334155;font-weight:800;font-size:.88rem}.color-control input{width:46px;height:38px;border:1px solid #cbd5e1;border-radius:8px;background:#fff;padding:3px;cursor:pointer}
.role-body{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(240px,.8fr);gap:18px;padding:18px 0}
.assets{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}
.asset-panel{border:1px solid #dbe4ef;border-radius:15px;padding:14px;background:#f8fafc;min-width:0}
.asset-title{font-weight:900;color:#1e293b;margin-bottom:12px;display:flex;align-items:center;gap:8px}.asset-title i{color:var(--role-accent);font-size:1.1rem}
.badge-preview,.frame-preview{height:150px;border-radius:12px;background:linear-gradient(145deg,#e8eef7,#f8fbff);display:flex;align-items:center;justify-content:center;position:relative;overflow:hidden;margin-bottom:12px}
.badge-preview img{width:112px;height:112px;object-fit:contain;filter:drop-shadow(0 5px 8px #0002)}
.badge-preview.empty,.frame-preview.empty{color:#8795a8;font-size:.85rem}
.frame-preview{background:radial-gradient(circle,#fff 0%,#e6edf7 75%)}.avatar-placeholder{width:76px;height:76px;border-radius:50%;background:#b9c8dc;color:#fff;display:grid;place-items:center;font-size:2.5rem}
.frame-art{position:absolute;width:128px;height:128px;object-fit:contain;animation:framePulse 2.8s ease-in-out infinite;filter:drop-shadow(0 0 5px color-mix(in srgb,var(--role-accent) 60%,transparent))}
@keyframes framePulse{0%,100%{transform:scale(1);filter:drop-shadow(0 0 3px color-mix(in srgb,var(--role-accent) 45%,transparent))}50%{transform:scale(1.055);filter:drop-shadow(0 0 12px color-mix(in srgb,var(--role-accent) 90%,transparent))}}
.upload-button{min-height:40px;border-radius:9px;background:#1e6bc1;color:#fff;display:flex;align-items:center;justify-content:center;font-weight:800;cursor:pointer;margin-bottom:9px}.frame-upload{background:#7044b8}.upload-button input{display:none}
.url-input,.text-input{width:100%;min-width:0;min-height:42px;border:1px solid #cbd5e1;border-radius:9px;background:#fff;color:#172033;padding:9px 11px;outline:none}.url-input:focus,.text-input:focus{border-color:#3985d6;box-shadow:0 0 0 3px #3985d622}
.hint{display:block;color:#64748b;line-height:1.6;margin-top:8px}
.role-settings{padding:14px;border:1px solid #dbe4ef;border-radius:15px;background:#fbfdff}
.field-label{display:block;color:#334155;font-weight:900;margin-bottom:8px}.preview-strip{margin-top:18px;padding:12px;background:#edf4fc;border-radius:12px}.preview-strip>span{display:block;color:#64748b;font-size:.78rem;font-weight:800;margin-bottom:10px}.mini-identity{display:flex;align-items:center;min-height:34px;color:#15243a}.mini-role-badge{width:150px;height:34px;object-fit:contain;display:block}
.gift-panel{border:1px solid #b9d8f7;border-radius:15px;background:linear-gradient(110deg,#eef7ff,#f8fbff);padding:15px}
.gift-title{display:flex;align-items:center;gap:11px;color:#174b7d;margin-bottom:14px}.gift-title>i{font-size:1.5rem;color:#1d75c9}.gift-title strong,.gift-title small{display:block}.gift-title strong{font-size:1rem;font-weight:900}.gift-title small{color:#55718e;margin-top:3px}
.gift-controls{display:grid;grid-template-columns:minmax(0,1fr) 150px auto;gap:10px;align-items:end}.gift-controls label span{display:block;font-size:.82rem;font-weight:900;color:#294766;margin-bottom:6px}
.gift-button{min-height:42px;padding:0 16px;border:0;border-radius:9px;background:linear-gradient(100deg,#1266bd,#268cdb);color:#fff;font-weight:900;white-space:nowrap}.gift-button:disabled{opacity:.48;cursor:not-allowed}
.gift-hint,.linked-state{display:block;margin-top:10px;font-size:.82rem;color:#64748b}.linked-state{color:#147a49;font-weight:800}.linked-state i{margin-inline-end:5px}
.save-bar{position:sticky;bottom:10px;z-index:4;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:14px 16px;background:#172a43;color:#eaf3ff;border:1px solid #365574;border-radius:15px;box-shadow:0 12px 30px #10233b33}
.save-button{min-height:44px;padding:0 22px;border:0;border-radius:10px;background:linear-gradient(100deg,#0e8b58,#22b477);color:#fff;font-weight:900}.save-button:disabled{opacity:.5}
.loading{padding:30px;text-align:center;color:#64748b}
@media(max-width:850px){.role-body{grid-template-columns:1fr}.gift-controls{grid-template-columns:1fr 140px}.gift-button{grid-column:1/-1}}
@media(max-width:560px){.role-card{padding:13px}.role-head,.save-bar{align-items:stretch;flex-direction:column}.assets{grid-template-columns:1fr}.gift-controls{grid-template-columns:1fr}.gift-button{grid-column:auto}}
</style>
