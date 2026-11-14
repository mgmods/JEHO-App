<template>
  <div v-if="message" class="alert alert-glass d-flex align-items-start gap-2" :class="toneClass" role="alert">
    <i :class="iconClass"></i>
    <div class="flex-grow-1">
      <strong v-if="title" class="d-block mb-1">{{ title }}</strong>
      <span>{{ message }}</span>
    </div>
    <button v-if="dismissible" type="button" class="btn-close" @click="$emit('dismiss')"></button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  message: { type: String, default: '' },
  title: { type: String, default: '' },
  type: { type: String, default: 'danger' },
  dismissible: { type: Boolean, default: true },
})

defineEmits(['dismiss'])

const toneClass = computed(() => {
  const map = {
    danger: 'border-danger',
    warning: 'border-warning',
    success: 'border-success',
    info: 'border-info',
  }
  return map[props.type] || map.danger
})

const iconClass = computed(() => {
  const map = {
    danger: 'bi bi-exclamation-triangle text-danger',
    warning: 'bi bi-exclamation-circle text-warning',
    success: 'bi bi-check-circle text-success',
    info: 'bi bi-info-circle text-info',
  }
  return map[props.type] || map.danger
})
</script>
