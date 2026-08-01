<template>
  <div>
    <PageHeader title="كأس الروم" subtitle="موسم الغرف · ترتيب بصري + جوائز المراكز" />
    <AlertMessage v-if="error" :message="error" type="danger" class="mb-3" @dismiss="error = ''" />
    <AlertMessage v-if="ok" :message="ok" type="success" class="mb-3" @dismiss="ok = ''" />

    <div class="glass p-3 mb-3">
      <div class="row g-3 align-items-end">
        <div class="col-md-3">
          <div class="form-check form-switch">
            <input id="cupOn" v-model="cfg.enabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="cupOn">تفعيل كأس الروم</label>
          </div>
        </div>
        <div class="col-md-3">
          <label class="form-label">مدة الموسم</label>
          <select v-model="cfg.period" class="form-select">
            <option value="weekly">أسبوعي</option>
            <option value="monthly">شهري</option>
          </select>
        </div>
        <div class="col-md-3">
          <label class="form-label">آي دي الروم الرسمي (UUID)</label>
          <input v-model.trim="cfg.officialRoomId" class="form-control" placeholder="UUID" />
        </div>
        <div class="col-md-3 d-flex gap-2 flex-wrap">
          <button class="btn btn-aurora" type="button" :disabled="saving" @click="saveCfg">حفظ</button>
          <button class="btn btn-outline-warning" type="button" :disabled="ending" @click="askEnd = true">
            إنهاء الموسم
          </button>
        </div>
      </div>
      <div class="mt-3">
        <h6 class="mb-2">جوائز المراكز</h6>
        <div class="widget-grid mb-3">
          <div v-for="(p, idx) in prizeCards" :key="idx" class="widget-card">
            <div class="widget-card-body">
              <div class="d-flex justify-content-between align-items-center mb-2">
                <span class="badge text-bg-warning text-dark">المركز {{ p.rank }}</span>
                <button class="btn btn-sm btn-ghost" type="button" @click="removePrize(idx)">
                  <i class="bi bi-x-lg" />
                </button>
              </div>
              <div class="row g-2">
                <div class="col-6">
                  <label class="form-label small">عملات</label>
                  <input v-model.number="p.coins" type="number" min="0" class="form-control form-control-sm" />
                </div>
                <div class="col-6">
                  <label class="form-label small">ألماس</label>
                  <input v-model.number="p.diamonds" type="number" min="0" class="form-control form-control-sm" />
                </div>
                <div class="col-6">
                  <label class="form-label small">VIP</label>
                  <input v-model.number="p.vipLevel" type="number" min="0" class="form-control form-control-sm" />
                </div>
                <div class="col-6">
                  <label class="form-label small">أيام VIP</label>
                  <input v-model.number="p.vipDays" type="number" min="1" class="form-control form-control-sm" />
                </div>
              </div>
            </div>
          </div>
        </div>
        <button class="btn btn-sm btn-ghost" type="button" @click="addPrize">
          <i class="bi bi-plus-lg me-1" /> إضافة جائزة
        </button>
      </div>
    </div>

    <div class="d-flex justify-content-between align-items-center mb-3">
      <h5 class="mb-0">الترتيب · {{ board.seasonKey || '—' }} · {{ periodLabel }}</h5>
      <button class="btn btn-sm btn-ghost" type="button" @click="loadBoard">تحديث</button>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else class="widget-grid">
      <div v-if="!(board.items || []).length" class="glass p-4 empty-state">لا بيانات بعد</div>
      <article v-for="item in board.items || []" :key="item.roomId" class="widget-card">
        <div class="position-relative">
          <img
            v-if="item.coverUrl"
            :src="absUrl(item.coverUrl)"
            class="cup-cover"
            alt=""
          />
          <div
            v-else
            class="cup-cover d-flex align-items-center justify-content-center text-muted"
          >
            <i class="bi bi-trophy" style="font-size: 2rem" />
          </div>
          <span class="cup-rank-badge">{{ item.rank }}</span>
        </div>
        <div class="widget-card-body">
          <h3 class="widget-card-title">{{ item.title }}</h3>
          <div class="widget-card-meta mb-2">
            ID {{ item.publicId || item.roomId }}
            <span v-if="item.isOfficial" class="badge bg-primary ms-1">رسمي</span>
            <span v-if="item.cupBadgeSeason" class="badge bg-warning text-dark ms-1">كأس</span>
          </div>
          <div class="d-flex justify-content-between align-items-center">
            <span class="small text-muted">{{ item.hostName || '—' }}</span>
            <span class="fw-bold text-warning">{{ formatNumber(item.score) }}</span>
          </div>
        </div>
      </article>
    </div>

    <ConfirmDialog
      v-model="askEnd"
      title="إنهاء الموسم؟"
      message="سيتم منح الجوائز وشارات الفائزين وبدء موسم جديد."
      confirm-label="إنهاء ومنح"
      @confirm="endSeason"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { roomCupApi, settingsApi } from '@/api'
import { formatNumber } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const ORIGIN = (import.meta.env.VITE_API_ORIGIN || 'https://api.adnova.bbs.tr').replace(/\/$/, '')

const cfg = reactive({
  enabled: true,
  period: 'weekly',
  officialRoomId: '',
})
const prizeCards = ref([
  { rank: 1, coins: 100000, diamonds: 0, vipLevel: 3, vipDays: 30 },
  { rank: 2, coins: 50000, diamonds: 0, vipLevel: 2, vipDays: 30 },
  { rank: 3, coins: 20000, diamonds: 0, vipLevel: 1, vipDays: 30 },
])
const board = ref({ items: [], seasonKey: '' })
const loading = ref(false)
const saving = ref(false)
const ending = ref(false)
const error = ref('')
const ok = ref('')
const askEnd = ref(false)

const periodLabel = computed(() => (cfg.period === 'monthly' ? 'شهري' : 'أسبوعي'))

function absUrl(u) {
  if (!u) return ''
  if (/^https?:\/\//i.test(u)) return u
  return `${ORIGIN}${u.startsWith('/') ? '' : '/'}${u}`
}

function addPrize() {
  const next = (prizeCards.value.at(-1)?.rank || 0) + 1
  prizeCards.value.push({ rank: next, coins: 0, diamonds: 0, vipLevel: 0, vipDays: 30 })
}

function removePrize(idx) {
  prizeCards.value.splice(idx, 1)
}

async function loadCfg() {
  error.value = ''
  const { data, error: err } = await settingsApi.get()
  if (err) {
    error.value = err.message || 'تعذر تحميل إعدادات كأس الروم'
    return
  }
  const rows = Array.isArray(data) ? data : data?.items || data?.data || []
  const list = Array.isArray(rows) ? rows : []
  const map = Object.fromEntries(list.map((r) => [r.key, r.value]))
  cfg.enabled = String(map['room_cup.enabled'] ?? 'true') !== 'false'
  cfg.period = map['room_cup.period'] || 'weekly'
  cfg.officialRoomId = map['official_room_id'] || ''
  if (map['room_cup.prizes']) {
    try {
      const parsed = JSON.parse(map['room_cup.prizes'])
      if (Array.isArray(parsed) && parsed.length) prizeCards.value = parsed
    } catch {
      /* keep defaults */
    }
  }
}

async function saveCfg() {
  ok.value = ''
  error.value = ''
  saving.value = true
  const prizesJson = JSON.stringify(prizeCards.value)
  const { error: err } = await settingsApi.update({
    'room_cup.enabled': String(!!cfg.enabled),
    'room_cup.period': cfg.period,
    'room_cup.prizes': prizesJson,
    official_room_id: cfg.officialRoomId || '',
  })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    ok.value = 'تم الحفظ'
    toast().success('تم حفظ إعدادات كأس الروم')
  }
}

async function loadBoard() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await roomCupApi.leaderboard()
  loading.value = false
  if (err) {
    error.value = err.message || 'تعذر تحميل ترتيب كأس الروم'
    board.value = { items: [], seasonKey: '' }
    return
  }
  board.value = data?.data || data || { items: [] }
  if (!board.value.items) board.value.items = []
}

async function endSeason() {
  ending.value = true
  error.value = ''
  const { error: err } = await roomCupApi.endSeason()
  ending.value = false
  if (err) {
    error.value = err.message || 'فشل إنهاء الموسم'
    toast().danger(error.value)
    return
  }
  ok.value = 'تم إغلاق الموسم'
  toast().success('تم إنهاء الموسم ومنح الجوائز')
  await loadBoard()
}

onMounted(async () => {
  await loadCfg()
  await loadBoard()
})
</script>
