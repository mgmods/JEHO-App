<template>
  <span v-if="code && code !== 'OTHER'" class="country-flag" :title="label || code">
    <img
      :src="src"
      :alt="code"
      width="20"
      height="15"
      loading="lazy"
      @error="onError"
    />
    <span v-if="showLabel && label" class="country-flag__label">{{ label }}</span>
  </span>
  <span v-else-if="showLabel && (label || raw)" class="text-muted">{{ label || raw || '—' }}</span>
  <span v-else-if="!hideEmpty" class="text-muted">—</span>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  country: { type: [String, Object], default: '' },
  showLabel: { type: Boolean, default: false },
  hideEmpty: { type: Boolean, default: false },
})

const broken = ref(false)

const raw = computed(() => {
  if (!props.country) return ''
  if (typeof props.country === 'object') {
    return String(props.country.code || props.country.country || props.country.name || '')
  }
  return String(props.country).trim()
})

const code = computed(() => {
  const v = raw.value
  if (!v) return ''
  const m = v.match(/\b([A-Za-z]{2})\b/)
  if (m) return m[1].toUpperCase()
  if (/^[A-Za-z]{2}$/.test(v)) return v.toUpperCase()
  return v.length <= 3 ? v.toUpperCase() : ''
})

const label = computed(() => {
  if (typeof props.country === 'object') {
    return props.country.name || props.country.label || code.value
  }
  return raw.value && raw.value !== code.value ? raw.value : code.value
})

const src = computed(() => {
  if (!code.value || broken.value) return ''
  return `https://flagcdn.com/w40/${code.value.toLowerCase()}.png`
})

watch(() => props.country, () => { broken.value = false })

function onError() {
  broken.value = true
}
</script>

<style scoped>
.country-flag {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  vertical-align: middle;
}
.country-flag img {
  border-radius: 2px;
  object-fit: cover;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.08);
}
.country-flag__label {
  font-size: 0.875rem;
}
</style>
