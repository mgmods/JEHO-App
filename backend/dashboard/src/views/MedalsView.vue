<template>
  <div class="medals-page" dir="rtl">
    <PageHeader title="إدارة المداليات" subtitle="إهداء المداليات للحسابات بشكل فردي أو لجميع مستخدمي JEHO." />
    <div v-if="error" class="alert alert-danger">{{ error }}</div>
    <div v-if="success" class="alert alert-success">{{ success }}</div>

    <section class="panel create-panel">
      <div class="section-heading">
        <div class="heading-icon"><i class="bi bi-award-fill"></i></div>
        <div><h2>إضافة مدالية</h2><p>ارفع صورة المدالية أو استخدم رابط صورة مباشر.</p></div>
      </div>
      <form class="form-grid" @submit.prevent="createMedal">
        <label>اسم المدالية<input v-model.trim="form.name" class="form-control" maxlength="80" required placeholder="مثلاً: مدالية التميز" /></label>
        <label>الوصف<input v-model.trim="form.description" class="form-control" maxlength="500" placeholder="وصف مختصر يظهر للإدارة" /></label>
        <label class="wide">رابط الصورة<input v-model.trim="form.imageUrl" class="form-control" type="url" required placeholder="https://..." /></label>
        <label class="upload-box wide">
          <span><i class="bi bi-cloud-arrow-up"></i> {{ uploading ? 'جارٍ رفع الصورة…' : 'رفع صورة المدالية' }}</span>
          <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" :disabled="uploading" @change="uploadImage" />
        </label>
        <div v-if="form.imageUrl" class="medal-preview"><img :src="form.imageUrl" alt="معاينة المدالية" @error="previewBroken = true" @load="previewBroken = false" /><span v-if="previewBroken">تعذر تحميل الصورة</span></div>
        <label>الترتيب<input v-model.number="form.sortOrder" class="form-control" type="number" min="0" /></label>
        <label class="check-row"><input v-model="form.isAnimated" type="checkbox" /> المدالية متحركة</label>
        <button class="btn btn-success wide submit-btn" type="submit" :disabled="saving || uploading">{{ saving ? 'جارٍ الحفظ…' : 'حفظ المدالية' }}</button>
      </form>
    </section>

    <section class="section-heading list-heading">
      <div><h2>المداليات الموجودة</h2><p>اختر أي مدالية سابقة وأهدها لمستخدم محدد أو لجميع الحسابات.</p></div>
      <button class="btn btn-outline-light" type="button" :disabled="loading" @click="load">{{ loading ? 'جارٍ التحديث…' : 'تحديث القائمة' }}</button>
    </section>

    <div v-if="loading && !medals.length" class="panel loading">جارٍ تحميل المداليات…</div>
    <div v-else-if="!medals.length" class="panel empty">لا توجد مداليات بعد. أضف أول مدالية من النموذج أعلاه.</div>
    <div v-else class="medal-grid">
      <article v-for="medal in medals" :key="medal.id" class="medal-card" :class="{ inactive: !medal.isActive }">
        <div class="card-top">
          <div class="medal-art"><img :src="medal.imageUrl" :alt="medal.name" loading="lazy" /></div>
          <span class="status" :class="medal.isActive ? 'on' : 'off'">{{ medal.isActive ? 'متاحة' : 'مخفية' }}</span>
        </div>
        <h3>{{ medal.name }}</h3>
        <p class="description">{{ medal.description || 'مدالية مميزة لحساب المستخدم' }}</p>
        <div class="gift-box">
          <label>رقم المستخدم أو ID العام<input v-model.trim="recipient[medal.id]" class="form-control" placeholder="أدخل ID المستخدم" /></label>
          <label>مدة الإهداء
            <select v-model.number="duration[medal.id]" class="form-select">
              <option :value="0">دائمة</option><option :value="1">يوم واحد</option><option :value="7">7 أيام</option><option :value="30">30 يوماً</option><option :value="90">90 يوماً</option><option :value="365">سنة</option>
            </select>
          </label>
          <button class="btn btn-success w-100" type="button" :disabled="busyId === medal.id" @click="giftOne(medal)"><i class="bi bi-gift"></i> {{ busyId === medal.id ? 'جارٍ الإهداء…' : 'إهداء لمستخدم' }}</button>
          <button class="btn btn-outline-success w-100" type="button" :disabled="busyId === medal.id" @click="giftEveryone(medal)"><i class="bi bi-people-fill"></i> إهداء لجميع الحسابات</button>
        </div>
        <div class="card-actions">
          <button class="btn btn-sm btn-outline-light" type="button" :disabled="busyId === medal.id" @click="toggleActive(medal)">{{ medal.isActive ? 'إخفاء من القائمة' : 'إظهار في القائمة' }}</button>
          <button class="btn btn-sm btn-outline-danger" type="button" :disabled="busyId === medal.id" @click="deleteMedal(medal)">حذف</button>
        </div>
      </article>
    </div>
    <p class="footnote"><i class="bi bi-shield-check"></i> الإهداء يُحفظ في قاعدة البيانات، ويصل للمستخدم إشعار داخل التطبيق. الإهداء الدائم لا ينتهي تلقائياً.</p>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { medalsApi, uploadsApi } from '@/api'

const medals = ref([])
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const error = ref('')
const success = ref('')
const busyId = ref('')
const previewBroken = ref(false)
const recipient = reactive({})
const duration = reactive({})
const form = reactive({ name: '', description: '', imageUrl: '', sortOrder: 0, isAnimated: false })

function unwrap(data) { return data?.data?.data ?? data?.data ?? data ?? {} }
async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data, error: err } = await medalsApi.list()
    if (err) throw new Error(err.message || 'تعذر تحميل المداليات')
    const value = unwrap(data)
    medals.value = Array.isArray(value) ? value : (Array.isArray(value.items) ? value.items : [])
    for (const item of medals.value) {
      if (duration[item.id] === undefined) duration[item.id] = 0
      if (recipient[item.id] === undefined) recipient[item.id] = ''
    }
  } catch (e) { error.value = e?.message || 'تعذر تحميل المداليات' }
  finally { loading.value = false }
}
async function uploadImage(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploading.value = true; error.value = ''
  try {
    const { data, error: err } = await uploadsApi.upload(file)
    if (err) throw new Error(err.message || 'فشل رفع الصورة')
    const payload = unwrap(data)
    const url = payload.url || payload.path
    if (!url) throw new Error('تم الرفع دون رابط للصورة')
    form.imageUrl = /^https?:\/\//i.test(url) ? url : new URL(url, window.location.origin).toString()
    previewBroken.value = false
  } catch (e) { error.value = e?.message || 'فشل رفع الصورة' }
  finally { uploading.value = false; event.target.value = '' }
}
async function createMedal() {
  saving.value = true; error.value = ''; success.value = ''
  try {
    const { data, error: err } = await medalsApi.create({
      name: form.name, description: form.description, imageUrl: form.imageUrl,
      category: 'general', awardType: 'manual', isActive: true,
      isAnimated: form.isAnimated, sortOrder: Number(form.sortOrder) || 0,
    })
    if (err) throw new Error(err.message || 'تعذر حفظ المدالية')
    success.value = 'تم حفظ المدالية بنجاح.'
    Object.assign(form, { name: '', description: '', imageUrl: '', sortOrder: 0, isAnimated: false })
    await load()
  } catch (e) { error.value = e?.message || 'تعذر حفظ المدالية' }
  finally { saving.value = false }
}
async function giftOne(medal) {
  const userId = String(recipient[medal.id] || '').trim()
  if (!userId) { error.value = 'أدخل ID المستخدم أولاً.'; return }
  busyId.value = medal.id; error.value = ''; success.value = ''
  try {
    const { data, error: err } = await medalsApi.grant(medal.id, { userId, durationDays: Number(duration[medal.id]) || 0 })
    if (err) throw new Error(err.message || 'تعذر إهداء المدالية')
    const payload = unwrap(data)
    success.value = `تم إهداء «${medal.name}» إلى ${payload.user?.displayName || payload.user?.username || userId}.`
    recipient[medal.id] = ''
  } catch (e) { error.value = e?.message || 'تعذر إهداء المدالية' }
  finally { busyId.value = '' }
}
async function giftEveryone(medal) {
  if (!window.confirm(`تأكيد إهداء «${medal.name}» لجميع الحسابات؟ الحساب الذي يملك المدالية الفعالة لن يحصل على نسخة مكررة.`)) return
  busyId.value = medal.id; error.value = ''; success.value = ''
  try {
    const { data, error: err } = await medalsApi.grantAll(medal.id, { durationDays: Number(duration[medal.id]) || 0 })
    if (err) throw new Error(err.message || 'تعذر إهداء المدالية لجميع الحسابات')
    const payload = unwrap(data)
    success.value = `اكتملت العملية: أُهديت المدالية إلى ${payload.granted ?? 0} حساب، وتُخطّي ${payload.skipped ?? 0} حساباً يملكها بالفعل.`
  } catch (e) { error.value = e?.message || 'تعذر إهداء المدالية لجميع الحسابات' }
  finally { busyId.value = '' }
}
async function toggleActive(medal) {
  busyId.value = medal.id; error.value = ''; success.value = ''
  try {
    const { error: err } = await medalsApi.update(medal.id, { isActive: !medal.isActive })
    if (err) throw new Error(err.message || 'تعذر تحديث حالة المدالية')
    success.value = 'تم تحديث حالة المدالية.'
    await load()
  } catch (e) { error.value = e?.message || 'تعذر تحديث حالة المدالية' }
  finally { busyId.value = '' }
}
async function deleteMedal(medal) {
  if (!window.confirm(`حذف المدالية «${medal.name}»؟ سيتم حذف ارتباطها بحسابات المستخدمين أيضاً.`)) return
  busyId.value = medal.id; error.value = ''; success.value = ''
  try {
    const { error: err } = await medalsApi.remove(medal.id)
    if (err) throw new Error(err.message || 'تعذر حذف المدالية')
    success.value = 'تم حذف المدالية.'
    await load()
  } catch (e) { error.value = e?.message || 'تعذر حذف المدالية' }
  finally { busyId.value = '' }
}
onMounted(load)
</script>

<style scoped>
.medals-page { color: #f7f8fc; }
.panel { background: linear-gradient(145deg, rgba(30,36,52,.96), rgba(18,23,36,.96)); border: 1px solid rgba(255,255,255,.09); border-radius: 20px; padding: 22px; margin-bottom: 26px; }
.section-heading { display:flex; align-items:center; gap:14px; margin: 22px 0 16px; }
.section-heading h2 { font-size: 1.15rem; font-weight: 800; margin:0 0 5px; }
.section-heading p { color: #a8b1c5; margin:0; font-size:.9rem; }
.heading-icon { width:48px;height:48px;border-radius:15px;display:grid;place-items:center;background:linear-gradient(135deg,#20b26b,#117b4b);font-size:1.35rem; }
.form-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;align-items:end; }
.form-grid label,.gift-box label { display:flex;flex-direction:column;gap:7px;font-size:.88rem;font-weight:700;color:#dce4f2; }
.form-grid .wide { grid-column:1/-1; }
.form-control,.form-select { background:#111827!important;color:#f8fafc!important;border-color:#3b465a!important; }
.upload-box { border:1px dashed #438e6c;border-radius:12px;padding:14px;cursor:pointer;display:flex;align-items:center;justify-content:center;color:#7ce2ad!important; }
.upload-box input { max-width:100%;margin-top:8px; }
.medal-preview { width:100px;height:100px;border-radius:22px;background:#0e1421;border:1px solid #3c4a60;display:grid;place-items:center;overflow:hidden;padding:7px; }
.medal-preview img { width:100%;height:100%;object-fit:contain; }
.check-row { flex-direction:row!important;align-items:center; }
.submit-btn { min-height:44px;font-weight:800; }
.list-heading { justify-content:space-between;align-items:center; }
.medal-grid { display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:18px; }
.medal-card { border-radius:20px;padding:17px;background:linear-gradient(155deg,#222b40,#141b2b);border:1px solid rgba(255,255,255,.1);box-shadow:0 12px 28px rgba(0,0,0,.15); }
.medal-card.inactive { opacity:.72; }
.card-top { display:flex;justify-content:space-between;align-items:start; }
.medal-art { width:92px;height:92px;border-radius:25px;padding:9px;background:radial-gradient(circle at 30% 20%,#485b73,#111827);box-shadow:0 0 0 1px #60738b; }
.medal-art img { width:100%;height:100%;object-fit:contain; }
.status { border-radius:999px;padding:5px 10px;font-size:.75rem;font-weight:800; }
.status.on { color:#7ce2ad;background:#12462f; }.status.off { color:#f9a8a8;background:#51252b; }
.medal-card h3 { font-size:1.05rem;font-weight:800;margin:14px 0 6px; }
.description { min-height:38px;color:#aeb8ca;font-size:.84rem; }
.gift-box { border-top:1px solid rgba(255,255,255,.1);margin-top:14px;padding-top:14px;display:grid;gap:11px; }
.card-actions { display:flex;justify-content:space-between;gap:8px;margin-top:14px; }
.empty,.loading { text-align:center;color:#bdc7d8; }
.footnote { color:#9ba8bd;font-size:.85rem;margin:20px 0; }
.footnote i { color:#69d49b; }
@media(max-width:650px) { .form-grid { grid-template-columns:1fr; }.form-grid .wide { grid-column:auto; }.list-heading { align-items:flex-start;gap:12px;flex-direction:column; } }
</style>
