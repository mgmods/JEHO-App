<template>
  <span class="badge badge-soft" :class="cls">{{ label }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  status: { type: String, default: '' },
})

const { t, te } = useI18n()

const map = {
  active: 'badge-soft-success',
  online: 'badge-soft-success',
  approved: 'badge-soft-success',
  completed: 'badge-soft-success',
  resolved: 'badge-soft-success',
  pending: 'badge-soft-warning',
  processing: 'badge-soft-warning',
  reviewing: 'badge-soft-warning',
  banned: 'badge-soft-danger',
  rejected: 'badge-soft-danger',
  suspended: 'badge-soft-danger',
  closed: 'badge-soft-muted',
  ended: 'badge-soft-muted',
  inactive: 'badge-soft-muted',
  dismissed: 'badge-soft-muted',
}

const label = computed(() => {
  const key = String(props.status || '').toLowerCase()
  if (te(`common.${key}`)) return t(`common.${key}`)
  if (te(`commonStatus.${key}`)) return t(`commonStatus.${key}`)
  return props.status || '—'
})

const cls = computed(() => {
  const key = String(props.status || '').toLowerCase()
  return map[key] || 'badge-soft-info'
})
</script>
