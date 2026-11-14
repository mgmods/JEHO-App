<template>
  <div>
    <PageHeader title="ملصقات" subtitle="أنشئ أقسام الملصقات وارفع الملصقات المتحركة أو الصور، وحدد شروط ظهورها للمستخدمين داخل التطبيق.">
      <template #actions>
        <button class="btn btn-outline-light btn-sm me-2" type="button" @click="addBlank">
          <i class="bi bi-plus-lg me-1"></i>{{ t('seatStickers.add') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
          {{ saving ? t('app.saving') : t('seatStickers.save') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <p class="text-secondary small mb-3">{{ t('seatStickers.hint') }}</p>
    <section class="glass p-3 mb-3">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <strong>أقسام وحزم الملصقات</strong>
        <button class="btn btn-outline-light btn-sm" type="button" @click="addCategory">إضافة قسم</button>
      </div>
      <div v-for="(category, index) in categories" :key="category.id" class="row g-2 align-items-end mb-3">
        <div class="col-md-3"><label class="form-label small">اسم القسم</label><input v-model="category.name" class="form-control form-control-sm" /></div>
        <div class="col-md-2"><label class="form-label small">المعرّف</label><input v-model="category.id" class="form-control form-control-sm" /></div>
        <div class="col-md-2"><label class="form-label small">نوع الوصول</label><select v-model="category.accessType" class="form-select form-select-sm"><option value="free">مجاني</option><option value="paid">شراء القسم بالكوينز</option><option value="vip">VIP</option><option value="cp">CP</option></select></div>
        <div class="col-md-2"><label class="form-label small">السعر</label><input v-model.number="category.priceCoins" type="number" min="0" class="form-control form-control-sm" /></div>
        <div class="col-md-1"><label class="form-label small">المدة بالأيام (0 دائم)</label><input v-model.number="category.durationDays" type="number" min="0" class="form-control form-control-sm" /></div>
        <div class="col-md-1"><label class="form-label small">VIP</label><input v-model.number="category.requiredVipLevel" type="number" min="1" max="7" class="form-control form-control-sm" /></div>
        <div class="col-md-1"><div class="form-check"><input v-model="category.isActive" class="form-check-input" type="checkbox" /><label class="form-check-label small">نشط</label></div><button class="btn btn-link btn-sm text-danger px-0" @click="categories.splice(index,1)">حذف</button></div>
        <div class="col-md-3"><label class="form-label small">الفئة</label><select v-model="category.audience" class="form-select form-select-sm"><option value="all">الجميع</option><option value="male">الشباب</option><option value="female">البنات</option></select></div>
        <div class="col-md-3"><label class="form-label small">رابط أيقونة القسم</label><input v-model="category.iconUrl" class="form-control form-control-sm" placeholder="/uploads/..." /></div>
        <div class="col-md-3"><div class="form-check"><input v-model="category.requiresCp" class="form-check-input" type="checkbox" /><label class="form-check-label small">يشترط CP فعال</label></div></div>
      </div>
    </section>

    <LoadingSpinner v-if="loading" />
    <template v-else>
      <div v-if="!rows.length" class="glass p-4 empty-state mb-3">{{ t('seatStickers.empty') }}</div>
      <div class="glass p-3 mb-3"><div class="form-check form-switch"><input id="seat-stickers-enabled" v-model="enabled" class="form-check-input" type="checkbox" /><label class="form-check-label" for="seat-stickers-enabled">تفعيل ملصقات المقاعد والإيموجيات المتحركة</label></div><small class="text-secondary">عند الإيقاف تختفي جميع الملصقات من التطبيق.</small></div>
      <div class="sticker-grid">
        <article v-for="(row, idx) in rows" :key="row.id || row.key || idx" class="glass p-3 sticker-card">
          <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
            <strong class="small">#{{ idx + 1 }} · {{ row.key || '—' }}</strong>
            <button class="btn btn-link btn-sm text-danger px-0" type="button" @click="removeAt(idx)">
              {{ t('seatStickers.remove') }}
            </button>
          </div>
          <div class="preview mb-2">
            <img v-if="row.url" :src="absUrl(row.url)" alt="" />
            <span v-else class="text-muted small">—</span>
          </div>
          <label class="form-label small mb-1">القسم</label>
          <select v-model="row.categoryId" class="form-select form-select-sm mb-2"><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select>
          <label class="form-label small mb-1">{{ t('seatStickers.key') }}</label>
          <input v-model="row.key" class="form-control form-control-sm mb-2" maxlength="32" placeholder="e01" />
          <label class="form-label small mb-1">{{ t('seatStickers.name') }}</label>
          <input v-model="row.name" class="form-control form-control-sm mb-2" maxlength="80" />
          <label class="form-label small mb-1">{{ t('seatStickers.sort') }}</label>
          <input v-model.number="row.sortOrder" type="number" class="form-control form-control-sm mb-2" min="1" />
          <label class="form-label small mb-1">نوع الإتاحة</label>
          <select v-model="row.accessType" class="form-select form-select-sm mb-2"><option value="free">مجاني</option><option value="paid">مدفوع بالكوينز</option><option value="vip">حصري VIP</option><option value="cp">حصري CP</option></select>
          <label class="form-label small mb-1">السعر بالكوينز</label><input v-model.number="row.priceCoins" type="number" min="0" class="form-control form-control-sm mb-2" />
          <label class="form-label small mb-1">الفئة</label><select v-model="row.audience" class="form-select form-select-sm mb-2"><option value="all">الجميع</option><option value="male">الشباب</option><option value="female">البنات</option></select>
          <label class="form-label small mb-1">مستوى VIP المطلوب</label><input v-model.number="row.requiredVipLevel" type="number" min="1" max="7" class="form-control form-control-sm mb-2" />
          <div class="form-check mb-2"><input v-model="row.requiresCp" class="form-check-input" type="checkbox" /><label class="form-check-label small">يشترط علاقة CP فعالة</label></div>
          <label class="form-label small mb-1">{{ t('seatStickers.file') }}</label>
          <input
            type="file"
            accept="image/gif,image/webp,image/png,image/jpeg,.gif,.webp,.png,.jpg,.jpeg"
            class="form-control form-control-sm mb-2"
            @change="(e) => onFile(e, row)"
          />
          <div class="form-check">
            <input :id="'active-' + idx" v-model="row.isActive" class="form-check-input" type="checkbox" />
            <label class="form-check-label small" :for="'active-' + idx">{{ t('seatStickers.active') }}</label>
          </div>
        </article>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { settingsApi, uploadsApi } from '@/api'
import { resolveAsset } from '@/utils/assets'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()

const rows = ref([])
const categories = ref([
  
])
const enabled = ref(true)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

function absUrl(u) {
  return resolveAsset(u)
}

function blankRow() {
  return {
    id: `s${Date.now().toString(36)}`,
    key: '',
    url: '',
    name: '',
    sortOrder: (rows.value?.length || 0) + 1,
    isActive: true, accessType: 'free', priceCoins: 0, audience: 'all', requiredVipLevel: 1, requiresCp: false,
    categoryId: categories.value[0]?.id || '',
  }
}


function parseSetting(list, key) {
  const found = (Array.isArray(list) ? list : list?.items || []).find((s) => s.key === key)
  if (!found?.value) return null
  try {
    return typeof found.value === 'string' ? JSON.parse(found.value) : found.value
  } catch {
    return null
  }
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await settingsApi.get()
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const cfg = parseSetting(data, 'seat_stickers')
  enabled.value = cfg?.enabled !== false
  categories.value = Array.isArray(cfg?.categories) ? cfg.categories.filter((x) => x && x.id !== 'classic').map((x, i) => ({ ...x, sortOrder: Number(x.sortOrder) || i + 1 })) : []
  if (cfg && Array.isArray(cfg.items) && cfg.items.length) {
    rows.value = cfg.items.map((r, i) => ({
      id: r.id || r.key || `s${i + 1}`,
      key: r.key || '',
      url: r.url || '',
      name: r.name || '',
      sortOrder: Number(r.sortOrder) || i + 1,
      isActive: r.isActive !== false,
      accessType: r.accessType || 'free', priceCoins: Number(r.priceCoins) || 0,
      audience: r.audience || 'all', requiredVipLevel: Number(r.requiredVipLevel) || 1,
      requiresCp: r.requiresCp === true,
      categoryId: r.categoryId === 'classic' ? '' : (r.categoryId || ''),
    }))
  } else {
    rows.value = []
  }
}

function addCategory() {
  const id = 'category' + Date.now().toString(36)
  categories.value.push({ id, name: 'ملصقات جديدة', sortOrder: categories.value.length + 1, isActive: true, accessType: 'free', priceCoins: 0, durationDays: 0, audience: 'all', requiredVipLevel: 1, requiresCp: false, iconUrl: '' })
}

function addBlank() {
  rows.value.push(blankRow())
}

function removeAt(idx) {
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
  row.url = absUrl(url)
  if (!row.categoryId && categories.value.length) row.categoryId = categories.value[0].id
  if (!row.key) {
    const base = String(file.name || '')
      .replace(/\.[^.]+$/, '')
      .toLowerCase()
      .replace(/[^a-z0-9_-]/g, '')
      .slice(0, 32)
    if (base) row.key = base
  }
  if (!row.name) row.name = row.key || file.name
  toast().success(t('app.success'))
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const items = rows.value
    .filter((r) => r.url && String(r.key || '').trim() && String(r.categoryId || '').trim())
    .map((r, i) => ({
      id: r.id || r.key,
      key: String(r.key).trim().toLowerCase(),
      url: absUrl(r.url),
      name: r.name || r.key,
      sortOrder: Number(r.sortOrder) || i + 1,
      isActive: r.isActive !== false,
      accessType: r.accessType || 'free', priceCoins: Number(r.priceCoins) || 0,
      audience: r.audience || 'all', requiredVipLevel: Number(r.requiredVipLevel) || 1,
      requiresCp: r.requiresCp === true,
      categoryId: r.categoryId || categories.value[0]?.id || 'male',
    }))
  const payload = {
    seat_stickers: JSON.stringify({ version: 4, enabled: enabled.value, categories: categories.value.filter((x) => x.id !== 'classic').map((x, i) => ({ ...x, id: String(x.id || ('category' + (i + 1))).trim().toLowerCase(), sortOrder: i + 1 })), items }),
  }
  const { error: err } = await settingsApi.update(payload)
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('seatStickers.saved')
    toast().success(t('seatStickers.saved'))
    await load()
  }
}

onMounted(load)
</script>

<style scoped>
.sticker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}
.preview {
  height: 88px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 10px;
  overflow: hidden;
}
.preview img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
</style>
