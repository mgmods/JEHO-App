<template>
  <div class="text-center py-5" v-if="show">
    <div class="spinner-border text-info" role="status">
      <span class="visually-hidden">{{ t('app.loading') }}</span>
    </div>
    <div v-if="label" class="mt-2 text-muted small">{{ label }}</div>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

defineProps({
  show: { type: Boolean, default: true },
  label: { type: String, default: '' },
})

const { t } = useI18n()
</script>
