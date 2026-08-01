import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const THEME_KEY = 'auralive_admin_theme'

export const useThemeStore = defineStore('theme', () => {
  const mode = ref('dark')

  const isDark = computed(() => mode.value === 'dark')

  function apply() {
    document.documentElement.setAttribute('data-theme', mode.value)
    document.documentElement.setAttribute('data-bs-theme', mode.value === 'dark' ? 'dark' : 'light')
  }

  function init() {
    const saved = localStorage.getItem(THEME_KEY)
    if (saved === 'light' || saved === 'dark') mode.value = saved
    else if (window.matchMedia('(prefers-color-scheme: light)').matches) mode.value = 'light'
    apply()
  }

  function toggle() {
    mode.value = mode.value === 'dark' ? 'light' : 'dark'
    localStorage.setItem(THEME_KEY, mode.value)
    apply()
  }

  function setMode(next) {
    mode.value = next === 'light' ? 'light' : 'dark'
    localStorage.setItem(THEME_KEY, mode.value)
    apply()
  }

  return { mode, isDark, init, toggle, setMode }
})
