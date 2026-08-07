<template>
  <div>
    <PageHeader :title="t('cosmetics.title')" :subtitle="t('cosmetics.subtitle')">
      <template #actions>
        <button class="btn btn-aurora btn-sm" type="button" @click="openCreate">{{ t('cosmetics.newItem') }}</button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="d-flex flex-wrap gap-2 mb-3">
      <button
        v-for="item in tabs"
        :key="item.key"
        type="button"
        class="btn btn-sm"
        :class="tab === item.key ? 'btn-aurora' : 'btn-ghost'"
        @click="switchTab(item.key)"
      >
        {{ item.label }}
      </button>
    </div>

    <div v-if="tab === 'vip_badge'" class="d-flex flex-wrap gap-2 mb-3">
      <button
        v-for="f in frameFilters"
        :key="f.key"
        type="button"
        class="btn btn-sm"
        :class="frameFilter === f.key ? 'btn-outline-primary' : 'btn-ghost'"
        @click="frameFilter = f.key"
      >
        {{ f.label }}
      </button>
    </div>

    <LoadingSpinner v-if="loading" />

    <template v-else>
      <BulkActionBar
        :count="filtered.length"
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
        <div v-if="!filtered.length" class="glass p-4 empty-state">{{ t('app.none') }}</div>
        <article
          v-for="item in filtered"
          :key="item.id || item.code"
          class="widget-card cosmetic-card position-relative"
          :class="{ wide: isWide(item), 'is-selected': isSelected(item.id) }"
        >
          <BulkCheck v-if="item.id" :checked="isSelected(item.id)" @toggle="toggle(item.id)" />
          <div class="widget-card-media preview-wrap" :class="[item.type || tab, { wide: isWide(item) }]">
            <template v-if="isFrameLike(item)">
              <img class="avatar-core" :src="avatarSvg" alt="" />
              <video
                v-if="mediaKind(wearSrc(item)) === 'video' && playingId === item.id"
                class="frame-overlay"
                :src="assetUrl(wearSrc(item))"
                muted
                loop
                autoplay
                playsinline
              />
              <SvgaPreview
                v-else-if="mediaKind(wearSrc(item)) === 'svga'"
                class="frame-overlay svga-frame"
                :src="wearSrc(item)"
              />
              <img
                v-else
                class="frame-overlay"
                :class="{ pulse: mediaKind(wearSrc(item)) === 'gif' || !!item.animationUrl }"
                :src="assetUrl(previewSrc(item) || wearSrc(item))"
                :alt="displayName(item)"
              />
            </template>
            <template v-else-if="(item.type || tab) === 'room_card'">
              <div class="phone-card room-card-preview">
                <div class="room-card-sample">
                  <img class="room-preview-cover" :src="avatarSvg" alt="" />
                  <div class="phone-overlay">
                    <span class="live-pill">{{ t('commonStatus.live') }}</span>
                    <span class="room-title">{{ t('app.brand') }}</span>
                  </div>
                </div>
                <img
                  class="room-frame-overlay"
                  :src="assetUrl(previewSrc(item))"
                  :alt="displayName(item)"
                />
              </div>
            </template>
            <template v-else-if="(item.type || tab) === 'entry_effect'">
              <video
                v-if="mediaKind(item.animationUrl) === 'video' && playingId === item.id"
                class="toast-preview"
                :src="assetUrl(item.animationUrl)"
                muted
                loop
                autoplay
                playsinline
                controls
              />
              <img
                v-else
                class="toast-preview"
                :src="assetUrl(previewSrc(item))"
                :alt="displayName(item)"
                @click="playingId = playingId === item.id ? null : item.id"
                style="cursor:pointer"
              />
              <div v-if="mediaLabel(item.animationUrl || item.previewUrl)" class="anim-chip">
                {{ mediaLabel(item.animationUrl || item.previewUrl) }}
                <span v-if="mediaKind(item.animationUrl) === 'video'" class="ms-1">
                  {{ playingId === item.id ? '■' : '▶' }}
                </span>
              </div>
            </template>
            <template v-else>
              <img class="badge-preview pulse" :src="assetUrl(previewSrc(item))" :alt="displayName(item)" />
            </template>
          </div>
          <div class="widget-card-body">
            <h3 class="widget-card-title text-truncate">{{ displayName(item) }}</h3>
            <div class="widget-card-meta mb-2">{{ item.code }} · {{ frameKindLabel(item) || item.type }}</div>
            <div class="small mb-2">
              <span v-if="item.coinPrice">{{ item.coinPrice }} {{ t('app.coins') }}</span>
              <span v-else class="text-muted">—</span>
              <span v-if="item.minVipLevel" class="badge text-bg-warning ms-1">VIP{{ item.minVipLevel }}+</span>
              <span v-if="item.minUserLevel && item.type !== 'vip_badge' && item.type !== 'host_badge'" class="badge text-bg-secondary ms-1">Lv{{ item.minUserLevel }}+</span>
              <span v-if="mediaLabel(wearSrc(item) || item.animationUrl)" class="badge text-bg-warning ms-1">{{ mediaLabel(wearSrc(item) || item.animationUrl) }}</span>
              <span v-if="item.isActive === false" class="badge text-bg-secondary ms-1">off</span>
            </div>
            <div class="action-btns">
              <button class="btn btn-sm btn-ghost" type="button" @click="openEdit(item)">{{ t('app.edit') }}</button>
              <button
                v-if="(item.type || tab) === 'entry_effect' && mediaKind(item.animationUrl) === 'video'"
                class="btn btn-sm btn-outline-primary"
                type="button"
                @click="playingId = playingId === item.id ? null : item.id"
              >
                {{ playingId === item.id ? 'إيقاف' : 'تشغيل' }}
              </button>
              <button class="btn btn-sm btn-outline-danger" type="button" @click="askHardDelete(item)">{{ t('app.delete') }}</button>
            </div>
          </div>
      </article>
      </div>
    </template>

    <div v-if="showModal" class="modal fade show d-block" tabindex="-1" style="background: rgba(0,0,0,.45)">
      <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content glass-modal">
          <div class="modal-header">
            <h5 class="modal-title">{{ editingId ? t('cosmetics.editItem') : t('cosmetics.newItem') }}</h5>
            <button type="button" class="btn-close" @click="showModal = false"></button>
          </div>
          <form @submit.prevent="save">
            <div class="modal-body">
              <div class="row g-3">
                <div class="col-md-4">
                  <label class="form-label">{{ t('app.code') }}</label>
                  <input v-model="form.code" class="form-control" required :disabled="!!editingId" />
                </div>
                <div class="col-md-4">
                  <label class="form-label">{{ t('app.name') }}</label>
                  <input v-model="form.name" class="form-control" required />
                </div>
                <div class="col-md-4">
                  <label class="form-label">{{ t('app.type') }}</label>
                  <select v-model="form.type" class="form-select">
                    <option v-for="item in tabs" :key="item.key" :value="item.key">{{ item.label }}</option>
                  </select>
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('app.price') }}</label>
                  <input v-model.number="form.coinPrice" type="number" min="0" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('cosmetics.minVip') }}</label>
                  <input v-model.number="form.minVipLevel" type="number" min="0" max="100" class="form-control" />
                  <div class="form-text">خطة VIP (1–100). الإطارات البصرية 1–7؛ VIP أعلى من 7 يبقى على أقصى إطار.</div>
                </div>
                <div class="col-md-3" v-if="form.type !== 'vip_badge' && form.type !== 'host_badge'">
                  <label class="form-label">{{ t('cosmetics.minLevel') }}</label>
                  <input v-model.number="form.minUserLevel" type="number" min="0" class="form-control" />
                  <div class="form-text">مستوى الحساب فقط — ليس VIP.</div>
                </div>
                <div class="col-md-3" v-else>
                  <label class="form-label">{{ t('cosmetics.minLevel') }}</label>
                  <input type="number" class="form-control" :value="0" disabled />
                  <div class="form-text text-warning">الإطارات مربوطة بالـ VIP فقط (لا مستوى الحساب).</div>
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('cosmetics.sortOrder') }}</label>
                  <input v-model.number="form.sortOrder" type="number" class="form-control" />
                </div>
                <div class="col-12">
                  <label class="form-label">{{ t('cosmetics.description') }}</label>
                  <textarea v-model="form.description" class="form-control" rows="2" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('cosmetics.uploadPreview') }}</label>
                  <input
                    type="file"
                    class="form-control"
                    accept="image/*,video/mp4,video/webm,video/quicktime,.gif,.webp,.mp4,.webm,.mov"
                    @change="onPreviewFile"
                  />
                  <div class="form-text">اختر ملف المعاينة — بدون رابط</div>
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('cosmetics.uploadAnimation') }}</label>
                  <input
                    type="file"
                    class="form-control"
                    accept="image/gif,image/webp,video/mp4,video/webm,video/quicktime,.gif,.webp,.mp4,.webm,.mov,.svga,application/octet-stream"
                    @change="onAnimFile"
                  />
                  <div class="form-text">ملف الحركة / الدخولية</div>
                </div>
                <div class="col-12">
                  <div class="form-text">{{ t('cosmetics.acceptHint') }}</div>
                </div>
                <div class="col-12">
                  <label class="form-label">{{ t('cosmetics.metaJson') }}</label>
                  <textarea v-model="form.metaJson" class="form-control font-monospace" rows="3" placeholder='{"offsetX":0,"offsetY":-4,"scale":1.15,"avatarScale":0.7}' />
                </div>
                <div class="col-12">
                  <div class="form-check form-switch">
                    <input id="cosmeticActive" v-model="form.isActive" class="form-check-input" type="checkbox" />
                    <label class="form-check-label" for="cosmeticActive">{{ t('cosmetics.active') }}</label>
                  </div>
                </div>
                <div class="col-12" v-if="form.previewUrl || form.animationUrl">
                  <div class="small text-muted mb-1">
                    <span v-if="form.type === 'entry_effect'">{{ t('cosmetics.entryPreviewHint') }}</span>
                    <span v-else-if="form.type === 'vip_badge'">{{ t('cosmetics.hostPreviewHint') }}</span>
                    <span v-else>{{ t('cosmetics.phonePreview') }}</span>
                  </div>
                  <div class="d-flex gap-3 align-items-center flex-wrap">
                    <video
                      v-if="mediaKind(formWearSrc) === 'video'"
                      :src="assetUrl(formWearSrc)"
                      muted
                      loop
                      autoplay
                      playsinline
                      controls
                      style="max-height:160px;max-width:240px;object-fit:contain;background:#000;border-radius:12px"
                    />
                    <div
                      v-else-if="mediaKind(formWearSrc) === 'svga'"
                      style="width:160px;height:160px;border-radius:12px;overflow:hidden;background:#0a1018"
                    >
                      <SvgaPreview :src="formWearSrc" />
                    </div>
                    <img
                      v-else-if="formWearSrc"
                      :src="assetUrl(formWearSrc)"
                      alt=""
                      style="max-height:120px;object-fit:contain"
                    />
                    <span v-if="mediaLabel(formWearSrc)" class="badge text-bg-warning">{{ mediaLabel(formWearSrc) }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-ghost" @click="showModal = false">{{ t('app.cancel') }}</button>
              <button type="submit" class="btn btn-aurora" :disabled="saving || uploading">
                {{ uploading ? t('app.saving') : saving ? t('app.saving') : t('app.save') }}
              </button>
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
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import SvgaPreview from '@/components/SvgaPreview.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { cosmeticsApi, uploadsApi } from '@/api'
import { resolveAsset } from '@/utils/assets'
import { toast } from '@/composables/useToast'
import { useBulkSelection } from '@/composables/useBulkSelection'
import { runBulk } from '@/composables/useBulk'
import BulkActionBar from '@/components/BulkActionBar.vue'
import BulkCheck from '@/components/BulkCheck.vue'

const { t } = useI18n()
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const error = ref('')
const success = ref('')
const items = ref([])
const tab = ref('vip_badge')
const frameFilter = ref('all')
const playingId = ref(null)
const showModal = ref(false)
const editingId = ref(null)
const form = ref(emptyForm())
const bulkBusy = ref(false)

const avatarSvg =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='80' height='80'%3E%3Cdefs%3E%3ClinearGradient id='g' x1='0' y1='0' x2='1' y2='1'%3E%3Cstop stop-color='%231a2a3a'/%3E%3Cstop offset='1' stop-color='%230a1520'/%3E%3C/linearGradient%3E%3C/defs%3E%3Ccircle cx='40' cy='40' r='36' fill='url(%23g)'/%3E%3Ccircle cx='40' cy='32' r='12' fill='%236a8899'/%3E%3Cellipse cx='40' cy='58' rx='18' ry='12' fill='%236a8899'/%3E%3C/svg%3E"

const tabs = computed(() => [
  { key: 'vip_badge', label: t('cosmetics.frames') },
  { key: 'entry_effect', label: t('cosmetics.entry') },
  { key: 'room_card', label: t('cosmetics.roomCards') },
  { key: 'room_background', label: t('cosmetics.roomBackgrounds') },
  { key: 'level_badge', label: t('cosmetics.levels') },
])

const frameFilters = [
  { key: 'all', label: 'الكل' },
  { key: 'vip', label: 'VIP (ثابت)' },
  { key: 'animated', label: 'متحرك SVGA' },
  { key: 'agency', label: 'وكالة شهرية' },
  { key: 'event', label: 'جوائز / فعاليات' },
]

function frameBucket(item) {
  const code = String(item?.code || '').toLowerCase()
  const name = String(item?.name || '').toLowerCase()
  if (code.includes('monthly_s') || code.includes('monthly_a') || name.includes('agency')) {
    return 'agency'
  }
  // Event / contest prizes first so "جوائز / فعاليات" stays accurate even when SVGA.
  if (
    code.includes('world_cup') ||
    code.includes('monopoly') ||
    code.includes('charm') ||
    code.includes('prize') ||
    /_top\d/.test(code) ||
    /top\d+_/.test(code) ||
    code.includes('prizetop')
  ) {
    return 'event'
  }
  if (/vip\d|_vip\d|vip_/.test(code) && !code.includes('svga')) {
    // Mikoo VIP1–7 static frames
    if (code.includes('vip') && item?.meta?.hasSvga !== true && mediaKind(wearSrc(item)) !== 'svga') {
      return 'vip'
    }
  }
  if (item?.meta?.hasSvga === true || mediaKind(wearSrc(item)) === 'svga') return 'animated'
  return 'event'
}

function frameKindLabel(item) {
  if ((item.type || tab.value) !== 'vip_badge') return ''
  const b = frameBucket(item)
  if (b === 'vip') return 'VIP ثابت'
  if (b === 'animated') return 'متحرك'
  if (b === 'agency') return 'وكالة شهرية'
  return 'فعالية'
}

function displayName(item) {
  const code = String(item?.code || '')
  const raw = String(item?.name || code)
  const mS = code.match(/monthly_s(\d)_agency/i)
  if (mS) return `مكافأة وكالة شهرية · رتبة S${mS[1]}`
  if (/monthly_a_agency/i.test(code)) return 'مكافأة وكالة شهرية · رتبة A'
  const mVip = code.match(/_vip(\d+)$/i) || code.match(/vip(\d+)/i)
  if (mVip && (item.type === 'vip_badge' || tab.value === 'vip_badge')) {
    if (frameBucket(item) === 'vip') return `إطار VIP${mVip[1]} (ثابت)`
  }
  // Fix mojibake-ish leftover names by preferring Arabic rewrite for common badges
  if (/vip_medal_mikoo_(\d+)/i.test(code)) {
    const n = code.match(/vip_medal_mikoo_(\d+)/i)[1]
    return `شارة VIP ${n}`
  }
  return raw
}

const filtered = computed(() => {
  let rows = items.value.filter((i) => (i.type || tab.value) === tab.value)
  // Hide legacy aristocracy vip1..vip10 medals from Frames tab.
  if (tab.value === 'vip_badge') {
    rows = rows.filter((i) => {
      const code = String(i.code || '')
      if (/^vip\d+$/i.test(code)) return false
      if (i?.meta?.aristocracy === true && !String(i.code || '').includes('frame_')) return false
      return true
    })
    if (frameFilter.value !== 'all') {
      rows = rows.filter((i) => frameBucket(i) === frameFilter.value)
    }
  }
  // Always sort VIP-numbered cosmetics 1→7 (Mikoo catalog order was scrambled).
  rows = [...rows].sort((a, b) => vipSortKey(a) - vipSortKey(b))
  return rows
})

const {
  selectedIds,
  selectedCount,
  allSelected,
  someSelected,
  isSelected,
  toggle,
  clear,
  toggleAll,
} = useBulkSelection(filtered)

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
    const data = await runBulk({ resource: 'cosmetics', action: key, ids, t })
    bulkBusy.value = false
    if (data) {
      clear()
      await load()
    }
  }
  confirmOpen.value = true
}

function vipSortKey(item) {
  const code = String(item?.code || '')
  const name = String(item?.name || '')
  const metaVip = Number(item?.meta?.vipLevel || 0)
  if (metaVip >= 1) return metaVip
  const m =
    code.match(/(?:^|_)vip(\d+)$/i) ||
    code.match(/vip_medal_mikoo_(\d+)/i) ||
    code.match(/level_vip_(\d+)/i) ||
    code.match(/vip[_-]?(\d+)/i) ||
    name.match(/VIP\s*(\d+)/i)
  if (m) return Number(m[1]) || 0
  return Number(item?.sortOrder || 0) + 1000
}

function switchTab(key) {
  tab.value = key
  playingId.value = null
  frameFilter.value = 'all'
  load()
}

function emptyForm() {
  return {
    code: '',
    name: '',
    type: 'vip_badge',
    description: '',
    coinPrice: 0,
    minVipLevel: 0,
    minUserLevel: 0,
    sortOrder: 0,
    previewUrl: '',
    animationUrl: '',
    metaJson: '',
    isActive: true,
  }
}

function assetUrl(path) {
  return resolveAsset(path)
}

function stripQuery(url) {
  const s = String(url || '')
  const q = s.indexOf('?')
  return q >= 0 ? s.slice(0, q) : s
}

/** Detect playable cosmetic media kind from URL extension. */
function mediaKind(url) {
  const lower = stripQuery(url).toLowerCase()
  if (!lower) return ''
  if (lower.includes('runtime.html') || lower.endsWith('.html') || lower.endsWith('.htm')) return ''
  if (lower.endsWith('.json')) return 'lottie'
  if (lower.endsWith('.svga')) return 'svga'
  if (/\.(mp4|webm|mov)$/.test(lower)) return 'video'
  if (lower.endsWith('.gif')) return 'gif'
  if (/\.(png|jpe?g|webp)$/.test(lower)) return 'image'
  return 'image'
}

function mediaLabel(url) {
  const kind = mediaKind(url)
  if (kind === 'video') return t('cosmetics.mediaVideo')
  if (kind === 'gif') return t('cosmetics.mediaGif')
  if (kind === 'svga') return t('cosmetics.mediaSvga')
  if (kind === 'lottie') return t('cosmetics.mediaLottie')
  if (kind === 'image' && url) return t('cosmetics.mediaImage')
  return ''
}

function isPlayableMotion(url) {
  const kind = mediaKind(url)
  return kind === 'video' || kind === 'gif' || kind === 'svga'
    || (kind === 'image' && stripQuery(url).toLowerCase().endsWith('.webp'))
}

/** Prefer GIF/MP4 animation over still preview / HTML engine. */
function wearSrc(item) {
  const anim = item?.animationUrl || ''
  const preview = item?.previewUrl || ''
  if (isPlayableMotion(anim)) return anim
  if (isPlayableMotion(preview) || mediaKind(preview) === 'image') return preview || anim
  return preview || anim
}

const formWearSrc = computed(() => wearSrc(form.value))

function isFrameLike(item) {
  const type = item.type || tab.value
  return type === 'vip_badge'
}

function isWide(item) {
  const type = item.type || tab.value
  return type === 'room_card' || type === 'entry_effect' || type === 'room_background'
}

function previewSrc(item) {
  return item.previewUrl || item.animationUrl || ''
}

function openCreate() {
  editingId.value = null
  form.value = { ...emptyForm(), type: tab.value }
  showModal.value = true
}

function openEdit(item) {
  editingId.value = item.id
  form.value = {
    code: item.code || '',
    name: item.name || '',
    type: item.type || tab.value,
    description: item.description || '',
    coinPrice: item.coinPrice || 0,
    minVipLevel: item.minVipLevel || 0,
    minUserLevel: item.minUserLevel || 0,
    sortOrder: item.sortOrder || 0,
    previewUrl: item.previewUrl || '',
    animationUrl: item.animationUrl || '',
    metaJson: item.meta ? JSON.stringify(item.meta, null, 2) : '',
    isActive: item.isActive !== false,
  }
  showModal.value = true
}

async function onPreviewFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploading.value = true
  error.value = ''
  try {
    const { data, error: err } = await uploadsApi.upload(file)
    if (err) throw err
    const url = data?.data?.url || data?.url
    if (url) {
      form.value.previewUrl = url
      // If still/preview is itself GIF/video and motion field empty/HTML — use it as motion too.
      const anim = String(form.value.animationUrl || '')
      if (!anim || mediaKind(anim) === '' || mediaKind(anim) === 'lottie') {
        if (isPlayableMotion(url)) form.value.animationUrl = url
      }
    }
  } catch (e) {
    error.value = e?.message || t('app.error')
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

async function onAnimFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploading.value = true
  error.value = ''
  try {
    const { data, error: err } = await uploadsApi.upload(file)
    if (err) throw err
    const url = data?.data?.url || data?.url
    if (url) {
      form.value.animationUrl = url
      // Auto-fill preview if empty so the card always has a thumbnail.
      if (!form.value.previewUrl) form.value.previewUrl = url
    }
  } catch (e) {
    error.value = e?.message || t('app.error')
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

async function load() {
  loading.value = true
  error.value = ''
  playingId.value = null
  try {
    const { data, error: err } = await cosmeticsApi.adminList({ type: tab.value })
    if (err) throw err
    const payload = data?.data ?? data
    items.value = Array.isArray(payload) ? payload : payload?.items || []
  } catch (e) {
    error.value = e?.message || t('app.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    let meta = null
    const rawMeta = String(form.value.metaJson || '').trim()
    if (rawMeta) {
      try {
        meta = JSON.parse(rawMeta)
      } catch {
        throw new Error(t('cosmetics.metaInvalid'))
      }
    }
    const payload = {
      code: form.value.code,
      name: form.value.name,
      type: form.value.type,
      description: form.value.description || null,
      coinPrice: Number(form.value.coinPrice || 0),
      minVipLevel: Number(form.value.minVipLevel || 0),
      minUserLevel: ['vip_badge', 'host_badge'].includes(form.value.type)
        ? 0
        : Number(form.value.minUserLevel || 0),
      sortOrder: Number(form.value.sortOrder || 0),
      previewUrl: form.value.previewUrl,
      animationUrl: form.value.animationUrl || null,
      meta,
      isActive: !!form.value.isActive,
    }
    // Host / VIP / entry: app plays GIF·WebP·MP4 only — drop HTML/Lottie engines.
    if (['vip_badge', 'entry_effect'].includes(payload.type)) {
      if (!isPlayableMotion(payload.animationUrl) && mediaKind(payload.animationUrl) !== 'image') {
        payload.animationUrl = isPlayableMotion(payload.previewUrl)
          ? payload.previewUrl
          : null
      }
    }
    const result = editingId.value
      ? await cosmeticsApi.update(editingId.value, payload)
      : await cosmeticsApi.create(payload)
    if (result.error) throw result.error
    success.value = t('app.success')
    toast().success(t('app.success'))
    showModal.value = false
    await load()
  } catch (e) {
    error.value = e?.message || t('app.error')
    toast().danger(error.value)
  } finally {
    saving.value = false
  }
}

const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
const pendingDelete = ref(null)
const pendingAction = ref(null)

function askHardDelete(item) {
  if (!item?.id) return
  const name = item.name || item.code || ''
  pendingDelete.value = item
  pendingAction.value = null
  confirmTitle.value = t('app.delete')
  confirmMsg.value = `${t('cosmetics.confirmDelete', { name })}\n\n${t('cosmetics.deleteHint')}`
  confirmOpen.value = true
}

async function runConfirm() {
  const action = pendingAction.value
  pendingAction.value = null
  if (typeof action === 'function') {
    await action()
    return
  }
  await doHardDelete()
}

async function doHardDelete() {
  const item = pendingDelete.value
  pendingDelete.value = null
  if (!item?.id) return
  const { error: err } = await cosmeticsApi.remove(item.id)
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('cosmetics.deleted')
    toast().success(t('cosmetics.deleted'))
    await load()
  }
}

onMounted(load)
</script>

<style scoped>
.preview-wrap {
  aspect-ratio: 1;
  position: relative;
  display: grid;
  place-items: center;
  background-color: #0a1018;
  background-image:
    linear-gradient(45deg, #121a24 25%, transparent 25%),
    linear-gradient(-45deg, #121a24 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #121a24 75%),
    linear-gradient(-45deg, transparent 75%, #121a24 75%);
  background-size: 16px 16px;
  background-position: 0 0, 0 8px, 8px -8px, -8px 0;
  overflow: hidden;
}
.widget-card-media.preview-wrap {
  border-radius: 0;
  border: none;
  aspect-ratio: 1;
}
.widget-card.wide {
  grid-column: span 2;
}
@media (max-width: 700px) {
  .widget-card.wide { grid-column: span 1; }
}
.preview-wrap.wide { aspect-ratio: 16 / 9; }
.avatar-core { width: 40%; height: 40%; border-radius: 50%; z-index: 1; }
.frame-overlay { position: absolute; inset: 2%; width: 96%; height: 96%; object-fit: contain; z-index: 2; }
.svga-frame :deep(canvas),
.svga-frame { width: 100%; height: 100%; }
.room-preview, .toast-preview { width: 100%; height: 100%; object-fit: contain; padding: 6%; }
.toast-preview { position: relative; z-index: 2; }
.entry-effect-layer { position: absolute; inset: 4%; z-index: 1; border-radius: 16px; background: radial-gradient(circle, rgba(168,85,247,.5), transparent 62%); animation: softPulse 1.6s ease-in-out infinite; }
.badge-preview { width: 62%; height: 62%; object-fit: contain; }
.pulse { animation: softPulse 2.4s ease-in-out infinite; }
.phone-card { position: relative; width: 100%; height: 100%; }
.room-card-preview { overflow: hidden; border-radius: 12px; }
.room-card-sample {
  position: absolute;
  inset: 10%;
  border-radius: 10px;
  overflow: hidden;
  background: #1a2433;
}
.room-preview-cover { width: 100%; height: 100%; object-fit: cover; opacity: 0.92; }
.room-frame-overlay {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: fill;
  z-index: 3;
  pointer-events: none;
}
.phone-overlay { position: absolute; inset: auto 10% 12% 10%; color: #fff; z-index: 2; }
.live-pill { background: #e11; border-radius: 999px; padding: 2px 8px; font-size: 10px; font-weight: 700; }
.room-title { display: block; margin-top: 6px; font-weight: 700; }
.anim-chip { position: absolute; top: 8px; inset-inline-end: 8px; background: rgba(250,204,21,.9); color: #111; border-radius: 8px; padding: 2px 6px; font-size: 10px; font-weight: 700; }
@keyframes softPulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.04); }
}
.cosmetic-card:hover { transform: translateY(-3px); transition: transform .2s ease; }
.widget-card.is-selected {
  outline: 2px solid rgba(45, 212, 191, 0.65);
  outline-offset: 2px;
}
</style>
