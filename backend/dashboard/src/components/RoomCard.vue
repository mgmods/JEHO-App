<template>
  <div class="room-card glass widget-card overflow-hidden" :class="{ 'is-selected': selected }">
    <BulkCheck :checked="selected" @toggle="$emit('toggle-select', room)" />
    <div class="room-card__hero" :style="heroStyle">
      <div class="room-card__scrim" />
      <div class="room-card__halo"></div>
      <div class="room-card__wings" aria-hidden="true"></div>
      <div class="room-card__top">
        <span class="neo-pill" :class="room.isLive ? 'neo-pill--live' : 'neo-pill--talk'">
          <i class="bi" :class="room.isLive ? 'bi-broadcast' : 'bi-mic-fill'"></i>
          {{ room.isLive ? 'LIVE' : (room.status || 'active') }}
        </span>
        <span class="room-card__viewers">
          <i class="bi bi-people-fill me-1"></i>
          {{ formatNumber(room.membersCount ?? room.memberCount ?? room.onlineCount ?? room.viewerCount ?? 0) }}
        </span>
      </div>
      <div class="room-card__center">
        <div class="room-card__host-wear">
          <div class="room-card__host-frame">
            <img
              v-if="hostAvatar"
              :src="hostAvatar"
              class="room-card__host-avatar"
              alt=""
              @error="avatarError = true"
            />
            <div v-else class="room-card__host-avatarFallback">{{ hostInitial }}</div>
          </div>
          <!-- Equipped head frame / VIP badge (static or SVGA) -->
          <div v-if="hostFrameUrl" class="room-card__frame-layer" aria-hidden="true">
            <SvgaPreview v-if="isSvgaFrame" class="room-card__frame-media" :src="hostFrameUrl" />
            <video
              v-else-if="isVideoFrame"
              class="room-card__frame-media"
              :src="hostFrameUrl"
              muted
              loop
              autoplay
              playsinline
            />
            <img v-else class="room-card__frame-media" :src="hostFrameUrl" alt="" />
          </div>
        </div>
      </div>
      <div class="room-card__bottom">
        <div class="room-card__title">{{ room.name || room.title || t('common.untitled') }}</div>
        <div class="room-card__host-name">{{ hostName }}</div>
        <div class="room-card__access small mt-1">
          <span class="neo-pill neo-pill--heat">
            <i class="bi bi-fire"></i>
            {{ accessLabel }}
          </span>
          <span v-if="room.roomKind === 'support' || room.isSupport" class="badge bg-success ms-1">
            خدمة عملاء
          </span>
          <span v-else-if="room.isPersistent || room.roomKind === 'agency'" class="badge bg-info ms-1">
            {{ t('rooms.agency') }}
          </span>
          <span v-if="entryFee > 0" class="text-warning ms-1">{{ formatNumber(entryFee) }} {{ t('common.coins') }}</span>
        </div>
        <div v-if="roomPublicId" class="small text-warning mt-1">ID: {{ roomPublicId }}</div>
      </div>
    </div>
    <div class="room-card__actions">
      <span class="small text-muted">{{ formatDate(room.createdAt) }}</span>
      <div class="action-btns">
        <button
          v-if="!isAgency"
          class="btn btn-sm"
          :class="isSupport ? 'btn-outline-secondary' : 'btn-outline-success'"
          type="button"
          @click="$emit('toggleSupport', room)"
        >
          {{ isSupport ? 'إلغاء خدمة عملاء' : 'ترقية لخدمة عملاء' }}
        </button>
        <button
          v-if="room.isLive"
          class="btn btn-sm btn-outline-danger"
          type="button"
          @click="$emit('forceEnd', room)"
        >
          إنهاء المباشر
        </button>
        <button
          class="btn btn-sm btn-outline-info"
          type="button"
          @click="$emit('edit', room)"
        >
          {{ t('rooms.access') }}
        </button>
        <button
          class="btn btn-sm btn-outline-warning"
          type="button"
          :disabled="room.status === 'closed'"
          @click="$emit('close', room)"
        >
          {{ t('app.close') }}
        </button>
        <button
          class="btn btn-sm btn-outline-danger"
          type="button"
          @click="$emit('delete', room)"
        >
          {{ t('app.delete') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BulkCheck from '@/components/BulkCheck.vue'
import SvgaPreview from '@/components/SvgaPreview.vue'
import { formatNumber, formatDate } from '@/composables/useUtils'
import { resolveAsset } from '@/utils/assets'

const props = defineProps({
  room: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})
const { t } = useI18n()

defineEmits(['close', 'delete', 'edit', 'forceEnd', 'toggleSupport', 'toggle-select'])

const avatarError = ref(false)
const isSupport = computed(
  () => props.room.roomKind === 'support' || props.room.isSupport === true,
)
const isAgency = computed(
  () =>
    !isSupport.value &&
    (props.room.isPersistent || props.room.roomKind === 'agency' || !!props.room.agencyId),
)

const hostName = computed(() =>
  props.room.ownerName
  || props.room.hostName
  || props.room.host?.displayName
  || props.room.host?.username
  || '—',
)

function absUrl(url) {
  if (!url) return null
  if (String(url).startsWith('http')) return url
  return resolveAsset(url) || `https://api.adnova.bbs.tr${String(url).startsWith('/') ? '' : '/'}${url}`
}

const hostAvatar = computed(() => {
  if (avatarError.value) return null
  const url = props.room.hostAvatarUrl
    || props.room.host?.avatarUrl
    || props.room.coverUrl
  return absUrl(url)
})

const hostFrameUrl = computed(() => {
  const raw =
    props.room.hostFrameAnimUrl
    || props.room.hostFrameUrl
    || props.room.host?.vipBadgeUrl
    || props.room.host?.hostBadgeUrl
    || null
  return absUrl(raw)
})

const isSvgaFrame = computed(() => /\.svga(\?|$)/i.test(String(hostFrameUrl.value || '')))
const isVideoFrame = computed(() =>
  /\.(mp4|webm|mov)(\?|$)/i.test(String(hostFrameUrl.value || '')),
)

const roomPublicId = computed(() => {
  const candidates = [
    props.room.publicId,
    props.room.roomCode,
    props.room.displayId,
    props.room.shortId,
  ]
  for (const c of candidates) {
    if (c == null || c === '') continue
    const s = String(c)
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(s)) continue
    if (s.length > 16) continue
    return s
  }
  return ''
})

const accessLabel = computed(() => {
  const mode = (props.room.accessMode || 'free').toLowerCase()
  if (mode === 'paid') return t('rooms.paid')
  if (mode === 'permanent') return t('rooms.permanent')
  return t('rooms.free')
})

const entryFee = computed(() => Number(props.room.entryFeeCoins || 0))

const hostInitial = computed(() => {
  const name = hostName.value || 'R'
  return name.charAt(0).toUpperCase()
})

const heroStyle = computed(() => {
  if (hostAvatar.value) {
    return { backgroundImage: `url(${hostAvatar.value})` }
  }
  return {
    backgroundImage: 'linear-gradient(145deg, #1a0b3a 0%, #0e0b1f 45%, #2a1458 100%)',
  }
})
</script>

<style scoped>
.room-card {
  position: relative;
  border-radius: 22px;
  overflow: hidden;
}
.room-card.is-selected {
  outline: 2px solid rgba(167, 139, 250, 0.8);
  outline-offset: 1px;
  box-shadow: 0 0 32px rgba(139, 92, 246, 0.35);
}

.room-card__hero {
  position: relative;
  min-height: 240px;
  background-size: cover;
  background-position: center top;
  isolation: isolate;
}

.room-card__scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(14, 11, 31, 0.2) 0%, rgba(14, 11, 31, 0.55) 40%, rgba(14, 11, 31, 0.96) 100%),
    radial-gradient(circle at 50% 40%, rgba(139, 92, 246, 0.25), transparent 55%);
}

.room-card__halo {
  position: absolute;
  inset: 16px;
  border-radius: 28px;
  border: 1px solid rgba(167, 139, 250, 0.35);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.04), 0 0 36px rgba(139, 92, 246, 0.2);
  z-index: 0;
  pointer-events: none;
}

.room-card__wings {
  display: none;
}

.room-card__top,
.room-card__center,
.room-card__bottom {
  position: relative;
  z-index: 1;
}

.room-card__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 14px 0;
}

.room-card__viewers {
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(167, 139, 250, 0.35);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 0.78rem;
  color: #fff;
  backdrop-filter: blur(8px);
}

.room-card__center {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

.room-card__host-wear {
  position: relative;
  width: 110px;
  height: 110px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.room-card__host-frame {
  width: 78px;
  height: 78px;
  border-radius: 50%;
  padding: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #a78bfa, #22d3ee, #f472b6);
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.18), 0 0 28px rgba(139, 92, 246, 0.45);
  position: relative;
  z-index: 1;
}

.room-card__host-avatar,
.room-card__host-avatarFallback {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #0e0b1f;
}

.room-card__host-avatarFallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 800;
  font-size: 1.4rem;
  background: linear-gradient(135deg, #8b5cf6, #6366f1);
}

.room-card__frame-layer {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.room-card__frame-media {
  width: 110px !important;
  height: 110px !important;
  object-fit: contain;
  background: transparent !important;
}

.room-card__bottom {
  padding: 14px 16px 16px;
  text-align: center;
}

.room-card__title {
  font-weight: 800;
  font-size: 1.12rem;
  color: #fff;
  line-height: 1.25;
  letter-spacing: -0.02em;
}

.room-card__host-name {
  margin-top: 0.25rem;
  font-size: 0.85rem;
  color: #c4b5fd;
  font-weight: 600;
}

.room-card__access {
  margin-top: 0.55rem !important;
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  justify-content: center;
  align-items: center;
}

.room-card__actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-top: 1px solid rgba(139, 92, 246, 0.15);
  background: rgba(14, 11, 31, 0.65);
}

.action-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  justify-content: flex-end;
}
</style>
