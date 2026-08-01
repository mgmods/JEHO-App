<template>
  <div>
    <PageHeader :title="t('ranking.title')" :subtitle="t('ranking.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="loading" @click="load">
          {{ t('common.refresh') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />

    <div class="glass p-3 mb-3">
      <div class="row g-2 align-items-end">
        <div class="col-md-3">
          <label class="form-label small">{{ t('ranking.period') }}</label>
          <select v-model="period" class="form-select form-select-sm" @change="load">
            <option value="daily">{{ t('ranking.daily') }}</option>
            <option value="weekly">{{ t('ranking.weekly') }}</option>
            <option value="monthly">{{ t('ranking.monthly') }}</option>
          </select>
        </div>
        <div class="col-md-3">
          <label class="form-label small">{{ t('ranking.category') }}</label>
          <select v-model="category" class="form-select form-select-sm" @change="load">
            <option value="rich">{{ t('ranking.rich') }}</option>
            <option value="popular">{{ t('ranking.popular') }}</option>
            <option value="gifts">{{ t('ranking.gifts') }}</option>
            <option value="host">{{ t('ranking.hosts') }}</option>
            <option value="agency">{{ t('ranking.agencies') }}</option>
            <option value="room">{{ t('ranking.rooms') }}</option>
          </select>
        </div>
      </div>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else class="widget-grid">
      <div v-if="!rows.length" class="glass p-4 empty-state">{{ t('common.noData') }}</div>
      <article v-for="(row, i) in rows" :key="row.targetId || row.userId || i" class="widget-card">
        <div class="widget-card-body d-flex gap-3 align-items-center">
          <div class="fw-bold text-warning" style="min-width: 2rem; font-size: 1.15rem">
            #{{ row.rank || i + 1 }}
          </div>
          <div class="rank-avatar-wrap">
            <img v-if="avatarFor(row)" :src="avatarFor(row)" class="rank-avatar" alt="" />
            <div v-else class="rank-avatar-fallback">{{ nameFor(row).charAt(0) || 'A' }}</div>
            <img v-if="frameFor(row)" :src="frameFor(row)" class="rank-frame" alt="" />
          </div>
          <div class="flex-grow-1 min-w-0">
            <RouterLink
              v-if="row.user?.id"
              :to="{ name: 'user-detail', params: { id: row.user.id } }"
              class="widget-card-title text-decoration-none d-block text-truncate"
            >
              {{ nameFor(row) }}
            </RouterLink>
            <div v-else class="widget-card-title text-truncate">{{ nameFor(row) }}</div>
            <div class="widget-card-meta">
              <span v-if="row.user">Lv.{{ row.user.level || 1 }}</span>
              <span v-else>{{ entityLabel }}</span>
              <span v-if="row.user?.username"> · @{{ row.user.username }}</span>
              <span v-if="row.user?.followersCount"> · {{ formatNumber(row.user.followersCount) }} متابع</span>
            </div>
            <div class="fw-bold text-warning mt-1">
              {{ formatNumber(row.score ?? row.totalCoins ?? row.value ?? 0) }} {{ scoreUnit }}
            </div>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rankingApi } from '@/api'
import { extractList, formatNumber } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import { resolveAsset } from '@/utils/assets'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { t } = useI18n()

const period = ref('weekly')
const category = ref('rich')
const rows = ref([])
const loading = ref(false)
const error = ref('')

const scoreUnit = computed(() => {
  if (category.value === 'rich') return t('ranking.coin')
  if (['popular', 'gifts', 'host'].includes(category.value)) return t('ranking.diamond')
  if (category.value === 'room') return t('ranking.viewer')
  return t('ranking.point')
})

const entityLabel = computed(() => {
  if (category.value === 'agency') return t('common.agency')
  if (category.value === 'room') return t('common.room')
  return t('common.user')
})

function nameFor(row) {
  return row?.user?.displayName || row?.user?.username || row?.targetName || row?.displayName || row?.username || '—'
}

function avatarFor(row) {
  return resolveAsset(row?.user?.avatarUrl || row?.avatarUrl || '')
}

function frameFor(row) {
  return resolveAsset(
    row?.user?.frameUrl || row?.user?.vipBadgeUrl || row?.user?.hostBadgeUrl || '',
  )
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await rankingApi.board(period.value, category.value, { limit: 50 })
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    rows.value = []
    return
  }
  const payload = data?.data ?? data
  rows.value = extractList(payload) || payload?.items || []
  if (!Array.isArray(rows.value)) rows.value = []
}

onMounted(load)
</script>
