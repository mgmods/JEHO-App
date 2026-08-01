<template>
  <div>
    <PageHeader :title="t('banners.title')" :subtitle="t('banners.subtitle')">
      <template #actions>
        <button class="btn btn-outline-light btn-sm me-2" type="button" @click="seedDefaults">
          <i class="bi bi-magic me-1"></i> {{ t('banners.seed') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" @click="addRow">
          <i class="bi bi-plus-lg me-1"></i> {{ t('banners.add') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="d-flex justify-content-between align-items-center mb-3">
      <h3 class="h6 mb-0">{{ t('banners.home') }}</h3>
      <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
        {{ saving ? t('app.saving') : t('banners.save') }}
      </button>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else class="widget-grid">
      <div v-if="!rows.length" class="glass p-4 empty-state">{{ t('banners.empty') }}</div>
      <article v-for="(row, idx) in rows" :key="idx" class="widget-card">
        <div class="widget-card-media banner-media">
          <img v-if="row.imageUrl" :src="absUrl(row.imageUrl)" alt="" />
          <i v-else class="bi bi-image text-muted" style="font-size: 2rem" />
        </div>
        <div class="widget-card-body">
          <div class="d-flex justify-content-between align-items-center mb-2">
            <h3 class="widget-card-title mb-0">{{ t('banners.bannerNumber', { number: idx + 1 }) }}</h3>
            <button class="btn btn-sm btn-outline-danger" type="button" @click="askRemove(idx)">
              {{ t('banners.remove') }}
            </button>
          </div>
          <div class="row g-2">
            <div class="col-12">
              <label class="form-label small">{{ t('common.imageUrl') }}</label>
              <input v-model="row.imageUrl" class="form-control form-control-sm" placeholder="https://…/uploads/…" />
              <input type="file" accept="image/*" class="form-control form-control-sm mt-2" @change="(e) => onFile(e, row)" />
            </div>
            <div class="col-12">
              <label class="form-label small">{{ t('common.title') }}</label>
              <input v-model="row.title" class="form-control form-control-sm" />
            </div>
            <div class="col-12">
              <label class="form-label small">{{ t('common.link') }}</label>
              <select v-model="row.link" class="form-select form-select-sm mb-2">
                <option value="">— {{ t('banners.select') }} —</option>
                <option v-for="p in promoLinks" :key="p.value" :value="p.value">{{ p.label }}</option>
                <option value="ranking">{{ t('banners.ranking') }}</option>
                <option value="custom">{{ t('banners.custom') }}</option>
              </select>
              <input
                v-if="row.link === 'custom' || (row.link && !isKnownLink(row.link))"
                v-model="row.link"
                class="form-control form-control-sm"
                placeholder="https://api.adnova.bbs.tr/promos/…"
              />
            </div>
          </div>
        </div>
      </article>
    </div>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="t('common.delete')"
      :message="confirmMsg"
      @confirm="doRemove"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { settingsApi, uploadsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const ORIGIN = (import.meta.env.VITE_API_ORIGIN || 'https://api.adnova.bbs.tr').replace(/\/$/, '')
const PROMO = `${ORIGIN}/promos`

const promoLinks = computed(() => [
  { label: t('contests.promo.midyear'), value: `${PROMO}/midyear.html` },
  { label: t('contests.promo.championship'), value: `${PROMO}/championship.html` },
  { label: t('contests.promo.battle'), value: `${PROMO}/battle.html` },
  { label: t('contests.promo.elite'), value: `${PROMO}/elite-stars.html` },
  { label: t('contests.promo.agencies'), value: `${PROMO}/agencies-monthly.html` },
  { label: t('contests.promo.recharge'), value: `${PROMO}/recharge.html` },
  { label: 'VIP', value: `${PROMO}/vip.html` },
])

const known = new Set([...promoLinks.value.map((p) => p.value), 'ranking', 'wallet', 'recharge', 'vip', ''])

const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const confirmOpen = ref(false)
const confirmMsg = ref('')
const pendingRemoveIdx = ref(-1)

function absUrl(u) {
  if (!u) return ''
  if (/^https?:\/\//i.test(u)) return u
  return `${ORIGIN}${u.startsWith('/') ? '' : '/'}${u}`
}

function isKnownLink(link) {
  return known.has(link) || promoLinks.value.some((p) => p.value === link)
}

function addRow() {
  rows.value.push({ imageUrl: '', title: '', link: `${PROMO}/midyear.html` })
}

function seedDefaults() {
  rows.value = [
    { imageUrl: `${ORIGIN}/banners/midyear.png`, title: 'احتفالية منتصف العام', link: `${PROMO}/midyear.html` },
    { imageUrl: `${ORIGIN}/banners/championship.png`, title: 'الطريق إلى البطولة', link: `${PROMO}/championship.html` },
    { imageUrl: `${ORIGIN}/banners/battle.png`, title: 'معركة الفرق', link: `${PROMO}/battle.html` },
    { imageUrl: `${ORIGIN}/banners/elite.png`, title: 'نخبة النجوم', link: `${PROMO}/elite-stars.html` },
    { imageUrl: `${ORIGIN}/banners/agencies.png`, title: 'الوكالات الوطنية', link: `${PROMO}/agencies-monthly.html` },
    { imageUrl: `${ORIGIN}/banners/recharge.png`, title: 'عرض الشحن المميز', link: `${PROMO}/recharge.html` },
  ]
  toast().info(t('banners.seed'))
}

function askRemove(idx) {
  pendingRemoveIdx.value = idx
  confirmMsg.value = `${t('banners.remove')} #${idx + 1}؟`
  confirmOpen.value = true
}

function doRemove() {
  const idx = pendingRemoveIdx.value
  pendingRemoveIdx.value = -1
  if (idx < 0) return
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
  row.imageUrl = absUrl(url)
  toast().success(t('app.success'))
}

function parseSetting(list, key, fallback) {
  const found = (Array.isArray(list) ? list : list?.items || []).find((s) => s.key === key)
  if (!found?.value) return fallback
  try {
    const parsed = JSON.parse(found.value)
    return Array.isArray(parsed) ? parsed : fallback
  } catch {
    return fallback
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
  rows.value = parseSetting(data, 'home_banners', [])
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    home_banners: JSON.stringify(
      rows.value
        .filter((r) => r.imageUrl)
        .map((r) => ({
          imageUrl: absUrl(r.imageUrl),
          title: r.title || '',
          link: r.link === 'custom' ? '' : r.link || '',
        })),
    ),
  }
  const { error: err } = await settingsApi.update(payload)
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('banners.saved')
    toast().success(t('banners.saved'))
  }
}

onMounted(load)
</script>

<style scoped>
.banner-media {
  aspect-ratio: 16 / 9;
}
.banner-media img {
  max-width: 100%;
  max-height: 100%;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
