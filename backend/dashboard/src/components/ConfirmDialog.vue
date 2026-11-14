<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  message: { type: String, default: '' },
  confirmLabel: { type: String, default: '' },
  cancelLabel: { type: String, default: '' },
  danger: { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])
const { t } = useI18n()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const titleText = computed(() => props.title || t('common.confirm'))
const confirmText = computed(() => props.confirmLabel || t('common.confirm'))
const cancelText = computed(() => props.cancelLabel || t('common.cancel'))

function onConfirm() {
  emit('confirm')
  open.value = false
}
function onCancel() {
  emit('cancel')
  open.value = false
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) document.body.style.overflow = 'hidden'
    else document.body.style.overflow = ''
  },
)
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="confirm-backdrop" @click.self="onCancel">
      <div class="confirm-card glass-strong" role="dialog" aria-modal="true">
        <div class="confirm-icon" :class="danger ? 'is-danger' : 'is-info'">
          <i class="bi" :class="danger ? 'bi-trash3' : 'bi-question-lg'" />
        </div>
        <h3 class="h5 fw-semibold mb-2">{{ titleText }}</h3>
        <p class="text-muted mb-4">{{ message }}</p>
        <div class="d-flex gap-2 justify-content-end">
          <button type="button" class="btn btn-ghost" @click="onCancel">{{ cancelText }}</button>
          <button
            type="button"
            class="btn"
            :class="danger ? 'btn-outline-danger' : 'btn-aurora'"
            @click="onConfirm"
          >
            {{ confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
