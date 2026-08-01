const API_ORIGIN = (import.meta.env.VITE_API_ORIGIN || 'https://api.adnova.bbs.tr').replace(/\/$/, '')

export function resolveAsset(path) {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  if (path.startsWith('/assets/') || path.startsWith('/uploads/')) {
    return `${API_ORIGIN}${path}`
  }
  if (path.startsWith('assets/') || path.startsWith('uploads/')) {
    return `${API_ORIGIN}/${path}`
  }
  if (path.startsWith('/')) return `${API_ORIGIN}${path}`
  return path
}
