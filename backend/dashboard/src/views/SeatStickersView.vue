<template>
  <div>
    <PageHeader :title="t('seatStickers.title')" :subtitle="t('seatStickers.subtitle')">
      <template #actions>
        <button class="btn btn-outline-light btn-sm me-2" type="button" :disabled="saving" @click="seedDefaults">
          {{ t('seatStickers.seed') }}
        </button>
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

    <LoadingSpinner v-if="loading" />
    <template v-else>
      <div v-if="!rows.length" class="glass p-4 empty-state mb-3">{{ t('seatStickers.empty') }}</div>
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
          <label class="form-label small mb-1">{{ t('seatStickers.key') }}</label>
          <input v-model="row.key" class="form-control form-control-sm mb-2" maxlength="32" placeholder="e01" />
          <label class="form-label small mb-1">{{ t('seatStickers.name') }}</label>
          <input v-model="row.name" class="form-control form-control-sm mb-2" maxlength="80" />
          <label class="form-label small mb-1">{{ t('seatStickers.sort') }}</label>
          <input v-model.number="row.sortOrder" type="number" class="form-control form-control-sm mb-2" min="1" />
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
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()
const ORIGIN = (import.meta.env.VITE_API_ORIGIN || 'https://api.adnova.bbs.tr').replace(/\/$/, '')

const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

function absUrl(u) {
  if (!u) return ''
  if (/^https?:\/\//i.test(u)) return u
  return `${ORIGIN}${u.startsWith('/') ? '' : '/'}${u}`
}

function blankRow() {
  return {
    id: `s${Date.now().toString(36)}`,
    key: '',
    url: '',
    name: '',
    sortOrder: (rows.value?.length || 0) + 1,
    isActive: true,
  }
}

function defaultSeed() {
  const out = []
  for (let n = 1; n <= 74; n++) {
    const key = `e${String(n).padStart(2, '0')}`
    const ext = n <= 26 ? 'gif' : 'webp'
    out.push({
      id: key,
      key,
      url: `${ORIGIN}/assets/seat-stickers/${key}.${ext}`,
      name: key.toUpperCase(),
      sortOrder: n,
      isActive: true,
    })
  }
  return out
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
  if (cfg && Array.isArray(cfg.items) && cfg.items.length) {
    rows.value = cfg.items.map((r, i) => ({
      id: r.id || r.key || `s${i + 1}`,
      key: r.key || '',
      url: r.url || '',
      name: r.name || '',
      sortOrder: Number(r.sortOrder) || i + 1,
      isActive: r.isActive !== false,
    }))
  } else {
    rows.value = defaultSeed()
  }
}

function addBlank() {
  rows.value.push(blankRow())
}

function removeAt(idx) {
  rows.value.splice(idx, 1)
}

function seedDefaults() {
  rows.value = defaultSeed()
  toast().info(t('seatStickers.seeded'))
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
    .filter((r) => r.url && String(r.key || '').trim())
    .map((r, i) => ({
      id: r.id || r.key,
      key: String(r.key).trim().toLowerCase(),
      url: absUrl(r.url),
      name: r.name || r.key,
      sortOrder: Number(r.sortOrder) || i + 1,
      isActive: r.isActive !== false,
    }))
  const payload = {
    seat_stickers: JSON.stringify({ version: 1, items }),
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
