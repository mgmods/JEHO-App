<template>
  <div>
    <PageHeader :title="t('dashboard.title')" :subtitle="t('dashboard.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="loading" @click="load">
          <i class="bi bi-arrow-clockwise me-1"></i> {{ t('common.refresh') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" :title="t('dashboard.apiUnavailable')" type="warning" @dismiss="error = ''" />

    <div class="row g-3 mb-3">
      <div class="col-6 col-xl-3" v-for="card in cards" :key="card.label">
        <StatCard :label="card.label" :value="card.value" :icon="card.icon" :hint="card.hint" :raw="card.raw" />
      </div>
    </div>

    <div class="row g-3">
      <div class="col-lg-8">
        <ChartCard
          :title="t('dashboard.usersGrowth')"
          :subtitle="t('dashboard.lastSevenDays')"
          type="line"
          :labels="userChart.labels"
          :datasets="userChart.datasets"
        />
      </div>
      <div class="col-lg-4">
        <ChartCard
          :title="t('dashboard.revenueMix')"
          :subtitle="t('dashboard.revenueMixSubtitle')"
          type="doughnut"
          :labels="revenueChart.labels"
          :datasets="revenueChart.datasets"
        />
      </div>
      <div class="col-lg-6">
        <ChartCard
          :title="t('dashboard.roomsActivity')"
          :subtitle="t('dashboard.roomsActivitySubtitle')"
          type="bar"
          :labels="roomsChart.labels"
          :datasets="roomsChart.datasets"
        />
      </div>
      <div class="col-lg-6">
        <div class="glass p-3 h-100">
          <h3 class="h6 fw-semibold mb-3">{{ t('dashboard.liveNow') }}</h3>
          <LoadingSpinner v-if="loading && !live.length" />
          <div v-else-if="!live.length" class="empty-state py-4">
            <i class="bi bi-broadcast d-block mb-2"></i>
            {{ t('dashboard.noLiveRooms') }}
          </div>
          <div v-else class="live-now-list">
            <article
              v-for="item in live"
              :key="item.id || item.title"
              class="widget-card live-now-card"
            >
              <div class="widget-card-body d-flex justify-content-between align-items-center gap-2 py-2">
                <div class="min-w-0">
                  <div class="widget-card-title text-truncate mb-0">
                    {{ item.title || item.roomName || t('dashboard.untitledStream') }}
                  </div>
                  <div class="widget-card-meta">
                    {{ item.hostName || item.userName || t('rooms.host') }}
                    · {{ formatNumber(item.viewers || item.viewerCount || 0) }} {{ t('rooms.viewers') }}
                  </div>
                </div>
                <StatusBadge status="live" />
              </div>
            </article>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { dashboardApi } from '@/api'
import { extractList, formatNumber, formatMoney } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import StatusBadge from '@/components/StatusBadge.vue'

const ChartCard = defineAsyncComponent(() => import('@/components/ChartCard.vue'))

const { t, locale } = useI18n()

const loading = ref(false)
const error = ref('')
const overview = ref({})
const charts = ref({})
const live = ref([])
let timer = null

const cards = computed(() => {
  const o = overview.value || {}
  return [
    { label: t('dashboard.users'), value: o.users ?? o.totalUsers ?? 0, icon: 'users', hint: t('dashboard.totalRegistered') },
    { label: t('dashboard.rooms'), value: o.rooms ?? o.totalRooms ?? 0, icon: 'rooms', hint: t('dashboard.activeRooms') },
    { label: t('dashboard.revenue'), value: formatMoney(o.revenue ?? o.totalRevenue ?? 0), icon: 'revenue', hint: t('dashboard.allTime'), raw: true },
    { label: t('dashboard.liveRooms'), value: o.liveRooms ?? o.streams ?? o.liveStreams ?? o.activeStreams ?? 0, icon: 'rooms', hint: t('dashboard.liveTotal') },
  ]
})

const fallbackDays = computed(() => ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'].map((day) => t(`dashboard.days.${day}`)))

const userChart = computed(() => {
  const c = charts.value?.users || charts.value?.userGrowth || {}
  return {
    labels: c.labels || fallbackDays.value,
    datasets: [
      {
        label: t('dashboard.users'),
        data: c.users || c.data || [],
        borderColor: '#14b8a6',
        backgroundColor: 'rgba(20, 184, 166, 0.15)',
        fill: true,
        tension: 0.4,
      },
    ],
  }
})

const revenueChart = computed(() => {
  const c = charts.value?.revenue || {}
  return {
    labels: c.labels || [t('gifts.title'), t('vip.title'), t('dashboard.recharge'), t('dashboard.other')],
    datasets: [
      {
        data: c.data || [],
        backgroundColor: ['#0d9488', '#06b6d4', '#2dd4bf', '#0e7490'],
        borderWidth: 0,
      },
    ],
  }
})

const roomsChart = computed(() => {
  const c = charts.value?.rooms || {}
  return {
    labels: c.labels || fallbackDays.value,
    datasets: [
      {
        label: t('dashboard.roomsCreated'),
        data: c.data || c.rooms || [],
        backgroundColor: 'rgba(6, 182, 212, 0.65)',
        borderRadius: 8,
      },
    ],
  }
})

async function load() {
  loading.value = true
  error.value = ''
  const [ov, ch, lv] = await Promise.all([
    dashboardApi.overview(),
    dashboardApi.charts({ range: '7d' }),
    dashboardApi.liveStats(),
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
  live.value = extractList(lv.data).slice(0, 8)
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
.live-now-card {
  border-radius: 12px;
}
.live-now-card .widget-card-body {
  padding: 0.75rem 0.9rem;
}
</style>
