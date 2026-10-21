<template>
  <section class="staff-admins-page">
    <PageHeader
      title="إدارة أدمن لوحة التحكم"
      subtitle="إنشاء حسابات إدارية مستقلة وتحديد صلاحيات كل حساب حسب الحاجة."
    >
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="loading" @click="load">
          <i class="bi bi-arrow-clockwise me-1"></i> تحديث القائمة
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <div class="staff-admins-note glass p-3 mb-3">
      <i class="bi bi-shield-lock-fill"></i>
      <div>
        <strong>إدارة آمنة للصلاحيات</strong>
        <p class="mb-0 small">هذه الصفحة متاحة للسوبر أدمن فقط. الحساب الجديد أدمن محدود الصلاحيات، ولا يحصل على صلاحيات السوبر أدمن أو إدارة الأدمن الآخرين.</p>
      </div>
    </div>

    <div class="row g-3 align-items-start">
      <div class="col-12 col-xl-5">
        <div class="glass p-3 p-lg-4">
          <div class="d-flex align-items-center gap-2 mb-3">
            <span class="staff-admins-icon"><i class="bi" :class="editingId ? 'bi-pencil-square' : 'bi-person-plus-fill'"></i></span>
            <div>
              <h5 class="mb-1">{{ editingId ? 'تعديل صلاحيات الأدمن' : 'إضافة أدمن جديد' }}</h5>
              <div class="small text-muted">بيانات الدخول والصلاحيات</div>
            </div>
          </div>

          <form @submit.prevent="save">
            <div class="mb-3">
              <label class="form-label">الاسم الظاهر</label>
              <input v-model.trim="form.displayName" class="form-control" maxlength="120" placeholder="اسم الأدمن" />
            </div>
            <div class="mb-3">
              <label class="form-label">البريد الإلكتروني</label>
              <input v-model.trim="form.email" class="form-control" type="email" required :disabled="!!editingId" autocomplete="username" placeholder="admin@example.com" />
            </div>
            <div class="mb-3">
              <label class="form-label">{{ editingId ? 'كلمة مرور جديدة (اختياري)' : 'كلمة المرور' }}</label>
              <input v-model="form.password" class="form-control" :required="!editingId" type="password" minlength="8" autocomplete="new-password" :placeholder="editingId ? 'اتركها فارغة دون تغيير' : '8 أحرف على الأقل'" />
              <div class="form-text">كلمة المرور لا تقل عن 8 أحرف.</div>
            </div>

            <div class="staff-admins-permissions-head">
              <div>
                <label class="form-label mb-1">صلاحيات الأقسام</label>
                <div class="small text-muted">اختر «قراءة» أو «إدارة» لكل قسم.</div>
              </div>
              <button class="btn btn-sm btn-ghost" type="button" @click="setAll('read')">قراءة للجميع</button>
            </div>
            <div class="staff-admins-permissions">
              <div v-for="module in modules" :key="module.key" class="staff-admins-permission-row">
                <span>{{ module.label }}</span>
                <select v-model="form.permissions[module.key]" class="form-select form-select-sm" :aria-label="'صلاحية ' + module.label">
                  <option value="none">بدون</option>
                  <option value="read">قراءة</option>
                  <option value="write">إدارة</option>
                </select>
              </div>
            </div>
            <div v-if="!hasAnyPermission" class="small text-danger mt-2">يجب منح صلاحية واحدة على الأقل.</div>

            <div class="d-flex gap-2 mt-4">
              <button class="btn btn-aurora flex-grow-1" type="submit" :disabled="saving || !hasAnyPermission">
                <i class="bi me-1" :class="saving ? 'bi-hourglass-split' : 'bi-check2-circle'"></i>
                {{ saving ? 'جارٍ الحفظ…' : editingId ? 'حفظ التعديلات' : 'إنشاء حساب الأدمن' }}
              </button>
              <button v-if="editingId" class="btn btn-ghost" type="button" :disabled="saving" @click="resetForm">إلغاء</button>
            </div>
          </form>
        </div>
      </div>

      <div class="col-12 col-xl-7">
        <div class="glass p-0 overflow-hidden">
          <div class="d-flex align-items-center justify-content-between gap-2 p-3 border-bottom" style="border-color: var(--border-color) !important">
            <div>
              <h5 class="mb-1">حسابات الأدمن</h5>
              <div class="small text-muted">الحسابات المنشأة من لوحة التحكم</div>
            </div>
            <span class="staff-admins-count">{{ admins.length }}</span>
          </div>
          <LoadingSpinner v-if="loading" />
          <div v-else-if="!admins.length" class="empty-state p-5 text-center">
            <i class="bi bi-people fs-2 d-block mb-2"></i>
            لا توجد حسابات أدمن إضافية حتى الآن.
          </div>
          <div v-else class="staff-admins-list">
            <article v-for="admin in admins" :key="admin.id" class="staff-admins-item">
              <div class="staff-admins-avatar"><i class="bi bi-person-badge-fill"></i></div>
              <div class="flex-grow-1 min-w-0">
                <div class="d-flex align-items-center flex-wrap gap-2">
                  <strong class="text-break">{{ admin.displayName || admin.name || admin.username }}</strong>
                  <span class="badge" :class="admin.status === 'active' ? 'text-bg-success' : 'text-bg-secondary'">
                    {{ admin.status === 'active' ? 'نشط' : 'معطّل' }}
                  </span>
                </div>
                <div class="small text-muted text-break">{{ admin.email }}</div>
                <div class="small text-muted mt-1">{{ permissionSummary(admin.permissions) }}</div>
              </div>
              <div class="staff-admins-actions">
                <button class="btn btn-sm btn-ghost" type="button" title="تعديل" @click="editAdmin(admin)">
                  <i class="bi bi-pencil-square"></i>
                </button>
                <button class="btn btn-sm" :class="admin.status === 'active' ? 'btn-outline-danger' : 'btn-outline-success'" type="button" @click="toggleAdmin(admin)">
                  {{ admin.status === 'active' ? 'تعطيل' : 'تفعيل' }}
                </button>
              </div>
            </article>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { operatorsApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { toast } from '@/composables/useToast'

const moduleLabels = {
  dashboard: 'الرئيسية والإحصائيات',
  conversations: 'المحادثات',
  paidMessages: 'الرسائل المدفوعة',
  profileActionIcons: 'أيقونات الملف الشخصي',
  users: 'المستخدمون',
  agencies: 'الوكالات',
  rooms: 'الرومات والبث',
  supportChest: 'صندوق الدعم',
  gifts: 'الهدايا',
  coins: 'باقات العملات',
  wallet: 'المحفظة',
  withdrawals: 'السحوبات',
  rechargeAgents: 'وكلاء الشحن',
  vip: 'VIP',
  badges: 'الشارات',
  vanityIds: 'الأيدي المميزة',
  cosmetics: 'المظاهر',
  offers: 'العروض',
  promos: 'العروض والترقيات',
  luckyBoxes: 'الصناديق اليومية',
  tasks: 'المهام',
  hostTarget: 'تارجيت المضيف',
  contests: 'تحديات PK',
  ranking: 'التصنيفات',
  roomCup: 'كأس الرومات',
  games: 'الألعاب',
  gameAds: 'إعلانات الألعاب',
  drama: 'المسلسلات',
  banners: 'البنرات والسبلاش',
  navIcons: 'أيقونات التنقل',
  seatStickers: 'ملصقات المقاعد',
  reports: 'البلاغات واللحظات',
  notifications: 'الإشعارات',
  cpSettings: 'إعدادات CP',
  settings: 'الإعدادات العامة',
  logs: 'سجلات النظام',
  policyBrochure: 'دليل السياسات',
  genderVerifications: 'التحققات',
}
const moduleKeys = Object.keys(moduleLabels)
const modules = ref(moduleKeys.map(key => ({ key, label: moduleLabels[key] })))
const admins = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const editingId = ref('')
const form = reactive({ displayName: '', email: '', password: '', permissions: freshPermissions() })

function freshPermissions() {
  return Object.fromEntries(moduleKeys.map(key => [key, 'none']))
}
const hasAnyPermission = computed(() => Object.values(form.permissions).some(level => level === 'read' || level === 'write'))

function setAll(level) {
  for (const key of moduleKeys) form.permissions[key] = level
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await operatorsApi.list()
  loading.value = false
  if (err) {
    error.value = err.message || 'تعذر تحميل حسابات الأدمن.'
    return
  }
  const payload = data?.data ?? data
  admins.value = Array.isArray(payload) ? payload : (payload?.items || [])
  const available = Array.isArray(payload?.modules) ? payload.modules : moduleKeys
  modules.value = available.map(key => ({ key, label: moduleLabels[key] || key }))
  for (const module of modules.value) {
    if (!(module.key in form.permissions)) form.permissions[module.key] = 'none'
  }
}

function resetForm() {
  editingId.value = ''
  form.displayName = ''
  form.email = ''
  form.password = ''
  form.permissions = freshPermissions()
}

function editAdmin(admin) {
  editingId.value = admin.id
  form.displayName = admin.displayName || admin.name || ''
  form.email = admin.email || ''
  form.password = ''
  form.permissions = { ...freshPermissions(), ...(admin.permissions || {}) }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function permissionSummary(permissions = {}) {
  const count = Object.values(permissions).filter(level => level === 'read' || level === 'write').length
  return count ? `${count} قسم بصلاحية محددة` : 'لا توجد أقسام مفعّلة'
}

async function save() {
  if (!hasAnyPermission.value) return
  saving.value = true
  error.value = ''
  const permissions = Object.fromEntries(modules.value.map(({ key }) => [key, form.permissions[key] || 'none']))
  const payload = { displayName: form.displayName, permissions }
  if (form.password) payload.password = form.password
  const result = editingId.value
    ? await operatorsApi.update(editingId.value, payload)
    : await operatorsApi.create({ ...payload, email: form.email, password: form.password })
  saving.value = false
  if (result.error) {
    error.value = result.error.message || 'تعذر حفظ حساب الأدمن.'
    return
  }
  toast(editingId.value ? 'تم تحديث حساب الأدمن' : 'تم إنشاء حساب الأدمن بنجاح')
  resetForm()
  await load()
}

async function toggleAdmin(admin) {
  const active = admin.status !== 'active'
  const result = await operatorsApi.update(admin.id, { active })
  if (result.error) {
    error.value = result.error.message || 'تعذر تحديث حالة الحساب.'
    return
  }
  toast(active ? 'تم تفعيل حساب الأدمن' : 'تم تعطيل حساب الأدمن')
  await load()
}

onMounted(load)
</script>

<style scoped>
.staff-admins-note { display:flex; gap:12px; align-items:flex-start; border-inline-start:3px solid var(--accent, #7c5cff); }
.staff-admins-note > i { font-size:1.25rem; color:var(--accent, #7c5cff); margin-top:2px; }
.staff-admins-icon,.staff-admins-avatar { display:grid; place-items:center; flex:0 0 auto; width:42px; height:42px; border-radius:14px; color:var(--accent, #7c5cff); background:color-mix(in srgb, var(--accent, #7c5cff) 12%, transparent); font-size:1.15rem; }
.staff-admins-permissions-head { display:flex; align-items:flex-end; justify-content:space-between; gap:12px; margin-bottom:10px; }
.staff-admins-permissions { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px; max-height:360px; overflow:auto; padding:2px; }
.staff-admins-permission-row { display:flex; align-items:center; justify-content:space-between; gap:8px; min-width:0; padding:9px 10px; border:1px solid var(--border-color); border-radius:12px; }
.staff-admins-permission-row > span { font-size:.82rem; line-height:1.35; }
.staff-admins-permission-row select { width:104px; flex:0 0 104px; }
.staff-admins-count { display:grid; place-items:center; min-width:34px; height:34px; padding:0 8px; border-radius:11px; background:color-mix(in srgb, var(--accent, #7c5cff) 12%, transparent); font-weight:700; }
.staff-admins-list { display:flex; flex-direction:column; }
.staff-admins-item { display:flex; align-items:center; gap:12px; padding:15px; border-bottom:1px solid var(--border-color); }
.staff-admins-item:last-child { border-bottom:0; }
.staff-admins-actions { display:flex; align-items:center; gap:6px; }
@media(max-width:600px) { .staff-admins-permissions { grid-template-columns:1fr; } .staff-admins-item { align-items:flex-start; flex-wrap:wrap; } .staff-admins-actions { width:100%; justify-content:flex-end; } }
</style>
