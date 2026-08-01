<template>
  <div>
    <PageHeader :title="t('games.title')" :subtitle="t('games.subtitle')">
      <template #actions>
        <button class="btn btn-outline-light btn-sm" type="button" :disabled="loading" @click="resetDefaults">
          {{ t('games.resetDefaults') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
          {{ t('games.saveToApp') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="glass p-3 mb-3 small text-muted">
      {{ t('games.hint') }}
    </div>

    <div class="glass p-3 mb-4">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
          <h3 class="h6 mb-1">{{ t('games.dailyBoss') }}</h3>
          <div class="small text-muted">{{ t('games.bossHint') }}</div>
        </div>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="savingBoss" @click="saveBoss">
          {{ t('games.saveBoss') }}
        </button>
      </div>
      <div class="row g-2">
        <div class="col-md"><label class="form-label small">HP</label><input v-model.number="boss.maxHp" type="number" min="100" class="form-control" /></div>
        <div class="col-md"><label class="form-label small">{{ t('games.attempts') }}</label><input v-model.number="boss.maxAttacks" type="number" min="1" class="form-control" /></div>
        <div class="col-md"><label class="form-label small">{{ t('games.minDamage') }}</label><input v-model.number="boss.minDamage" type="number" min="1" class="form-control" /></div>
        <div class="col-md"><label class="form-label small">{{ t('games.maxDamage') }}</label><input v-model.number="boss.maxDamage" type="number" min="1" class="form-control" /></div>
        <div class="col-md"><label class="form-label small">{{ t('games.coinReward') }}</label><input v-model.number="boss.rewardCoins" type="number" min="1" class="form-control" /></div>
      </div>
    </div>

    <div class="glass p-3">
      <LoadingSpinner v-if="loading" />
      <div v-else-if="!games.length" class="empty-state p-5 text-center">{{ t('games.noGames') }}</div>
      <div v-else class="catalog-grid widget-grid">
        <article
          v-for="(g, idx) in games"
          :key="g.id || idx"
          class="catalog-card widget-card"
          :class="{ 'is-off': g.enabled === false }"
        >
          <div class="catalog-card__art widget-card-media">
            <img
              v-if="g.coverUrl && !coverBroken[g.id]"
              :src="g.coverUrl"
              :alt="displayTitle(g)"
              loading="lazy"
              @error="markCoverBroken(g.id)"
            />
            <div v-else class="catalog-card__fallback">
              <i class="bi bi-controller"></i>
            </div>
            <div v-if="g.enabled === false" class="catalog-card__veil">
              <span>{{ t('games.hidden') }}</span>
            </div>
            <div class="catalog-card__toolbar">
              <button
                type="button"
                class="tool-btn"
                :title="t('common.edit')"
                @click="openEdit(idx)"
              >
                <i class="bi bi-pencil-fill"></i>
              </button>
              <button
                type="button"
                class="tool-btn"
                :title="g.enabled === false ? t('games.showInApp') : t('games.hideInApp')"
                @click="toggleEnabled(idx)"
              >
                <i class="bi" :class="g.enabled === false ? 'bi-eye-slash-fill' : 'bi-eye-fill'"></i>
              </button>
              <button
                type="button"
                class="tool-btn tool-btn--danger"
                :title="t('common.delete')"
                @click="removeGame(idx)"
              >
                <i class="bi bi-trash-fill"></i>
              </button>
            </div>
          </div>
          <div class="catalog-card__meta">
            <h4 class="catalog-card__title" :title="displayTitle(g)">{{ displayTitle(g) }}</h4>
            <p class="catalog-card__sub">{{ g.titleEn && g.titleEn !== g.title ? g.titleEn : g.id }}</p>
          </div>
        </article>
      </div>
    </div>

    <div v-if="editIdx !== null && editDraft" class="edit-overlay" @click.self="closeEdit">
      <div class="glass edit-panel">
        <div class="edit-panel__head">
          <div class="edit-panel__preview">
            <img
              v-if="editDraft.coverUrl"
              :src="editDraft.coverUrl"
              :alt="editDraft.title"
              @error="onCoverError($event)"
            />
            <div v-else class="catalog-card__fallback"><i class="bi bi-controller"></i></div>
          </div>
          <div class="flex-grow-1 min-w-0">
            <h3 class="h5 mb-1 text-truncate">{{ displayTitle(editDraft) || t('common.edit') }}</h3>
            <div class="small text-muted font-monospace" dir="ltr">{{ editDraft.id }}</div>
          </div>
          <button class="btn btn-sm btn-ghost" type="button" @click="closeEdit">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <div class="mb-3">
          <label class="form-label small">{{ t('games.titleAr') }}</label>
          <input v-model="editDraft.title" class="form-control" />
        </div>
        <div class="mb-3">
          <label class="form-label small">{{ t('games.titleEn') }}</label>
          <input v-model="editDraft.titleEn" class="form-control" dir="ltr" />
        </div>
        <div class="mb-3">
          <label class="form-label small">{{ t('games.coverUrl') }}</label>
          <input v-model="editDraft.coverUrl" class="form-control" dir="ltr" />
        </div>
        <div class="form-check form-switch mb-4">
          <input id="edit-enabled" v-model="editDraft.enabled" class="form-check-input" type="checkbox" />
          <label class="form-check-label" for="edit-enabled">{{ t('games.showInApp') }}</label>
        </div>

        <div class="d-flex justify-content-end gap-2">
          <button class="btn btn-outline-light" type="button" @click="closeEdit">{{ t('common.cancel') }}</button>
          <button class="btn btn-aurora" type="button" @click="applyEdit">{{ t('common.save') }}</button>
        </div>
      </div>
    </div>

    <div class="glass p-3 mt-4">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <h3 class="h6 mb-0">{{ t('games.store') }}</h3>
        <div class="d-flex gap-2">
          <button class="btn btn-ghost btn-sm" type="button" :disabled="loadingStore" @click="loadStore">{{ t('common.refresh') }}</button>
          <button class="btn btn-aurora btn-sm" type="button" :disabled="savingStore" @click="saveStore">{{ t('games.saveCatalog') }}</button>
        </div>
      </div>
      <p class="small text-muted mb-3">{{ t('games.storeHint') }}</p>
      <LoadingSpinner v-if="loadingStore" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle mb-0">
          <thead>
            <tr>
              <th>SKU</th>
              <th>{{ t('common.title') }}</th>
              <th>{{ t('games.section') }}</th>
              <th>{{ t('common.coins') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in storeItems" :key="idx">
              <td><input v-model="item.sku" class="form-control form-control-sm" /></td>
              <td><input v-model="item.title" class="form-control form-control-sm" /></td>
              <td><input v-model="item.section" class="form-control form-control-sm" /></td>
              <td style="width:100px"><input v-model.number="item.costPoints" type="number" min="0" class="form-control form-control-sm" /></td>
              <td><button class="btn btn-sm btn-ghost" type="button" @click="storeItems.splice(idx,1)"><i class="bi bi-trash"></i></button></td>
            </tr>
          </tbody>
        </table>
        <button class="btn btn-ghost btn-sm mt-2" type="button" @click="storeItems.push({ sku: '', title: '', section: 'store', costPoints: 50 })">+ {{ t('games.addItem') }}</button>
      </div>
    </div>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="t('common.delete')"
      :message="confirmMsg"
      @confirm="doRemoveGame"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { settingsApi, gameStoreApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const games = ref([])
const storeItems = ref([])
const loading = ref(false)
const loadingStore = ref(false)
const saving = ref(false)
const savingStore = ref(false)
const savingBoss = ref(false)
const error = ref('')
const success = ref('')
const coverBroken = reactive({})
const editIdx = ref(null)
const editDraft = ref(null)
const boss = reactive({
  maxHp: 5000,
  maxAttacks: 20,
  minDamage: 8,
  maxDamage: 18,
  rewardCoins: 1000,
})

const ORIGIN = 'https://api.adnova.bbs.tr'
const MIKOO = [
  ['7updown', '7 Up Down'],
  ['cleopatra-slot', 'Cleopatra Slot'],
  ['cleopatra-slots', 'Cleopatra Slots'],
  ['crash', 'Crash'],
  ['fishing', 'Fishing'],
  ['football-plinko', 'Football Plinko'],
  ['fortune-slot', 'جواهر الحظ', 'Fortune Gems'],
  ['greedy-box', 'Greedy Box'],
  ['hilo', 'Hilo'],
  ['line-slots', 'Line Slots'],
  ['luck-car', 'Luck Car'],
  ['megaways-slots', 'Megaways Slots'],
  ['olympians', 'Olympians'],
  ['pirate-king', 'Pirate King'],
  ['royal-battle', 'Royal Battle'],
  ['slot777', 'Slot 777'],
  ['sugar-rush', 'Sugar Rush'],
  ['swimsuit-party', 'Swimsuit Party'],
]

function isLegacyHtmlGame(g) {
  if (!g) return true
  const id = String(g.id || '').toLowerCase()
  const playUrl = String(g.playUrl || '').toLowerCase()
  return id === 'lucky-wheel' || id === 'dice' || id === 'wheel'
    || id === 'xo' || id === 'tic_tac_toe' || id === 'tictactoe'
    || playUrl.includes('lucky-wheel')
    || playUrl.includes('/games/dice.html')
    || playUrl.endsWith('dice.html')
    || playUrl.includes('tic_tac_toe')
    || id === 'fireforce'
    || playUrl.includes('fireforce')
}

function buildDefaults() {
  let order = 10
  return MIKOO.map(([id, title, titleEn]) => ({
    id,
    title,
    titleEn: titleEn || title,
    coverUrl: `${ORIGIN}/games/mikoo/covers/${id}.png?v=20260728c`,
    playUrl: `${ORIGIN}/games/mikoo/${id}/index.html`,
    sortOrder: order++,
    mode: 'mikoo_slot',
    enabled: true,
  }))
}

const defaults = buildDefaults()

function normalizeGame(g) {
  return {
    ...g,
    enabled: g.enabled !== false && g.visible !== false,
    mode: g.mode || 'mikoo_slot',
  }
}

function displayTitle(g) {
  if (!g) return ''
  return (g.title && String(g.title).trim()) || g.titleEn || g.id || ''
}

function markCoverBroken(id) {
  if (id) coverBroken[id] = true
}

function onCoverError(ev) {
  ev.target.style.visibility = 'hidden'
}

function openEdit(idx) {
  const g = games.value[idx]
  if (!g) return
  editIdx.value = idx
  editDraft.value = {
    id: g.id,
    title: g.title || '',
    titleEn: g.titleEn || '',
    coverUrl: g.coverUrl || '',
    playUrl: g.playUrl || '',
    mode: g.mode || 'mikoo_slot',
    enabled: g.enabled !== false,
    sortOrder: g.sortOrder,
    bridge: g.bridge,
    bsModuleId: g.bsModuleId,
    gameType: g.gameType,
  }
}

function closeEdit() {
  editIdx.value = null
  editDraft.value = null
}

function applyEdit() {
  if (editIdx.value === null || !editDraft.value) return
  const i = editIdx.value
  const prev = games.value[i] || {}
  games.value[i] = normalizeGame({
    ...prev,
    ...editDraft.value,
    enabled: editDraft.value.enabled !== false,
  })
  if (editDraft.value.id) delete coverBroken[editDraft.value.id]
  closeEdit()
}

function toggleEnabled(idx) {
  const g = games.value[idx]
  if (!g) return
  g.enabled = g.enabled === false
}

function removeGame(idx) {
  pendingRemoveIdx.value = idx
  const g = games.value[idx]
  confirmMsg.value = `${t('common.delete')} «${displayTitle(g)}»؟`
  confirmOpen.value = true
}

const confirmOpen = ref(false)
const confirmMsg = ref('')
const pendingRemoveIdx = ref(-1)

function doRemoveGame() {
  const idx = pendingRemoveIdx.value
  pendingRemoveIdx.value = -1
  if (idx < 0) return
  games.value.splice(idx, 1)
  if (editIdx.value === idx) closeEdit()
  else if (editIdx.value !== null && editIdx.value > idx) editIdx.value -= 1
}

function resetDefaults() {
  Object.keys(coverBroken).forEach((k) => delete coverBroken[k])
  games.value = defaults.map(normalizeGame)
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await settingsApi.get()
  loading.value = false
  if (err) {
    error.value = err.message
    games.value = defaults.map(normalizeGame)
    return
  }
  const rows = Array.isArray(data) ? data : (data?.data || data || [])
  const setting = (key, fallback) => {
    const value = Number(Array.isArray(rows) ? rows.find((r) => r.key === key)?.value : NaN)
    return Number.isFinite(value) ? value : fallback
  }
  boss.maxHp = setting('games.boss.maxHp', boss.maxHp)
  boss.maxAttacks = setting('games.boss.maxAttacks', boss.maxAttacks)
  boss.minDamage = setting('games.boss.minDamage', boss.minDamage)
  boss.maxDamage = setting('games.boss.maxDamage', boss.maxDamage)
  boss.rewardCoins = setting('games.boss.rewardCoins', boss.rewardCoins)
  let found = null
  if (Array.isArray(rows)) found = rows.find((r) => r.key === 'app_games')
  if (found?.value) {
    try {
      const parsed = JSON.parse(found.value)
      const filtered = (Array.isArray(parsed) ? parsed : [])
        .filter((g) => !isLegacyHtmlGame(g))
        .map(normalizeGame)
      games.value = filtered.length ? filtered : defaults.map(normalizeGame)
      return
    } catch (_) { /* fallthrough */ }
  }
  games.value = defaults.map(normalizeGame)
}

async function loadStore() {
  loadingStore.value = true
  const { data, error: err } = await gameStoreApi.catalog()
  loadingStore.value = false
  if (err) {
    error.value = err.message
    storeItems.value = []
    return
  }
  const payload = data?.data ?? data ?? {}
  storeItems.value = (Array.isArray(payload.items) ? payload.items : (Array.isArray(payload) ? payload : [])).map((i) => ({ ...i }))
}

async function saveStore() {
  savingStore.value = true
  error.value = ''
  const { error: err } = await gameStoreApi.saveCatalog(storeItems.value)
  savingStore.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('games.catalogSaved')
    toast().success(success.value)
  }
}

async function save() {
  saving.value = true
  error.value = ''
  const payload = games.value
    .filter((g) => !isLegacyHtmlGame(g))
    .map((g, i) => ({
      ...g,
      sortOrder: i + 1,
      enabled: g.enabled !== false,
    }))
  const { error: err } = await settingsApi.update({ app_games: JSON.stringify(payload) })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    games.value = payload.map(normalizeGame)
    success.value = t('games.gamesSaved')
    toast().success(success.value)
  }
}

async function saveBoss() {
  savingBoss.value = true
  const { error: err } = await settingsApi.update({
    'games.boss.maxHp': String(Math.floor(boss.maxHp)),
    'games.boss.maxAttacks': String(Math.floor(boss.maxAttacks)),
    'games.boss.minDamage': String(Math.floor(boss.minDamage)),
    'games.boss.maxDamage': String(Math.floor(boss.maxDamage)),
    'games.boss.rewardCoins': String(Math.floor(boss.rewardCoins)),
  })
  savingBoss.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('games.bossSaved')
    toast().success(success.value)
  }
}

onMounted(() => {
  load()
  loadStore()
})
</script>

<style scoped>
.catalog-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(128px, 1fr));
  gap: 14px;
}

.catalog-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  overflow: hidden;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.catalog-card:hover {
  transform: translateY(-3px);
  border-color: rgba(255, 255, 255, 0.18);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.28);
}

.catalog-card.is-off {
  opacity: 0.62;
}

.catalog-card__art {
  position: relative;
  aspect-ratio: 1 / 1;
  background:
    radial-gradient(circle at 30% 20%, rgba(255, 255, 255, 0.12), transparent 55%),
    rgba(0, 0, 0, 0.28);
}

.catalog-card__art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.catalog-card__fallback {
  width: 100%;
  height: 100%;
  min-height: 96px;
  display: grid;
  place-items: center;
  font-size: 2rem;
  color: rgba(255, 255, 255, 0.35);
}

.catalog-card__veil {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(8, 10, 18, 0.55);
  backdrop-filter: blur(2px);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.catalog-card__toolbar {
  position: absolute;
  inset-inline: 8px;
  bottom: 8px;
  display: flex;
  justify-content: center;
  gap: 6px;
  opacity: 0;
  transform: translateY(6px);
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.catalog-card:hover .catalog-card__toolbar,
.catalog-card:focus-within .catalog-card__toolbar {
  opacity: 1;
  transform: translateY(0);
}

.tool-btn {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 999px;
  display: grid;
  place-items: center;
  color: #fff;
  background: rgba(12, 14, 22, 0.78);
  backdrop-filter: blur(8px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
}

.tool-btn:hover {
  background: rgba(255, 255, 255, 0.18);
}

.tool-btn--danger:hover {
  background: rgba(220, 53, 69, 0.85);
}

.catalog-card__meta {
  padding: 10px 8px 12px;
  text-align: center;
}

.catalog-card__title {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.7em;
}

.catalog-card__sub {
  margin: 4px 0 0;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.45);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  direction: ltr;
}

.edit-overlay {
  position: fixed;
  inset: 0;
  z-index: 1050;
  display: grid;
  place-items: center;
  padding: 1rem;
  background: rgba(4, 6, 12, 0.62);
  backdrop-filter: blur(6px);
}

.edit-panel {
  width: min(440px, 100%);
  padding: 1.15rem;
  border-radius: 18px;
}

.edit-panel__head {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  margin-bottom: 1rem;
}

.edit-panel__preview {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  overflow: hidden;
  flex: 0 0 64px;
  background: rgba(0, 0, 0, 0.25);
}

.edit-panel__preview img,
.edit-panel__preview .catalog-card__fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
  min-height: 0;
  font-size: 1.4rem;
}

.font-monospace {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

@media (hover: none) {
  .catalog-card__toolbar {
    opacity: 1;
    transform: none;
  }
}

@media (min-width: 1200px) {
  .catalog-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  }
}
</style>
