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
  const origin = apiOrigin()

  // Uploaded media is served by this app's API, not by a browser route or a
  // stale host saved by an older dashboard version. Normalize both relative
  // and absolute /uploads URLs so previews survive revisiting the dashboard.
  if (/^(https?:)?\/\//i.test(value)) {
    try {
      const parsed = new URL(value, window.location.origin)
      if (/\/uploads\//i.test(parsed.pathname)) {
        return `${origin}${parsed.pathname}${parsed.search}${parsed.hash}`
      }
    } catch {
      return value
    }
    return value
  }
  if (/^(data|blob):/i.test(value)) return value
  if (value.startsWith('/')) return `${origin}${value}`
  if (/^(assets|uploads)\//i.test(value)) return `${origin}/${value}`
  return value
}
