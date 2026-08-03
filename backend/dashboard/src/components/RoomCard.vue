<template>
  <div class="room-card glass widget-card overflow-hidden" :class="{ 'is-selected': selected }">
    <BulkCheck :checked="selected" @toggle="$emit('toggle-select', room)" />
    <div class="room-card__hero" :style="heroStyle">
      <div class="room-card__scrim" />
      <div class="room-card__halo"></div>
      <div class="room-card__wings" aria-hidden="true"></div>
      <div class="room-card__top">
        <StatusBadge :status="room.status || 'active'" />
        <span class="room-card__viewers">
          <i class="bi bi-people-fill me-1"></i>
          {{ formatNumber(room.membersCount ?? room.memberCount ?? room.onlineCount ?? room.viewerCount ?? 0) }}
        </span>
      </div>
      <div class="room-card__bottom">
        <div class="room-card__title">{{ room.name || room.title || t('common.untitled') }}</div>
        <div class="room-card__access small mt-1">
          <span class="badge bg-dark border border-secondary">
            {{ accessLabel }}
          </span>
          <span v-if="room.isLive" class="badge bg-danger ms-1">مباشر</span>
          <span v-if="room.roomKind === 'support' || room.isSupport" class="badge bg-success ms-1">
            خدمة عملاء
          </span>
          <span v-else-if="room.isPersistent || room.roomKind === 'agency'" class="badge bg-info ms-1">
            {{ t('rooms.agency') }}
          </span>
          <span v-if="entryFee > 0" class="text-warning ms-1">{{ formatNumber(entryFee) }} {{ t('common.coins') }}</span>
        </div>
        <div class="room-card__host">
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
          <span>{{ hostName }}</span>
        </div>
        <div class="small text-warning mt-1">ID: {{ roomPublicId }}</div>
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
import StatusBadge from '@/components/StatusBadge.vue'
import BulkCheck from '@/components/BulkCheck.vue'
import { formatNumber, formatDate } from '@/composables/useUtils'

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

const hostAvatar = computed(() => {
  if (avatarError.value) return null
  const url = props.room.hostAvatarUrl
    || props.room.host?.avatarUrl
    || props.room.coverUrl
  if (!url) return null
  if (url.startsWith('http')) return url
  return `https://api.adnova.bbs.tr${url.startsWith('/') ? '' : '/'}${url}`
})

const roomPublicId = computed(() => props.room.publicId || props.room.id || '—')

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
    backgroundImage: 'linear-gradient(135deg, #0B1220 0%, #12352F 45%, #00C2A8 100%)',
  }
})
</script>

<style scoped>
.room-card {
  position: relative;
  border-radius: var(--al-radius);
  overflow: hidden;
}
.room-card.is-selected {
  outline: 2px solid rgba(45, 212, 191, 0.75);
  outline-offset: 1px;
}

.room-card__hero {
  position: relative;
  min-height: 200px;
  background-size: cover;
  background-position: center top;
  isolation: isolate;
}

.room-card__scrim {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(7, 17, 28, 0.15) 0%, rgba(7, 17, 28, 0.92) 100%);
}

.room-card__halo {
  position: absolute;
  inset: 18px;
  border-radius: 32px;
  border: 1px solid rgba(255, 215, 130, 0.45);
  box-shadow:
    0 0 0 2px rgba(255, 255, 255, 0.06),
    0 0 30px rgba(255, 191, 0, 0.18);
  z-index: 0;
}

.room-card__wings {
  position: absolute;
  inset-inline: 22px;
  top: 18px;
  bottom: 18px;
  border-radius: 28px;
  background:
    radial-gradient(circle at left center, rgba(255, 218, 128, 0.28), transparent 28%),
    radial-gradient(circle at right center, rgba(255, 218, 128, 0.28), transparent 28%);
  opacity: 0.95;
  z-index: 0;
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

.room-card__host-frame {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.6), transparent 34%),
    linear-gradient(135deg, rgba(255, 215, 130, 0.95), rgba(255, 143, 85, 0.92));
  box-shadow: 0 8px 22px rgba(255, 166, 0, 0.2);
}

.room-card__host-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(255, 255, 255, 0.75);
}

.room-card__host-avatarFallback {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 800;
  background: linear-gradient(135deg, rgba(13, 148, 136, 0.95), rgba(6, 182, 212, 0.95));
}

.room-card__actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-top: 1px solid var(--border-color);
}
</style>
