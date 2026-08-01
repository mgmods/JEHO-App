import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'https://api.adnova.bbs.tr/api/v1',
  timeout: 120000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
})

api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      const auth = useAuthStore()
      auth.logout()
      if (router.currentRoute.value.name !== 'login') {
        router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    }
    return Promise.reject(normalizeError(error))
  },
)

export function normalizeError(error) {
  if (error.isNormalized) return error
  const message =
    error.response?.data?.message ||
    error.response?.data?.error ||
    (Array.isArray(error.response?.data?.errors) ? error.response.data.errors.join(', ') : null) ||
    (error.code === 'ERR_NETWORK' ? 'Unable to reach API server. Is it running on localhost:3000?' : null) ||
    error.message ||
    'Request failed'

  return {
    isNormalized: true,
    status: error.response?.status || 0,
    message: typeof message === 'string' ? message : JSON.stringify(message),
    data: error.response?.data || null,
    original: error,
  }
}

export async function safeRequest(fn) {
  try {
    const res = await fn()
    let data = res.data
    // NestJS TransformInterceptor: { success, data, timestamp }
    if (data && typeof data === 'object' && data.success === true && 'data' in data) {
      data = data.data
    }
    return { data, error: null }
  } catch (error) {
    return { data: null, error: normalizeError(error) }
  }
}

export default api
