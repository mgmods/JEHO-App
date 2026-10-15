const configuredApi = import.meta.env.VITE_API_URL || ''
const configuredOrigin = import.meta.env.VITE_API_ORIGIN || ''

function apiOrigin() {
  const explicit = String(configuredOrigin || '').trim()
  if (explicit) return explicit.replace(/\/$/, '')
  const api = String(configuredApi || '').trim()
  if (api) {
    try {
      const parsed = new URL(api, window.location.origin)
      return parsed.origin
    } catch {
      // Fall through to the dashboard's own origin when the API URL is malformed.
    }
  }
  // The admin dashboard is served by the same backend in JEHO-OWN/Blitz.
  // Never silently send uploaded media to the old api.adnova.bbs.tr host.
  return window.location.origin.replace(/\/$/, '')
}

export function resolveAsset(path) {
  if (!path) return ''
  const value = String(path).trim()
  if (!value) return ''
  if (/^(https?:)?\/\//i.test(value) || /^(data|blob):/i.test(value)) return value
  const origin = apiOrigin()
  if (value.startsWith('/')) return `${origin}${value}`
  if (/^(assets|uploads)\//i.test(value)) return `${origin}/${value}`
  return value
}
