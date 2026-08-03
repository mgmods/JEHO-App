<template>
  <label class="bulk-check" @click.stop>
    <input
      type="checkbox"
      class="form-check-input m-0"
      :checked="checked"
      @change="$emit('toggle')"
    />
  </label>
</template>

<script setup>
defineProps({
  checked: { type: Boolean, default: false },
})
defineEmits(['toggle'])
</script>

<style scoped>
.bulk-check {
  position: absolute;
  top: 0.55rem;
  inset-inline-start: 0.55rem;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.55rem;
  height: 1.55rem;
  border-radius: 0.4rem;
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.25);
  cursor: pointer;
}
.bulk-check .form-check-input {
  width: 1.05rem;
  height: 1.05rem;
  cursor: pointer;
}
</style>
