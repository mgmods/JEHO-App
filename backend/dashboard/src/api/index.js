import api, { safeRequest } from './client'

export const authApi = {
  login: (payload) => safeRequest(() => api.post('/admin/auth/login', payload)),
  me: () => safeRequest(() => api.get('/admin/auth/me')),
}

export const dashboardApi = {
  overview: () => safeRequest(() => api.get('/admin/dashboard/overview')),
  charts: (params) => safeRequest(() => api.get('/admin/dashboard/charts', { params })),
  liveStats: () => safeRequest(() => api.get('/admin/dashboard/live')),
}

export const usersApi = {
  list: (params) => safeRequest(() => api.get('/admin/users', { params })),
  get: (id) => safeRequest(() => api.get(`/admin/users/${id}`)),
  update: (id, data) => safeRequest(() => api.patch(`/admin/users/${id}`, data)),
  ban: (id, data = {}) => safeRequest(() => api.post(`/admin/users/${id}/ban`, data)),
  unban: (id) => safeRequest(() => api.post(`/admin/users/${id}/unban`)),
  delete: (id) => safeRequest(() => api.delete(`/admin/users/${id}`)),
  resetBaseline: (id) => safeRequest(() => api.post(`/admin/users/${id}/reset-baseline`)),
  resetAllBaseline: () =>
    safeRequest(() => api.post('/admin/users/maintenance/reset-all-baseline')),
}

export const roomsApi = {
  list: (params) => safeRequest(() => api.get('/admin/rooms', { params })),
  get: (id) => safeRequest(() => api.get(`/admin/rooms/${id}`)),
  update: (id, data) => safeRequest(() => api.patch(`/admin/rooms/${id}`, data)),
  close: (id) => safeRequest(() => api.post(`/admin/rooms/${id}/close`)),
  delete: (id, opts = {}) =>
    safeRequest(() =>
      api.delete(`/admin/rooms/${id}`, {
        params: opts.force ? { force: '1' } : undefined,
      }),
    ),
}

export const streamsApi = {
  list: (params) => safeRequest(() => api.get('/admin/streams', { params })),
  get: (id) => safeRequest(() => api.get(`/admin/streams/${id}`)),
  end: (id) => safeRequest(() => api.post(`/admin/streams/${id}/end`)),
  forceEnd: (id, data = {}) => safeRequest(() => api.post(`/admin/streams/${id}/force-end`, data)),
}

export const giftsApi = {
  list: (params) => safeRequest(() => api.get('/admin/gifts', { params })),
  get: (id) => safeRequest(() => api.get(`/admin/gifts/${id}`)),
  create: (data) => safeRequest(() => api.post('/admin/gifts', data)),
  update: (id, data) => safeRequest(() => api.patch(`/admin/gifts/${id}`, data)),
  delete: (id) => safeRequest(() => api.delete(`/admin/gifts/${id}`)),
}

export const walletApi = {
  transactions: (params) => safeRequest(() => api.get('/admin/wallet/transactions', { params })),
  withdraws: (params) => safeRequest(() => api.get('/admin/wallet/withdraws', { params })),
  approveWithdraw: (id) => safeRequest(() => api.post(`/admin/wallet/withdraws/${id}/approve`)),
  rejectWithdraw: (id, data = {}) => safeRequest(() => api.post(`/admin/wallet/withdraws/${id}/reject`, data)),
  recharges: (params) => safeRequest(() => api.get('/admin/wallet/recharges', { params })),
  completeRecharge: (id, data = {}) =>
    safeRequest(() => api.post(`/admin/wallet/recharges/${id}/complete`, data)),
  cancelRecharge: (id) => safeRequest(() => api.post(`/admin/wallet/recharges/${id}/cancel`)),
  packages: () => safeRequest(() => api.get('/admin/wallet/packages')),
  savePackages: (data) => safeRequest(() => api.put('/admin/wallet/packages', data)),
  withdrawPackages: () => safeRequest(() => api.get('/admin/wallet/withdraw-packages')),
  saveWithdrawPackages: (data) => safeRequest(() => api.put('/admin/wallet/withdraw-packages', data)),
  adjust: (data) => safeRequest(() => api.post('/admin/wallet/adjust', data)),
  adjustUser: (userId, data) => safeRequest(() => api.post(`/admin/wallet/${userId}/adjust`, data)),
}

export const vipApi = {
  list: (params) => safeRequest(() => api.get('/admin/vip', { params })),
  plans: () => safeRequest(() => api.get('/admin/vip/plans')),
  createPlan: (data) => safeRequest(() => api.post('/admin/vip/plans', data)),
  updatePlan: (id, data) => safeRequest(() => api.patch(`/admin/vip/plans/${id}`, data)),
  deletePlan: (id) => safeRequest(() => api.delete(`/admin/vip/plans/${id}`)),
  assign: (data) => safeRequest(() => api.post('/admin/vip/assign', data)),
  revoke: (id) => safeRequest(() => api.post(`/admin/vip/${id}/revoke`)),
}

export const agenciesApi = {
  list: (params) => safeRequest(() => api.get('/admin/agencies', { params })),
  get: (id) => safeRequest(() => api.get(`/admin/agencies/${id}`)),
  create: (data) => safeRequest(() => api.post('/admin/agencies', data)),
  update: (id, data) => safeRequest(() => api.patch(`/admin/agencies/${id}`, data)),
  approve: (id) => safeRequest(() => api.post(`/admin/agencies/${id}/approve`)),
  suspend: (id, data = {}) => safeRequest(() => api.post(`/admin/agencies/${id}/suspend`, data)),
  removeMember: (id, userId) => safeRequest(() => api.delete(`/admin/agencies/${id}/members/${userId}`)),
  delete: (id) => safeRequest(() => api.delete(`/admin/agencies/${id}`)),
  applications: (params) => safeRequest(() => api.get('/admin/agencies/applications', { params })),
  approveApplication: (id, data = {}) =>
    safeRequest(() => api.post(`/admin/agencies/applications/${id}/approve`, data)),
  rejectApplication: (id, data = {}) =>
    safeRequest(() => api.post(`/admin/agencies/applications/${id}/reject`, data)),
  regenerateActivationCode: (id) =>
    safeRequest(() => api.post(`/admin/agencies/${id}/activation-code`)),
}

export const rechargeAgentsApi = {
  applications: () => safeRequest(() => api.get('/admin/recharge-agents/applications')),
  approve: (id, data = {}) => safeRequest(() => api.post(`/admin/recharge-agents/applications/${id}/approve`, data)),
  reject: (id, data = {}) => safeRequest(() => api.post(`/admin/recharge-agents/applications/${id}/reject`, data)),
  agents: () => safeRequest(() => api.get('/admin/recharge-agents')),
  assign: (data) => safeRequest(() => api.post('/admin/recharge-agents/assign', data)),
  update: (id, data) => safeRequest(() => api.patch(`/admin/recharge-agents/${id}`, data)),
  adjustFloat: (id, data) => safeRequest(() => api.post(`/admin/recharge-agents/${id}/float`, data)),
  remove: (id) => safeRequest(() => api.delete(`/admin/recharge-agents/${id}`)),
  pricing: () => safeRequest(() => api.get('/admin/recharge-agent-pricing')),
  updatePricing: (data) => safeRequest(() => api.patch('/admin/recharge-agent-pricing', data)),
  contacts: () => safeRequest(() => api.get('/admin/recharge-agent-contacts')),
  upsertContact: (data) => safeRequest(() => api.post('/admin/recharge-agent-contacts', data)),
  removeContact: (id) => safeRequest(() => api.delete(`/admin/recharge-agent-contacts/${id}`)),
}

export const reportsApi = {
  list: (params) => safeRequest(() => api.get('/admin/reports', { params })),
  get: (id) => safeRequest(() => api.get(`/admin/reports/${id}`)),
  resolve: (id, data = {}) => safeRequest(() => api.post(`/admin/reports/${id}/resolve`, data)),
  dismiss: (id, data = {}) => safeRequest(() => api.post(`/admin/reports/${id}/dismiss`, data)),
}

export const genderVerificationsApi = {
  list: (params) => safeRequest(() => api.get('/admin/gender-verifications', { params })),
  approve: (id, data = {}) =>
    safeRequest(() => api.post(`/admin/gender-verifications/${id}/approve`, data)),
  reject: (id, data = {}) =>
    safeRequest(() => api.post(`/admin/gender-verifications/${id}/reject`, data)),
}

export const notificationsApi = {
  list: (params) => safeRequest(() => api.get('/admin/notifications', { params })),
  send: (data) => safeRequest(() => api.post('/admin/notifications/send', data)),
}

export const settingsApi = {
  get: () => safeRequest(() => api.get('/admin/settings')),
  update: (data) => safeRequest(() => api.patch('/admin/settings', data)),
}

export const dramaApi = {
  settings: () => safeRequest(() => api.get('/admin/drama/settings')),
  patchSettings: (data) => safeRequest(() => api.patch('/admin/drama/settings', data)),
  series: () => safeRequest(() => api.get('/admin/drama/series')),
  seriesGet: (id) => safeRequest(() => api.get(`/admin/drama/series/${id}`)),
}

export const gameAdsApi = {
  settings: () => safeRequest(() => api.get('/admin/games/ads')),
  patchSettings: (data) => safeRequest(() => api.patch('/admin/games/ads', data)),
}

export const paymentSettingsApi = {
  getBinancePay: () => safeRequest(() => api.get('/admin/payment-settings/binance-pay')),
  revealBinancePay: () => safeRequest(() => api.post('/admin/payment-settings/binance-pay/reveal')),
  updateBinancePay: (data) => safeRequest(() => api.patch('/admin/payment-settings/binance-pay', data)),
  testBinancePay: () => safeRequest(() => api.post('/admin/payment-settings/binance-pay/test')),
  reconcileBinanceWallet: () => safeRequest(() => api.post('/admin/payment-settings/binance-pay/reconcile')),
  getFourthwall: () => safeRequest(() => api.get('/admin/payment-settings/fourthwall')),
  updateFourthwall: (data) => safeRequest(() => api.patch('/admin/payment-settings/fourthwall', data)),
  testFourthwall: () => safeRequest(() => api.post('/admin/payment-settings/fourthwall/test')),
  syncFourthwallPackages: () =>
    safeRequest(() => api.post('/admin/payment-settings/fourthwall/sync-packages')),
  getShamCash: () => safeRequest(() => api.get('/admin/payment-settings/sham-cash')),
  updateShamCash: (data) => safeRequest(() => api.patch('/admin/payment-settings/sham-cash', data)),
}

export const luckyBoxesApi = {
  adminList: () => safeRequest(() => api.get('/lucky-boxes/admin/boxes')),
  adminUpsert: (data) => safeRequest(() => api.post('/lucky-boxes/admin/boxes', data)),
  adminDelete: (id) => safeRequest(() => api.delete(`/lucky-boxes/admin/boxes/${id}`)),
  adminOpens: (params) => safeRequest(() => api.get('/lucky-boxes/admin/opens', { params })),
}

export const roomCupApi = {
  leaderboard: () => safeRequest(() => api.get('/room-cup/leaderboard')),
  endSeason: () => safeRequest(() => api.post('/room-cup/admin/end-season')),
}

export const logsApi = {
  list: (params) => safeRequest(() => api.get('/admin/logs', { params })),
}

export const cosmeticsApi = {
  catalog: (params) => safeRequest(() => api.get('/cosmetics', { params })),
  adminList: (params) => safeRequest(() => api.get('/admin/cosmetics', { params })),
  create: (data) => safeRequest(() => api.post('/admin/cosmetics', data)),
  update: (id, data) => safeRequest(() => api.patch(`/admin/cosmetics/${id}`, data)),
  remove: (id) => safeRequest(() => api.delete(`/admin/cosmetics/${id}`)),
}

export const uploadsApi = {
  upload: (file) =>
    safeRequest(() => {
      const fd = new FormData()
      fd.append('file', file)
      return api.post('/uploads', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    }),
  remove: (filename) =>
    safeRequest(() => api.delete(`/uploads/${encodeURIComponent(filename)}`)),
}

export const zegoSettingsApi = {
  get: () => safeRequest(() => api.get('/admin/zego-settings')),
  reveal: () => safeRequest(() => api.post('/admin/zego-settings/reveal')),
  update: (data) => safeRequest(() => api.patch('/admin/zego-settings', data)),
}

export const contestsApi = {
  list: () => safeRequest(() => api.get('/admin/contests')),
  get: (id) => safeRequest(() => api.get(`/admin/contests/${id}`)),
  create: (data) => safeRequest(() => api.post('/admin/contests', data)),
  update: (id, data) => safeRequest(() => api.patch(`/admin/contests/${id}`, data)),
  end: (id) => safeRequest(() => api.post(`/admin/contests/${id}/end`)),
  endAll: () => safeRequest(() => api.post('/admin/contests/end-all')),
  remove: (id) => safeRequest(() => api.delete(`/admin/contests/${id}`)),
}

export const gameStoreApi = {
  catalog: (params) => safeRequest(() => api.get('/admin/games-store/catalog', { params })),
  saveCatalog: (items) => safeRequest(() => api.put('/admin/games-store/catalog', { items })),
}

export const tasksApi = {
  list: () => safeRequest(() => api.get('/admin/tasks')),
  save: (items) => safeRequest(() => api.put('/admin/tasks', { items })),
}

export const rankingApi = {
  board: (period, category, params) =>
    safeRequest(() => api.get(`/admin/ranking/${period}/${category}`, { params })),
}

