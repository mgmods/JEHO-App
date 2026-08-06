<template>
  <div class="sauti-home">
    <AlertMessage v-if="error" :message="error" :title="t('dashboard.apiUnavailable')" type="warning" @dismiss="error = ''" />

    <!-- KPI -->
    <div class="row g-3 mb-3">
      <div class="col-6 col-xl-3" v-for="card in cards" :key="card.label">
        <StatCard
          :label="card.label"
          :value="card.value"
          :icon="card.icon"
          :hint="card.hint"
          :delta="card.delta"
          :delta-note="card.deltaNote"
          :raw="card.raw"
          :tone="card.tone"
        />
      </div>
    </div>

    <!-- Server health + log cleanup (card style, no separate board) -->
    <div class="mb-3">
      <ServerHealthCard
        :health="systemHealth"
        :loading="healthLoading"
        :cleaning="cleaningLogs"
        @refresh="loadHealth"
        @clean="onCleanLogs"
      />
    </div>

    <!-- Charts -->
    <div class="row g-3 mb-3">
      <div class="col-lg-8">
        <ChartCard
          :title="t('dashboard.generalStats')"
          type="line"
          :labels="combinedChart.labels"
          :datasets="combinedChart.datasets"
        />
      </div>
      <div class="col-lg-4">
        <ChartCard
          :title="t('dashboard.revenueByChannel') || 'إيرادات حسب القناة'"
          type="doughnut"
          :labels="revenueChart.labels"
          :datasets="revenueChart.datasets"
          :center-label="t('dashboard.totalRevenueLabel') || 'إيرادات'"
          :center-value="revenueCenter"
        />
        <div v-if="revenueBreakdown.length" class="s-panel mt-2 p-2">
          <div
            v-for="row in revenueBreakdown"
            :key="row.provider"
            class="d-flex justify-content-between align-items-center small py-1 border-bottom border-secondary-subtle"
          >
            <span class="text-secondary">{{ row.label }} · {{ row.count }}</span>
            <strong dir="ltr">{{ formatMoney(row.total) }}</strong>
          </div>
        </div>
      </div>
    </div>

    <!-- Lists -->
    <div class="row g-3 mb-3">
      <div class="col-lg-4">
        <div class="s-panel">
          <div class="s-panel-head">
            <h3>{{ t('dashboard.topRooms') }}</h3>
          </div>
          <LoadingSpinner v-if="loading && !live.length" />
          <div v-else-if="!live.length" class="empty-state py-3">{{ t('dashboard.noLiveRooms') }}</div>
          <div v-else class="s-list">
            <div v-for="(item, idx) in live" :key="'room-' + (item.id || idx)" class="s-row">
              <div class="s-ava rooms">
                <i class="bi bi-mic-fill"></i>
              </div>
              <div class="min-w-0 flex-grow-1">
                <div class="s-title text-truncate">{{ item.title || item.roomName || t('dashboard.untitledStream') }}</div>
                <div class="s-meta text-truncate">ID: {{ shortId(item.id) }}</div>
              </div>
              <div class="s-right">
                <div class="s-val">{{ formatNumber(item.viewers || item.viewerCount || 0) }}</div>
                <div class="s-sub">{{ t('rooms.viewers') }}</div>
              </div>
            </div>
          </div>
          <RouterLink class="s-foot-btn" :to="{ name: 'rooms' }">{{ t('dashboard.viewAllRooms') }}</RouterLink>
        </div>
      </div>

      <div class="col-lg-4">
        <div class="s-panel">
          <div class="s-panel-head">
            <h3>{{ t('dashboard.latestTx') }}</h3>
          </div>
          <LoadingSpinner v-if="loading && !transactions.length" />
          <div v-else-if="!transactions.length" class="empty-state py-3">{{ t('dashboard.noTransactions') }}</div>
          <div v-else class="s-list">
            <div v-for="(tx, idx) in transactions" :key="'tx-' + (tx.id || idx)" class="s-row">
              <div class="s-ava users">{{ txInitials(tx) }}</div>
              <div class="min-w-0 flex-grow-1">
                <div class="s-title text-truncate">{{ txName(tx) }}</div>
                <div class="s-meta text-truncate">{{ txMeta(tx) }}</div>
              </div>
              <div class="s-right">
                <div class="s-val money">{{ txAmount(tx) }}</div>
              </div>
            </div>
          </div>
          <RouterLink class="s-foot-btn" :to="{ name: 'wallet' }">{{ t('dashboard.viewAllTx') }}</RouterLink>
        </div>
      </div>

      <div class="col-lg-4">
        <div class="s-panel">
          <div class="s-panel-head">
            <h3>{{ t('dashboard.liveActivity') }}</h3>
            <span class="s-live-badge">LIVE</span>
          </div>
          <LoadingSpinner v-if="loading && !activity.length" />
          <div v-else-if="!activity.length" class="empty-state py-3">{{ t('dashboard.noLiveRooms') }}</div>
          <div v-else class="s-list s-timeline">
            <div v-for="(item, idx) in activity" :key="'act-' + idx" class="s-row s-tl">
              <span class="s-dot" :class="item.dot"></span>
              <div class="min-w-0 flex-grow-1">
                <div class="s-title">{{ item.title }}</div>
                <div class="s-meta">{{ item.meta }}</div>
              </div>
            </div>
          </div>
          <RouterLink class="s-foot-btn" :to="{ name: 'rooms' }">{{ t('dashboard.viewAllActivity') }}</RouterLink>
        </div>
      </div>
    </div>

    <!-- Bottom 5 -->
    <div class="s-mini-row">
      <div v-for="m in miniStats" :key="m.label" class="s-mini" :class="m.tone">
        <div class="s-mini-ic"><i :class="['bi', m.icon]"></i></div>
        <div>
          <div class="s-mini-lab">{{ m.label }}</div>
          <div class="s-mini-val">{{ m.value }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { dashboardApi, walletApi, logsApi } from '@/api'
import { extractList, formatNumber, formatMoney } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import StatCard from '@/components/StatCard.vue'
import ServerHealthCard from '@/components/ServerHealthCard.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const ChartCard = defineAsyncComponent(() => import('@/components/ChartCard.vue'))
const { t, locale } = useI18n()

const loading = ref(false)
const error = ref('')
const overview = ref({})
const charts = ref({})
const live = ref([])
const transactions = ref([])
const systemHealth = ref(null)
const healthLoading = ref(false)
const cleaningLogs = ref(false)
let timer = null

function seriesDelta(arr) {
  const data = (arr || []).map(Number).filter((n) => !Number.isNaN(n))
  if (data.length < 2) return ''
  const prev = data[data.length - 2]
  const last = data[data.length - 1]
  if (prev === 0) return last > 0 ? '+100%' : ''
  const pct = ((last - prev) / Math.abs(prev)) * 100
  const sign = pct >= 0 ? '+' : ''
  return `${sign}${pct.toFixed(1)}%`
}

const cards = computed(() => {
  const o = overview.value || {}
  const users = charts.value?.users || charts.value?.userGrowth || {}
  const rooms = charts.value?.rooms || {}
  const usersSeries = users.users || users.data || []
  const roomsSeries = rooms.data || rooms.rooms || []
  return [
    {
      label: t('dashboard.totalUsersLabel'),
      value: o.users ?? o.totalUsers ?? 0,
      tone: 'users',
      delta: seriesDelta(usersSeries) || '+0%',
      deltaNote: t('dashboard.fromLastPeriod'),
    },
    {
      label: t('dashboard.activeRoomsNow'),
      value: o.liveRooms ?? o.streams ?? o.liveStreams ?? o.activeStreams ?? 0,
      tone: 'rooms',
      delta: seriesDelta(roomsSeries) || '+0%',
      deltaNote: t('dashboard.fromLastPeriod'),
    },
    {
      label: t('dashboard.activeUsersNow'),
      value: o.openRooms ?? o.rooms ?? 0,
      tone: 'live',
      delta: '',
      hint: t('dashboard.activeRooms'),
    },
    {
      label: t('dashboard.totalRevenueLabel'),
      value: formatMoney(o.revenue ?? o.totalRevenue ?? o.totalRechargeFiat ?? 0),
      tone: 'revenue',
      raw: true,
      delta: '',
      hint: t('dashboard.allTime'),
    },
  ]
})

const centerTotal = computed(() => formatNumber(overview.value?.users ?? overview.value?.totalUsers ?? 0))

const revenueBreakdown = computed(() => {
  const fromOverview = overview.value?.revenueByProvider
  if (Array.isArray(fromOverview) && fromOverview.length) return fromOverview
  const fromCharts = charts.value?.revenue?.byProvider
  return Array.isArray(fromCharts) ? fromCharts : []
})

const revenueCenter = computed(() =>
  formatMoney(overview.value?.revenue ?? overview.value?.totalRevenue ?? overview.value?.totalRechargeFiat ?? 0),
)

const miniStats = computed(() => {
  const o = overview.value || {}
  const users = charts.value?.users || {}
  const rooms = charts.value?.rooms || {}
  const uSeries = users.users || users.data || []
  const rSeries = rooms.data || rooms.rooms || []
  const todayUsers = uSeries.length ? Number(uSeries[uSeries.length - 1] || 0) : 0
  const todayRooms = rSeries.length ? Number(rSeries[rSeries.length - 1] || 0) : 0
  const coinVol = charts.value?.revenue?.coinVolume?.values || []
  return [
    { label: t('dashboard.newUsersToday'), value: `+${formatNumber(todayUsers)}`, icon: 'bi-person-plus', tone: 't-p' },
    { label: t('dashboard.newRoomsToday'), value: `+${formatNumber(todayRooms)}`, icon: 'bi-door-open', tone: 't-b' },
    {
      label: t('dashboard.txToday') || 'إيراد اليوم',
      value: formatMoney(o.todayRevenue ?? 0),
      icon: 'bi-cash-stack',
      tone: 't-g',
    },
    {
      label: t('dashboard.rechargeCoins'),
      value: formatNumber(coinVol[0] ?? 0),
      icon: 'bi-gem',
      tone: 't-y',
    },
    {
      label: t('dashboard.giftsSent'),
      value: formatNumber(coinVol[1] ?? o.activeGifts ?? 0),
      icon: 'bi-gift-fill',
      tone: 't-r',
    },
  ]
})

const activity = computed(() => {
  const items = live.value.slice(0, 7).map((room) => ({
    title: t('dashboard.roomLiveEvent', {
      name: room.title || room.roomName || t('dashboard.untitledStream'),
    }),
    meta: `${room.hostName || room.userName || '—'} · ${formatNumber(room.viewers || room.viewerCount || 0)} ${t('rooms.viewers')}`,
    dot: 'live',
  }))
  const o = overview.value || {}
  if (Number(o.pendingWithdraws || 0) > 0) {
    items.push({ title: t('dashboard.pendingWithdraws'), meta: formatNumber(o.pendingWithdraws), dot: 'warn' })
  }
  if (Number(o.pendingReports || 0) > 0) {
    items.push({ title: t('dashboard.pendingReports'), meta: formatNumber(o.pendingReports), dot: 'danger' })
  }
  return items.slice(0, 8)
})

const fallbackDays = computed(() =>
  ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'].map((d) => t(`dashboard.days.${d}`)),
)

const combinedChart = computed(() => {
  const users = charts.value?.users || charts.value?.userGrowth || {}
  const rooms = charts.value?.rooms || {}
  const labels = users.labels || rooms.labels || charts.value?.labels || fallbackDays.value
  const uData = users.users || users.data || []
  const rData = rooms.data || rooms.rooms || []
  const sumData = uData.map((u, i) => Number(u || 0) + Number(rData[i] || 0) * 3)
  return {
    labels,
    datasets: [
      {
        label: t('dashboard.users'),
        data: uData,
        borderColor: '#6c5ce7',
        backgroundColor: 'rgba(108, 92, 231, 0.12)',
        fill: true,
        tension: 0.4,
        pointRadius: 4,
        pointBackgroundColor: '#6c5ce7',
        pointBorderColor: '#1c1e2d',
        pointBorderWidth: 2,
        borderWidth: 2.5,
      },
      {
        label: t('dashboard.roomsCreated'),
        data: rData,
        borderColor: '#3b82f6',
        backgroundColor: 'rgba(59, 130, 246, 0.08)',
        fill: true,
        tension: 0.4,
        pointRadius: 4,
        pointBackgroundColor: '#3b82f6',
        pointBorderColor: '#1c1e2d',
        pointBorderWidth: 2,
        borderWidth: 2.5,
      },
      {
        label: t('dashboard.activitySeries'),
        data: sumData,
        borderColor: '#00c566',
        backgroundColor: 'transparent',
        fill: false,
        tension: 0.4,
        pointRadius: 4,
        pointBackgroundColor: '#00c566',
        pointBorderColor: '#1c1e2d',
        pointBorderWidth: 2,
        borderWidth: 2.5,
      },
    ],
  }
})

const revenueChart = computed(() => {
  const c = charts.value?.revenue || {}
  const labels = c.labels || revenueBreakdown.value.map((r) => r.label)
  const data = c.values || c.data || revenueBreakdown.value.map((r) => r.total)
  return {
    labels: labels.length ? labels : ['—'],
    datasets: [
      {
        data: data.length ? data : [0],
        backgroundColor: ['#6c5ce7', '#3b82f6', '#f5b942', '#00c566', '#ff5a7a', '#a78bfa', '#22d3ee'],
        borderWidth: 0,
        hoverOffset: 6,
        spacing: 3,
      },
    ],
  }
})

function shortId(id) {
  if (!id) return '—'
  const s = String(id)
  return s.length > 8 ? s.slice(0, 8) : s
}
function txName(tx) {
  return tx.userName || tx.displayName || tx.username || tx.user?.displayName || tx.user?.username || t('dashboard.userFallback')
}
function txInitials(tx) {
  return txName(tx).split(/\s+/).map((p) => p[0]).join('').slice(0, 2).toUpperCase()
}
function txMeta(tx) {
  const id = tx.id || tx.reference || tx.referenceId || ''
  return id ? `TXN${String(id).slice(0, 6)}` : (tx.type || '—')
}
function txAmount(tx) {
  const amount = Number(tx.amount ?? tx.amountFiat ?? tx.value ?? 0)
  const abs = Math.abs(amount)
  const sign = amount >= 0 ? '+' : '-'
  if (tx.currency === 'USD' || tx.amountFiat != null) {
    return `${sign}$${abs.toLocaleString(locale.value === 'ar' ? 'ar' : 'en', { maximumFractionDigits: 2 })}`
  }
  return `${sign}${formatNumber(abs)}`
}

async function loadHealth() {
  healthLoading.value = true
  const { data, error: err } = await dashboardApi.systemHealth()
  healthLoading.value = false
  if (err) {
    // soft-fail — keep rest of dashboard usable
    return
  }
  systemHealth.value = data || null
}

async function onCleanLogs(payload) {
  const mode = payload?.mode
  let body = {}
  let confirmMsg = t('system.confirmCleanOld')
  if (mode === 'all') {
    body = { olderThanDays: 0 }
    confirmMsg = t('system.confirmCleanAll')
  } else if (mode === 'days') {
    body = { olderThanDays: Number(payload.days) || 30 }
    confirmMsg = t('system.confirmCleanOld')
  } else if (mode === 'resolved') {
    body = { resolvedOnly: true }
    confirmMsg = t('system.confirmCleanResolved')
  }
  if (!window.confirm(confirmMsg)) return
  cleaningLogs.value = true
  const { data, error: err } = await logsApi.cleanup(body)
  cleaningLogs.value = false
  if (err) {
    toast().danger(err.message)
    return
  }
  const deleted = data?.deleted ?? 0
  toast().success(t('system.cleaned', { count: deleted }))
  await loadHealth()
}

async function load() {
  loading.value = true
  error.value = ''
  const [ov, ch, lv, tx] = await Promise.all([
    dashboardApi.overview(),
    dashboardApi.charts({ range: '7d' }),
    dashboardApi.liveStats(),
    walletApi.transactions({ limit: 8 }),
  ])
  loading.value = false
  if (ov.error && ch.error && lv.error) {
    error.value = ov.error.message || t('dashboard.apiError')
    toast().danger(error.value)
  } else if (ov.error || ch.error || lv.error) {
    error.value = [ov.error, ch.error, lv.error].filter(Boolean).map((e) => e.message).join(' · ')
    toast().warning(error.value)
  }
  overview.value = ov.data || {}
  charts.value = ch.data || {}
  live.value = extractList(lv.data).slice(0, 6)
  transactions.value = extractList(tx.data).slice(0, 6)
  loadHealth()
}

onMounted(() => {
  load()
  timer = setInterval(load, 60000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.sauti-home { max-width: 1580px; }

.s-panel {
  padding: 1.15rem 1.2rem 1.05rem;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 380px;
  position: relative;
  overflow: hidden;
}
.s-panel::after {
  content: '';
  position: absolute;
  inset: auto -20% -50% auto;
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.16), transparent 70%);
  pointer-events: none;
}
.s-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.85rem;
  position: relative;
  z-index: 1;
}
.s-panel-head h3 {
  margin: 0;
  font-size: 0.98rem;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.02em;
}
.s-live-badge {
  font-size: 0.65rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #fda4af;
  background: rgba(244, 63, 94, 0.2);
  border: 1px solid rgba(244, 63, 94, 0.4);
  padding: 0.22rem 0.55rem;
  border-radius: 999px;
  box-shadow: 0 0 12px rgba(244, 63, 94, 0.25);
}
.s-list { flex: 1; position: relative; z-index: 1; }
.s-row {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 0.78rem 0;
  border-bottom: 1px solid rgba(139, 92, 246, 0.1);
}
.s-row:last-child { border-bottom: 0; }
.s-ava {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 0.75rem;
  font-weight: 800;
  box-shadow: 0 0 0 2px rgba(139, 92, 246, 0.25), 0 0 16px rgba(139, 92, 246, 0.2);
}
.s-ava.rooms {
  color: #67e8f9;
  background: linear-gradient(135deg, rgba(34, 211, 238, 0.22), rgba(139, 92, 246, 0.18));
}
.s-ava.users {
  color: #c4b5fd;
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.28), rgba(244, 114, 182, 0.14));
}
.s-title { font-size: 0.9rem; font-weight: 700; color: #f4f6fb; }
.s-meta { font-size: 0.72rem; color: #9ca3c7; margin-top: 0.14rem; }
.s-right { text-align: end; flex-shrink: 0; }
.s-val { font-size: 0.92rem; font-weight: 800; color: #fff; letter-spacing: -0.02em; }
.s-val.money { color: #34d399; text-shadow: 0 0 12px rgba(52, 211, 153, 0.3); }
.s-sub { font-size: 0.68rem; color: #7a829c; }
.s-tl { align-items: flex-start; }
.s-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  margin-top: 0.4rem;
  flex-shrink: 0;
  background: #8b5cf6;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.2), 0 0 10px rgba(139, 92, 246, 0.45);
}
.s-dot.live { background: #34d399; box-shadow: 0 0 0 4px rgba(52, 211, 153, 0.18), 0 0 10px rgba(52, 211, 153, 0.4); }
.s-dot.warn { background: #fbbf24; box-shadow: 0 0 0 4px rgba(251, 191, 36, 0.18), 0 0 10px rgba(251, 191, 36, 0.35); }
.s-dot.danger { background: #f43f5e; box-shadow: 0 0 0 4px rgba(244, 63, 94, 0.18), 0 0 10px rgba(244, 63, 94, 0.4); }

.s-foot-btn {
  display: block;
  text-align: center;
  margin-top: 0.95rem;
  padding: 0.7rem 0.85rem;
  font-size: 0.84rem;
  text-decoration: none;
  position: relative;
  z-index: 1;
}

.s-mini-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.9rem;
}
.s-mini {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 1.05rem 1.1rem;
  min-height: 92px;
  position: relative;
  overflow: hidden;
}
.s-mini-ic {
  width: 46px;
  height: 46px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 1.1rem;
  flex-shrink: 0;
}
.s-mini-lab { font-size: 0.72rem; color: #9ca3c7; font-weight: 500; margin-bottom: 0.2rem; }
.s-mini-val {
  font-size: 1.25rem;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.03em;
  text-shadow: 0 0 18px rgba(167, 139, 250, 0.2);
}
.s-mini.t-p .s-mini-ic {
  color: #c4b5fd;
  background: rgba(139, 92, 246, 0.18);
  box-shadow: 0 0 18px rgba(139, 92, 246, 0.3);
}
.s-mini.t-b .s-mini-ic {
  color: #67e8f9;
  background: rgba(34, 211, 238, 0.14);
  box-shadow: 0 0 18px rgba(34, 211, 238, 0.25);
}
.s-mini.t-g .s-mini-ic {
  color: #6ee7b7;
  background: rgba(52, 211, 153, 0.12);
  box-shadow: 0 0 18px rgba(52, 211, 153, 0.25);
}
.s-mini.t-y .s-mini-ic {
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.12);
  box-shadow: 0 0 18px rgba(251, 191, 36, 0.25);
}
.s-mini.t-r .s-mini-ic {
  color: #fda4af;
  background: rgba(244, 63, 94, 0.12);
  box-shadow: 0 0 18px rgba(244, 63, 94, 0.25);
}

@media (max-width: 1199.98px) {
  .s-mini-row { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 767.98px) {
  .s-mini-row { grid-template-columns: 1fr 1fr; }
  .s-panel { min-height: 0; }
}
</style>
