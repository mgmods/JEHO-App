<template>
  <div class="user-card glass widget-card">
    <div class="user-card__row">
      <div class="user-card__avatarWrap">
        <img
          v-if="avatarUrl"
          :src="avatarUrl"
          class="user-card__avatar"
          alt=""
          @error="avatarError = true"
        />
        <div v-else class="user-card__avatarFallback">{{ initials }}</div>
      </div>

      <div class="user-card__main">
        <div class="user-card__titleRow">
          <div class="user-card__name" :title="displayName">{{ displayName }}</div>
          <StatusBadge :status="statusLabel" />
        </div>
        <div class="user-card__sub">
          <span v-if="publicId" class="mono">ID {{ publicId }}</span>
          <span v-if="username">@{{ username }}</span>
        </div>
        <div class="user-card__chips">
          <span class="chip">
            <CountryFlag :country="country" show-label hide-empty />
            <span v-if="!country">—</span>
          </span>
          <span class="chip">{{ genderLabel }}</span>
          <span v-if="user.level" class="chip">Lv.{{ user.level }}</span>
          <span v-if="user.vipLevel" class="chip">VIP {{ user.vipLevel }}</span>
          <span v-if="user.genderVerified" class="chip chip--ok">✓ موثّقة</span>
          <span v-if="user.isGuest" class="chip chip--muted">ضيف</span>
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

    <div class="user-card__meta">
      <div class="meta-item" :title="user.email || ''">
        <i class="bi bi-envelope"></i>
        <span>{{ user.email || '—' }}</span>
      </div>
      <div class="meta-item">
        <i class="bi bi-telephone"></i>
        <span>{{ user.phone || '—' }}</span>
      </div>
      <div class="meta-item">
        <i class="bi bi-coin"></i>
        <span>{{ formatNumber(coins) }}</span>
      </div>
      <div class="meta-item">
        <i class="bi bi-gem"></i>
        <span>{{ formatNumber(diamonds) }}</span>
      </div>
      <div class="meta-item">
        <i class="bi bi-heart"></i>
        <span>{{ formatNumber(charmScore) }}</span>
      </div>
      <div class="meta-item">
        <i class="bi bi-trophy"></i>
        <span>{{ formatNumber(wealthScore) }}</span>
      </div>
      <div class="meta-item">
        <i class="bi bi-star"></i>
        <span>{{ formatNumber(popularityScore) }}</span>
      </div>
      <div class="meta-item" v-if="user.city">
        <i class="bi bi-geo-alt"></i>
        <span>{{ user.city }}</span>
      </div>
      <div class="meta-item" v-if="birthday">
        <i class="bi bi-cake2"></i>
        <span>{{ birthday }}</span>
      </div>
      <div class="meta-item">
        <i class="bi bi-calendar3"></i>
        <span>{{ formatDate(joinedAt) }}</span>
      </div>
      <div class="meta-item" v-if="user.lastOnlineAt">
        <i class="bi bi-circle-fill online-dot"></i>
        <span>{{ formatDate(user.lastOnlineAt) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import StatusBadge from '@/components/StatusBadge.vue'
import CountryFlag from '@/components/CountryFlag.vue'
import { formatDate, formatNumber } from '@/composables/useUtils'

const props = defineProps({
  user: { type: Object, required: true },
})
const { t, locale } = useI18n()

defineEmits(['ban', 'unban', 'delete'])

const avatarError = ref(false)

const displayName = computed(() => props.user.name || props.user.displayName || props.user.username || '—')
const username = computed(() => props.user.username || '')
const publicId = computed(() => props.user.publicId || '')
const email = computed(() => props.user.email || '')
const country = computed(() => props.user.country || props.user.countryCode || '')
const joinedAt = computed(() => props.user.createdAt || props.user.joinedAt)
const birthday = computed(() => props.user.birthday || '')
const coins = computed(() => props.user.coins ?? props.user.balance ?? 0)
const diamonds = computed(() => props.user.diamonds ?? 0)
const charmScore = computed(() => props.user.charmScore ?? 0)
const wealthScore = computed(() => props.user.wealthScore ?? 0)
const popularityScore = computed(() => props.user.popularityScore ?? 0)

const isBanned = computed(() => props.user.isBanned || props.user.status === 'banned')
const statusLabel = computed(() => props.user.status || (isBanned.value ? 'banned' : 'active'))

const genderLabel = computed(() => {
  const g = String(props.user.gender || '').toLowerCase()
  if (locale.value === 'ar') {
    if (g === 'male') return 'ذكر'
    if (g === 'female') return 'أنثى'
    if (g === 'other') return 'آخر'
    return 'غير محدد'
  }
  if (g === 'male') return 'Male'
  if (g === 'female') return 'Female'
  if (g === 'other') return 'Other'
  return 'Unspecified'
})

const avatarUrl = computed(() => {
  if (avatarError.value) return null
  const url = props.user.avatarUrl || props.user.avatar
  if (!url) return null
  if (String(url).startsWith('http')) return url
  return `https://api.adnova.bbs.tr${String(url).startsWith('/') ? '' : '/'}${url}`
})

const initials = computed(() => {
  const name = displayName.value || ''
  return name ? name.charAt(0).toUpperCase() : 'U'
})
</script>

<style scoped>
.user-card {
  border-radius: var(--al-radius);
  padding: 12px;
}

.user-card__row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.user-card__avatarWrap {
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  overflow: hidden;
  border: 1px solid var(--border-color);
  background: rgba(0, 0, 0, 0.15);
}

.user-card__avatar,
.user-card__avatarFallback {
  width: 44px;
  height: 44px;
  object-fit: cover;
}

.user-card__avatarFallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 1rem;
  background: linear-gradient(135deg, var(--al-teal), var(--al-cyan));
}

.user-card__main {
  flex: 1 1 auto;
  min-width: 0;
}

.user-card__titleRow {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.user-card__name {
  font-weight: 700;
  font-size: 0.95rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.user-card__sub {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 2px;
  font-size: 0.75rem;
  color: var(--text-muted);
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.user-card__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.72rem;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--border-color);
}

.chip--ok {
  color: #34d399;
}

.chip--muted {
  opacity: 0.75;
}

.user-card__actions {
  flex: 0 0 auto;
}

.user-card__meta {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 6px 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--border-color);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.74rem;
  color: var(--text-muted);
  min-width: 0;
}

.meta-item i {
  flex: 0 0 auto;
  opacity: 0.75;
}

.meta-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.online-dot {
  font-size: 0.45rem;
  color: #34d399;
}
</style>
