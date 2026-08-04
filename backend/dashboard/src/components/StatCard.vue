<template>
  <div class="kpi-card h-100" :class="toneClass">
    <div class="kpi-icon" :aria-hidden="true">
      <svg v-if="kind === 'users'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
        <path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </svg>
      <svg v-else-if="kind === 'rooms'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
        <path d="M12 1a3 3 0 0 0-3 3v11a3 3 0 1 0 6 0V4a3 3 0 0 0-3-3z" />
        <path d="M19 10v1a7 7 0 0 1-14 0v-1" />
        <path d="M12 18v4" /><path d="M8 22h8" />
      </svg>
      <svg v-else-if="kind === 'live'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
        <path d="M4 10v4" /><path d="M8 7v10" /><path d="M12 4v16" /><path d="M16 7v10" /><path d="M20 10v4" />
      </svg>
      <svg v-else-if="kind === 'revenue'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v10" />
        <path d="M15.5 9.5c0-1.5-1.5-2.5-3.5-2.5s-3.5 1-3.5 2.5 1.5 2.2 3.5 2.5 3.5 1 3.5 2.5-1.5 2.5-3.5 2.5-3.5-1-3.5-2.5" />
      </svg>
      <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
        <path d="M4 19V5" /><path d="M10 19V9" /><path d="M16 19v-6" /><path d="M22 19V8" />
      </svg>
    </div>
    <div class="kpi-body min-w-0">
      <div class="kpi-label">{{ label }}</div>
      <div class="kpi-value">{{ display }}</div>
      <div v-if="delta" class="kpi-delta" :class="deltaTone">
        <span class="kpi-delta-num">{{ delta }}</span>
        <span v-if="deltaNote" class="kpi-delta-note">{{ deltaNote }}</span>
      </div>
      <div v-else-if="hint" class="kpi-hint">{{ hint }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatNumber } from '@/composables/useUtils'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], default: 0 },
  icon: { type: String, default: 'users' },
  hint: { type: String, default: '' },
  delta: { type: String, default: '' },
  deltaNote: { type: String, default: '' },
  raw: { type: Boolean, default: false },
  tone: { type: String, default: '' },
})

const display = computed(() => (props.raw ? props.value : formatNumber(props.value)))

const kind = computed(() => {
  if (props.tone) return props.tone
  const i = String(props.icon || '').toLowerCase()
  if (i.includes('people') || i === 'users') return 'users'
  if (i.includes('mic') || i === 'live' || i.includes('broadcast') || i.includes('wave')) return 'live'
  if (i.includes('door') || i === 'rooms') return 'rooms'
  if (i.includes('currency') || i.includes('dollar') || i === 'revenue') return 'revenue'
  return i || 'users'
})

const toneClass = computed(() => `tone-${kind.value}`)
const deltaTone = computed(() =>
  String(props.delta || '').trim().startsWith('-') ? 'is-down' : 'is-up',
)
</script>

<style scoped>
.kpi-card {
  display: flex;
  align-items: center;
  gap: 1.05rem;
  padding: 1.25rem 1.3rem;
  border-radius: 22px;
  min-height: 122px;
  position: relative;
  overflow: hidden;
}
.kpi-card::before {
  content: '';
  position: absolute;
  inset: auto -20% -40% auto;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.22), transparent 70%);
  pointer-events: none;
}
.kpi-icon {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
.kpi-icon svg { width: 24px; height: 24px; }
.kpi-body { position: relative; z-index: 1; }
.kpi-label {
  font-size: 0.8rem;
  color: #9ca3c7;
  font-weight: 500;
  margin-bottom: 0.3rem;
}
.kpi-value {
  font-size: 1.8rem;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.03em;
  line-height: 1.1;
  text-shadow: 0 0 24px rgba(167, 139, 250, 0.25);
}
.kpi-delta {
  margin-top: 0.45rem;
  font-size: 0.78rem;
  font-weight: 700;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.35rem;
}
.kpi-delta.is-up .kpi-delta-num { color: #34d399; text-shadow: 0 0 12px rgba(52, 211, 153, 0.35); }
.kpi-delta.is-down .kpi-delta-num { color: #fb7185; }
.kpi-delta-note {
  color: #7a829c;
  font-weight: 500;
  font-size: 0.72rem;
}
.kpi-hint {
  margin-top: 0.35rem;
  font-size: 0.72rem;
  color: #6b7485;
}

.tone-users .kpi-icon {
  color: #c4b5fd;
  background: rgba(139, 92, 246, 0.18);
  box-shadow: 0 0 22px rgba(139, 92, 246, 0.35), inset 0 0 0 1px rgba(167, 139, 250, 0.35);
}
.tone-rooms .kpi-icon {
  color: #67e8f9;
  background: rgba(34, 211, 238, 0.14);
  box-shadow: 0 0 22px rgba(34, 211, 238, 0.28), inset 0 0 0 1px rgba(34, 211, 238, 0.3);
}
.tone-live .kpi-icon {
  color: #6ee7b7;
  background: rgba(52, 211, 153, 0.12);
  box-shadow: 0 0 22px rgba(52, 211, 153, 0.25), inset 0 0 0 1px rgba(52, 211, 153, 0.3);
}
.tone-revenue .kpi-icon {
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.12);
  box-shadow: 0 0 22px rgba(251, 191, 36, 0.28), inset 0 0 0 1px rgba(251, 191, 36, 0.3);
}
</style>
