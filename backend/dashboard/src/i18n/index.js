import { createI18n } from 'vue-i18n'
import { applyDashboardLocale, readDashboardLocale } from '@/utils/locale'
import en from './locales/en.json'
import ar from './locales/ar.json'

const initial = applyDashboardLocale(readDashboardLocale())

export const i18n = createI18n({
  legacy: false,
  locale: initial,
  fallbackLocale: 'en',
  messages: { en, ar },
  globalInjection: true,
})

export function setDashboardLocale(locale) {
  const normalized = applyDashboardLocale(locale)
  i18n.global.locale.value = normalized
  return normalized
}

export default i18n
