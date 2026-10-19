<template>
  <div class="container-fluid">
    <PageHeader title="الشارات" subtitle="إدارة كتالوج الشارات ومنحها للمستخدمين" />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <div class="glass p-3 mb-3">
      <h5 class="mb-3">{{ editingId ? 'تعديل شارة' : 'إضافة شارة جديدة' }}</h5>
      <form class="row g-3" @submit.prevent="save">
        <div class="col-md-4"><label class="form-label">اسم الشارة</label><input v-model.trim="form.name" class="form-control" maxlength="80" required /></div>
        <div class="col-md-5"><label class="form-label">رابط صورة الشارة</label><input v-model.trim="form.imageUrl" class="form-control" maxlength="512" placeholder="https://... أو ارفع صورة" required /></div>
        <div class="col-md-3"><label class="form-label">رفع صورة من الجهاز</label><input class="form-control" type="file" accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml" :disabled="uploadingImage" @change="uploadImage" /><small v-if="uploadingImage" class="text-muted">جارٍ رفع الصورة…</small></div>
        <div class="col-md-6"><label class="form-label">الوصف</label><input v-model.trim="form.description" class="form-control" maxlength="500" /></div>
        <div class="col-md-2"><label class="form-label">الفئة</label><input v-model.trim="form.category" class="form-control" /></div>
        <div class="col-md-2"><label class="form-label">نوع المنح</label><select v-model="form.awardType" class="form-select"><option value="manual">يدوي</option><option value="achievement">إنجاز تلقائي</option></select></div>
        <template v-if="form.awardType === 'achievement'">
          <div class="col-md-4"><label class="form-label">شرط الإنجاز</label><select v-model="form.achievementKey" class="form-select" required><option :value="null" disabled>اختر الشرط</option><option value="level">المستوى</option><option value="experience">الخبرة</option><option value="followers">عدد المتابعين</option><option value="recharge">إجمالي الشحن التراكمي</option><option value="support">إجمالي الدعم المرسل للآخرين</option><option value="received">إجمالي الهدايا المستلمة</option></select></div>
          <div class="col-md-3"><label class="form-label">الحد المطلوب</label><input v-model.number="form.achievementThreshold" type="number" min="1" step="1" class="form-control" required /></div>
        </template>
        <div class="col-md-2"><label class="form-label">الترتيب</label><input v-model.number="form.sortOrder" type="number" class="form-control" /></div>
        <div class="col-md-2 d-flex align-items-end gap-3"><label class="form-check"><input v-model="form.isActive" class="form-check-input" type="checkbox" /> مفعلة</label><label class="form-check"><input v-model="form.isAnimated" class="form-check-input" type="checkbox" /> متحركة</label></div>
        <div class="col-12 d-flex gap-2"><button class="btn btn-aurora" :disabled="saving">{{ saving ? 'جارٍ الحفظ…' : 'حفظ الشارة' }}</button><button v-if="editingId" class="btn btn-outline-secondary" type="button" @click="reset">إلغاء التعديل</button></div>
      </form>
    </div>
    <div class="glass p-3 mb-3">
      <h5 class="mb-3">منح أو سحب شارة</h5>
      <div class="row g-2 align-items-end">
        <div class="col-md-5"><label class="form-label">الشارة</label><select v-model="grant.badgeId" class="form-select"><option value="">اختر شارة</option><option v-for="b in badges" :key="b.id" :value="b.id">{{ b.name }}</option></select></div>
        <div class="col-md-5"><label class="form-label">معرّف المستخدم (UUID أو ID العام)</label><input v-model.trim="grant.userId" class="form-control" /></div>
        <div class="col-md-2 d-grid gap-2"><button class="btn btn-success" :disabled="!grant.badgeId || !grant.userId" @click="grantBadge">منح</button><button class="btn btn-outline-danger" :disabled="!grant.badgeId || !grant.userId" @click="revokeBadge">سحب</button></div>
      </div>
    </div>
    <div class="glass p-0 overflow-hidden">
      <div class="table-responsive"><table class="table align-middle mb-0"><thead><tr><th>الشارة</th><th>الوصف</th><th>الفئة</th><th>الحالة</th><th>الإجراءات</th></tr></thead>
      <tbody><tr v-for="b in badges" :key="b.id"><td><div class="d-flex align-items-center gap-2"><img :src="b.imageUrl" alt="" style="width:42px;height:42px;object-fit:contain" /><strong>{{ b.name }}</strong></div></td><td>{{ b.description || '—' }}</td><td>{{ b.category }}</td><td>{{ b.isActive ? 'مفعلة' : 'مخفية' }}</td><td class="text-nowrap"><button class="btn btn-sm btn-outline-primary me-2" @click="edit(b)">تعديل</button><button class="btn btn-sm btn-outline-danger" @click="remove(b)">حذف</button></td></tr><tr v-if="!loading && !badges.length"><td colspan="5" class="text-center p-4">لا توجد شارات بعد. أضف أول شارة أعلاه.</td></tr></tbody></table></div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { badgesApi, uploadsApi } from '@/api'

const badges = ref([])
const loading = ref(false)
const saving = ref(false)
const uploadingImage = ref(false)
const error = ref('')
const editingId = ref('')
const blank = () => ({ name: '', description: '', imageUrl: '', category: 'general', awardType: 'manual', achievementKey: null, achievementThreshold: 0, isActive: true, isAnimated: false, sortOrder: 0 })
const form = reactive(blank())
const grant = reactive({ badgeId: '', userId: '' })

async function load() {
  loading.value = true
  try { const r = await badgesApi.list(); badges.value = r?.data?.data ?? r?.data ?? [] }
  catch (e) { error.value = e?.response?.data?.message || 'تعذر تحميل الشارات. تحقق من صلاحيات الداشبورد.' }
  finally { loading.value = false }
}
async function uploadImage(event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { error.value = 'اختر ملف صورة صالحًا.'; event.target.value = ''; return }
  uploadingImage.value = true
  error.value = ''
  try {
    const response = await uploadsApi.upload(file)
    const payload = response?.data?.data ?? response?.data ?? {}
    if (!payload.url) throw new Error('لم يُرجع الخادم رابط الصورة.')
    form.imageUrl = payload.url
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر رفع صورة الشارة.'
  } finally {
    uploadingImage.value = false
    event.target.value = ''
  }
}
function reset() { editingId.value = ''; Object.assign(form, blank()) }
function edit(b) { editingId.value = b.id; Object.assign(form, { ...blank(), ...b }) }
async function save() {
  saving.value = true; error.value = ''
  try {
    const payload = { ...form }
    if (editingId.value) await badgesApi.update(editingId.value, payload)
    else await badgesApi.create(payload)
    reset(); await load()
  } catch (e) { error.value = e?.response?.data?.message || 'تعذر حفظ الشارة.' }
  finally { saving.value = false }
}
async function grantBadge() {
  error.value = ''
  try { await badgesApi.grant(grant.badgeId, grant.userId); window.alert('تم منح الشارة للمستخدم.'); }
  catch (e) { error.value = e?.response?.data?.message || 'تعذر منح الشارة.' }
}
async function revokeBadge() {
  error.value = ''
  try { await badgesApi.revoke(grant.badgeId, grant.userId); window.alert('تم سحب الشارة من المستخدم.'); }
  catch (e) { error.value = e?.response?.data?.message || 'تعذر سحب الشارة.' }
}
async function remove(b) {
  if (!window.confirm('حذف الشارة "' + b.name + '" وسجلات ملكيتها؟')) return
  try { await badgesApi.remove(b.id); await load() }
  catch (e) { error.value = e?.response?.data?.message || 'تعذر حذف الشارة.' }
}
onMounted(load)
</script>
