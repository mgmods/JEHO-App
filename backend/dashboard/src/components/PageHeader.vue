<template>
  <div class="page-header mb-3">
    <div class="d-flex flex-wrap align-items-end justify-content-between gap-2">
      <div>
        <div class="page-eyebrow">JEHO CHAT</div>
        <h1 class="page-title">{{ title }}</h1>
        <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
      </div>
      <div class="d-flex flex-wrap gap-2">
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
})
</script>
