<template>
  <div class="room-card glass widget-card overflow-hidden" :class="{ 'is-selected': selected }">
    <BulkCheck :checked="selected" @toggle="$emit('toggle-select', stream)" />
    <div class="room-card__hero" :style="heroStyle">
      <div class="room-card__scrim" />

      <div class="room-card__top">
        <StatusBadge :status="statusLabel" />

        <span class="room-card__viewers">
          <i class="bi bi-people-fill me-1"></i>
          {{ formatNumber(viewers) }}
        </span>
      </div>

      <div class="room-card__bottom">
        <div class="room-card__title">{{ title }}</div>

        <div class="room-card__host">
          <img
            v-if="hostAvatar"
            :src="hostAvatar"
            class="room-card__host-avatar"
            alt=""
            @error="avatarError = true"
          />
          <span>{{ hostName }}</span>
        </div>

        <div class="small text-muted mt-1">#{{ shortId }}</div>
      </div>
    </div>

    <div class="room-card__actions">
      <span class="small text-muted">{{ formatDate(startedAt) }}</span>
      <div class="action-btns">
        <button
          class="btn btn-sm btn-outline-danger"
          type="button"
          :disabled="isEnded"
          @click="$emit('forceEnd', stream)"
        >
          {{ t('streams.end') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import StatusBadge from '@/components/StatusBadge.vue'
import BulkCheck from '@/components/BulkCheck.vue'
import { formatDate, formatNumber } from '@/composables/useUtils'

const props = defineProps({
  stream: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})
const { t } = useI18n()
defineEmits(['forceEnd', 'toggle-select'])

const avatarError = ref(false)

const title = computed(() => props.stream.title || props.stream.roomName || t('common.untitled'))

const shortId = computed(() => {
  const id = props.stream.id || ''
  return id.length > 8 ? id.slice(0, 8) + '…' : id
})

const viewers = computed(() => props.stream.viewers ?? props.stream.viewerCount ?? props.stream.onlineCount ?? 0)

const gifts = computed(() => props.stream.giftsValue ?? props.stream.giftCoins ?? 0)

const startedAt = computed(() => props.stream.startedAt || props.stream.createdAt || props.stream.startTime)

const statusLabel = computed(() => props.stream.status || (props.stream.isLive ? 'live' : 'ended'))

const isEnded = computed(() => String(statusLabel.value).toLowerCase() === 'ended')

const hostName = computed(
  () =>
    props.stream.hostName ||
    props.stream.host?.displayName ||
    props.stream.host?.username ||
    props.stream.user?.name ||
    props.stream.streamerName ||
    '—',
)

const hostAvatar = computed(() => {
  if (avatarError.value) return null
  const url = props.stream.hostAvatarUrl || props.stream.host?.avatarUrl
  if (!url) return null
  if (url.startsWith('http')) return url
  return `https://api.adnova.bbs.tr${url.startsWith('/') ? '' : '/'}${url}`
})

const heroStyle = computed(() => {
  if (hostAvatar.value) return { backgroundImage: `url(${hostAvatar.value})` }
  return {
    backgroundImage: 'linear-gradient(135deg, #0B1220 0%, #12352F 45%, rgba(0,194,168,0.95) 100%)',
  }
})
</script>

<style scoped>
.room-card {
  border-radius: var(--al-radius);
  overflow: hidden;
}

.room-card__hero {
  position: relative;
  min-height: 190px;
  background-size: cover;
  background-position: center top;
}

.room-card__scrim {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(7, 17, 28, 0.15) 0%, rgba(7, 17, 28, 0.92) 100%);
}

.room-card__top,
.room-card__bottom {
  position: relative;
  z-index: 1;
}

.room-card__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px 0;
}

.room-card__viewers {
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(0, 194, 168, 0.35);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 0.78rem;
  color: #fff;
}

.room-card__bottom {
  padding: 48px 14px 14px;
}

.room-card__title {
  font-weight: 700;
  font-size: 1.1rem;
  color: #fff;
  line-height: 1.2;
}

.room-card__host {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: var(--al-teal-bright);
  font-size: 0.9rem;
}

.room-card__host-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(0, 194, 168, 0.6);
}

.room-card__actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-top: 1px solid var(--border-color);
}
.room-card.is-selected {
  outline: 2px solid rgba(45, 212, 191, 0.65);
  outline-offset: 2px;
}
</style>

