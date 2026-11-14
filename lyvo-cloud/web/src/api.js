const TOKEN_KEY = 'lyvo_cloud_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(t) {
  if (t) localStorage.setItem(TOKEN_KEY, t)
  else localStorage.removeItem(TOKEN_KEY)
}

async function request(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(path, { ...opts, headers })
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = { raw: text }
  }
  if (!res.ok) {
    const err = new Error(data?.error || res.statusText || 'request_failed')
    err.status = res.status
    err.data = data
    throw err
  }
  return data
}

export const api = {
  register: (body) => request('/api/auth/register', { method: 'POST', body: JSON.stringify(body) }),
  login: (body) => request('/api/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  me: () => request('/api/auth/me'),
  dashboard: () => request('/api/dashboard'),
  createProject: (body) => request('/api/projects', { method: 'POST', body: JSON.stringify(body) }),
  revealSecret: (id) => request(`/api/projects/${id}/reveal-secret`),
  deleteProject: (id) => request(`/api/projects/${id}`, { method: 'DELETE' }),
  packages: () => request('/api/packages'),
  buyPackage: (packageId) =>
    request('/api/billing/buy-package', { method: 'POST', body: JSON.stringify({ packageId }) }),
  checkout: (amountUsd) =>
    request('/api/billing/checkout', { method: 'POST', body: JSON.stringify({ amountUsd }) }),
  sellerPackages: () => request('/api/seller/packages'),
  sellerCreatePackage: (body) =>
    request('/api/seller/packages', { method: 'POST', body: JSON.stringify(body) }),
  sellerUpdatePackage: (id, body) =>
    request(`/api/seller/packages/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  sellerDeletePackage: (id) => request(`/api/seller/packages/${id}`, { method: 'DELETE' }),
  sellerSales: () => request('/api/seller/sales'),
  sellerPayments: () => request('/api/seller/payments'),
  sellerSavePayments: (body) =>
    request('/api/seller/payments', { method: 'PATCH', body: JSON.stringify(body) }),
  sellerTestPayments: () =>
    request('/api/seller/payments/test', { method: 'POST', body: '{}' }),
}
