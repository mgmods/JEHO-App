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

    <section class="glass p-3 mb-3">
      <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <div>
          <h5 class="mb-1">مكافآت المتصدرين</h5>
          <small class="text-muted">اختر صور وأسماء جوائز المراكز الثلاثة، واعرض المعاينة قبل الحفظ.</small>
        </div>
        <label class="form-check form-switch mb-0">
          <input v-model="rewardConfig.enabled" class="form-check-input" type="checkbox">
          <span class="form-check-label">تفعيل عرض المكافآت</span>
        </label>
      </div>
      <div class="btn-group btn-group-sm mb-3" role="group">
        <button class="btn" :class="rewardPeriod === 'weekly' ? 'btn-warning' : 'btn-outline-secondary'" @click="rewardPeriod = 'weekly'">أسبوعي</button>
        <button class="btn" :class="rewardPeriod === 'monthly' ? 'btn-warning' : 'btn-outline-secondary'" @click="rewardPeriod = 'monthly'">شهري</button>
      </div>
      <div class="row g-3">
        <div v-for="(reward, index) in rewardConfig[rewardPeriod]" :key="rewardPeriod + index" class="col-md-4">
          <div class="p-3 rounded border h-100">
            <div class="fw-bold text-warning mb-2">المركز {{ index + 1 }}</div>
            <div class="reward-preview mb-2">
              <img v-if="reward.imageUrl" :src="resolveAsset(reward.imageUrl) || reward.imageUrl" alt="معاينة المكافأة">
              <span v-else>صورة المكافأة</span>
            </div>
            <label class="form-label small">اختيار مكافأة من متجر التطبيق</label>
            <select v-model="reward.cosmeticId" class="form-select form-select-sm mb-2" @change="applyStoreReward(reward)">
              <option value="">مكافأة بصورة مخصصة</option>
              <option v-for="item in cosmeticCatalog" :key="item.id" :value="item.id">{{ item.name }} · {{ item.type }}</option>
            </select>
            <label class="form-label small">اسم المكافأة</label>
            <input v-model="reward.name" class="form-control form-control-sm mb-2" maxlength="80" placeholder="مثلاً: إطار ذهبي">
            <label class="form-label small">رابط HTTPS أو مسار صورة داخل التطبيق</label>
            <input v-model="reward.imageUrl" class="form-control form-control-sm mb-2" type="url" placeholder="https://...">
            <label class="form-label small">وصف اختياري</label>
            <input v-model="reward.description" class="form-control form-control-sm" maxlength="180" placeholder="تفاصيل الجائزة">
          </div>
        </div>
      </div>
      <div class="d-flex justify-content-end gap-2 mt-3">
        <button class="btn btn-outline-secondary btn-sm" :disabled="savingRewards" @click="loadRewards">إعادة تحميل</button>
        <button class="btn btn-warning btn-sm" :disabled="savingRewards" @click="saveRewards">{{ savingRewards ? 'جارٍ الحفظ…' : 'حفظ المكافآت' }}</button>
      </div>
    </section>

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
          <div class="fw-bold text-warning" style="min-width: 2rem; font-size: 1.15rem">#{{ row.rank || i + 1 }}</div>
          <div class="rank-avatar-wrap">
            <img v-if="avatarFor(row)" :src="avatarFor(row)" class="rank-avatar" alt="">
            <div v-else class="rank-avatar-fallback">{{ nameFor(row).charAt(0) || 'A' }}</div>
            <img v-if="frameFor(row)" :src="frameFor(row)" class="rank-frame" alt="">
          </div>
          <div class="flex-grow-1 min-w-0">
            <RouterLink v-if="row.user?.id" :to="{ name: 'user-detail', params: { id: row.user.id } }" class="widget-card-title text-decoration-none d-block text-truncate">{{ nameFor(row) }}</RouterLink>
            <div v-else class="widget-card-title text-truncate">{{ nameFor(row) }}</div>
            <div class="widget-card-meta">
              <span v-if="row.user">Lv.{{ row.user.level || 1 }}</span>
              <span v-else>{{ entityLabel }}</span>
              <span v-if="row.user?.username"> · @{{ row.user.username }}</span>
              <span v-if="row.user?.followersCount"> · {{ formatNumber(row.user.followersCount) }} متابع</span>
            </div>
            <div class="fw-bold text-warning mt-1">{{ formatNumber(row.score ?? row.totalCoins ?? row.value ?? 0) }} {{ scoreUnit }}</div>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rankingApi, cosmeticsApi } from '@/api'
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
const rewardPeriod = ref('weekly')
const savingRewards = ref(false)
const emptyRewards = () => Array.from({ length: 3 }, () => ({ name: '', imageUrl: '', description: '', cosmeticId: '' }))
const rewardConfig = ref({ enabled: true, weekly: emptyRewards(), monthly: emptyRewards() })
const cosmeticCatalog = ref([])
function selectedCosmetic(id) { return cosmeticCatalog.value.find(item => String(item.id) === String(id)) }
function applyStoreReward(reward) {
  const item = selectedCosmetic(reward.cosmeticId)
  if (!item) return
  reward.name = item.name || reward.name
  reward.imageUrl = item.previewUrl || item.imageUrl || reward.imageUrl
  reward.description = reward.description || item.description || ''
}
async function loadCosmetics() {
  const { data, error: err } = await cosmeticsApi.catalog()
  if (!err) cosmeticCatalog.value = data?.data || data || []
}

const scoreUnit = computed(() => {
  if (category.value === 'rich') return t('ranking.coin')
  if (['popular', 'gifts', 'host'].includes(category.value)) return t('ranking.diamond')
  if (category.value === 'room') return t('ranking.viewer')
  return t('ranking.point')
})
const entityLabel = computed(() => category.value === 'agency' ? t('common.agency') : category.value === 'room' ? t('common.room') : t('common.user'))
function nameFor(row) { return row?.user?.displayName || row?.user?.username || row?.targetName || row?.displayName || row?.username || '—' }
function avatarFor(row) { return resolveAsset(row?.user?.avatarUrl || row?.avatarUrl || '') }
function frameFor(row) { return resolveAsset(row?.user?.frameUrl || row?.user?.vipBadgeUrl || row?.user?.hostBadgeUrl || '') }

async function loadRewards() {
  const { data, error: err } = await rankingApi.rewards()
  if (err) { toast().danger(err.message); return }
  const payload = data?.data ?? data ?? {}
  const normalize = list => Array.from({ length: 3 }, (_, i) => ({ name: list?.[i]?.name || '', imageUrl: list?.[i]?.imageUrl || '', description: list?.[i]?.description || '', cosmeticId: list?.[i]?.cosmeticId || '' }))
  rewardConfig.value = { enabled: payload.enabled !== false, weekly: normalize(payload.weekly), monthly: normalize(payload.monthly) }
}
async function saveRewards() {
  savingRewards.value = true
  const payload = { enabled: rewardConfig.value.enabled, weekly: rewardConfig.value.weekly, monthly: rewardConfig.value.monthly }
  const { data, error: err } = await rankingApi.saveRewards(payload)
  savingRewards.value = false
  if (err) { toast().danger(err.message); return }
  const saved = data?.data ?? data
  if (saved) rewardConfig.value = { enabled: saved.enabled !== false, weekly: saved.weekly || emptyRewards(), monthly: saved.monthly || emptyRewards() }
  toast().success('تم حفظ مكافآت المتصدرين')
}
async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await rankingApi.board(period.value, category.value, { limit: 50 })
  loading.value = false
  if (err) { error.value = err.message; toast().danger(err.message); rows.value = []; return }
  const payload = data?.data ?? data
  rows.value = extractList(payload) || payload?.items || []
  if (!Array.isArray(rows.value)) rows.value = []
}
onMounted(() => { load(); loadRewards(); loadCosmetics() })
</script>

<style scoped>
.reward-preview{height:112px;border-radius:16px;border:1px dashed var(--bs-border-color);background:linear-gradient(145deg,#321016,#10182d);display:flex;align-items:center;justify-content:center;overflow:hidden;color:var(--bs-secondary-color);font-size:12px}
.reward-preview img{width:100%;height:100%;object-fit:contain}
</style>
