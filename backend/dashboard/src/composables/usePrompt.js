import { reactive } from 'vue'

const state = reactive({
  open: false,
  title: '',
  message: '',
  label: '',
  placeholder: '',
  defaultValue: '',
  required: false,
  _resolve: null,
})

/** Professional prompt replacing window.prompt. Returns string or null if cancelled. */
export function askPrompt(options = {}) {
  return new Promise((resolve) => {
    if (state._resolve) {
      state._resolve(null)
      state._resolve = null
    }
    state.title = options.title || ''
    state.message = options.message || ''
    state.label = options.label || ''
    state.placeholder = options.placeholder || ''
    state.defaultValue = options.defaultValue != null ? String(options.defaultValue) : ''
    state.required = !!options.required
    state._resolve = resolve
    state.open = true
  })
}

export function usePromptHost() {
  function confirm(value) {
    const resolve = state._resolve
    state._resolve = null
    state.open = false
    resolve?.(value)
  }
  function cancel() {
    const resolve = state._resolve
    state._resolve = null
    state.open = false
    resolve?.(null)
  }
  return { state, confirm, cancel }
}

export function promptApi() {
  return { ask: askPrompt }
}
