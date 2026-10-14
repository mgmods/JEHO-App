<template>
  <div>
    <PageHeader title="إدارة الشارات" subtitle="إنشاء الشارات وإدارة مكتسبات المستخدمين دون ربطها باشتراك VIP" />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <section class="glass p-3 mb-3">
      <h5 class="mb-3">إضافة شارة جديدة</h5>
      <form class="row g-2 align-items-end" @submit.prevent="createBadge">
        <div class="col-md-3">
          <label class="form-label">رمز الشارة</label>
          <input v-model.trim="form.code" class="form-control" pattern="[a-zA-Z0-9_-]{2,64}" required placeholder="first_gift" />
        </div>
        <div class="col-md-3">
          <label class="form-label">اسم الشارة</label>
          <input v-model.trim="form.name" class="form-control" maxlength="100" required />
        </div>
        <div class="col-md-3">
          <label class="form-label">رابط الصورة</label>
          <input v-model.trim="form.imageUrl" class="form-control" type="url" placeholder="https://..." />
        </div>
        <div class="col-md-3">
          <label class="form-label">ترتيب العرض</label>
          <input v-model.number="form.sortOrder" class="form-control" type="number" />
        </div>
        <div class="col-12">
          <label class="form-label">الوصف / شروط الحصول</label>
          <textarea v-model.trim="form.description" class="form-control" rows="2" maxlength="1000" />
        </div>
        <div class="col-12">
          <button class="btn btn-aurora" type="submit" :disabled="saving">{{ saving ? 'جارٍ الحفظ…' : 'إضافة الشارة' }}</button>
          <button class="btn btn-ghost ms-2" type="button" :disabled="loading" @click="load">تحديث القائمة</button>
        </div>
      </form>
    </section>

    <section class="glass p-3 mb-3">
      <h5 class="mb-3">منح أو سحب شارة</h5>
      <form class="row g-2 align-items-end" @submit.prevent="grantBadge">
        <div class="col-md-4">
          <label class="form-label">معرّف المستخدم (UUID)</label>
          <input v-model.trim="award.userId" class="form-control" required placeholder="معرّف المستخدم من صفحة المستخدمين" />
        </div>
        <div class="col-md-4">
          <label class="form-label">الشارة</label>
          <select v-model="award.badgeId" class="form-select" required>
            <option value="" disabled>اختر الشارة</option>
            <option v-for="badge in badges.filter(b => b.isActive)" :key="badge.id" :value="badge.id">{{ badge.name }} ({{ badge.code }})</option>
          </select>
        </div>
        <div class="col-md-4">
          <label class="form-label">ملاحظة إدارية (اختياري)</label>
          <input v-model.trim="award.note" class="form-control" maxlength="500" />
        </div>
        <div class="col-12">
          <button class="btn btn-aurora" type="submit" :disabled="saving || !award.userId || !award.badgeId">منح الشارة</button>
          <button class="btn btn-outline-danger ms-2" type="button" :disabled="saving || !award.userId || !award.badgeId" @click="revokeBadge">سحب الشارة</button>
        </div>
      </form>
    </section>

    <section class="glass p-0 overflow-hidden">
      <div class="p-3 border-bottom"><h5 class="mb-0">الشارات المسجلة</h5></div>
      <LoadingSpinner v-if="loading" />
      <div v-else-if="!badges.length" class="empty-state p-5 text-center">لا توجد شارات بعد. أضف أول شارة من النموذج أعلاه.</div>
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle mb-0">
          <thead><tr><th>الصورة</th><th>الاسم / الرمز</th><th>الوصف</th><th>المكتسبون</th><th>الحالة</th><th>تعديل</th></tr></thead>
          <tbody>
            <tr v-for="badge in badges" :key="badge.id">
              <td><img v-if="badge.imageUrl" :src="badge.imageUrl" :alt="badge.name" style="width:42px;height:42px;object-fit:contain" /><span v-else>🏅</span></td>
              <td><strong>{{ badge.name }}</strong><div class="small text-muted">{{ badge.code }}</div></td>
              <td style="min-width:180px">{{ badge.description || '—' }}</td>
              <td>{{ badge.earnedCount || 0 }}</td>
              <td><span :class="badge.isActive ? 'text-success' : 'text-muted'">{{ badge.isActive ? 'مفعّلة' : 'معطّلة' }}</span></td>
              <td><button class="btn btn-sm btn-ghost" type="button" @click="editBadge(badge)">تعديل</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="editing" class="glass p-3 mt-3">
      <h5 class="mb-3">تعديل الشارة: {{ editing.name }}</h5>
      <form class="row g-2" @submit.prevent="saveEdit">
        <div class="col-md-4"><label class="form-label">الاسم</label><input v-model.trim="editForm.name" class="form-control" required maxlength="100" /></div>
        <div class="col-md-4"><label class="form-label">رابط الصورة</label><input v-model.trim="editForm.imageUrl" class="form-control" type="url" /></div>
        <div class="col-md-4"><label class="form-label">ترتيب العرض</label><input v-model.number="editForm.sortOrder" class="form-control" type="number" /></div>
        <div class="col-12"><label class="form-label">الوصف / شروط الحصول</label><textarea v-model.trim="editForm.description" class="form-control" rows="2" maxlength="1000" /></div>
        <div class="col-12 form-check form-switch ms-2"><input id="badgeActive" v-model="editForm.isActive" class="form-check-input" type="checkbox" /><label for="badgeActive" class="form-check-label">الشارة مفعّلة</label></div>
        <div class="col-12"><button class="btn btn-aurora" type="submit" :disabled="saving">حفظ التعديل</button><button class="btn btn-ghost ms-2" type="button" @click="editing = null">إلغاء</button></div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { badgesApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const badges = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const editing = ref(null)
const form = reactive({ code: '', name: '', imageUrl: '', description: '', sortOrder: 0 })
const award = reactive({ userId: '', badgeId: '', note: '' })
const editForm = reactive({ name: '', imageUrl: '', description: '', sortOrder: 0, isActive: true })

function unwrap(data) { return data?.data ?? data }
async function load() {
  loading.value = true; error.value = ''
  const result = await badgesApi.list()
  loading.value = false
  if (result.error) { error.value = result.error.message || 'تعذر تحميل الشارات'; return }
  const payload = unwrap(result.data)
  badges.value = Array.isArray(payload) ? payload : (payload?.items || [])
}
async function createBadge() {
  saving.value = true; error.value = ''; success.value = ''
  const result = await badgesApi.create({ ...form, imageUrl: form.imageUrl || null, description: form.description || null })
  saving.value = false
  if (result.error) { error.value = result.error.message || 'تعذر إنشاء الشارة'; return }
  success.value = 'تمت إضافة الشارة إلى الكتالوج.'
  Object.assign(form, { code: '', name: '', imageUrl: '', description: '', sortOrder: 0 })
  await load()
}
function editBadge(badge) {
  editing.value = badge
  Object.assign(editForm, { name: badge.name || '', imageUrl: badge.imageUrl || '', description: badge.description || '', sortOrder: Number(badge.sortOrder || 0), isActive: badge.isActive !== false })
}
async function saveEdit() {
  if (!editing.value) return
  saving.value = true; error.value = ''
  const result = await badgesApi.update(editing.value.id, { ...editForm, imageUrl: editForm.imageUrl || null, description: editForm.description || null })
  saving.value = false
  if (result.error) { error.value = result.error.message || 'تعذر حفظ الشارة'; return }
  success.value = 'تم حفظ التعديل.'
  editing.value = null
  await load()
}
async function grantBadge() {
  saving.value = true; error.value = ''; success.value = ''
  const result = await badgesApi.grant(award.userId, award.badgeId, { note: award.note || undefined })
  saving.value = false
  if (result.error) { error.value = result.error.message || 'تعذر منح الشارة'; return }
  success.value = unwrap(result.data)?.alreadyEarned ? 'المستخدم يملك هذه الشارة بالفعل.' : 'تم منح الشارة بنجاح.'
  toast().success(success.value)
  await load()
}
async function revokeBadge() {
  if (!window.confirm('هل تريد سحب هذه الشارة من المستخدم؟')) return
  saving.value = true; error.value = ''; success.value = ''
  const result = await badgesApi.revoke(award.userId, award.badgeId)
  saving.value = false
  if (result.error) { error.value = result.error.message || 'تعذر سحب الشارة'; return }
  success.value = 'تم سحب الشارة.'
  await load()
}
onMounted(load)
</script>
