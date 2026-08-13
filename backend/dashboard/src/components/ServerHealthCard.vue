<template>
  <div class="srv-row">
    <!-- Server status (profile-style) -->
    <section class="neo-card srv-status">
      <div class="srv-status__top">
        <div class="srv-ring" :style="ringStyle(cpuPercent)">
          <div class="srv-ring__inner">
            <i class="bi bi-hdd-rack"></i>
          </div>
          <span class="srv-ring__dot" :class="statusClass"></span>
        </div>
        <div class="min-w-0 flex-grow-1">
          <div class="d-flex align-items-center gap-2 flex-wrap">
            <h3 class="srv-title mb-0">{{ t('system.serverStatus') }}</h3>
            <span class="neo-pill" :class="statusPill">{{ statusLabel }}</span>
          </div>
          <div class="srv-host text-truncate">{{ hostLabel }}</div>
          <div class="srv-meta">{{ metaLine }}</div>
        </div>
        <button class="btn btn-sm btn-ghost" type="button" :disabled="loading" @click="$emit('refresh')">
          <i class="bi bi-arrow-clockwise" :class="{ 'spin': loading }"></i>
        </button>
      </div>

      <div class="srv-stats">
        <div class="srv-stat">
          <div class="srv-stat__val">{{ fmtPct(cpuPercent) }}</div>
          <div class="srv-stat__lab">CPU</div>
        </div>
        <div class="srv-stat">
          <div class="srv-stat__val">{{ fmtPct(memPercent) }}</div>
          <div class="srv-stat__lab">{{ t('system.memory') }}</div>
        </div>
        <div class="srv-stat">
          <div class="srv-stat__val">{{ fmtPct(diskPercent) }}</div>
          <div class="srv-stat__lab">{{ t('system.disk') }}</div>
        </div>
        <div class="srv-stat">
          <div class="srv-stat__val">{{ uptimeShort }}</div>
          <div class="srv-stat__lab">{{ t('system.uptime') }}</div>
        </div>
      </div>

      <div class="srv-bars">
        <div class="srv-bar">
          <div class="srv-bar__head">
            <span><i class="bi bi-cpu me-1"></i>CPU</span>
            <span>{{ fmtPct(cpuPercent) }} · {{ cores }} {{ t('system.cores') }}</span>
          </div>
          <div class="srv-bar__track">
            <div class="srv-bar__fill is-cpu" :style="{ width: clampPct(cpuPercent) + '%' }"></div>
          </div>
        </div>
        <div class="srv-bar">
          <div class="srv-bar__head">
            <span><i class="bi bi-memory me-1"></i>{{ t('system.memory') }}</span>
            <span>{{ bytes(usedMem) }} / {{ bytes(totalMem) }}</span>
          </div>
          <div class="srv-bar__track">
            <div class="srv-bar__fill is-mem" :style="{ width: clampPct(memPercent) + '%' }"></div>
          </div>
        </div>
        <div class="srv-bar">
          <div class="srv-bar__head">
            <span><i class="bi bi-device-hdd me-1"></i>{{ t('system.disk') }}</span>
            <span v-if="disk">{{ bytes(disk.usedBytes) }} / {{ bytes(disk.totalBytes) }}</span>
            <span v-else>—</span>
          </div>
          <div class="srv-bar__track">
            <div class="srv-bar__fill is-disk" :style="{ width: clampPct(diskPercent) + '%' }"></div>
          </div>
        </div>
      </div>
    </section>

    <!-- Logs cleanup -->
    <section class="neo-card srv-logs">
      <div class="srv-logs__head">
        <div>
          <h3 class="srv-title mb-0">{{ t('system.logCleanup') }}</h3>
          <div class="srv-meta">{{ t('system.logCleanupHint') }}</div>
        </div>
        <span class="neo-pill neo-pill--heat">
          <i class="bi bi-journal-text"></i>
          {{ formatNumber(logsTotal) }}
        </span>
      </div>

      <div class="srv-log-rows">
        <div class="srv-log-row">
          <div class="srv-log-ic is-all"><i class="bi bi-collection"></i></div>
          <div class="flex-grow-1 min-w-0">
            <div class="srv-log-title">{{ t('system.allLogs') }}</div>
            <div class="srv-log-sub">{{ t('system.cleanAllHint') }}</div>
          </div>
          <button
            class="btn btn-sm btn-outline-danger"
            type="button"
            :disabled="cleaning"
            @click="$emit('clean', { mode: 'all' })"
          >
            {{ t('system.cleanAll') }}
          </button>
        </div>
        <div class="srv-log-row">
          <div class="srv-log-ic is-old"><i class="bi bi-calendar-x"></i></div>
          <div class="flex-grow-1 min-w-0">
            <div class="srv-log-title">{{ t('system.oldLogs') }}</div>
            <div class="srv-log-sub">{{ t('system.olderThanDays', { days: 30 }) }}</div>
          </div>
          <button
            class="btn btn-sm btn-ghost"
            type="button"
            :disabled="cleaning"
            @click="$emit('clean', { mode: 'days', days: 30 })"
          >
            {{ t('system.cleanOld') }}
          </button>
        </div>
        <div class="srv-log-row">
          <div class="srv-log-ic is-done"><i class="bi bi-check2-circle"></i></div>
          <div class="flex-grow-1 min-w-0">
            <div class="srv-log-title">{{ t('system.resolvedLogs') }}</div>
            <div class="srv-log-sub">{{ t('system.resolvedOnly') }}</div>
          </div>
          <button
            class="btn btn-sm btn-ghost"
            type="button"
            :disabled="cleaning"
            @click="$emit('clean', { mode: 'resolved' })"
          >
            {{ t('system.cleanResolved') }}
          </button>
        </div>
      </div>

      <div class="srv-log-foot">
        <div class="srv-mini">
          <span class="srv-mini__lab">{{ t('system.unresolved') }}</span>
          <span class="srv-mini__val warn">{{ formatNumber(logsUnresolved) }}</span>
        </div>
        <div class="srv-mini">
          <span class="srv-mini__lab">{{ t('system.critical') }}</span>
          <span class="srv-mini__val danger">{{ formatNumber(logsCritical) }}</span>
        </div>
        <RouterLink class="s-foot-btn srv-foot-link" :to="{ name: 'logs' }">
          {{ t('system.viewLogs') }}
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatNumber } from '@/composables/useUtils'

const props = defineProps({
  health: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  cleaning: { type: Boolean, default: false },
})

defineEmits(['refresh', 'clean'])

const { t, locale } = useI18n()

const h = computed(() => props.health || {})
const cpuPercent = computed(() => Number(h.value?.cpu?.percent ?? 0))
const memPercent = computed(() => Number(h.value?.memory?.usedPercent ?? 0))
const disk = computed(() => h.value?.disk || null)
const diskPercent = computed(() => Number(disk.value?.usedPercent ?? 0))
const usedMem = computed(() => Number(h.value?.memory?.usedBytes ?? 0))
const totalMem = computed(() => Number(h.value?.memory?.totalBytes ?? 0))
const cores = computed(() => Number(h.value?.cores || h.value?.cpu?.cores || 0))
const logsTotal = computed(() => Number(h.value?.logs?.total ?? 0))
const logsUnresolved = computed(() => Number(h.value?.logs?.unresolved ?? 0))
const logsCritical = computed(() => Number(h.value?.logs?.critical ?? 0))

const status = computed(() => String(h.value?.status || 'ok'))
const statusClass = computed(() => {
  if (status.value === 'critical') return 'is-crit'
  if (status.value === 'warn') return 'is-warn'
  return 'is-ok'
})
const statusPill = computed(() => {
  if (status.value === 'critical') return 'neo-pill--live'
  if (status.value === 'warn') return 'neo-pill--heat'
  return 'neo-pill--talk'
})
const statusLabel = computed(() => {
  if (status.value === 'critical') return t('system.statusCritical')
  if (status.value === 'warn') return t('system.statusWarn')
  return t('system.statusOk')
})
const hostLabel = computed(() => h.value?.host || t('system.unknownHost'))
const metaLine = computed(() => {
  const parts = [h.value?.platform, h.value?.node, h.value?.arch].filter(Boolean)
  return parts.join(' · ') || '—'
})
const uptimeShort = computed(() => formatUptime(h.value?.uptimeSec || h.value?.processUptimeSec || 0))

function clampPct(n) {
  const v = Number(n) || 0
  return Math.max(0, Math.min(100, v))
}
function fmtPct(n) {
  return `${clampPct(n).toFixed(0)}%`
}
function ringStyle(pct) {
  const p = clampPct(pct)
  return {
    background: `conic-gradient(#8b5cf6 ${p * 3.6}deg, rgba(139, 92, 246, 0.12) 0)`,
  }
}
function bytes(n) {
  const v = Number(n) || 0
  if (v < 1024) return `${v} B`
  const u = ['KB', 'MB', 'GB', 'TB']
  let x = v
  let i = -1
  do {
    x /= 1024
    i++
  } while (x >= 1024 && i < u.length - 1)
  return `${x.toFixed(x >= 10 ? 0 : 1)} ${u[i]}`
}
function formatUptime(sec) {
  const s = Math.max(0, Math.floor(Number(sec) || 0))
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (d > 0) return locale.value === 'ar' ? `${d}ي ${h}س` : `${d}d ${h}h`
  if (h > 0) return locale.value === 'ar' ? `${h}س ${m}د` : `${h}h ${m}m`
  return locale.value === 'ar' ? `${m}د` : `${m}m`
}
</script>

<style scoped>
.srv-row {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 0.95rem;
}
@media (max-width: 991.98px) {
  .srv-row { grid-template-columns: 1fr; }
}

.neo-card {
  border-radius: 22px;
  padding: 1.15rem 1.2rem 1.2rem;
  position: relative;
  overflow: hidden;
}
.neo-card::after {
  content: '';
  position: absolute;
  inset: auto -25% -55% auto;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.18), transparent 70%);
  pointer-events: none;
}

.srv-status__top {
  display: flex;
  align-items: flex-start;
  gap: 0.95rem;
  position: relative;
  z-index: 1;
}

.srv-ring {
  width: 74px;
  height: 74px;
  border-radius: 50%;
  padding: 3px;
  flex-shrink: 0;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.15), 0 0 28px rgba(139, 92, 246, 0.4);
  position: relative;
}
.srv-ring__inner {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #12102a;
  border: 3px solid #0e0b1f;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c4b5fd;
  font-size: 1.45rem;
}
.srv-ring__dot {
  position: absolute;
  bottom: 4px;
  inset-inline-end: 4px;
  width: 13px;
  height: 13px;
  border-radius: 50%;
  border: 2px solid #0e0b1f;
}
.srv-ring__dot.is-ok { background: #34d399; box-shadow: 0 0 10px rgba(52, 211, 153, 0.7); }
.srv-ring__dot.is-warn { background: #fbbf24; box-shadow: 0 0 10px rgba(251, 191, 36, 0.7); }
.srv-ring__dot.is-crit { background: #f43f5e; box-shadow: 0 0 10px rgba(244, 63, 94, 0.7); }

.srv-title {
  font-size: 1.05rem;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.02em;
}
.srv-host {
  margin-top: 0.2rem;
  font-size: 0.88rem;
  font-weight: 650;
  color: #c4b5fd;
}
.srv-meta {
  margin-top: 0.15rem;
  font-size: 0.72rem;
  color: #9ca3c7;
}

.srv-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.55rem;
  margin-top: 1rem;
  position: relative;
  z-index: 1;
}
.srv-stat {
  text-align: center;
  padding: 0.65rem 0.4rem;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.28);
  border: 1px solid rgba(255, 255, 255, 0.05);
}
.srv-stat__val {
  font-size: 1.05rem;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.02em;
}
.srv-stat__lab {
  margin-top: 0.15rem;
  font-size: 0.68rem;
  color: #9ca3c7;
  font-weight: 500;
}

.srv-bars {
  margin-top: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  position: relative;
  z-index: 1;
}
.srv-bar__head {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: #c5cce0;
  margin-bottom: 0.35rem;
  font-weight: 600;
}
.srv-bar__track {
  height: 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
}
.srv-bar__fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.5s ease;
}
.srv-bar__fill.is-cpu {
  background: linear-gradient(90deg, #8b5cf6, #a78bfa);
  box-shadow: 0 0 12px rgba(139, 92, 246, 0.55);
}
.srv-bar__fill.is-mem {
  background: linear-gradient(90deg, #22d3ee, #67e8f9);
  box-shadow: 0 0 12px rgba(34, 211, 238, 0.4);
}
.srv-bar__fill.is-disk {
  background: linear-gradient(90deg, #f472b6, #fbbf24);
  box-shadow: 0 0 12px rgba(244, 114, 182, 0.35);
}

.srv-logs__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.85rem;
  position: relative;
  z-index: 1;
}

.srv-log-rows {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  position: relative;
  z-index: 1;
}
.srv-log-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.7rem 0.75rem;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.28);
  border: 1px solid rgba(255, 255, 255, 0.05);
}
.srv-log-ic {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 1rem;
}
.srv-log-ic.is-all {
  color: #fda4af;
  background: rgba(244, 63, 94, 0.12);
  box-shadow: 0 0 14px rgba(244, 63, 94, 0.2);
}
.srv-log-ic.is-old {
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.12);
  box-shadow: 0 0 14px rgba(251, 191, 36, 0.2);
}
.srv-log-ic.is-done {
  color: #6ee7b7;
  background: rgba(52, 211, 153, 0.12);
  box-shadow: 0 0 14px rgba(52, 211, 153, 0.2);
}
.srv-log-title { font-size: 0.88rem; font-weight: 700; color: #fff; }
.srv-log-sub { font-size: 0.7rem; color: #9ca3c7; margin-top: 0.1rem; }

.srv-log-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
  margin-top: 0.95rem;
  position: relative;
  z-index: 1;
}
.srv-mini {
  padding: 0.45rem 0.7rem;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.05);
}
.srv-mini__lab { display: block; font-size: 0.65rem; color: #9ca3c7; }
.srv-mini__val { font-weight: 800; font-size: 0.95rem; color: #fff; }
.srv-mini__val.warn { color: #fbbf24; }
.srv-mini__val.danger { color: #fda4af; }
.srv-foot-link {
  margin-top: 0 !important;
  flex: 1 1 auto;
  min-width: 8rem;
  text-align: center;
}

.spin { animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
