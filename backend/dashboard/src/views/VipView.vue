<template>
  <div>
    <PageHeader v-if="activeTab === 'plans'" :title="t('vip.title')" :subtitle="t('vip.subtitle')">
      <template #actions>
        <template v-if="activeTab === 'plans'">
        <button class="btn btn-ghost btn-sm" type="button" @click="openPlan()">
          <i class="bi bi-plus-lg me-1"></i> {{ t('vip.newPlan') }}
        </button>
        <button class="btn btn-aurora btn-sm" type="button" @click="showAssign = true">
          <i class="bi bi-person-plus me-1"></i> {{ t('vip.assignVip') }}
        </button>
        </template>
      </template>
    </PageHeader>

    <div class="d-flex flex-wrap gap-2 mb-3" role="tablist" aria-label="إدارة VIP">
      <button type="button" class="btn btn-sm" :class="activeTab === 'plans' ? 'btn-aurora' : 'btn-ghost'" @click="setTab('plans')">مستويات VIP والأعضاء</button>
      <button type="button" class="btn btn-sm" :class="activeTab === 'visuals' ? 'btn-aurora' : 'btn-ghost'" @click="setTab('visuals')">مظاهر VIP حسب المستوى</button>
    </div>

    <template v-if="activeTab === 'plans'">
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <LoadingSpinner v-if="loading && !plans.length" />
    <div v-else class="widget-grid mb-3">
      <div v-if="!plans.length" class="glass p-4 empty-state">{{ t('vip.noPlans') }}</div>
      <article
        v-for="p in plans"
        :key="p.id"
        class="widget-card vip-card"
        :style="{ '--vip-accent': tierColor(p.level) }"
      >
        <div class="widget-card-media vip-media">
          <img
            class="vip-badge"
            :src="badgeSrc(p)"
            :alt="p.name"
            width="128"
            height="128"
            loading="lazy"
            decoding="async"
            @error="(e) => (e.target.src = fallbackBadge(p.level))"
          />
          <span class="vip-tier-chip">{{ t('common.level') }} {{ p.level || '—' }}</span>
        </div>
        <div class="widget-card-body">
          <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
            <h3 class="widget-card-title text-truncate mb-0">{{ p.name }}</h3>
            <StatusBadge :status="p.status || (p.isActive === false ? 'inactive' : 'active')" />
          </div>
          <div class="vip-price mb-1">
            <span class="coin-icon" aria-hidden="true">◈</span>
            <strong>{{ formatNumber(p.coinPrice ?? p.price ?? 0) }}</strong>
            <span class="vip-price-unit">{{ t('vip.perMonth') }}</span>
          </div>
          <div class="widget-card-meta mb-2">
            {{ p.durationDays || 30 }} {{ t('vip.days') }}
          </div>
          <ul v-if="(p.benefits || []).length" class="vip-benefits mb-3">
            <li v-for="(b, i) in (p.benefits || []).slice(0, 4)" :key="i">{{ b }}</li>
          </ul>
          <div class="action-btns">
            <button class="btn btn-sm btn-ghost" type="button" @click="openPlan(p)">
              <i class="bi bi-pencil" />
            </button>
            <button class="btn btn-sm btn-outline-danger" type="button" @click="askRemovePlan(p)">
              <i class="bi bi-trash" />
            </button>
          </div>
        </div>
      </article>
    </div>

    <div class="widget-card overflow-hidden">
      <div class="widget-card-body border-bottom py-3" style="border-color: var(--border-color) !important">
        <h3 class="h6 mb-0">{{ t('vip.members') }}</h3>
      </div>
      <div class="px-3 pt-2">
        <BulkActionBar
          :count="members.length"
          :selected-count="memberSelectedCount"
          :all-selected="memberAllSelected"
          :some-selected="memberSomeSelected"
          :busy="bulkBusy"
          :actions="memberBulkActions"
          @toggle-all="memberToggleAll"
          @clear="memberClear"
          @action="onMemberBulkAction"
        />
      </div>
      <LoadingSpinner v-if="loading" />
      <div v-else class="table-responsive">
        <table class="table table-glass table-hover align-middle mb-0">
          <thead>
            <tr>
              <th style="width:2.2rem"></th>
              <th>{{ t('common.user') }}</th>
              <th>{{ t('vip.plan') }}</th>
              <th>{{ t('vip.expires') }}</th>
              <th>{{ t('common.status') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!members.length">
              <td colspan="6" class="empty-state">{{ t('vip.noMembers') }}</td>
            </tr>
            <tr v-for="m in members" :key="m.id">
              <td>
                <input
                  type="checkbox"
                  class="form-check-input"
                  :checked="memberIsSelected(m.id)"
                  @change="memberToggle(m.id)"
                />
              </td>
              <td>{{ m.userName || m.user?.displayName || m.user?.username || '—' }}</td>
              <td>{{ m.planName || m.plan?.name || m.level || '—' }}</td>
              <td>{{ formatDate(m.expiresAt || m.endDate) }}</td>
              <td><StatusBadge :status="m.status || 'active'" /></td>
              <td class="text-end">
                <button class="btn btn-sm btn-outline-danger" type="button" @click="askRevoke(m)">{{ t('vip.revoke') }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="showPlan" class="modal fade show d-block" tabindex="-1" style="background: rgba(0,0,0,.45)">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content glass-modal">
          <div class="modal-header">
            <h5 class="modal-title">{{ planForm.id ? t('vip.editPlan') : t('vip.newVipPlan') }}</h5>
            <button type="button" class="btn-close" @click="showPlan = false"></button>
          </div>
          <form @submit.prevent="savePlan">
            <div class="modal-body">
              <div class="mb-3">
                <label class="form-label">{{ t('common.name') }}</label>
                <input v-model="planForm.name" class="form-control" required />
              </div>
              <div class="row g-2">
                <div class="col-6">
                  <label class="form-label">{{ t('app.price') }} ({{ t('vip.perMonth') }})</label>
                  <input v-model.number="planForm.price" type="number" min="0" class="form-control" required />
                </div>
                <div class="col-6">
                  <label class="form-label">{{ t('vip.durationDays') }}</label>
                  <input v-model.number="planForm.durationDays" type="number" min="1" class="form-control" required />
                </div>
              </div>
              <div class="mb-3 mt-3">
                <label class="form-label">{{ t('common.level') }}</label>
                <input v-model.number="planForm.level" type="number" min="1" max="100" class="form-control" />
              </div>
              <div class="mb-3">
                <label class="form-label">{{ t('vip.benefits') }}</label>
                <input v-model="planForm.benefitsText" class="form-control" />
              </div>
              <hr />
              <h6 class="mb-2">مظاهر هذا المستوى — ترفع مباشرة إلى VIP {{ planForm.level }}</h6>
              <div class="mb-3">
                <label class="form-label">شارة / شعار VIP</label>
                <input type="file" class="form-control" accept="image/*,.gif,.webp" :disabled="!!uploadingVisual" @change="uploadPlanVisual($event, 'badgeUrl')" />
                <div v-if="planForm.badgeUrl" class="vip-upload-preview"><img :src="assetUrl(planForm.badgeUrl)" alt="معاينة شارة VIP" /></div>
              </div>
              <div class="row g-3">
                <div v-for="asset in [
                  { key: 'vipFrameUrl', label: 'إطار VIP' },
                  { key: 'vipCardUrl', label: 'بطاقة VIP' },
                  { key: 'profileCardUrl', label: 'بطاقة الملف الشخصي' },
                  { key: 'chatBubbleUrl', label: 'فقاعة الدردشة' },
                  { key: 'nameplateUrl', label: 'لوحة الاسم' },
                ]" :key="asset.key" class="col-md-6">
                  <label class="form-label">{{ asset.label }}</label>
                  <input type="file" class="form-control" accept="image/*,video/mp4,video/webm,.gif,.webp,.mp4,.webm,.svga" :disabled="!!uploadingVisual" @change="uploadPlanVisual($event, asset.key)" />
                  <div v-if="planForm.visuals[asset.key]" class="vip-upload-preview">
                    <video v-if="/\.(mp4|webm)(\?|$)/i.test(planForm.visuals[asset.key])" :src="assetUrl(planForm.visuals[asset.key])" muted autoplay loop playsinline />
                    <img v-else :src="assetUrl(planForm.visuals[asset.key])" :alt="asset.label" />
                    <button type="button" class="btn btn-sm btn-outline-danger mt-1" @click="planForm.visuals[asset.key] = ''">إزالة من الخطة</button>
                  </div>
                </div>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-ghost" @click="showPlan = false">{{ t('common.cancel') }}</button>
              <button type="submit" class="btn btn-aurora" :disabled="saving">{{ t('common.save') }}</button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <div v-if="showAssign" class="modal fade show d-block" tabindex="-1" style="background: rgba(0,0,0,.45)">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content glass-modal">
          <div class="modal-header">
            <h5 class="modal-title">{{ t('vip.assignVip') }}</h5>
            <button type="button" class="btn-close" @click="showAssign = false"></button>
          </div>
          <form @submit.prevent="assign">
            <div class="modal-body">
              <div class="mb-3">
                <label class="form-label">{{ t('common.user') }} {{ t('common.id') }}</label>
                <input v-model="assignForm.userId" class="form-control" required />
              </div>
              <div class="mb-3">
                <label class="form-label">{{ t('vip.plan') }}</label>
                <select v-model="assignForm.planId" class="form-select" required>
                  <option disabled value="">{{ t('vip.selectPlan') }}</option>
                  <option v-for="p in plans" :key="p.id" :value="p.id">
                    {{ p.name }} · {{ formatNumber(p.coinPrice ?? p.price ?? 0) }} {{ t('common.coins') }}
                  </option>
                </select>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-ghost" @click="showAssign = false">{{ t('common.cancel') }}</button>
              <button type="submit" class="btn btn-aurora" :disabled="saving">{{ t('vip.assign') }}</button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="confirmTitle"
      :message="confirmMsg"
      @confirm="runConfirm"
    />
    </template>
    <template v-else>
      <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
      <div class="widget-grid mb-3">
        <article v-for="p in plans" :key="p.id" class="widget-card vip-card" :style="{ '--vip-accent': tierColor(p.level) }">
          <div class="widget-card-media vip-media vip-visual-preview">
            <img v-if="p.badgeUrl" :src="assetUrl(p.badgeUrl)" alt="شارة VIP" />
            <span v-else class="vip-tier-chip">VIP {{ p.level }}</span>
          </div>
          <div class="widget-card-body">
            <h3 class="widget-card-title">{{ p.name || ('VIP ' + p.level) }}</h3>
            <div class="small text-muted mb-2">إدارة ملفات هذا المستوى مباشرة، بدون فتح المتجر</div>
            <div class="vip-asset-list">
              <span :class="{ 'is-set': p.visuals?.vipFrameUrl }">إطار VIP</span>
              <span :class="{ 'is-set': p.visuals?.vipCardUrl }">بطاقة VIP</span>
              <span :class="{ 'is-set': p.visuals?.profileCardUrl }">بطاقة الملف الشخصي</span>
              <span :class="{ 'is-set': p.visuals?.chatBubbleUrl }">فقاعة الدردشة</span>
              <span :class="{ 'is-set': p.visuals?.nameplateUrl }">لوحة الاسم</span>
            </div>
            <div class="action-btns mt-3">
              <button class="btn btn-sm btn-aurora" type="button" @click="openPlan(p)">تعديل مظاهر VIP {{ p.level }}</button>
            </div>
          </div>
        </article>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { vipApi } from '@/api'
import { extractList, formatNumber, formatDate } from '@/composables/useUtils'
import { resolveAsset } from '@/utils/assets'
import { toast } from '@/composables/useToast'
import { useBulkSelection } from '@/composables/useBulkSelection'
import { runBulk } from '@/composables/useBulk'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import BulkActionBar from '@/components/BulkActionBar.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { uploadsApi } from '@/api'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const activeTab = ref(route.query.tab === 'visuals' ? 'visuals' : 'plans')

function setTab(tab) {
  activeTab.value = tab
  const query = tab === 'visuals' ? { tab: 'visuals' } : {}
  router.replace({ name: 'vip', query })
}

watch(() => route.query.tab, (tab) => {
  activeTab.value = tab === 'visuals' ? 'visuals' : 'plans'
})

const plans = ref([])
const members = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const showPlan = ref(false)
const showAssign = ref(false)
const planForm = reactive({
  id: null, name: '', price: 99, durationDays: 30, level: 1, benefitsText: '',
  badgeUrl: '', visuals: { vipFrameUrl: '', vipCardUrl: '', profileCardUrl: '', chatBubbleUrl: '', nameplateUrl: '' },
})
const uploadingVisual = ref('')
const assignForm = reactive({ userId: '', planId: '' })
const bulkBusy = ref(false)

const {
  selectedIds: memberSelectedIds,
  selectedCount: memberSelectedCount,
  allSelected: memberAllSelected,
  someSelected: memberSomeSelected,
  isSelected: memberIsSelected,
  toggle: memberToggle,
  clear: memberClear,
  toggleAll: memberToggleAll,
} = useBulkSelection(members)

const memberBulkActions = computed(() => [
  { key: 'revoke', label: t('bulk.revokeSelected'), icon: 'bi-x-circle', variant: 'btn-outline-danger' },
])

async function onMemberBulkAction(key) {
  const ids = memberSelectedIds.value
  if (!ids.length || key !== 'revoke') return
  confirmTitle.value = t('bulk.selectAll')
  confirmMsg.value = `${t('bulk.revokeSelected')} (${ids.length})?`
  pendingAction.value = async () => {
    bulkBusy.value = true
    const data = await runBulk({ resource: 'vip', action: key, ids, t })
    bulkBusy.value = false
    if (data) {
      memberClear()
      await load()
    }
  }
  confirmOpen.value = true
}

function tierForLevel(level) {
  // Mikoo medals are VIP1–7 only.
  return Math.max(1, Math.min(7, Number(level || 1)))
}

const LEVEL_COLORS = {
  1: '#8d6e63',
  2: '#90a4ae',
  3: '#c9a227',
  4: '#e91e8c',
  5: '#7c4dff',
  6: '#00bcd4',
  7: '#ff6f00',
}

function tierColor(level) {
  return LEVEL_COLORS[tierForLevel(level)] || '#00c9b1'
}

function assetUrl(path) { return resolveAsset(path) }

function badgeSrc(p) {
  const lvl = tierForLevel(p.level)
  const fromPlan = p.badgeUrl || `/assets/cosmetics/vip/vip_medal_mikoo_${lvl}.png?v=20260801vip7`
  return resolveAsset(fromPlan)
}

function fallbackBadge(level) {
  return resolveAsset(`/assets/cosmetics/vip/vip_medal_mikoo_${tierForLevel(level)}.png?v=20260801vip7`)
}

async function load() {
  loading.value = true
  error.value = ''
  const [p, m] = await Promise.all([vipApi.plans(), vipApi.list({ limit: 50 })])
  loading.value = false
  if (p.error && m.error) error.value = p.error.message
  else if (p.error || m.error) error.value = (p.error || m.error).message
  // Show Mikoo VIP1–7 only (we do not have medal art for VIP8–100).
  plans.value = extractList(p.data)
    .filter((row) => Number(row?.level || 0) >= 1 && Number(row?.level || 0) <= 7)
    .sort((a, b) => Number(a.level || 0) - Number(b.level || 0))
  members.value = extractList(m.data)
}

function openPlan(p = null) {
  if (p) {
    Object.assign(planForm, {
      id: p.id,
      name: p.name || '',
      price: p.coinPrice ?? p.price ?? 99,
      durationDays: p.durationDays || p.duration || 30,
      level: Math.max(1, Math.min(100, Number(p.level || 1))),
      benefitsText: (p.benefits || p.perks || []).join(', '),
      badgeUrl: p.badgeUrl || '',
      visuals: {
        vipFrameUrl: p.visuals?.vipFrameUrl || '',
        vipCardUrl: p.visuals?.vipCardUrl || '',
        profileCardUrl: p.visuals?.profileCardUrl || '',
        chatBubbleUrl: p.visuals?.chatBubbleUrl || '',
        nameplateUrl: p.visuals?.nameplateUrl || '',
      },
    })
  } else {
    Object.assign(planForm, {
      id: null, name: '', price: 99, durationDays: 30, level: plans.value.length + 1, benefitsText: '',
      badgeUrl: '', visuals: { vipFrameUrl: '', vipCardUrl: '', profileCardUrl: '', chatBubbleUrl: '', nameplateUrl: '' },
    })
  }
  showPlan.value = true
}

async function uploadPlanVisual(event, field) {
  const file = event.target.files?.[0]
  if (!file) return
  uploadingVisual.value = field
  error.value = ''
  try {
    const { data, error: err } = await uploadsApi.upload(file)
    if (err) throw err
    const url = data?.data?.url || data?.url
    if (!url) throw new Error('لم يرجع الخادم رابط الملف بعد الرفع')
    if (field === 'badgeUrl') planForm.badgeUrl = url
    else planForm.visuals[field] = url
    toast().success('تم رفع الملف')
  } catch (e) {
    error.value = e?.message || t('app.error')
    toast().danger(error.value)
  } finally {
    uploadingVisual.value = ''
    event.target.value = ''
  }
}

async function savePlan() {
  saving.value = true
  const payload = {
    name: planForm.name,
    price: planForm.price,
    durationDays: planForm.durationDays,
    level: Math.max(1, Math.min(100, Number(planForm.level || 1))),
    benefits: planForm.benefitsText.split(',').map((s) => s.trim()).filter(Boolean),
    badgeUrl: planForm.badgeUrl || null,
    visuals: { ...planForm.visuals },
  }
  const result = planForm.id
    ? await vipApi.updatePlan(planForm.id, payload)
    : await vipApi.createPlan(payload)
  saving.value = false
  if (result.error) {
    error.value = result.error.message
    toast().danger(result.error.message)
    return
  }
  showPlan.value = false
  success.value = t('vip.planSaved')
  toast().success(t('vip.planSaved'))
  await load()
}

const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
const pendingAction = ref(null)

function askRemovePlan(p) {
  pendingAction.value = { type: 'deletePlan', payload: p }
  confirmTitle.value = t('common.delete')
  confirmMsg.value = t('vip.confirmDelete', { name: p.name })
  confirmOpen.value = true
}

function askRevoke(m) {
  pendingAction.value = { type: 'revoke', payload: m }
  confirmTitle.value = t('vip.revoke')
  confirmMsg.value = t('vip.confirmRevoke')
  confirmOpen.value = true
}

async function runConfirm() {
  const action = pendingAction.value
  pendingAction.value = null
  if (!action) return
  if (typeof action === 'function') {
    await action()
    return
  }
  if (action.type === 'deletePlan') await doRemovePlan(action.payload)
  else if (action.type === 'revoke') await doRevoke(action.payload)
}

async function doRemovePlan(p) {
  const { error: err } = await vipApi.deletePlan(p.id)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('vip.planDeleted')
    toast().success(t('vip.planDeleted'))
    await load()
  }
}

async function assign() {
  saving.value = true
  const { error: err } = await vipApi.assign({ ...assignForm })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  showAssign.value = false
  success.value = t('vip.vipAssigned')
  toast().success(t('vip.vipAssigned'))
  await load()
}

async function doRevoke(m) {
  const { error: err } = await vipApi.revoke(m.id)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('vip.vipRevoked')
    toast().success(t('vip.vipRevoked'))
    await load()
  }
}

onMounted(load)
</script>

<style scoped>
.vip-card {
  --vip-accent: #00c9b1;
  border-color: color-mix(in srgb, var(--vip-accent) 35%, var(--border-color));
}

.vip-card:hover {
  border-color: color-mix(in srgb, var(--vip-accent) 55%, transparent);
  box-shadow: 0 16px 48px color-mix(in srgb, var(--vip-accent) 22%, transparent);
}

.vip-media {
  aspect-ratio: 1 / 1;
  min-height: 160px;
  background:
    radial-gradient(circle at 50% 35%, color-mix(in srgb, var(--vip-accent) 42%, transparent), transparent 58%),
    linear-gradient(165deg, rgba(255, 255, 255, 0.06), rgba(0, 0, 0, 0.28));
}

.vip-badge {
  width: 72%;
  height: 72%;
  max-width: 140px;
  max-height: 140px;
  object-fit: contain;
  filter: drop-shadow(0 10px 22px rgba(0, 0, 0, 0.45));
}

.vip-tier-chip {
  position: absolute;
  inset-inline-start: 0.75rem;
  inset-block-end: 0.75rem;
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
  color: #fff;
  background: color-mix(in srgb, var(--vip-accent) 85%, #000);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.28);
}

.vip-visual-preview { min-height: 140px; }
.vip-visual-preview img { max-width: 70%; max-height: 120px; object-fit: contain; }
.vip-asset-list { display: flex; flex-wrap: wrap; gap: 6px; }
.vip-asset-list span { border: 1px solid var(--border-color); border-radius: 8px; padding: 4px 7px; font-size: .72rem; opacity: .7; }
.vip-asset-list span.is-set { opacity: 1; border-color: #00c9b1; color: #00c9b1; }
.vip-upload-preview { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
.vip-upload-preview img, .vip-upload-preview video { max-width: 180px; max-height: 100px; object-fit: contain; border-radius: 8px; background: #111; }
.vip-price {
  color: #ffe08a;
  font-size: 1.12rem;
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.vip-price .coin-icon {
  color: #ffd56a;
  font-size: 1rem;
}

.vip-price-unit {
  font-size: 0.75rem;
  color: var(--text-secondary);
  font-weight: 500;
}

.vip-benefits {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 0.35rem;
  font-size: 0.78rem;
  color: var(--text-secondary);
}

.vip-benefits li {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  line-height: 1.35;
}

.vip-benefits li::before {
  content: '';
  width: 6px;
  height: 6px;
  margin-top: 0.4rem;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--vip-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--vip-accent) 25%, transparent);
}
</style>
