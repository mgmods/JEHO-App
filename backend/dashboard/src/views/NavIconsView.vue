<template>
  <div>
    <PageHeader :title="t('navIcons.title')" :subtitle="t('navIcons.subtitle')">
      <template #actions>
        <button class="btn btn-outline-light btn-sm me-2" type="button" :disabled="saving" @click="clearAll">
          {{ t('navIcons.clear') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
          {{ saving ? t('app.saving') : t('navIcons.save') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <p class="text-secondary small mb-3">{{ t('navIcons.hint') }}</p>

    <LoadingSpinner v-if="loading" />
    <div v-else class="nav-icons-grid">
      <article v-for="tab in tabs" :key="tab.key" class="glass p-3 nav-icon-card">
        <h3 class="h6 mb-2">{{ tab.label }}</h3>
        <div class="d-flex gap-3 flex-wrap">
          <div class="icon-slot">
            <div class="icon-preview">
              <img v-if="form[tab.key].normal" :src="absUrl(form[tab.key].normal)" alt="" />
              <span v-else class="text-muted small">—</span>
            </div>
            <label class="form-label small mb-1">{{ t('navIcons.normal') }}</label>
            <input
              type="file"
              accept="image/png,image/jpeg,image/jpg,image/webp,image/svg+xml,.png,.jpg,.jpeg,.webp,.svg"
              class="form-control form-control-sm"
              @change="(e) => onFile(e, tab.key, 'normal')"
            />
            <button
              v-if="form[tab.key].normal"
              class="btn btn-link btn-sm text-danger px-0"
              type="button"
              @click="form[tab.key].normal = ''"
            >
              {{ t('navIcons.remove') }}
            </button>
          </div>
          <div class="icon-slot">
            <div class="icon-preview">
              <img v-if="form[tab.key].selected" :src="absUrl(form[tab.key].selected)" alt="" />
              <span v-else class="text-muted small">—</span>
            </div>
            <label class="form-label small mb-1">{{ t('navIcons.selected') }}</label>
            <input
              type="file"
              accept="image/png,image/jpeg,image/jpg,image/webp,image/svg+xml,.png,.jpg,.jpeg,.webp,.svg"
              class="form-control form-control-sm"
              @change="(e) => onFile(e, tab.key, 'selected')"
            />
            <button
              v-if="form[tab.key].selected"
              class="btn btn-link btn-sm text-danger px-0"
              type="button"
              @click="form[tab.key].selected = ''"
            >
              {{ t('navIcons.remove') }}
            </button>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { settingsApi, uploadsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()
const ORIGIN = (import.meta.env.VITE_API_ORIGIN || 'https://api.adnova.bbs.tr').replace(/\/$/, '')

const KEYS = ['party', 'drama', 'games', 'chat', 'me']

const tabs = computed(() => [
  { key: 'party', label: t('navIcons.tabs.party') },
  { key: 'drama', label: t('navIcons.tabs.drama') },
  { key: 'games', label: t('navIcons.tabs.games') },
  { key: 'chat', label: t('navIcons.tabs.chat') },
  { key: 'me', label: t('navIcons.tabs.me') },
])

function emptyForm() {
  const o = {}
  for (const k of KEYS) o[k] = { normal: '', selected: '' }
  return o
}

const form = reactive(emptyForm())
const version = ref(1)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

function absUrl(u) {
  if (!u) return ''
  if (/^https?:\/\//i.test(u)) return u
  return `${ORIGIN}${u.startsWith('/') ? '' : '/'}${u}`
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

function applyConfig(cfg) {
  const next = emptyForm()
  if (cfg && typeof cfg === 'object') {
    version.value = Math.max(1, Number(cfg.version) || 1)
    for (const k of KEYS) {
      const p = cfg[k] || cfg.tabs?.[k] || {}
      next[k].normal = p.normal || p.icon || ''
      next[k].selected = p.selected || ''
    }
  }
  for (const k of KEYS) {
    form[k].normal = next[k].normal
    form[k].selected = next[k].selected
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
  applyConfig(parseSetting(data, 'app_nav_icons'))
}

async function onFile(e, tabKey, field) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  const name = (file.name || '').toLowerCase()
  const ok =
    file.type === 'image/png' ||
    file.type === 'image/jpeg' ||
    file.type === 'image/webp' ||
    file.type === 'image/svg+xml' ||
    /\.(png|jpe?g|webp|svg)$/i.test(name)
  if (!ok) {
    error.value = t('navIcons.badFormat')
    toast().danger(t('navIcons.badFormat'))
    return
  }
  const { data, error: err } = await uploadsApi.upload(file)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const url = absUrl(data?.url || data?.data?.url || '')
  if (!url) {
    toast().danger(t('app.error'))
    return
  }
  form[tabKey][field] = url
  toast().success(t('app.success'))
}

function clearAll() {
  applyConfig(null)
  version.value = 1
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const body = {
    version: version.value,
    party: { ...form.party },
    drama: { ...form.drama },
    games: { ...form.games },
    chat: { ...form.chat },
    me: { ...form.me },
  }
  const { error: err } = await settingsApi.update({
    app_nav_icons: JSON.stringify(body),
  })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('navIcons.saved')
    toast().success(t('navIcons.saved'))
    await load()
  }
}

onMounted(load)
</script>

<style scoped>
.nav-icons-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}
.nav-icon-card {
  border-radius: 14px;
}
.icon-slot {
  flex: 1;
  min-width: 120px;
}
.icon-preview {
  width: 64px;
  height: 64px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
  overflow: hidden;
}
.icon-preview img {
  width: 48px;
  height: 48px;
  object-fit: contain;
}
</style>
