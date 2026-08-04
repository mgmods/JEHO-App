<template>
  <div class="load-wrap text-center py-4" v-if="show">
    <div class="load-svga">
      <SvgaPreview :src="loadingSrc" />
    </div>
    <div v-if="label" class="mt-2 text-muted small">{{ label }}</div>
    <div v-else class="mt-1 text-muted small">{{ t('app.loading') }}</div>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import SvgaPreview from '@/components/SvgaPreview.vue'
import loadingSrc from '@/assets/svga/svga_room_loading.svga?url'

defineProps({
  show: { type: Boolean, default: true },
  label: { type: String, default: '' },
})

const { t } = useI18n()
</script>

<style scoped>
.load-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 140px;
}
.load-svga {
  width: 96px;
  height: 96px;
  max-width: 28vw;
  max-height: 28vw;
}
</style>
