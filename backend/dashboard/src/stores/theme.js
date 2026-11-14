import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const THEME_KEY = 'auralive_admin_theme'

export const useThemeStore = defineStore('theme', () => {
  // Admin UI is dark-only to match the design reference
  const mode = ref('dark')

  const isDark = computed(() => true)

  function apply() {
    document.documentElement.setAttribute('data-theme', 'dark')
    document.documentElement.setAttribute('data-bs-theme', 'dark')
    document.documentElement.style.background = '#0f111a'
    document.documentElement.style.color = '#f4f6fb'
    document.body && (document.body.style.background = '#0f111a')
    document.body && (document.body.style.color = '#f4f6fb')
  }

  function init() {
    mode.value = 'dark'
    try {
      localStorage.setItem(THEME_KEY, 'dark')
    } catch {
      /* ignore */
    }
    apply()
  }

  function toggle() {
    // Dark-only shell — ignore light switch so pages never flash white
    init()
  }

  function setMode() {
    init()
  }

  return { mode, isDark, init, toggle, setMode }
})
