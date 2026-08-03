<template>
  <div>
    <PageHeader :title="t('gifts.title')" :subtitle="t('gifts.subtitle')">
      <template #actions>
        <button class="btn btn-aurora btn-sm" type="button" @click="openCreate">
          <i class="bi bi-plus-lg me-1"></i> {{ t('gifts.newGift') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <LoadingSpinner v-if="loading" />
    <template v-else>
      <BulkActionBar
        :count="gifts.length"
        :selected-count="selectedCount"
        :all-selected="allSelected"
        :some-selected="someSelected"
        :busy="bulkBusy"
        :actions="bulkActions"
        @toggle-all="toggleAll"
        @clear="clear"
        @action="onBulkAction"
      />
      <div class="widget-grid">
        <div v-if="!gifts.length" class="glass p-4 empty-state">{{ t('app.none') }}</div>
        <article v-for="g in gifts" :key="g.id" class="widget-card position-relative" :class="{ 'is-selected': isSelected(g.id) }">
          <BulkCheck :checked="isSelected(g.id)" @toggle="toggle(g.id)" />
        <div class="widget-card-media">
          <video
            v-if="isVideo(g.animationUrl)"
            :src="absUrl(g.animationUrl)"
            muted
            loop
            autoplay
            playsinline
          />
          <img
            v-else-if="g.iconUrl"
            :src="absUrl(g.iconUrl)"
            alt=""
          />
          <img
            v-else-if="isImageAnim(g.animationUrl)"
            :src="absUrl(g.animationUrl)"
            alt=""
          />
          <i v-else class="bi bi-gift text-muted" style="font-size: 2rem" />
        </div>
        <div class="widget-card-body">
          <div class="d-flex justify-content-between gap-2">
            <h3 class="widget-card-title">{{ g.name }}</h3>
            <StatusBadge :status="g.isActive === false ? 'inactive' : 'active'" />
          </div>
          <div class="widget-card-meta mb-2">
            {{ formatNumber(g.coinPrice ?? 0) }} · {{ giftTypeLabel(g.type) }}
            <span v-if="isVideo(g.animationUrl)"> · فيديو</span>
            <span v-else-if="isGif(g.animationUrl)"> · GIF</span>
          </div>
          <div class="action-btns">
            <button class="btn btn-sm btn-ghost" type="button" @click="openEdit(g)">
              <i class="bi bi-pencil" />
            </button>
            <button class="btn btn-sm btn-outline-danger" type="button" @click="askRemove(g)">
              <i class="bi bi-trash" />
            </button>
          </div>
        </div>
      </article>
      </div>
    </template>

    <div v-if="showModal" class="modal fade show d-block" tabindex="-1" style="background: rgba(0,0,0,.45)">
      <div class="modal-dialog modal-dialog-centered modal-lg">
        <div class="modal-content glass-modal">
          <div class="modal-header">
            <h5 class="modal-title">{{ editingId ? t('gifts.editGift') : t('gifts.newGift') }}</h5>
            <button type="button" class="btn-close" @click="showModal = false"></button>
          </div>
          <form @submit.prevent="save">
            <div class="modal-body">
              <div class="row g-3">
                <div class="col-md-6">
                  <label class="form-label">{{ t('app.name') }}</label>
                  <input v-model="form.name" class="form-control" required />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('gifts.coinPrice') }}</label>
                  <input v-model.number="form.coinPrice" type="number" min="1" class="form-control" required />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('gifts.diamondValue') }}</label>
                  <input v-model.number="form.diamondValue" type="number" min="1" class="form-control" />
                </div>
                <div class="col-md-4">
                  <label class="form-label">{{ t('gifts.giftType') }}</label>
                  <select v-model="form.type" class="form-select">
                    <option value="normal">{{ t('gifts.normal') }}</option>
                    <option value="lucky">{{ t('gifts.lucky') }}</option>
                    <option value="combo">{{ t('gifts.combo') }}</option>
                    <option value="premium">{{ t('gifts.premium') }}</option>
                  </select>
                </div>
                <template v-if="form.type === 'lucky'">
                  <div class="col-12">
                    <div class="small text-muted mb-2">{{ t('gifts.luckyHint') }}</div>
                  </div>
                  <div class="col-md-4">
                    <label class="form-label">{{ t('gifts.winChance') }}</label>
                    <input v-model.number="form.luckyConfig.winChance" type="number" min="0" max="1" step="0.01" class="form-control" />
                  </div>
                  <div class="col-md-4">
                    <label class="form-label">{{ t('gifts.minMultiplier') }}</label>
                    <input v-model.number="form.luckyConfig.minMultiplier" type="number" min="1" max="8" step="0.1" class="form-control" />
                  </div>
                  <div class="col-md-4">
                    <label class="form-label">{{ t('gifts.maxMultiplier') }}</label>
                    <input v-model.number="form.luckyConfig.maxMultiplier" type="number" min="1" max="8" step="0.1" class="form-control" />
                  </div>
                </template>
                <div class="col-md-4">
                  <label class="form-label">{{ t('gifts.sortOrder') }}</label>
                  <input v-model.number="form.sortOrder" type="number" class="form-control" />
                </div>
                <div class="col-md-4">
                  <label class="form-label">{{ t('app.status') }}</label>
                  <select v-model="form.status" class="form-select">
                    <option value="active">{{ t('app.active') }}</option>
                    <option value="inactive">{{ t('app.inactive') }}</option>
                  </select>
                </div>
                <div class="col-md-8">
                  <label class="form-label">{{ t('gifts.icon') }} URL</label>
                  <input v-model="form.iconUrl" class="form-control" required />
                </div>
                <div class="col-md-4">
                  <label class="form-label">{{ t('gifts.uploadIcon') }}</label>
                  <input type="file" accept="image/*,.gif,.webp" class="form-control" @change="onFile" />
                </div>
                <div class="col-md-8">
                  <label class="form-label">{{ t('gifts.animation') }} (GIF / فيديو MP4)</label>
                  <input
                    v-model="form.animationUrl"
                    class="form-control"
                    placeholder="رابط GIF أو MP4 أو اتركه فارغاً"
                  />
                  <div class="form-text">ارفع فيديو أو GIF للهدايا الثابتة داخل الروم — بدون ملفات حركة Lottie.</div>
                </div>
                <div class="col-md-4">
                  <label class="form-label">رفع GIF / فيديو</label>
                  <input
                    type="file"
                    accept="image/gif,image/webp,video/mp4,video/webm,video/quicktime,.gif,.webp,.mp4,.webm,.mov"
                    class="form-control"
                    @change="onAnimFile"
                  />
                </div>
                <div class="col-6" v-if="form.iconUrl">
                  <div class="small text-muted mb-1">أيقونة</div>
                  <img :src="absUrl(form.iconUrl)" alt="" width="88" height="88" style="object-fit: contain" />
                </div>
                <div class="col-6" v-if="form.animationUrl">
                  <div class="small text-muted mb-1">عرض الأنيميشن</div>
                  <video
                    v-if="isVideo(form.animationUrl)"
                    :src="absUrl(form.animationUrl)"
                    controls
                    muted
                    loop
                    playsinline
                    style="max-width: 160px; max-height: 160px; border-radius: 12px"
                  />
                  <img
                    v-else-if="isImageAnim(form.animationUrl)"
                    :src="absUrl(form.animationUrl)"
                    alt=""
                    style="max-width: 160px; max-height: 160px; object-fit: contain"
                  />
                  <div v-else class="small text-muted">{{ form.animationUrl }}</div>
                </div>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-ghost" @click="showModal = false">{{ t('app.cancel') }}</button>
              <button type="submit" class="btn btn-aurora" :disabled="saving">{{ saving ? t('app.saving') : t('app.save') }}</button>
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { giftsApi, uploadsApi } from '@/api'
import { extractList, formatNumber } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import { useBulkSelection } from '@/composables/useBulkSelection'
import { runBulk } from '@/composables/useBulk'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import BulkActionBar from '@/components/BulkActionBar.vue'
import BulkCheck from '@/components/BulkCheck.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()
const ORIGIN = (import.meta.env.VITE_API_ORIGIN || 'https://api.adnova.bbs.tr').replace(/\/$/, '')

const gifts = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const showModal = ref(false)
const editingId = ref(null)
const bulkBusy = ref(false)

const {
  selectedIds,
  selectedCount,
  allSelected,
  someSelected,
  isSelected,
  toggle,
  clear,
  toggleAll,
} = useBulkSelection(gifts)

const bulkActions = computed(() => [
  { key: 'activate', label: t('bulk.activateSelected'), icon: 'bi-check2-circle', variant: 'btn-outline-success' },
  { key: 'deactivate', label: t('bulk.deactivateSelected'), icon: 'bi-pause-circle', variant: 'btn-outline-warning' },
  { key: 'delete', label: t('bulk.deleteSelected'), icon: 'bi-trash', variant: 'btn-outline-danger' },
])

async function onBulkAction(key) {
  const ids = selectedIds.value
  if (!ids.length) return
  const messages = {
    activate: t('bulk.confirmApprove', { count: ids.length }),
    deactivate: t('bulk.confirmSuspend', { count: ids.length }),
    delete: t('bulk.confirmDelete', { count: ids.length }),
  }
  confirmTitle.value = t('bulk.selectAll')
  confirmMsg.value = messages[key] || messages.delete
  pendingAction.value = async () => {
    bulkBusy.value = true
    const data = await runBulk({ resource: 'gifts', action: key, ids, t })
    bulkBusy.value = false
    if (data) {
      clear()
      await load()
    }
  }
  confirmOpen.value = true
}

const form = reactive({
  name: '',
  coinPrice: 10,
  diamondValue: 8,
  type: 'normal',
  iconUrl: '',
  animationUrl: '',
  sortOrder: 0,
  status: 'active',
  luckyConfig: {
    winChance: 0.1,
    minMultiplier: 1,
    maxMultiplier: 5,
  },
})

function absUrl(u) {
  if (!u) return ''
  if (/^https?:\/\//i.test(u)) return u
  return `${ORIGIN}${u.startsWith('/') ? '' : '/'}${u}`
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await giftsApi.list({ limit: 200 })
  loading.value = false
  if (err) {
    error.value = err.message
    gifts.value = []
    return
  }
  gifts.value = extractList(data)
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    coinPrice: 10,
    diamondValue: 8,
    type: 'normal',
    iconUrl: '',
    animationUrl: '',
    sortOrder: 0,
    status: 'active',
    luckyConfig: {
      winChance: 0.1,
      minMultiplier: 1,
      maxMultiplier: 5,
    },
  })
  showModal.value = true
}

function openEdit(g) {
  editingId.value = g.id
  Object.assign(form, {
    name: g.name || '',
    coinPrice: g.coinPrice ?? 10,
    diamondValue: g.diamondValue ?? Math.max(1, Math.floor((g.coinPrice || 1) * 0.8)),
    type: g.type || 'normal',
    iconUrl: g.iconUrl || '',
    animationUrl: g.animationUrl || '',
    sortOrder: g.sortOrder || 0,
    status: g.isActive === false ? 'inactive' : 'active',
    luckyConfig: {
      winChance: Number(g.luckyConfig?.winChance ?? 0.1),
      minMultiplier: Number(g.luckyConfig?.minMultiplier ?? 1),
      maxMultiplier: Number(g.luckyConfig?.maxMultiplier ?? 5),
    },
  })
  showModal.value = true
}

async function upload(file) {
  const { data, error: err } = await uploadsApi.upload(file)
  if (err) throw err
  return data?.url || data?.data?.url || ''
}

async function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  saving.value = true
  try {
    form.iconUrl = absUrl(await upload(file))
  } catch (err) {
    error.value = err.message
  } finally {
    saving.value = false
  }
}

async function onAnimFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  saving.value = true
  try {
    form.animationUrl = absUrl(await upload(file))
  } catch (err) {
    error.value = err.message
  } finally {
    saving.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    name: form.name,
    iconUrl: form.iconUrl,
    animationUrl: form.animationUrl || null,
    coinPrice: Number(form.coinPrice) || 1,
    diamondValue: Number(form.diamondValue) || 1,
    type: form.type || 'normal',
    sortOrder: Number(form.sortOrder) || 0,
    isActive: form.status === 'active',
    luckyConfig:
      form.type === 'lucky'
        ? {
            winChance: Math.max(0, Math.min(1, Number(form.luckyConfig.winChance) || 0)),
            minMultiplier: Math.max(1, Number(form.luckyConfig.minMultiplier) || 1),
            maxMultiplier: Math.max(1, Number(form.luckyConfig.maxMultiplier) || 1),
          }
        : null,
  }
  const result = editingId.value
    ? await giftsApi.update(editingId.value, payload)
    : await giftsApi.create(payload)
  saving.value = false
  if (result.error) {
    error.value = result.error.message
    toast().danger(result.error.message)
    return
  }
  showModal.value = false
  success.value = t('app.success')
  toast().success(t('app.success'))
  await load()
}

const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
const pendingDelete = ref(null)
const pendingAction = ref(null)

function isVideo(url) {
  return /\.(mp4|webm|mov)(\?|$)/i.test(String(url || ''))
}
function isGif(url) {
  return /\.gif(\?|$)/i.test(String(url || ''))
}
function isImageAnim(url) {
  return /\.(gif|webp|png|jpe?g)(\?|$)/i.test(String(url || ''))
}

function askRemove(g) {
  pendingDelete.value = g
  pendingAction.value = null
  confirmTitle.value = t('common.delete')
  confirmMsg.value = `${t('app.delete')} «${g.name}»؟`
  confirmOpen.value = true
}

async function runConfirm() {
  const action = pendingAction.value
  pendingAction.value = null
  if (typeof action === 'function') {
    await action()
    return
  }
  await doRemove()
}

async function doRemove() {
  const g = pendingDelete.value
  pendingDelete.value = null
  if (!g?.id) return
  const { error: err } = await giftsApi.delete(g.id)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('app.success')
    toast().success(t('app.success'))
    await load()
  }
}

onMounted(load)

function giftTypeLabel(type) {
  const key = String(type || 'normal').toLowerCase()
  const map = {
    normal: 'gifts.normal',
    lucky: 'gifts.lucky',
    premium: 'gifts.premium',
    combo: 'gifts.combo',
  }
  return t(map[key] || 'gifts.normal')
}
</script>

<style scoped>
.widget-card.is-selected {
  outline: 2px solid rgba(45, 212, 191, 0.65);
  outline-offset: 2px;
}
</style>
