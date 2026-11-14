<template>
  <div class="glass p-3 h-100 chart-card">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <div>
        <h3 class="h6 mb-0 fw-semibold chart-title">{{ title }}</h3>
        <div v-if="subtitle" class="chart-sub">{{ subtitle }}</div>
      </div>
      <slot name="actions" />
    </div>
    <div class="chart-wrap" :class="{ 'has-center': type === 'doughnut' && centerValue }">
      <component :is="chartComponent" :data="chartData" :options="mergedOptions" />
      <div v-if="type === 'doughnut' && centerValue" class="chart-center">
        <div class="chart-center-value">{{ centerValue }}</div>
        <div class="chart-center-label">{{ centerLabel || 'Total' }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js'
import { Line, Bar, Doughnut } from 'vue-chartjs'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Title,
  Tooltip,
  Legend,
  Filler,
)

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  type: { type: String, default: 'line' },
  labels: { type: Array, default: () => [] },
  datasets: { type: Array, default: () => [] },
  options: { type: Object, default: () => ({}) },
  centerLabel: { type: String, default: '' },
  centerValue: { type: [String, Number], default: '' },
})

const chartComponent = computed(() => {
  if (props.type === 'bar') return Bar
  if (props.type === 'doughnut') return Doughnut
  return Line
})

const chartData = computed(() => ({
  labels: props.labels,
  datasets: props.datasets,
}))

const mergedOptions = computed(() => {
  const base = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: {
        position: props.type === 'doughnut' ? 'right' : 'bottom',
        align: 'center',
        labels: {
          color: '#8b93ad',
          font: { family: 'system-ui, sans-serif', size: 11 },
          boxWidth: 8,
          padding: 16,
          usePointStyle: true,
          pointStyle: 'circle',
        },
      },
      tooltip: {
        backgroundColor: 'rgba(15, 17, 26, 0.95)',
        borderColor: 'rgba(108, 92, 231, 0.35)',
        borderWidth: 1,
        titleColor: '#fff',
        bodyColor: '#c5cce0',
        padding: 12,
        cornerRadius: 10,
      },
    },
    scales:
      props.type === 'doughnut'
        ? undefined
        : {
            x: {
              ticks: { color: '#7a829c', font: { size: 11 } },
              grid: { display: false },
              border: { display: false },
            },
            y: {
              ticks: { color: '#7a829c', font: { size: 11 } },
              grid: { color: 'rgba(255, 255, 255, 0.04)', drawBorder: false },
              border: { display: false },
            },
          },
    cutout: props.type === 'doughnut' ? '74%' : undefined,
  }
  return {
    ...base,
    ...props.options,
    plugins: { ...base.plugins, ...(props.options?.plugins || {}) },
  }
})
</script>

<style scoped>
.chart-card {
  border-radius: 22px !important;
  padding: 1.2rem 1.25rem !important;
  position: relative;
  overflow: hidden;
}
.chart-card::before {
  content: '';
  position: absolute;
  inset: auto -15% -45% auto;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.18), transparent 70%);
  pointer-events: none;
}
.chart-title { color: #fff; font-size: 0.95rem; font-weight: 700; letter-spacing: -0.02em; }
.chart-sub { font-size: 0.75rem; color: #9ca3c7; margin-top: 0.2rem; }
.chart-wrap { height: 300px; position: relative; }
.chart-wrap.has-center { height: 290px; }
.chart-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  padding-inline-end: 28%;
  text-align: center;
}
.chart-center-value {
  font-size: 1.4rem;
  font-weight: 700;
  color: #fff;
  letter-spacing: -0.03em;
}
.chart-center-label {
  font-size: 0.72rem;
  color: #7a829c;
  font-weight: 600;
  margin-top: 0.15rem;
}
@media (max-width: 767.98px) {
  .chart-center { padding-inline-end: 0; }
}
</style>
