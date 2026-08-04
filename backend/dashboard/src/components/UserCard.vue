<template>
  <div class="user-card glass widget-card" :class="{ 'is-selected': selected }">
    <BulkCheck :checked="selected" @toggle="$emit('toggle-select', user)" />
    <div class="user-card__row">
      <div class="user-card__avatarWrap neo-avatar">
        <div class="user-card__wear">
          <div class="user-card__avatar-core">
            <img
              v-if="avatarUrl"
              :src="avatarUrl"
              alt=""
              @error="avatarError = true"
            />
            <span v-else>{{ initials }}</span>
          </div>
          <div v-if="frameUrl" class="user-card__frame-layer" aria-hidden="true">
            <SvgaPreview v-if="isSvgaFrame" class="user-card__frame-media" :src="frameUrl" />
            <video
              v-else-if="isVideoFrame"
              class="user-card__frame-media"
              :src="frameUrl"
              muted
              loop
              autoplay
              playsinline
            />
            <img v-else class="user-card__frame-media" :src="frameUrl" alt="" />
          </div>
          <i v-if="!isBanned" class="neo-avatar__dot" aria-hidden="true"></i>
        </div>
      </div>

      <div class="user-card__main">
        <div class="user-card__titleRow">
          <div class="user-card__name" :title="displayName">{{ displayName }}</div>
          <StatusBadge :status="statusLabel" />
          <span v-if="staffRole === 'super'" class="role-pill role-pill--super">
            {{ t('users.roleSuper') }}
          </span>
          <span v-else-if="staffRole === 'manager'" class="role-pill role-pill--manager">
            {{ t('users.roleManager') }}
          </span>
        </div>
        <div class="user-card__sub">
          <span v-if="publicId" class="mono">ID {{ publicId }}</span>
          <span v-if="username">@{{ username }}</span>
          <span v-if="country" class="chip-inline">
            <CountryFlag :country="country" show-label hide-empty />
          </span>
          <span class="chip-inline">{{ genderLabel }}</span>
        </div>
      </div>

      <div class="action-btns user-card__actions">
        <RouterLink class="btn btn-sm btn-ghost" :to="{ name: 'user-detail', params: { id: user.id } }">
          <i class="bi bi-eye"></i>
        </RouterLink>
        <button
          v-if="isBanned"
          class="btn btn-sm btn-outline-success"
          type="button"
          @click="$emit('unban', user)"
        >
          {{ t('users.unban') }}
        </button>
        <button
          v-else
          class="btn btn-sm btn-outline-danger"
          type="button"
          @click="$emit('ban', user)"
        >
          {{ t('users.ban') }}
        </button>
        <button
          class="btn btn-sm btn-danger"
          type="button"
          :title="t('users.deleteUser')"
          @click="$emit('delete', user)"
        >
          <i class="bi bi-trash"></i>
        </button>
      </div>
    </div>

    <!-- Stats row (level / vip / charm / wealth / popularity) -->
    <div class="user-card__stats">
      <div class="ustat">
        <div class="ustat__val">{{ user.level ?? 0 }}</div>
        <div class="ustat__lab">Lv</div>
      </div>
      <div class="ustat">
        <div class="ustat__val">{{ user.vipLevel ?? 0 }}</div>
        <div class="ustat__lab">VIP</div>
      </div>
      <div class="ustat">
        <div class="ustat__val">{{ formatNumber(charmScore) }}</div>
        <div class="ustat__lab"><i class="bi bi-heart-fill"></i></div>
      </div>
      <div class="ustat">
        <div class="ustat__val">{{ formatNumber(wealthScore) }}</div>
        <div class="ustat__lab"><i class="bi bi-trophy-fill"></i></div>
      </div>
      <div class="ustat">
        <div class="ustat__val">{{ formatNumber(popularityScore) }}</div>
        <div class="ustat__lab"><i class="bi bi-star-fill"></i></div>
      </div>
    </div>

    <!-- Wallets -->
    <div class="user-card__wallets">
      <div class="neo-balance neo-balance--gem">
        <span class="neo-balance__icon"><i class="bi bi-gem"></i></span>
        <span class="neo-balance__val">{{ formatNumber(diamonds) }}</span>
      </div>
      <div class="neo-balance neo-balance--coin">
        <span class="neo-balance__icon"><i class="bi bi-coin"></i></span>
        <span class="neo-balance__val">{{ formatNumber(coins) }}</span>
      </div>
    </div>

    <!-- Contact + meta single line grid -->
    <div class="user-card__meta">
      <div class="meta-item" :title="user.email || ''">
        <i class="bi bi-envelope"></i>
        <span>{{ user.email || '—' }}</span>
      </div>
      <div class="meta-item">
        <i class="bi bi-telephone"></i>
        <span>{{ user.phone || '—' }}</span>
      </div>
      <div class="meta-item" v-if="user.city">
        <i class="bi bi-geo-alt"></i>
        <span>{{ user.city }}</span>
      </div>
      <div class="meta-item">
        <i class="bi bi-calendar3"></i>
        <span>{{ formatDate(joinedAt) }}</span>
      </div>
      <div class="meta-item" v-if="user.lastOnlineAt">
        <i class="bi bi-circle-fill online-dot"></i>
        <span>{{ formatDate(user.lastOnlineAt) }}</span>
      </div>
      <div class="meta-item" v-if="user.genderVerified">
        <i class="bi bi-patch-check-fill text-success"></i>
        <span>موثّقة</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import StatusBadge from '@/components/StatusBadge.vue'
import CountryFlag from '@/components/CountryFlag.vue'
import BulkCheck from '@/components/BulkCheck.vue'
import SvgaPreview from '@/components/SvgaPreview.vue'
import { formatDate, formatNumber } from '@/composables/useUtils'
import { resolveAsset } from '@/utils/assets'

const props = defineProps({
  user: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})
const { t, locale } = useI18n()

defineEmits(['ban', 'unban', 'delete', 'toggle-select'])

const avatarError = ref(false)

const displayName = computed(() => props.user.name || props.user.displayName || props.user.username || '—')
const username = computed(() => props.user.username || '')
const publicId = computed(() => {
  const p = props.user.publicId
  if (!p) return ''
  // hide internal UUIDs
  if (/^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(String(p))) return ''
  return p
})
const country = computed(() => props.user.country || props.user.countryCode || '')
const joinedAt = computed(() => props.user.createdAt || props.user.joinedAt)
const coins = computed(() => props.user.coins ?? props.user.balance ?? 0)
const diamonds = computed(() => props.user.diamonds ?? 0)
const charmScore = computed(() => props.user.charmScore ?? 0)
const wealthScore = computed(() => props.user.wealthScore ?? 0)
const popularityScore = computed(() => props.user.popularityScore ?? 0)

const staffRole = computed(() => {
  const raw = String(props.user.staffRole || '').toLowerCase()
  if (raw === 'super' || raw === 'super_admin' || props.user.isSuperAdmin) return 'super'
  if (raw === 'manager' || props.user.isManager) return 'manager'
  if (props.user.isAdmin) return 'super'
  return 'none'
})

const isBanned = computed(() => props.user.isBanned || props.user.status === 'banned')
const statusLabel = computed(() => props.user.status || (isBanned.value ? 'banned' : 'active'))

const genderLabel = computed(() => {
  const g = String(props.user.gender || '').toLowerCase()
  if (locale.value === 'ar') {
    if (g === 'male') return 'ذكر'
    if (g === 'female') return 'أنثى'
    if (g === 'other') return 'آخر'
    return '—'
  }
  if (g === 'male') return 'Male'
  if (g === 'female') return 'Female'
  if (g === 'other') return 'Other'
  return '—'
})

function absUrl(url) {
  if (!url) return null
  if (String(url).startsWith('http')) return url
  return resolveAsset(url) || `https://api.adnova.bbs.tr${String(url).startsWith('/') ? '' : '/'}${url}`
}

const avatarUrl = computed(() => {
  if (avatarError.value) return null
  const url = props.user.avatarUrl || props.user.avatar
  return absUrl(url)
})

const frameUrl = computed(() => {
  const raw =
    props.user.frameAnimUrl
    || props.user.frameUrl
    || props.user.vipBadgeUrl
    || props.user.hostBadgeUrl
    || null
  return absUrl(raw)
})

const isSvgaFrame = computed(() => /\.svga(\?|$)/i.test(String(frameUrl.value || '')))
const isVideoFrame = computed(() =>
  /\.(mp4|webm|mov)(\?|$)/i.test(String(frameUrl.value || '')),
)

const initials = computed(() => {
  const name = displayName.value || ''
  return name ? name.charAt(0).toUpperCase() : 'U'
})
</script>

<style scoped>
.user-card {
  position: relative;
  border-radius: 22px;
  padding: 14px 16px 12px;
}
.user-card.is-selected {
  outline: 2px solid rgba(167, 139, 250, 0.75);
  outline-offset: 1px;
  box-shadow: 0 0 28px rgba(139, 92, 246, 0.3);
}

.user-card__row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.user-card__avatarWrap { flex: 0 0 auto; }
.user-card__main { flex: 1 1 auto; min-width: 0; }

.user-card__wear {
  position: relative;
  width: 56px;
  height: 56px;
}
.user-card__avatar-core {
  position: absolute;
  inset: 12%;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #2a1458, #0e0b1f);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 800;
  font-size: 1.1rem;
  z-index: 1;
}
.user-card__avatar-core img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.user-card__frame-layer {
  position: absolute;
  inset: -8%;
  z-index: 2;
  pointer-events: none;
}
.user-card__frame-media {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
.user-card__wear :deep(.neo-avatar__dot),
.user-card__wear .neo-avatar__dot {
  position: absolute;
  right: 2px;
  bottom: 2px;
  z-index: 3;
}

.user-card__titleRow {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.user-card__name {
  font-weight: 800;
  font-size: 1rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  color: #fff;
  letter-spacing: -0.02em;
}

.role-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.65rem;
  font-weight: 800;
  letter-spacing: 0.02em;
}
.role-pill--super {
  color: #fde68a;
  background: rgba(245, 158, 11, 0.18);
  border: 1px solid rgba(251, 191, 36, 0.45);
}
.role-pill--manager {
  color: #a5f3fc;
  background: rgba(6, 182, 212, 0.16);
  border: 1px solid rgba(34, 211, 238, 0.4);
}

.user-card__sub {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 0.75rem;
  color: #9ca3c7;
}

.chip-inline {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(139, 92, 246, 0.12);
  border: 1px solid rgba(167, 139, 250, 0.2);
  color: #ddd6fe;
  font-size: 0.7rem;
  font-weight: 600;
}

.mono {
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
  color: #c4b5fd;
  font-weight: 700;
}

.user-card__actions { flex: 0 0 auto; }

.user-card__stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
  margin-top: 12px;
}

.ustat {
  text-align: center;
  padding: 0.55rem 0.25rem;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.28);
  border: 1px solid rgba(255, 255, 255, 0.05);
  min-width: 0;
}
.ustat__val {
  font-size: 0.92rem;
  font-weight: 800;
  color: #fff;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ustat__lab {
  margin-top: 0.15rem;
  font-size: 0.65rem;
  color: #9ca3c7;
  font-weight: 600;
}
.ustat__lab i { color: #a78bfa; font-size: 0.7rem; }

.user-card__wallets {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 10px;
}

.user-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  color: #9ca3c7;
  min-width: 0;
}
.meta-item i { flex: 0 0 auto; color: #a78bfa; opacity: 0.9; }
.meta-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.online-dot { font-size: 0.45rem; color: #34d399 !important; }

@media (max-width: 575.98px) {
  .user-card__row { flex-wrap: wrap; }
  .user-card__actions { width: 100%; justify-content: flex-end; }
  .user-card__stats { grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 4px; }
  .ustat { padding: 0.45rem 0.1rem; }
  .ustat__val { font-size: 0.8rem; }
}
</style>
