export function extractList(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  // nested interceptor leftover
  if (data.data && !Array.isArray(data.items)) {
    const nested = extractList(data.data)
    if (nested.length) return nested
  }
  if (Array.isArray(data.items)) return data.items
  if (Array.isArray(data.data)) return data.data
  if (Array.isArray(data.results)) return data.results
  if (Array.isArray(data.users)) return data.users
  if (Array.isArray(data.rooms)) return data.rooms
  if (Array.isArray(data.streams)) return data.streams
  if (Array.isArray(data.gifts)) return data.gifts
  if (Array.isArray(data.transactions)) return data.transactions
  if (Array.isArray(data.withdraws)) return data.withdraws
  if (Array.isArray(data.recharges)) return data.recharges
  if (Array.isArray(data.agencies)) return data.agencies
  if (Array.isArray(data.reports)) return data.reports
  if (Array.isArray(data.logs)) return data.logs
  if (Array.isArray(data.plans)) return data.plans
  if (Array.isArray(data.notifications)) return data.notifications
  return []
}

export function formatNumber(n) {
  if (n == null || Number.isNaN(Number(n))) return '—'
  return new Intl.NumberFormat('en-US').format(Number(n))
}

export function formatMoney(n, currency = 'USD') {
  if (n == null || Number.isNaN(Number(n))) return '—'
  const amount = Number(n)
  const code = String(currency || 'USD').toUpperCase()
  // ISO-4217 only — crypto tickers like USDT crash Intl and blank the wallet orders tab.
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: code,
      maximumFractionDigits: 2,
    }).format(amount)
  } catch {
    return `${amount.toFixed(2)} ${code}`
  }
}

export function formatDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString()
}
