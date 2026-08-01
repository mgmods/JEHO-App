<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  message: { type: String, default: '' },
  label: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  defaultValue: { type: String, default: '' },
  confirmLabel: { type: String, default: '' },
  cancelLabel: { type: String, default: '' },
  required: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])
const { t } = useI18n()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const value = ref('')
const inputEl = ref(null)

watch(
  () => props.modelValue,
  async (v) => {
    if (v) {
      value.value = props.defaultValue ?? ''
      document.body.style.overflow = 'hidden'
      await nextTick()
      inputEl.value?.focus?.()
      inputEl.value?.select?.()
    } else {
      document.body.style.overflow = ''
    }
  },
)

function onConfirm() {
  const text = String(value.value ?? '')
  if (props.required && !text.trim()) return
  emit('confirm', text)
  open.value = false
}

function onCancel() {
  emit('cancel')
  open.value = false
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="confirm-backdrop" @click.self="onCancel">
      <div class="confirm-card glass-strong" role="dialog" aria-modal="true">
        <div class="confirm-icon is-info">
          <i class="bi bi-pencil-square" />
        </div>
        <h3 class="h5 fw-semibold mb-2">{{ title || t('common.confirm') }}</h3>
        <p v-if="message" class="text-muted mb-3">{{ message }}</p>
        <label v-if="label" class="form-label small">{{ label }}</label>
        <input
          ref="inputEl"
          v-model="value"
          class="form-control mb-4"
          :placeholder="placeholder"
          @keyup.enter="onConfirm"
          @keyup.escape="onCancel"
        />
        <div class="d-flex gap-2 justify-content-end">
          <button type="button" class="btn btn-ghost" @click="onCancel">
            {{ cancelLabel || t('common.cancel') }}
          </button>
          <button type="button" class="btn btn-aurora" @click="onConfirm">
            {{ confirmLabel || t('common.confirm') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
