import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'

const TOKEN_KEY = 'auralive_admin_token'
const USER_KEY = 'auralive_admin_user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref(safeParse(localStorage.getItem(USER_KEY)))
  const loading = ref(false)
  const error = ref(null)

  const isAuthenticated = computed(() => Boolean(token.value))
  const displayName = computed(() => user.value?.name || user.value?.email || 'Admin')

  function setSession(nextToken, nextUser) {
    token.value = nextToken || ''
    user.value = nextUser || null
    if (nextToken) localStorage.setItem(TOKEN_KEY, nextToken)
    else localStorage.removeItem(TOKEN_KEY)
    if (nextUser) localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
    else localStorage.removeItem(USER_KEY)
  }

  async function login(credentials) {
    loading.value = true
    error.value = null
    const { data, error: err } = await authApi.login(credentials)
    loading.value = false
    if (err) {
      error.value = err.message
      return false
    }
    const nextToken = data?.token || data?.accessToken || data?.access_token || data?.data?.token
    const nextUser = data?.user || data?.admin || data?.data?.user || { email: credentials.email, name: 'Admin' }
    if (!nextToken) {
      error.value = 'Login succeeded but no token was returned'
      return false
    }
    setSession(nextToken, nextUser)
    return true
  }

  async function fetchMe() {
    if (!token.value) return null
    const { data, error: err } = await authApi.me()
    if (err) return null
    const nextUser = data?.user || data?.admin || data?.data || data
    if (nextUser) {
      user.value = nextUser
      localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
    }
    return nextUser
  }

  function logout() {
    setSession('', null)
    error.value = null
  }

  return {
    token,
    user,
    loading,
    error,
    isAuthenticated,
    displayName,
    login,
    fetchMe,
    logout,
    setSession,
  }
})

function safeParse(value) {
  if (!value) return null
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}
