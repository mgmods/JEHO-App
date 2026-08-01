<template>
  <RouterView />
  <ToastHost />
  <PromptDialog
    v-model="prompt.state.open"
    :title="prompt.state.title"
    :message="prompt.state.message"
    :label="prompt.state.label"
    :placeholder="prompt.state.placeholder"
    :default-value="prompt.state.defaultValue"
    :required="prompt.state.required"
    @confirm="prompt.confirm"
    @cancel="prompt.cancel"
  />
</template>

<script setup>
import { onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'
import ToastHost from '@/components/ToastHost.vue'
import PromptDialog from '@/components/PromptDialog.vue'
import { usePromptHost } from '@/composables/usePrompt'

const theme = useThemeStore()
const prompt = usePromptHost()

onMounted(() => {
  theme.init()
})
</script>
