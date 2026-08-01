const STORAGE_KEY = 'auralive_admin_locale'

/** Apply dashboard document direction + language (ar = RTL). */
export function applyDashboardLocale(locale) {
  const normalized = String(locale || 'ar').toLowerCase().startsWith('en') ? 'en' : 'ar'
  const root = document.documentElement
  root.lang = normalized
  root.dir = normalized === 'ar' ? 'rtl' : 'ltr'
  try {
    localStorage.setItem(STORAGE_KEY, normalized)
  } catch {
    /* private mode */
  }
  return normalized
}

export function readDashboardLocale() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) return saved
  } catch {
    /* ignore */
  }
  return 'ar'
}
