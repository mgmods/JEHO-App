<template>
  <div>
    <PageHeader :title="t('drama.title')" :subtitle="t('drama.subtitle')">
      <template #actions>
        <button class="btn btn-outline-light btn-sm" type="button" :disabled="loading || loadingSeries" @click="reloadAll">
          {{ t('common.refresh') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
          {{ t('common.save') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <LoadingSpinner v-if="loading" />
    <div v-else class="row g-3 mb-4">
      <div class="col-12">
        <div class="glass p-3">
          <div class="form-check form-switch">
            <input id="dramaEnabled" v-model="form.enabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="dramaEnabled">{{ t('drama.enabled') }}</label>
          </div>
          <div class="small text-muted mt-1">{{ t('drama.enabledHint') }}</div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="glass p-3 h-100">
          <h3 class="h6 mb-3">{{ t('drama.source') }}</h3>
          <div class="mb-2">
            <label class="form-label small">{{ t('drama.baseUrl') }}</label>
            <input v-model="form.source.baseUrl" class="form-control" dir="ltr" />
          </div>
          <div class="mb-2">
            <label class="form-label small">{{ t('drama.packageName') }}</label>
            <input v-model="form.source.packageName" class="form-control" dir="ltr" />
          </div>
          <div class="mb-2">
            <label class="form-label small">{{ t('drama.signature') }}</label>
            <input v-model="form.source.signature" class="form-control" dir="ltr" />
          </div>
          <div class="mb-0">
            <label class="form-label small">{{ t('drama.integritySecret') }}</label>
            <input v-model="form.source.integritySecret" class="form-control" dir="ltr" />
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="glass p-3 h-100">
          <h3 class="h6 mb-3">{{ t('drama.rewards') }}</h3>
          <div class="form-check form-switch mb-3">
            <input id="dramaRewards" v-model="form.rewards.enabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="dramaRewards">{{ t('drama.rewardsEnabled') }}</label>
          </div>
          <div class="mb-2">
            <label class="form-label small">{{ t('drama.coinsPerEpisode') }}</label>
            <input v-model.number="form.rewards.coinsPerEpisode" type="number" min="0" class="form-control" />
          </div>
          <div class="mb-0">
            <label class="form-label small">{{ t('drama.watchThreshold') }}</label>
            <input
              v-model.number="form.rewards.watchThreshold"
              type="number"
              min="0.5"
              max="0.95"
              step="0.05"
              class="form-control"
            />
            <div class="form-text">{{ t('drama.watchThresholdHint') }}</div>
          </div>
        </div>
      </div>

      <div class="col-12">
        <div class="glass p-3">
          <h3 class="h6 mb-3">{{ t('drama.ads') }}</h3>
          <div class="form-check form-switch mb-3">
            <input id="dramaAds" v-model="form.ads.adsEnabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="dramaAds">{{ t('drama.adsEnabled') }}</label>
          </div>
          <div class="row g-2">
            <div class="col-md-6">
              <label class="form-label small">App ID</label>
              <input v-model="form.ads.appId" class="form-control" dir="ltr" />
            </div>
            <div class="col-md-6">
              <label class="form-label small">Banner</label>
              <input v-model="form.ads.bannerId" class="form-control" dir="ltr" />
            </div>
            <div class="col-md-6">
              <label class="form-label small">Interstitial</label>
              <input v-model="form.ads.interstitialId" class="form-control" dir="ltr" />
            </div>
            <div class="col-md-6">
              <label class="form-label small">Rewarded</label>
              <input v-model="form.ads.rewardedId" class="form-control" dir="ltr" />
            </div>
            <div class="col-md-6">
              <label class="form-label small">{{ t('drama.rewardedInterval') }}</label>
              <input v-model.number="form.ads.rewardedInterval" type="number" min="1" class="form-control" />
            </div>
            <div class="col-md-6">
              <label class="form-label small">{{ t('drama.freeEpisodes') }}</label>
              <input v-model.number="form.ads.freeEpisodesCount" type="number" min="0" class="form-control" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Catalog fetched from drama source (same list the app shows) -->
    <div class="glass p-3">
      <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <div>
          <h3 class="h6 mb-1">{{ t('drama.catalog') }}</h3>
          <div class="small text-muted">{{ t('drama.catalogHint') }}</div>
        </div>
        <div class="d-flex align-items-center gap-2">
          <span class="badge text-bg-secondary">{{ series.length }}</span>
          <button class="btn btn-ghost btn-sm" type="button" :disabled="loadingSeries" @click="loadSeries">
            <i class="bi bi-arrow-clockwise me-1"></i>{{ t('drama.fetchCatalog') }}
          </button>
        </div>
      </div>

      <div class="mb-3">
        <input
          v-model="search"
          class="form-control"
          :placeholder="t('drama.searchPlaceholder')"
        />
      </div>

      <LoadingSpinner v-if="loadingSeries" />
      <div v-else-if="!filteredSeries.length" class="empty-state p-5 text-center">
        {{ seriesError || t('drama.noSeries') }}
      </div>
      <div v-else class="widget-grid">
        <button
          v-for="item in filteredSeries"
          :key="item.id"
          type="button"
          class="widget-card drama-card text-start p-0 border-0"
          @click="openSeries(item)"
        >
          <div class="widget-card-media drama-cover">
            <img v-if="coverOf(item)" :src="coverOf(item)" :alt="item.title" loading="lazy" />
            <div v-else class="drama-cover-fallback"><i class="bi bi-film"></i></div>
            <span v-if="item.isFeatured" class="drama-badge">{{ t('drama.featured') }}</span>
          </div>
          <div class="widget-card-body">
            <h3 class="widget-card-title text-truncate">{{ item.title || '—' }}</h3>
            <div class="widget-card-meta d-flex flex-wrap gap-2">
              <span><i class="bi bi-collection-play me-1"></i>{{ item.episodeCount || 0 }}</span>
              <span><i class="bi bi-eye me-1"></i>{{ formatCount(item.totalViews) }}</span>
              <span><i class="bi bi-heart me-1"></i>{{ formatCount(item.totalLikes) }}</span>
            </div>
          </div>
        </button>
      </div>
    </div>

    <div v-if="detail" class="drama-modal" @click.self="closeDetail">
      <div class="glass drama-modal-panel p-3">
        <div class="d-flex justify-content-between align-items-start gap-2 mb-3">
          <div>
            <h3 class="h5 mb-1">{{ detail.title }}</h3>
            <div class="small text-muted">ID · {{ detail.id }}</div>
          </div>
          <button class="btn btn-sm btn-ghost" type="button" @click="closeDetail">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>
        <div class="row g-3">
          <div class="col-md-4">
            <img v-if="coverOf(detail)" class="w-100 rounded" :src="coverOf(detail)" :alt="detail.title" />
            <p class="small text-muted mt-2 mb-0">{{ detail.description || t('drama.noDescription') }}</p>
          </div>
          <div class="col-md-8">
            <LoadingSpinner v-if="loadingDetail" />
            <div v-else-if="!(detail.episodes || []).length" class="empty-state py-4">{{ t('drama.noEpisodes') }}</div>
            <div v-else class="table-responsive">
              <table class="table table-glass table-hover align-middle mb-0">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>{{ t('common.title') }}</th>
                    <th>{{ t('drama.views') }}</th>
                    <th>{{ t('drama.likes') }}</th>
                    <th>{{ t('drama.lock') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="ep in detail.episodes" :key="ep.id">
                    <td>{{ ep.episodeNumber }}</td>
                    <td>{{ ep.title || ('EP ' + ep.episodeNumber) }}</td>
                    <td>{{ formatCount(ep.viewCount) }}</td>
                    <td>{{ formatCount(ep.likeCount) }}</td>
                    <td>{{ ep.isLocked ? t('drama.locked') : t('drama.open') }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { dramaApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()
const loading = ref(false)
const loadingSeries = ref(false)
const loadingDetail = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const seriesError = ref('')
const search = ref('')
const series = ref([])
const detail = ref(null)

const form = reactive({
  enabled: true,
  ads: {
    adsEnabled: true,
    testMode: false,
    appId: '',
    bannerId: '',
    interstitialId: '',
    rewardedId: '',
    rewardedInterval: 1,
    freeEpisodesCount: 1,
  },
  rewards: {
    enabled: true,
    coinsPerEpisode: 5,
    watchThreshold: 0.8,
  },
  source: {
    baseUrl: '',
    packageName: '',
    signature: '',
    integritySecret: '',
  },
})

const filteredSeries = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return series.value
  return series.value.filter((item) => {
    const title = String(item.title || '').toLowerCase()
    const id = String(item.id || '')
    return title.includes(q) || id.includes(q)
  })
})

function applyPayload(data) {
  if (!data || typeof data !== 'object') return
  form.enabled = data.enabled !== false
  if (data.ads && typeof data.ads === 'object') Object.assign(form.ads, data.ads, { testMode: false })
  if (data.rewards && typeof data.rewards === 'object') Object.assign(form.rewards, data.rewards)
  if (data.source && typeof data.source === 'object') Object.assign(form.source, data.source)
}

function absUrl(url) {
  if (!url) return ''
  const raw = String(url).trim()
  if (!raw) return ''
  if (/^https?:\/\//i.test(raw) || raw.startsWith('data:')) return raw
  const base = String(form.source.baseUrl || '').replace(/\/?$/, '/')
  if (!base || base === '/') return raw
  return raw.startsWith('/') ? `${base.replace(/\/$/, '')}${raw}` : `${base}${raw}`
}

function coverOf(item) {
  return absUrl(item?.coverUrl)
}

function formatCount(n) {
  const v = Number(n || 0)
  if (!Number.isFinite(v)) return '0'
  if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(1)}M`
  if (v >= 1_000) return `${(v / 1_000).toFixed(1)}K`
  return String(Math.round(v))
}

async function loadSettings() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await dramaApi.settings()
  loading.value = false
  if (err) {
    error.value = err.message || String(err)
    return
  }
  applyPayload(data?.data ?? data)
}

async function loadSeries() {
  loadingSeries.value = true
  seriesError.value = ''
  const { data, error: err } = await dramaApi.series()
  loadingSeries.value = false
  if (err) {
    series.value = []
    seriesError.value = err.message || String(err)
    return
  }
  const payload = data?.data ?? data
  series.value = Array.isArray(payload) ? payload : []
  if (!series.value.length) {
    seriesError.value = t('drama.noSeries')
  }
}

async function reloadAll() {
  await loadSettings()
  await loadSeries()
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const { data, error: err } = await dramaApi.patchSettings({
    enabled: !!form.enabled,
    ads: { ...form.ads, testMode: false },
    rewards: { ...form.rewards },
    source: { ...form.source },
  })
  saving.value = false
  if (err) {
    error.value = err.message || String(err)
    toast().danger(error.value)
    return
  }
  applyPayload(data?.data ?? data)
  success.value = t('drama.saved')
  toast().success(success.value)
  await loadSeries()
}

async function openSeries(item) {
  if (!item?.id) return
  detail.value = { ...item, episodes: [] }
  loadingDetail.value = true
  const { data, error: err } = await dramaApi.seriesGet(item.id)
  loadingDetail.value = false
  if (err) {
    error.value = err.message || String(err)
    return
  }
  detail.value = data?.data ?? data
}

function closeDetail() {
  detail.value = null
}

onMounted(reloadAll)
</script>

<style scoped>
.drama-card {
  border: 1px solid rgba(255, 255, 255, 0.06);
  color: inherit;
  background: transparent;
  transition: transform 0.15s ease, border-color 0.15s ease;
  cursor: pointer;
}
.drama-card:hover {
  transform: translateY(-2px);
  border-color: rgba(58, 235, 171, 0.35);
}
.drama-cover {
  position: relative;
  aspect-ratio: 3 / 4;
  background: rgba(0, 0, 0, 0.25);
  overflow: hidden;
}
.drama-cover img {
  width: 100%;
  height: 100%;
  max-width: none;
  max-height: none;
  object-fit: cover;
  display: block;
}
.drama-cover-fallback {
  height: 100%;
  display: grid;
  place-items: center;
  font-size: 2rem;
  opacity: 0.45;
}
.drama-badge {
  position: absolute;
  top: 8px;
  inset-inline-start: 8px;
  background: rgba(254, 44, 85, 0.92);
  color: #fff;
  border-radius: 999px;
  font-size: 0.7rem;
  padding: 0.15rem 0.5rem;
}
.drama-modal {
  position: fixed;
  inset: 0;
  z-index: 1080;
  background: rgba(0, 0, 0, 0.55);
  display: grid;
  place-items: center;
  padding: 1rem;
}
.drama-modal-panel {
  width: min(960px, 100%);
  max-height: min(86vh, 900px);
  overflow: auto;
}
</style>
