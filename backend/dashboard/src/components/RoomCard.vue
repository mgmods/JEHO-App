<template>
  <article class="rc glass widget-card" :class="{ 'is-selected': selected, 'rc--busy': isBusy, 'rc--live': room.isLive }">
    <BulkCheck :checked="selected" @toggle="$emit('toggle-select', room)" />

    <div class="rc__main">
      <div class="rc__avatar-wrap" aria-hidden="true">
        <img
          v-if="hostAvatar"
          :src="hostAvatar"
          class="rc__avatar"
          alt=""
          @error="avatarError = true"
        />
        <div v-else class="rc__avatar rc__avatar--fallback">{{ hostInitial }}</div>
        <span v-if="hostFrameUrl" class="rc__frame">
          <SvgaPreview v-if="isSvgaFrame" class="rc__frame-media" :src="hostFrameUrl" />
          <video
            v-else-if="isVideoFrame"
            class="rc__frame-media"
            :src="hostFrameUrl"
            muted
            loop
            autoplay
            playsinline
          />
          <img v-else class="rc__frame-media" :src="hostFrameUrl" alt="" />
        </span>
        <span v-if="isBusy" class="rc__pulse" />
      </div>

      <div class="rc__body">
        <div class="rc__row-top">
          <span class="rc__status" :class="room.isLive ? 'rc__status--live' : 'rc__status--idle'">
            <i class="bi" :class="room.isLive ? 'bi-broadcast' : 'bi-mic-fill'"></i>
            {{ room.isLive ? 'LIVE' : (room.status || 'active') }}
          </span>
          <span class="rc__occupancy" :class="{ 'rc__occupancy--on': isBusy }">
            <span
              class="rc-wave"
              :class="{ 'rc-wave--on': isBusy }"
              aria-hidden="true"
            >
              <i /><i /><i /><i /><i />
            </span>
            <span class="rc__count">{{ formatNumber(peopleCount) }}</span>
          </span>
        </div>

        <h3 class="rc__title">{{ room.name || room.title || t('common.untitled') }}</h3>
        <p class="rc__host">{{ hostName }}</p>

        <div class="rc__meta">
          <span class="rc__chip">{{ accessLabel }}</span>
          <span v-if="isSupport" class="rc__chip rc__chip--ok">خدمة عملاء</span>
          <span v-else-if="isAgency" class="rc__chip rc__chip--info">{{ t('rooms.agency') }}</span>
          <span v-else class="rc__chip rc__chip--muted">شخصي</span>
          <span v-if="entryFee > 0" class="rc__chip rc__chip--coin">
            {{ formatNumber(entryFee) }} {{ t('common.coins') }}
          </span>
          <span v-if="roomPublicId" class="rc__chip rc__chip--id">ID {{ roomPublicId }}</span>
        </div>
      </div>
    </div>

    <div class="rc__actions">
      <span class="rc__date">{{ formatDate(room.createdAt) }}</span>
      <div class="rc__btns">
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
        <button class="btn btn-sm btn-outline-info" type="button" @click="$emit('edit', room)">
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
        <button class="btn btn-sm btn-outline-danger" type="button" @click="$emit('delete', room)">
          {{ t('app.delete') }}
        </button>
      </div>
    </div>
  </article>
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
const isAgency = computed(() => {
  if (isSupport.value) return false
  const kind = String(props.room.roomKind || '').toLowerCase()
  if (kind === 'agency') return true
  if (kind === 'standard' || kind === 'support' || kind === 'personal') return false
  return !!(props.room.agencyId)
})

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
const hostInitial = computed(() => (hostName.value || 'R').charAt(0).toUpperCase())

const peopleCount = computed(() =>
  Number(
    props.room.membersCount
    ?? props.room.memberCount
    ?? props.room.onlineCount
    ?? props.room.viewerCount
    ?? 0,
  ) || 0,
)

/** Animate waves when room has people or is live. */
const isBusy = computed(() => peopleCount.value > 0 || !!props.room.isLive)
</script>

<style scoped>
.rc {
  position: relative;
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(18, 20, 28, 0.72);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.rc.is-selected {
  outline: 2px solid rgba(45, 212, 191, 0.7);
  outline-offset: 1px;
}
.rc--busy {
  border-color: rgba(45, 212, 191, 0.28);
}
.rc--live {
  border-color: rgba(248, 113, 113, 0.35);
}

.rc__main {
  display: flex;
  gap: 14px;
  padding: 16px 16px 12px;
  align-items: flex-start;
}

.rc__avatar-wrap {
  position: relative;
  width: 64px;
  height: 64px;
  flex-shrink: 0;
}
.rc__avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(255, 255, 255, 0.12);
  display: block;
}
.rc__avatar--fallback {
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 1.25rem;
  color: #fff;
  background: linear-gradient(145deg, #334155, #1e293b);
}
.rc__frame {
  position: absolute;
  inset: -10px;
  pointer-events: none;
  display: grid;
  place-items: center;
}
.rc__frame-media {
  width: 84px !important;
  height: 84px !important;
  object-fit: contain;
  background: transparent !important;
}
.rc__pulse {
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 2px solid rgba(45, 212, 191, 0.55);
  animation: rc-pulse 1.6s ease-out infinite;
  pointer-events: none;
}
.rc--live .rc__pulse {
  border-color: rgba(248, 113, 113, 0.65);
}

.rc__body {
  flex: 1;
  min-width: 0;
}
.rc__row-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.rc__status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.15);
  color: #cbd5e1;
}
.rc__status--live {
  background: rgba(239, 68, 68, 0.18);
  color: #fca5a5;
}
.rc__occupancy {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: #94a3b8;
  font-size: 0.8rem;
  font-weight: 700;
}
.rc__occupancy--on {
  color: #5eead4;
  border-color: rgba(45, 212, 191, 0.35);
  background: rgba(13, 148, 136, 0.15);
}

.rc-wave {
  display: inline-flex;
  align-items: flex-end;
  justify-content: center;
  gap: 2px;
  height: 14px;
  min-width: 16px;
  opacity: 0.4;
}
.rc-wave i {
  display: block;
  width: 2.5px;
  height: 4px;
  border-radius: 2px;
  background: currentColor;
  transform-origin: bottom center;
}
.rc-wave--on {
  opacity: 1;
}
.rc-wave--on i {
  animation: rc-bar 0.85s ease-in-out infinite;
}
.rc-wave--on i:nth-child(1) { animation-delay: 0s; height: 5px; }
.rc-wave--on i:nth-child(2) { animation-delay: 0.12s; height: 10px; }
.rc-wave--on i:nth-child(3) { animation-delay: 0.24s; height: 14px; }
.rc-wave--on i:nth-child(4) { animation-delay: 0.36s; height: 9px; }
.rc-wave--on i:nth-child(5) { animation-delay: 0.48s; height: 6px; }

.rc__title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 800;
  color: #f8fafc;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.rc__host {
  margin: 2px 0 0;
  font-size: 0.82rem;
  color: #94a3b8;
  font-weight: 600;
}
.rc__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
.rc__chip {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: #e2e8f0;
}
.rc__chip--ok { background: rgba(34, 197, 94, 0.18); color: #86efac; }
.rc__chip--info { background: rgba(56, 189, 248, 0.16); color: #7dd3fc; }
.rc__chip--muted { background: rgba(100, 116, 139, 0.25); color: #cbd5e1; }
.rc__chip--coin { background: rgba(234, 179, 8, 0.16); color: #fde68a; }
.rc__chip--id { font-family: ui-monospace, monospace; color: #fcd34d; }

.rc__actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 14px 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(0, 0, 0, 0.2);
}
.rc__date {
  font-size: 0.72rem;
  color: #64748b;
  white-space: nowrap;
}
.rc__btns {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  justify-content: flex-end;
}

@keyframes rc-bar {
  0%, 100% { transform: scaleY(0.35); opacity: 0.65; }
  50% { transform: scaleY(1); opacity: 1; }
}
@keyframes rc-pulse {
  0% { transform: scale(1); opacity: 0.85; }
  100% { transform: scale(1.18); opacity: 0; }
}
</style>
