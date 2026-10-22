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
    <div v-if="pageRuntimeError" class="alert alert-danger d-flex flex-wrap justify-content-between align-items-center gap-2" role="alert">
      <span>تعذّر عرض جزء من صفحة الألعاب: {{ pageRuntimeError }}</span>
      <button class="btn btn-sm btn-outline-light" type="button" @click="retryPage">إعادة تحميل بيانات الألعاب</button>
    </div>
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="glass p-3 mb-3 small text-muted">
      {{ t('games.hint') }}
    </div>

    <nav class="glass p-3 mb-4 d-flex flex-wrap gap-2" aria-label="أقسام إدارة الألعاب">
      <a class="btn btn-outline-light btn-sm" href="#game-catalog">قائمة الألعاب وإظهارها</a>
      <a class="btn btn-outline-light btn-sm" href="#game-odds">الإعدادات العامة وحدود الرهان</a>
      <a class="btn btn-outline-light btn-sm" href="#game-runtime-controls">تشغيل وإيقاف الألعاب</a>
      <a class="btn btn-outline-light btn-sm" href="#game-win-rates">نسب الفوز</a>
      <a class="btn btn-outline-light btn-sm" href="#game-store">متجر الألعاب</a>
    </nav>

    <div id="game-catalog" class="glass p-3">
      <LoadingSpinner v-if="loading && !safeGames.length" />
      <div v-else-if="!safeGames.length" class="empty-state p-5 text-center">
        <i class="bi bi-controller fs-1 d-block mb-2"></i>
        <strong class="d-block">قائمة الألعاب فارغة — استعد القائمة الأساسية</strong>
        <p class="small text-muted mt-2 mb-3">استعادة القائمة لا تغيّر إعدادات نسب الفوز. بعد الاستعادة اضغط «حفظ إلى التطبيق» لتثبيت الألعاب في الخادم.</p>
        <div class="d-flex justify-content-center flex-wrap gap-2">
          <button class="btn btn-outline-light btn-sm" type="button" @click="load">إعادة تحميل البيانات</button>
          <button class="btn btn-aurora btn-sm" type="button" @click="resetDefaults">استعادة الألعاب الأساسية</button>
          <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="resetDefaults(); save()">استعادة وحفظ الألعاب</button>
        </div>
      </div>
      <div v-else class="catalog-grid widget-grid">
        <article
          v-for="(g, idx) in safeGames"
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

    <div id="game-runtime-controls" class="glass p-3 mb-4">
      <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
        <div><h3 class="h6 mb-1">التحكم بتشغيل وإيقاف كل الألعاب</h3><p class="small text-muted mb-0">كل لعبة لها مفتاح مستقل. احفظ التغييرات لتطبيق حالة التشغيل من إعدادات الخادم.</p><div class="small text-muted mt-1">عدد الألعاب المتاحة للإدارة: {{ manageableGames.length }}</div></div>
        <div class="d-flex flex-wrap gap-2"><button class="btn btn-outline-light btn-sm" type="button" :disabled="savingOdds" @click="setAllGamesEnabled(false)">إيقاف الكل</button><button class="btn btn-outline-light btn-sm" type="button" :disabled="savingOdds" @click="setAllGamesEnabled(true)">تشغيل الكل</button><button class="btn btn-aurora btn-sm" type="button" :disabled="savingOdds" @click="saveOdds">حفظ حالة الألعاب</button></div>
      </div>
      <div class="row g-2">
        <div v-for="game in manageableGames" :key="game.id" class="col-6 col-md-4 col-lg-3">
          <label class="form-check form-switch border rounded p-2 ps-5 d-block h-100"><input class="form-check-input" type="checkbox" :checked="!disabledGames.includes(game.id)" @change="setGameEnabled(game.id, $event.target.checked)" /><span class="small">{{ game.label }}</span><span class="d-block small" :class="disabledGames.includes(game.id) ? 'text-danger' : 'text-success'">{{ disabledGames.includes(game.id) ? 'متوقفة' : 'مفعّلة' }}</span></label>
        </div>
      </div>
    </div>

    <div id="game-odds" class="glass p-3 mb-4">
      <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
        <div>
          <h3 class="h6 mb-1">{{ t('games.oddsTitle') }}</h3>
          <div class="small text-muted">{{ t('games.oddsHint') }}</div>
        </div>
        <div class="d-flex flex-wrap gap-2 align-items-center">
          <span class="badge bg-success-subtle text-success-emphasis">
            {{ t('games.houseEdge') }}: {{ houseEdgePct }}%
          </span>
          <span class="badge bg-info-subtle text-info-emphasis">
            {{ t('games.playerRtp') }}: {{ playerRtpPct }}%
          </span>
          <button class="btn btn-outline-light btn-sm" type="button" :disabled="savingOdds" @click="applyOddsPreset('conservative')">
            {{ t('games.presetConservative') }}
          </button>
          <button class="btn btn-outline-light btn-sm" type="button" :disabled="savingOdds" @click="applyOddsPreset('balanced')">
            {{ t('games.presetBalanced') }}
          </button>
          <button class="btn btn-outline-light btn-sm" type="button" :disabled="savingOdds" @click="applyOddsPreset('generous')">
            {{ t('games.presetGenerous') }}
          </button>
          <button class="btn btn-aurora btn-sm" type="button" :disabled="savingOdds" @click="saveOdds">
            {{ t('games.saveOdds') }}
          </button>
        </div>
      </div>
      <div class="row g-2">
        <div class="col-md-3">
          <label class="form-label small">{{ t('games.playerRtp') }} (0.55–0.82)</label>
          <input v-model.number="odds.playerRtp" type="number" min="0.55" max="0.82" step="0.01" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('games.spinHitRate') }}</label>
          <input v-model.number="odds.spinHitRate" type="number" min="0.05" max="0.22" step="0.005" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('games.fishingHitRate') }}</label>
          <input v-model.number="odds.fishingHitRate" type="number" min="0.04" max="0.18" step="0.005" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('games.crashCashoutRake') }}</label>
          <input v-model.number="odds.crashCashoutRake" type="number" min="0.9" max="0.99" step="0.01" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('games.hiloPayoutMult') }}</label>
          <input v-model.number="odds.hiloPayoutMult" type="number" min="1.15" max="1.7" step="0.05" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('games.royalBattlePayGate') }}</label>
          <input v-model.number="odds.royalBattlePayGate" type="number" min="0.2" max="0.55" step="0.01" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('games.maxBet') }}</label>
          <input v-model.number="odds.maxBet" type="number" min="100" max="50000" step="100" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('games.maxWinMult') }}</label>
          <input v-model.number="odds.maxWinMult" type="number" min="3" max="25" step="1" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('games.maxWinAbsolute') }}</label>
          <input v-model.number="odds.maxWinAbsolute" type="number" min="1000" max="500000" step="1000" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('games.maxCrashRatio') }}</label>
          <input v-model.number="odds.maxCrashRatio" type="number" min="3" max="20" step="1" class="form-control" />
        </div>
      </div>
      <div class="small text-muted mt-2">{{ t('games.oddsSafety') }}</div>
      <div class="small mt-2">
        <span class="text-muted">{{ t('games.oddsCoverage', { count: coveredCount }) }}</span>
        <span class="ms-1">{{ coveredGamesLabel }}</span>
      </div>
    </div>



    <div id="game-win-rates" class="glass p-3 mb-4">
      <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
        <div>
          <h3 class="h6 mb-1">نسبة الفوز لكل لعبة</h3>
          <p class="small text-muted mb-0">نسبة موحّدة لكل المستخدمين. هذه الإعدادات لا تُطبّق على نتائج يحسمها مزوّد خارجي ما لم يدعم المزوّد التحكم بها.</p>
        </div>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="savingOdds" @click="saveOdds">حفظ نسب الفوز</button>
      </div>
      <div class="row g-2">
        <div v-for="game in manageableGames" :key="'rate-'+game.id" class="col-6 col-md-4 col-lg-3">
          <label class="form-label small mb-1">{{ game.label }} — نسبة الفوز %</label>
          <input :value="gameWinRates[game.id] ?? ''" @input="gameWinRates[game.id] = $event.target.value === '' ? undefined : Math.max(0, Math.min(100, Number($event.target.value)))" type="number" min="0" max="100" step="1" class="form-control form-control-sm" :disabled="!supportsDirectWinRate(game.id)" />
          <div class="small mt-1" :class="supportsDirectWinRate(game.id) ? 'text-success' : 'text-muted'">{{ supportsDirectWinRate(game.id) ? 'تُطبّق من الخادم' : 'نتيجة مشتركة/محرك خارجي؛ يلزم ضبط منفصل' }}</div>
        </div>
      </div>
      <div class="small text-muted mt-2">0% = لا فوز في ألعاب الخادم التي تدعم هذا الضبط، 100% = فوز في كل محاولة مؤهلة. قد تبقى حدود الجوائز والحد الأقصى للربح مطبّقة.</div>
    </div>

    <div class="glass p-3 mb-4">
      <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
        <div>
          <h3 class="h6 mb-1">نسبة العائد للألعاب ذات النتيجة المشتركة</h3>
          <p class="small text-muted mb-0">تؤثر على اختيار نتيجة الجولة المشتركة، وليست ضمانًا أن يربح أو يخسر كل لاعب في الجولة نفسها.</p>
        </div>
      </div>
      <div class="row g-2">
        <div v-for="game in manageableGames.filter(g => supportsSharedRtp(game.id))" :key="'rtp-'+game.id" class="col-6 col-md-4 col-lg-3">
          <label class="form-label small mb-1">{{ game.label }} — عائد اللاعب %</label>
          <input :value="Math.round(Number(gameRtpRates[game.id] ?? odds.playerRtp) * 100)" @input="gameRtpRates[game.id] = Math.max(55, Math.min(82, Number($event.target.value) || 55)) / 100" type="number" min="55" max="82" step="1" class="form-control form-control-sm" />
        </div>
      </div>
      <div class="small text-muted mt-2">يُحفظ هذا الإعداد مع زر «حفظ نسب الفوز» أعلاه. ألعاب Crash لها إعدادات مستقلة لخصم السحب وحدّ المضاعف في القسم الأول.</div>
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
          <label class="form-label small">{{ t('games.cover') || 'غلاف اللعبة' }}</label>
          <input type="file" accept="image/*,.webp" class="form-control" @change="onCoverFile" />
          <div class="form-text">اختر صورة الغلاف — بدون رابط</div>
          <img
            v-if="editDraft.coverUrl"
            :src="editDraft.coverUrl"
            alt=""
            class="mt-2 rounded-circle"
            style="width: 96px; height: 96px; object-fit: cover"
          />
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

    <div id="game-store" class="glass p-3 mt-4">
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
import { computed, onMounted, onErrorCaptured, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { gamesCatalogApi, gameStoreApi, uploadsApi, gameOddsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const games = ref([])
// Never let malformed/null rows from a stale catalog crash the entire admin page.
const safeGames = computed(() => (Array.isArray(games.value) ? games.value : [])
  .filter((game) => game && typeof game === 'object' && !Array.isArray(game))
  .map((game, index) => ({ ...game, id: String(game.id || `recovered-game-${index}`) })))
const storeItems = ref([])
const loading = ref(false)
const loadingStore = ref(false)
const saving = ref(false)
const savingStore = ref(false)
const savingOdds = ref(false)
const error = ref('')
const success = ref('')
const pageRuntimeError = ref('')
const coverBroken = reactive({})
const editIdx = ref(null)
const editDraft = ref(null)
const odds = reactive({
  playerRtp: 0.7,
  spinHitRate: 0.115,
  fishingHitRate: 0.09,
  crashCashoutRake: 0.97,
  hiloPayoutMult: 1.4,
  royalBattlePayGate: 0.36,
  maxBet: 10000,
  maxWinMult: 12,
  maxWinAbsolute: 80000,
  maxCrashRatio: 8,
})
const coveredGames = ref([])
const disabledGames = ref([])
const gameWinRates = reactive({})
const gameRtpRates = reactive({})
const manageableGames = [
  ...['7updown','bounty-football','camel-racing','cleopatra-slot','cleopatra-slots','crash','fishing','football-plinko','fortune-slot','greedy-box','greedy-lion','hilo','line-slots','luck-car','lucky77','megaways-slots','olympians','pirate-king','royal-battle','slot777','sugar-rush','swimsuit-party'].map(id => ({ id, label: id })),
  { id: 'slots', label: 'سلوت مصغّر' }, { id: 'roulette', label: 'الروليت' }, { id: 'plinko', label: 'بلينكو مصغّر' }, { id: 'rps', label: 'حجر ورقة مقص' }, { id: 'blackjack', label: 'بلاك جاك مصغّر' }, { id: 'poker', label: 'بوكر النرد' }, { id: 'tarot', label: 'التاروت' }, { id: 'penalty', label: 'ركلة الجزاء' }, { id: 'reaction', label: 'رد الفعل' }, { id: 'witch', label: 'العرافة' }, { id: 'dice', label: 'النرد' }, { id: 'lucky-wheel', label: 'عجلة الحظ' }, { id: 'ono', label: 'أونو' }, { id: 'domino', label: 'الدومينو' }, { id: 'ludo', label: 'لودو' },
]
const directWinRateGameIds = new Set([
  'slots','roulette','plinko','crash','rps','hilo','witch','blackjack','poker','tarot','penalty','reaction',
  'dice','lucky-wheel','cleopatra-slot','cleopatra-slots','fishing','football-plinko','fortune-slot',
  'line-slots','megaways-slots','olympians','pirate-king','slot777','sugar-rush','swimsuit-party',
  'royal-battle',
])
function supportsDirectWinRate(gameId) { return directWinRateGameIds.has(String(gameId || '').toLowerCase()) }
const sharedOutcomeRtpGameIds = new Set(['7updown','bounty-football','camel-racing','greedy-box','greedy-lion','luck-car','lucky77'])
function supportsSharedRtp(gameId) { return sharedOutcomeRtpGameIds.has(String(gameId || '').toLowerCase()) }

const coveredCount = computed(() => coveredGames.value.length || 22)
const coveredGamesLabel = computed(() =>
  coveredGames.value.length ? coveredGames.value.join(' · ') : t('games.oddsCoverageAll'),
)
const playerRtpPct = computed(() => Math.round((Number(odds.playerRtp) || 0) * 1000) / 10)
const houseEdgePct = computed(() => Math.round((1 - (Number(odds.playerRtp) || 0)) * 1000) / 10)

function applyOddsPayload(data) {
  if (!data || typeof data !== 'object') return
  ;[
    'playerRtp',
    'spinHitRate',
    'fishingHitRate',
    'crashCashoutRake',
    'hiloPayoutMult',
    'royalBattlePayGate',
    'maxBet',
    'maxWinMult',
    'maxWinAbsolute',
    'maxCrashRatio',
  ].forEach((k) => {
    if (data[k] != null && Number.isFinite(Number(data[k]))) odds[k] = Number(data[k])
  })
  if (Array.isArray(data.coveredGames)) coveredGames.value = data.coveredGames.map(String)
  if (Array.isArray(data.disabledGames)) disabledGames.value = data.disabledGames.map(String)
  if (data.gameWinRates && typeof data.gameWinRates === 'object') Object.entries(data.gameWinRates).forEach(([id, value]) => { if (Number.isFinite(Number(value))) gameWinRates[id] = Math.max(0, Math.min(100, Number(value))) })
  if (data.gameRtpRates && typeof data.gameRtpRates === 'object') Object.entries(data.gameRtpRates).forEach(([id, value]) => { if (Number.isFinite(Number(value))) gameRtpRates[id] = Math.max(0.55, Math.min(0.82, Number(value))) })
}

async function loadOdds() {
  try {
    const { data, error: err } = await gameOddsApi.get()
    if (err) {
      error.value = 'تعذّر تحميل إعدادات التحكم بالألعاب: ' + err.message
      return
    }
    applyOddsPayload(data?.data ?? data)
  } catch (err) {
    error.value = 'تعذّر تحميل إعدادات التحكم بالألعاب: ' + (err?.message || String(err))
  }
}

function setGameEnabled(gameId, enabled) {
  const id = String(gameId || '').trim().toLowerCase()
  if (!id) return
  disabledGames.value = enabled
    ? disabledGames.value.filter((item) => item !== id)
    : [...new Set([...disabledGames.value, id])]
}

function setAllGamesEnabled(enabled) {
  disabledGames.value = enabled ? [] : manageableGames.map((game) => game.id)
}

async function saveOdds() {
  savingOdds.value = true
  error.value = ''
  const { data, error: err } = await gameOddsApi.update({ ...odds, disabledGames: [...disabledGames.value], gameWinRates: Object.fromEntries(manageableGames.filter(g => supportsDirectWinRate(g.id) && gameWinRates[g.id] !== undefined && gameWinRates[g.id] !== '').map(g => [g.id, Math.max(0, Math.min(100, Number(gameWinRates[g.id])))])), gameRtpRates: Object.fromEntries(manageableGames.filter(g => supportsSharedRtp(g.id)).map(g => [g.id, Number.isFinite(Number(gameRtpRates[g.id])) ? Math.max(0.55, Math.min(0.82, Number(gameRtpRates[g.id]))) : Number(odds.playerRtp)])) })
  savingOdds.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  applyOddsPayload(data?.data ?? data)
  success.value = t('games.oddsSaved')
  toast().success(success.value)
}

async function applyOddsPreset(preset) {
  savingOdds.value = true
  error.value = ''
  const { data, error: err } = await gameOddsApi.applyPreset(preset)
  savingOdds.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  applyOddsPayload(data?.data ?? data)
  success.value = t('games.oddsSaved')
  toast().success(success.value)
}

const ORIGIN = (() => {
  const configured = String(import.meta.env.VITE_API_BASE_URL || '').trim()
  const fallback = typeof window !== 'undefined' ? window.location.origin : ''
  return (configured || fallback).replace(/\/api\/v1\/?$/i, '').replace(/\/+$/, '')
})()
const MIKOO = [
  ['7updown', '٧ فوق تحت', '7 Up Down'],
  ['bounty-football', 'كرة القدم الجوائز', 'Bounty Football'],
  ['camel-racing', 'سباق الأسود', 'Camel Racing'],
  ['cleopatra-slot', 'كليوباترا', 'Cleopatra Slot'],
  ['cleopatra-slots', 'فتحات كليوباترا', 'Cleopatra Spins'],
  ['crash', 'كراش', 'Crash'],
  ['fishing', 'صيد السمك', 'Fishing'],
  ['football-plinko', 'بلينكو كرة القدم', 'Football Plinko'],
  ['fortune-slot', 'جواهر الحظ', 'Fortune Gems'],
  ['greedy-box', 'صندوق الطمع', 'Greedy Box'],
  ['greedy-lion', 'الأسد الطماع', 'Greedy Lion'],
  ['hilo', 'هاي لو', 'Hilo'],
  ['line-slots', 'فتحات الخط', 'Line slots'],
  ['luck-car', 'سيارة الحظ', 'Lucky Car'],
  ['lucky77', 'لاكي ٧٧', 'Lucky 77'],
  ['megaways-slots', 'ميجاوايز', 'Megaways Slots'],
  ['olympians', 'الأوليمبيون', 'Olympians'],
  ['pirate-king', 'ملك القراصنة', 'Pirate King'],
  ['royal-battle', 'المعركة الملكية', 'Royal Battle'],
  ['slot777', 'سلوت ٧٧٧', 'Slot 777'],
  ['sugar-rush', 'سكر راش', 'Sugar Rush'],
  ['swimsuit-party', 'حفلة السباحة', 'Swimsuit Party'],
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
    coverUrl: `${ORIGIN}/games/mikoo/covers/${id}.png?v=20260804g`,
    playUrl: `${ORIGIN}/games/mikoo/${id}/index.html`,
    sortOrder: order++,
    mode: 'mikoo_slot',
    enabled: true,
  }))
}

const defaults = buildDefaults()
// Render the known catalog immediately; slow settings requests must not look like an empty route.
games.value = defaults.map((game) => ({ ...game }))

function normalizeGame(g) {
  const normalizeGameUrl = (value) => {
    const raw = String(value || '').trim()
    if (!raw) return ''
    // Old saved catalog rows still contain the retired API host; keep their game
    // IDs/settings but resolve bundled cover/play assets through the current backend.
    if (/^https?:\/\/api\.adnova\.bbs\.tr(?=\/|$)/i.test(raw)) {
      return raw.replace(/^https?:\/\/api\.adnova\.bbs\.tr/i, ORIGIN)
    }
    if (raw.startsWith('/')) return `${ORIGIN}${raw}`
    if (!/^https?:\/\//i.test(raw) && !raw.startsWith('data:')) {
      return `${ORIGIN}/${raw.replace(/^\/+/, '')}`
    }
    return raw
  }
  return {
    ...g,
    coverUrl: normalizeGameUrl(g.coverUrl),
    playUrl: normalizeGameUrl(g.playUrl),
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

async function onCoverFile(e) {
  const file = e.target.files?.[0]
  if (!file || !editDraft.value) return
  const { data, error: err } = await uploadsApi.upload(file)
  if (err) {
    toast().danger(err.message)
    return
  }
  let url = data?.url || data?.data?.url || ''
  if (url && !String(url).startsWith('http')) {
    url = `${ORIGIN}${String(url).startsWith('/') ? '' : '/'}${url}`
  }
  editDraft.value.coverUrl = url
  toast().success(t('app.success') || 'تم')
  e.target.value = ''
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
  if (!games.value.length) games.value = defaults.map(normalizeGame)
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
  // Keep the known catalog visible even if the API returns an empty/stale value.
  // This prevents the Games dashboard from rendering an empty catalog after backend sleep
  // or when the app_games setting has not been initialized yet.
  const fallbackCatalog = () => defaults.map(normalizeGame)
  if (!Array.isArray(games.value) || games.value.length === 0) {
    games.value = fallbackCatalog()
  }
  try {
    const response = await gamesCatalogApi.get()
    if (response?.error) {
      error.value = 'تعذّر تحميل قائمة الألعاب المحفوظة؛ تم عرض القائمة الأساسية مؤقتاً. ' + response.error.message
      games.value = fallbackCatalog()
      return
    }

    const payload = response?.data?.data ?? response?.data ?? {}
    const rawCatalog = typeof payload === 'string'
      ? payload
      : (payload?.value ?? payload?.catalog ?? '')
    let saved = []
    if (Array.isArray(rawCatalog)) {
      saved = rawCatalog
    } else if (String(rawCatalog || '').trim()) {
      try {
        const parsed = JSON.parse(String(rawCatalog))
        if (Array.isArray(parsed)) saved = parsed
      } catch (_) {
        error.value = 'قائمة الألعاب المحفوظة غير صالحة؛ تم عرض القائمة الأساسية مؤقتاً.'
      }
    }

    const merged = new Map()
    // Add defaults first, then overlay saved values so missing/deleted rows cannot blank the page.
    for (const game of defaults) {
      const normalized = normalizeGame(game)
      merged.set(String(normalized.id || '').toLowerCase(), normalized)
    }
    for (const game of saved.filter((item) => item && !isLegacyHtmlGame(item))) {
      const normalized = normalizeGame(game)
      const id = String(normalized.id || '').trim().toLowerCase()
      if (!id) continue
      merged.set(id, { ...(merged.get(id) || {}), ...normalized })
    }

    games.value = [...merged.values()].sort((a, b) => {
      const ao = Number(a.sortOrder)
      const bo = Number(b.sortOrder)
      return (Number.isFinite(ao) ? ao : 9999) - (Number.isFinite(bo) ? bo : 9999)
    })
    if (!games.value.length) games.value = fallbackCatalog()
  } catch (err) {
    error.value = 'حدث خطأ أثناء تحميل الألعاب؛ تم عرض القائمة الأساسية مؤقتاً. ' + (err?.message || String(err))
    games.value = fallbackCatalog()
  } finally {
    loading.value = false
  }
}

async function loadStore() {
  loadingStore.value = true
  try {
    const { data, error: err } = await gameStoreApi.catalog()
    if (err) {
      error.value = 'تعذّر تحميل متجر الألعاب: ' + err.message
      storeItems.value = []
      return
    }
    const payload = data?.data ?? data ?? {}
    storeItems.value = (Array.isArray(payload.items) ? payload.items : (Array.isArray(payload) ? payload : [])).map((i) => ({ ...i }))
  } catch (err) {
    error.value = 'تعذّر تحميل متجر الألعاب: ' + (err?.message || String(err))
    storeItems.value = []
  } finally {
    loadingStore.value = false
  }
}

async function retryPage() {
  pageRuntimeError.value = ''
  await Promise.all([load(), loadStore(), loadOdds()])
}

onErrorCaptured((err, instance, info) => {
  pageRuntimeError.value = [
    err?.message || String(err || 'خطأ غير معروف'),
    info ? `Vue phase: ${info}` : '',
    err?.stack || '',
  ].filter(Boolean).join('\n\n')
  console.error('[JEHO Games] captured component error', { error: err, info, component: instance?.$options?.name || instance?.$?.type?.__name || 'unknown' })
  return false
})

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
  const { error: err } = await gamesCatalogApi.save(JSON.stringify(payload))
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

onMounted(() => {
  load()
  loadStore()
  loadOdds()
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
  align-items: center;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  overflow: visible;
  padding: 14px 10px 12px;
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
  width: min(100%, 128px);
  aspect-ratio: 1 / 1;
  border-radius: 50%;
  overflow: hidden;
  background:
    radial-gradient(circle at 30% 20%, rgba(255, 255, 255, 0.12), transparent 55%),
    rgba(0, 0, 0, 0.28);
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.12), 0 8px 20px rgba(0, 0, 0, 0.28);
}

.catalog-card__art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 50%;
}

.catalog-card__fallback {
  width: 100%;
  height: 100%;
  min-height: 96px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 2rem;
  color: rgba(255, 255, 255, 0.35);
}

.catalog-card__veil {
  position: absolute;
  inset: 0;
  border-radius: 50%;
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
  inset-inline: 6px;
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
  border-radius: 50%;
  overflow: hidden;
  flex: 0 0 64px;
  background: rgba(0, 0, 0, 0.25);
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.12);
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
