<template>
  <div v-if="visible" class="bulk-bar glass sticky-bulk">
    <div class="d-flex flex-wrap align-items-center gap-2 justify-content-between">
      <div class="d-flex align-items-center gap-2">
        <div class="form-check m-0">
          <input
            class="form-check-input"
            type="checkbox"
            :id="checkboxId"
            :checked="allSelected"
            :indeterminate.prop="someSelected"
            @change="$emit('toggle-all')"
          />
          <label class="form-check-label fw-medium" :for="checkboxId">
            {{ t('bulk.selectAll') }}
            <span class="text-muted small ms-1">({{ count }})</span>
          </label>
        </div>
        <span v-if="selectedCount > 0" class="badge bg-aurora">
          {{ t('bulk.selected', { count: selectedCount }) }}
        </span>
      </div>
      <div class="d-flex flex-wrap gap-2">
        <button
          v-for="action in actions"
          :key="action.key"
          type="button"
          class="btn btn-sm"
          :class="action.variant || 'btn-outline-light'"
          :disabled="!selectedCount || busy"
          @click="$emit('action', action.key)"
        >
          <i v-if="action.icon" :class="['bi', action.icon, 'me-1']"></i>
          {{ action.label }}
        </button>
        <button
          v-if="selectedCount > 0"
          type="button"
          class="btn btn-sm btn-ghost"
          :disabled="busy"
          @click="$emit('clear')"
        >
          {{ t('bulk.clear') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  count: { type: Number, default: 0 },
  selectedCount: { type: Number, default: 0 },
  allSelected: { type: Boolean, default: false },
  someSelected: { type: Boolean, default: false },
  actions: { type: Array, default: () => [] },
  busy: { type: Boolean, default: false },
  /** Show bar even when nothing selected (so Select All is always available). */
  alwaysShow: { type: Boolean, default: true },
})

defineEmits(['toggle-all', 'action', 'clear'])

const { t } = useI18n()
const checkboxId = `bulk-all-${Math.random().toString(36).slice(2, 8)}`
const visible = computed(() => props.alwaysShow && props.count > 0)
</script>

<style scoped>
.sticky-bulk {
  position: sticky;
  top: 0.5rem;
  z-index: 20;
  padding: 0.75rem 1rem;
  margin-bottom: 0.75rem;
  border: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(10px);
}
.bg-aurora {
  background: linear-gradient(135deg, #2dd4bf, #38bdf8);
  color: #041018;
}
</style>
