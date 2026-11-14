const configuredApi = import.meta.env.VITE_API_URL || ''
const configuredOrigin = import.meta.env.VITE_API_ORIGIN || ''

function apiOrigin() {
  const currentOrigin = window.location.origin.replace(/\/$/, '')
  const isLegacyHost = (hostname) => String(hostname || '').toLowerCase() === 'api.adnova.bbs.tr'

  const explicit = String(configuredOrigin || '').trim()
  if (explicit) {
    try {
      const parsed = new URL(explicit, currentOrigin)
      if (!isLegacyHost(parsed.hostname)) return parsed.origin
    } catch {
      // Ignore malformed/legacy overrides and use the active dashboard origin.
    }
  }

  const api = String(configuredApi || '').trim()
  if (api) {
    try {
      const parsed = new URL(api, currentOrigin)
      if (!isLegacyHost(parsed.hostname)) return parsed.origin
    } catch {
      // Fall through to the dashboard's own origin when the API URL is malformed.
    }
  }

  // JEHO-OWN/Blitz serves the dashboard and upload route from the same origin.
  // Never send saved uploads to the retired api.adnova.bbs.tr host.
  return currentOrigin
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
      const normalizedPath = parsed.pathname.replace(/^\/api\/v\d+(?=\/uploads\/)/i, '')
      if (/^\/uploads\//i.test(normalizedPath)) {
        return `${origin}${normalizedPath}${parsed.search}${parsed.hash}`
      }
    } catch {
      return value
    }
    return value
  }
  if (/^(data|blob):/i.test(value)) return value
  const normalizedValue = value.replace(/^\/?api\/v\d+(?=\/uploads\/)/i, '')
  if (normalizedValue.startsWith('/')) return `${origin}${normalizedValue}`
  if (/^(assets|uploads)\//i.test(normalizedValue)) return `${origin}/${normalizedValue}`
  return value
}
