import { computed, ref, watch } from 'vue'

/**
 * Multi-select for dashboard lists (current page / current array).
 * @param {import('vue').Ref<any[]>} itemsRef
 * @param {(row: any) => string} idOf
 */
export function useBulkSelection(itemsRef, idOf = (row) => row?.id) {
  const selected = ref(new Set())

  const ids = computed(() =>
    (itemsRef.value || [])
      .map((row) => idOf(row))
      .filter((id) => id != null && String(id).length > 0)
      .map(String),
  )

  const selectedIds = computed(() => [...selected.value])
  const selectedCount = computed(() => selected.value.size)
  const allSelected = computed(
    () => ids.value.length > 0 && ids.value.every((id) => selected.value.has(id)),
  )
  const someSelected = computed(
    () => selectedCount.value > 0 && !allSelected.value,
  )

  function isSelected(id) {
    return selected.value.has(String(id))
  }

  function toggle(id) {
    const key = String(id)
    const next = new Set(selected.value)
    if (next.has(key)) next.delete(key)
    else next.add(key)
    selected.value = next
  }

  function selectAll() {
    selected.value = new Set(ids.value)
  }

  function clear() {
    selected.value = new Set()
  }

  function toggleAll() {
    if (allSelected.value) clear()
    else selectAll()
  }

  // Drop selections that left the current page.
  watch(
    ids,
    (list) => {
      const allow = new Set(list)
      const next = new Set([...selected.value].filter((id) => allow.has(id)))
      if (next.size !== selected.value.size) selected.value = next
    },
    { deep: false },
  )

  return {
    selected,
    selectedIds,
    selectedCount,
    allSelected,
    someSelected,
    isSelected,
    toggle,
    selectAll,
    clear,
    toggleAll,
  }
}
