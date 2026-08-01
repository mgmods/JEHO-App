<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const toasts = ref([])
let seq = 0

function push(message, type = 'success', ms = 3200) {
  const id = ++seq
  toasts.value.push({ id, message, type })
  setTimeout(() => dismiss(id), ms)
}

function dismiss(id) {
  toasts.value = toasts.value.filter((t) => t.id !== id)
}

function success(message) { push(message, 'success') }
function warning(message) { push(message, 'warning') }
function danger(message) { push(message, 'danger') }
function info(message) { push(message, 'info') }

defineExpose({ success, warning, danger, info, push, dismiss })

const api = { success, warning, danger, info, push }
onMounted(() => {
  window.__jehoToast = api
})
onUnmounted(() => {
  if (window.__jehoToast === api) delete window.__jehoToast
})
</script>

<template>
  <div class="toast-host" aria-live="polite">
    <TransitionGroup name="toast-slide">
      <div
        v-for="t in toasts"
        :key="t.id"
        class="toast-card"
        :class="`toast-${t.type}`"
        @click="dismiss(t.id)"
      >
        <i
          class="bi me-2"
          :class="{
            'bi-check-circle-fill': t.type === 'success',
            'bi-exclamation-triangle-fill': t.type === 'warning',
            'bi-x-circle-fill': t.type === 'danger',
            'bi-info-circle-fill': t.type === 'info',
          }"
        />
        <span>{{ t.message }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>
