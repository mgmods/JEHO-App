<template>
  <article class="ac glass" :class="{ 'is-selected': selected, [`ac--${statusKey}`]: true }">
    <BulkCheck :checked="selected" @toggle="$emit('toggle-select', agency)" />

    <div class="ac__top">
      <div class="ac__mark" aria-hidden="true">
        <img v-if="logoUrl" :src="logoUrl" class="ac__logo" alt="" @error="logoError = true" />
        <div v-else class="ac__logo ac__logo--fallback">{{ initial }}</div>
      </div>
      <div class="ac__head">
        <div class="ac__title-row">
          <h3 class="ac__name">{{ agency.name || t('common.untitled') }}</h3>
          <i
            v-if="agency.isVerified"
            class="bi bi-patch-check-fill ac__verified"
            :title="t('agencies.verified')"
          ></i>
          <StatusBadge :status="agency.status || 'pending'" />
        </div>
        <div class="ac__sub">
          <span v-if="agency.publicId" class="ac__id">ID {{ agency.publicId }}</span>
          <span class="ac__owner">
            <i class="bi bi-person"></i>
            {{ ownerLabel }}
          </span>
        </div>
      </div>
    </div>

    <div class="ac__stats">
      <div class="ac-stat">
        <span class="ac-stat__v">{{ formatNumber(hosts) }}</span>
        <span class="ac-stat__l">{{ t('agencies.hosts') }}</span>
      </div>
      <div class="ac-stat">
        <span class="ac-stat__v">{{ formatNumber(diamonds) }}</span>
        <span class="ac-stat__l">{{ t('common.diamonds') }}</span>
      </div>
      <div class="ac-stat">
        <span class="ac-stat__v">{{ commissionLabel }}</span>
        <span class="ac-stat__l">{{ t('agencies.commission') }}</span>
      </div>
    </div>

    <div v-if="agency.activationCode" class="ac__code">
      <span class="ac__code-lab">{{ t('agencies.activationCode') }}</span>
      <code>{{ agency.activationCode }}</code>
      <button class="btn btn-sm btn-ghost" type="button" :title="t('agencies.copyCode')" @click="$emit('copy-code', agency.activationCode)">
        <i class="bi bi-clipboard"></i>
      </button>
      <button class="btn btn-sm btn-ghost" type="button" :title="t('agencies.regenerateCode')" @click="$emit('regen-code', agency)">
        <i class="bi bi-arrow-repeat"></i>
      </button>
    </div>
    <div v-else class="ac__code ac__code--empty">
      <span class="text-muted">{{ t('agencies.activationCode') }}: —</span>
    </div>

    <div class="ac__actions">
      <button
        v-if="isPendingOrSuspended"
        class="btn btn-sm btn-outline-success"
        type="button"
        @click="$emit('approve', agency)"
      >
        {{ isSuspended ? t('agencies.reactivate') : t('agencies.approve') }}
      </button>
      <button
        v-if="isActiveOrPending"
        class="btn btn-sm btn-outline-warning"
        type="button"
        @click="$emit('suspend', agency)"
      >
        {{ t('agencies.suspend') }}
      </button>
      <button class="btn btn-sm btn-ghost" type="button" :title="t('common.members')" @click="$emit('members', agency)">
        <i class="bi bi-people"></i>
      </button>
      <button class="btn btn-sm btn-ghost" type="button" @click="$emit('edit', agency)">
        <i class="bi bi-pencil"></i>
      </button>
      <button class="btn btn-sm btn-outline-danger" type="button" :title="t('app.delete')" @click="$emit('delete', agency)">
        <i class="bi bi-trash"></i>
      </button>
    </div>
  </article>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BulkCheck from '@/components/BulkCheck.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { formatNumber } from '@/composables/useUtils'
import { resolveAsset } from '@/utils/assets'

const props = defineProps({
  agency: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})
const { t } = useI18n()

defineEmits([
  'toggle-select',
  'approve',
  'suspend',
  'delete',
  'edit',
  'members',
  'copy-code',
  'regen-code',
])

const logoError = ref(false)

const statusKey = computed(() => String(props.agency.status || 'pending').toLowerCase())
const isSuspended = computed(() => statusKey.value === 'suspended')
const isPendingOrSuspended = computed(
  () => statusKey.value === 'pending' || statusKey.value === 'suspended',
)
const isActiveOrPending = computed(
  () => statusKey.value === 'active' || statusKey.value === 'pending',
)

const ownerLabel = computed(
  () =>
    props.agency.ownerName
    || props.agency.owner?.displayName
    || props.agency.owner?.username
    || props.agency.ownerId
    || '—',
)

const hosts = computed(() =>
  Number(props.agency.hostsCount ?? props.agency.membersCount ?? props.agency.memberCount ?? 0) || 0,
)
const diamonds = computed(() => Number(props.agency.totalDiamonds || 0) || 0)
const commissionLabel = computed(() => {
  const c = props.agency.commission ?? props.agency.commissionRate ?? props.agency.commissionPercent
  if (c == null || c === '') return '—'
  return `${c}%`
})

const initial = computed(() => String(props.agency.name || 'A').charAt(0).toUpperCase())

const logoUrl = computed(() => {
  if (logoError.value) return null
  const raw = props.agency.logoUrl || props.agency.coverUrl || props.agency.avatarUrl
  if (!raw) return null
  if (String(raw).startsWith('http')) return raw
  return resolveAsset(raw) || `https://api.adnova.bbs.tr${String(raw).startsWith('/') ? '' : '/'}${raw}`
})
</script>

<style scoped>
.ac {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-radius: 16px;
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(18, 20, 28, 0.72);
}
.ac.is-selected {
  outline: 2px solid rgba(45, 212, 191, 0.7);
  outline-offset: 1px;
}
.ac--active { border-color: rgba(34, 197, 94, 0.28); }
.ac--pending { border-color: rgba(234, 179, 8, 0.28); }
.ac--suspended { border-color: rgba(248, 113, 113, 0.3); }

.ac__top {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.ac__mark { flex-shrink: 0; }
.ac__logo {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  object-fit: cover;
  border: 1px solid rgba(255, 255, 255, 0.1);
  display: block;
}
.ac__logo--fallback {
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 1.2rem;
  color: #fff;
  background: linear-gradient(145deg, #0f766e, #115e59);
}
.ac__head { flex: 1; min-width: 0; }
.ac__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ac__name {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 800;
  color: #f8fafc;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.ac__verified { color: #38bdf8; font-size: 1rem; }
.ac__sub {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
  font-size: 0.78rem;
  color: #94a3b8;
}
.ac__id {
  font-family: ui-monospace, monospace;
  color: #fcd34d;
  font-weight: 700;
}
.ac__owner {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.ac__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.ac-stat {
  text-align: center;
  padding: 10px 6px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.28);
  border: 1px solid rgba(255, 255, 255, 0.05);
  min-width: 0;
}
.ac-stat__v {
  display: block;
  font-weight: 800;
  font-size: 0.95rem;
  color: #fff;
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ac-stat__l {
  display: block;
  margin-top: 3px;
  font-size: 0.65rem;
  color: #94a3b8;
  font-weight: 600;
}

.ac__code {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 0.78rem;
}
.ac__code-lab { color: #94a3b8; font-weight: 600; }
.ac__code code {
  font-size: 0.85rem;
  color: #fde68a;
  font-weight: 700;
}
.ac__code--empty { color: #64748b; }

.ac__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
  padding-top: 2px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
</style>
