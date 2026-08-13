<template>
  <article class="uc glass" :class="{ 'is-selected': selected, 'uc--banned': isBanned }">
    <BulkCheck :checked="selected" @toggle="$emit('toggle-select', user)" />

    <div class="uc__top">
      <div class="uc__avatar-wrap" aria-hidden="true">
        <img
          v-if="avatarUrl"
          :src="avatarUrl"
          class="uc__avatar"
          alt=""
          @error="avatarError = true"
        />
        <div v-else class="uc__avatar uc__avatar--fallback">{{ initials }}</div>
        <span v-if="frameUrl" class="uc__frame">
          <SvgaPreview v-if="isSvgaFrame" class="uc__frame-media" :src="frameUrl" />
          <video
            v-else-if="isVideoFrame"
            class="uc__frame-media"
            :src="frameUrl"
            muted
            loop
            autoplay
            playsinline
          />
          <img v-else class="uc__frame-media" :src="frameUrl" alt="" />
        </span>
      </div>

      <div class="uc__identity">
        <div class="uc__name-row">
          <h3 class="uc__name" :title="displayName">{{ displayName }}</h3>
          <StatusBadge :status="statusLabel" />
        </div>
        <div class="uc__sub">
          <span v-if="publicId" class="uc__id">ID {{ publicId }}</span>
          <span v-if="username">@{{ username }}</span>
          <span v-if="country" class="uc__chip">
            <CountryFlag :country="country" show-label hide-empty />
          </span>
          <span class="uc__chip">{{ genderLabel }}</span>
          <span v-if="staffRole === 'super'" class="uc__chip uc__chip--gold">{{ t('users.roleSuper') }}</span>
          <span v-else-if="staffRole === 'manager'" class="uc__chip uc__chip--cyan">{{ t('users.roleManager') }}</span>
          <span v-if="user.genderVerified" class="uc__chip uc__chip--ok">موثّقة</span>
        </div>
      </div>
    </div>

    <div class="uc__stats">
      <div class="uc-stat"><span class="uc-stat__v">{{ user.level ?? 0 }}</span><span class="uc-stat__l">Lv</span></div>
      <div class="uc-stat"><span class="uc-stat__v">{{ user.vipLevel ?? 0 }}</span><span class="uc-stat__l">VIP</span></div>
      <div class="uc-stat"><span class="uc-stat__v">{{ formatNumber(charmScore) }}</span><span class="uc-stat__l">سحر</span></div>
      <div class="uc-stat"><span class="uc-stat__v">{{ formatNumber(wealthScore) }}</span><span class="uc-stat__l">ثروة</span></div>
      <div class="uc-stat"><span class="uc-stat__v">{{ formatNumber(popularityScore) }}</span><span class="uc-stat__l">شهرة</span></div>
    </div>

    <div class="uc__wallets">
      <div class="uc-bal uc-bal--gem">
        <i class="bi bi-gem"></i>
        <span>{{ formatNumber(diamonds) }}</span>
        <small>ماس</small>
      </div>
      <div class="uc-bal uc-bal--coin">
        <i class="bi bi-coin"></i>
        <span>{{ formatNumber(coins) }}</span>
        <small>كوينز</small>
      </div>
    </div>

    <div class="uc__meta">
      <span v-if="user.email" :title="user.email"><i class="bi bi-envelope"></i>{{ user.email }}</span>
      <span v-if="user.phone"><i class="bi bi-telephone"></i>{{ user.phone }}</span>
      <span v-if="user.city"><i class="bi bi-geo-alt"></i>{{ user.city }}</span>
      <span><i class="bi bi-calendar3"></i>{{ formatDate(joinedAt) }}</span>
      <span v-if="user.lastOnlineAt"><i class="bi bi-clock-history"></i>{{ formatDate(user.lastOnlineAt) }}</span>
    </div>

    <div class="uc__actions">
      <RouterLink class="btn btn-sm btn-ghost" :to="{ name: 'user-detail', params: { id: user.id } }">
        <i class="bi bi-eye"></i> عرض
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
        class="btn btn-sm btn-outline-danger"
        type="button"
        :title="t('users.deleteUser')"
        @click="$emit('delete', user)"
      >
        <i class="bi bi-trash"></i>
      </button>
    </div>
  </article>
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
  return absUrl(props.user.avatarUrl || props.user.avatar)
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
.uc {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-radius: 16px;
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(18, 20, 28, 0.72);
}
.uc.is-selected {
  outline: 2px solid rgba(45, 212, 191, 0.7);
  outline-offset: 1px;
}
.uc--banned {
  border-color: rgba(248, 113, 113, 0.35);
}

.uc__top {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.uc__avatar-wrap {
  position: relative;
  width: 56px;
  height: 56px;
  flex-shrink: 0;
}
.uc__avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(255, 255, 255, 0.12);
  display: block;
}
.uc__avatar--fallback {
  display: grid;
  place-items: center;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(145deg, #334155, #1e293b);
}
.uc__frame {
  position: absolute;
  inset: -10px;
  pointer-events: none;
  display: grid;
  place-items: center;
}
.uc__frame-media {
  width: 76px !important;
  height: 76px !important;
  object-fit: contain;
  background: transparent !important;
}

.uc__identity {
  flex: 1;
  min-width: 0;
}
.uc__name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.uc__name {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 800;
  color: #f8fafc;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.uc__sub {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
  font-size: 0.78rem;
  color: #94a3b8;
}
.uc__id {
  font-family: ui-monospace, monospace;
  color: #fcd34d;
  font-weight: 700;
}
.uc__chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: #e2e8f0;
  font-size: 0.7rem;
  font-weight: 600;
}
.uc__chip--gold { background: rgba(245, 158, 11, 0.16); color: #fde68a; }
.uc__chip--cyan { background: rgba(6, 182, 212, 0.16); color: #a5f3fc; }
.uc__chip--ok { background: rgba(34, 197, 94, 0.16); color: #86efac; }

.uc__stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
}
.uc-stat {
  text-align: center;
  padding: 8px 4px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.28);
  border: 1px solid rgba(255, 255, 255, 0.05);
  min-width: 0;
}
.uc-stat__v {
  display: block;
  font-weight: 800;
  font-size: 0.88rem;
  color: #fff;
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.uc-stat__l {
  display: block;
  margin-top: 2px;
  font-size: 0.62rem;
  color: #94a3b8;
  font-weight: 600;
}

.uc__wallets {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.uc-bal {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.06);
  font-weight: 800;
  color: #fff;
}
.uc-bal i { opacity: 0.9; }
.uc-bal small {
  margin-inline-start: auto;
  font-size: 0.68rem;
  font-weight: 600;
  color: #94a3b8;
}
.uc-bal--gem { border-color: rgba(45, 212, 191, 0.25); }
.uc-bal--gem i { color: #5eead4; }
.uc-bal--coin { border-color: rgba(234, 179, 8, 0.25); }
.uc-bal--coin i { color: #fbbf24; }

.uc__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding-top: 4px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 0.72rem;
  color: #94a3b8;
}
.uc__meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.uc__meta i { color: #64748b; }

.uc__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
  padding-top: 2px;
}
</style>
