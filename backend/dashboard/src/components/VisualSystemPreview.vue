<template>
  <div v-if="modelValue" class="visual-preview-backdrop" @click.self="close">
    <section class="visual-preview-panel">
      <header>
        <strong>{{ title || 'معاينة المؤثر' }}</strong>
        <div class="d-flex gap-2">
          <button class="btn btn-sm btn-ghost" type="button" @click="play">إعادة التشغيل</button>
          <button class="btn btn-sm btn-outline-light" type="button" @click="close">إغلاق</button>
        </div>
      </header>
      <iframe
        ref="frame"
        :src="runtimeUrl"
        title="Visual effect preview"
        allow="autoplay"
        @load="play"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  effectType: { type: String, default: 'gift' },
  payload: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:modelValue'])
const frame = ref(null)
const origin = (import.meta.env.VITE_API_ORIGIN || 'https://api.adnova.bbs.tr').replace(/\/$/, '')
const runtimeUrl = computed(() => `${origin}/visual-system/runtime.html`)

function play() {
  if (!props.modelValue) return
  const win = frame.value?.contentWindow
  if (!win) return
  try {
    win.postMessage({ source: 'jeho-dashboard', type: 'stopAll', payload: {} }, '*')
  } catch (_) { /* ignore */ }
  window.setTimeout(() => {
    win.postMessage({
      source: 'jeho-dashboard',
      type: props.effectType,
      payload: props.payload,
    }, '*')
  }, 40)
}

function close() {
  emit('update:modelValue', false)
}

watch(() => props.payload, () => setTimeout(play, 0), { deep: true })
</script>

<style scoped>
.visual-preview-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2100;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(2, 7, 15, .84);
  backdrop-filter: blur(8px);
}
.visual-preview-panel {
  width: min(920px, 96vw);
  overflow: hidden;
  border: 1px solid rgba(53, 231, 210, .35);
  border-radius: 24px;
  background: #101d2d;
  box-shadow: 0 24px 80px rgba(0, 0, 0, .55);
}
header {
  min-height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 18px;
}
iframe {
  display: block;
  width: 100%;
  height: min(70vh, 640px);
  border: 0;
  background: radial-gradient(circle at center, #172b42, #07111d 70%);
}
</style>
