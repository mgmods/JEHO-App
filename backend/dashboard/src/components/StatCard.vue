<template>
  <div class="glass widget-card stat-card h-100" :class="toneClass">
    <div class="d-flex justify-content-between align-items-start gap-2">
      <div class="min-w-0">
        <div class="stat-label">{{ label }}</div>
        <div class="stat-value">{{ display }}</div>
        <div v-if="hint" class="small text-muted mt-1">{{ hint }}</div>
      </div>
      <div class="stat-icon" :aria-label="label">
        <svg v-if="kind === 'users'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
        <svg v-else-if="kind === 'rooms'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M3 21V8l9-5 9 5v13" />
          <path d="M9 21v-8h6v8" />
          <path d="M9 13h6" />
        </svg>
        <svg v-else-if="kind === 'revenue'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v10" />
          <path d="M15.5 9.5c0-1.5-1.5-2.5-3.5-2.5s-3.5 1-3.5 2.5 1.5 2.2 3.5 2.5 3.5 1 3.5 2.5-1.5 2.5-3.5 2.5-3.5-1-3.5-2.5" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M4 19V5" /><path d="M10 19V9" /><path d="M16 19v-6" /><path d="M22 19V8" />
        </svg>
      </div>
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
  raw: { type: Boolean, default: false },
})

const display = computed(() => (props.raw ? props.value : formatNumber(props.value)))

const kind = computed(() => {
  const i = String(props.icon || '').toLowerCase()
  if (i.includes('people') || i === 'users') return 'users'
  if (i.includes('door') || i === 'rooms') return 'rooms'
  if (i.includes('currency') || i.includes('dollar') || i === 'revenue') return 'revenue'
  return i || 'users'
})

const toneClass = computed(() => `tone-${kind.value}`)
</script>

<style scoped>
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: linear-gradient(135deg, rgba(13, 148, 136, 0.28), rgba(6, 182, 212, 0.22));
  color: #2ef3e1;
  box-shadow: inset 0 0 0 1px rgba(46, 243, 225, 0.18);
}
.stat-icon svg {
  width: 24px;
  height: 24px;
}
.tone-users .stat-icon { color: #7dd3fc; background: linear-gradient(135deg, rgba(56,189,248,.25), rgba(14,116,144,.2)); }
.tone-rooms .stat-icon { color: #86efac; background: linear-gradient(135deg, rgba(74,222,128,.22), rgba(21,128,61,.18)); }
.tone-revenue .stat-icon { color: #fde68a; background: linear-gradient(135deg, rgba(251,191,36,.25), rgba(180,83,9,.18)); }
</style>
