<template>
  <div class="glass p-3 h-100">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <div>
        <h3 class="h6 mb-0 fw-semibold">{{ title }}</h3>
        <div v-if="subtitle" class="bilingual-muted">{{ subtitle }}</div>
      </div>
      <slot name="actions" />
    </div>
    <div class="chart-wrap">
      <component :is="chartComponent" :data="chartData" :options="mergedOptions" />
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

const mergedOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      labels: {
        color: getComputedStyle(document.documentElement).getPropertyValue('--text-secondary').trim() || '#a8c9c6',
        font: { family: 'Outfit' },
      },
    },
  },
  scales:
    props.type === 'doughnut'
      ? undefined
      : {
          x: {
            ticks: { color: '#6f9190' },
            grid: { color: 'rgba(45,212,191,0.08)' },
          },
          y: {
            ticks: { color: '#6f9190' },
            grid: { color: 'rgba(45,212,191,0.08)' },
          },
        },
  ...props.options,
}))
</script>
