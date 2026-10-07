const API_URL = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '')
const STORAGE_KEY = 'racehorse.auth'

export function getAuth() {
  try { return JSON.parse(sessionStorage.getItem(STORAGE_KEY)) } catch { return null }
}

export function saveAuth(auth) { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(auth)) }
export function clearAuth() { sessionStorage.removeItem(STORAGE_KEY) }

async function parseResponse(response) {
  if (response.status === 204) return null
  const type = response.headers.get('content-type') || ''
  const body = type.includes('application/json') ? await response.json() : await response.text()
  if (!response.ok) {
    const error = new Error(body?.message || body || `Yêu cầu thất bại (${response.status})`)
    error.status = response.status
    error.validationErrors = body?.validationErrors
    throw error
  }
  return body
}

async function refreshAccessToken() {
  const auth = getAuth()
  if (!auth?.refreshToken) throw new Error('Phiên đăng nhập đã hết hạn')
  const response = await fetch(`${API_URL}/api/auth/refresh`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: auth.refreshToken }),
  })
  const refreshed = await parseResponse(response)
  saveAuth(refreshed)
  return refreshed.accessToken
}

export async function api(path, options = {}, retry = true) {
  const auth = getAuth()
  const headers = new Headers(options.headers || {})
  if (auth?.accessToken) headers.set('Authorization', `Bearer ${auth.accessToken}`)
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const response = await fetch(`${API_URL}${path}`, { ...options, headers, cache: 'no-store' })
  if (response.status === 401 && retry && auth?.refreshToken && path !== '/api/auth/refresh') {
    try {
      const token = await refreshAccessToken()
      headers.set('Authorization', `Bearer ${token}`)
      return parseResponse(await fetch(`${API_URL}${path}`, { ...options, headers, cache: 'no-store' }))
    } catch (error) { clearAuth(); throw error }
  }
  return parseResponse(response)
}

export const authApi = {
  login: (payload) => api('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) }, false),
  register: (payload) => api('/api/auth/register', { method: 'POST', body: JSON.stringify(payload) }, false),
  roles: () => api('/api/auth/register-roles', {}, false),
  me: () => api('/api/auth/me'),
  logout: async () => {
    const auth = getAuth()
    try {
      if (auth?.refreshToken) await api('/api/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken: auth.refreshToken }) }, false)
    } finally { clearAuth() }
  },
}

export const pageContent = (page) => Array.isArray(page) ? page : (page?.content || [])

export const stableCareApi = {
  // FR13 Stables & Routines
  getStables: (includeInactive = false) => api(`/api/stables?includeInactive=${includeInactive}`),
  getStable: (id) => api(`/api/stables/${id}`),
  getRoutines: (horseId) => api(`/api/stables/horses/${horseId}/daily-routines`),
  addRoutine: (horseId, payload) => api(`/api/stables/horses/${horseId}/daily-routines`, { method: 'POST', body: JSON.stringify(payload) }),
  updateRoutine: (id, payload) => api(`/api/stables/daily-routines/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deleteRoutine: (id) => api(`/api/stables/daily-routines/${id}`, { method: 'DELETE' }),

  // FR14 Nutrition
  getActiveNutritionPlan: (horseId, date) => api(`/api/horses/${horseId}/nutrition-plan${date ? `?date=${date}` : ''}`),
  getNutritionHistory: (horseId) => api(`/api/horses/${horseId}/nutrition-plan/history`),
  getDailyFeedingChecklist: (horseId, date) => api(`/api/horses/${horseId}/nutrition-plan/daily-checklist${date ? `?date=${date}` : ''}`),
  getStandardPlans: () => api('/api/nutrition/standard-plans'),
  getHorsesActivePlans: (date) => api(`/api/nutrition/horses-active${date ? `?date=${date}` : ''}`),
  getStablesWorkload: (date) => api(`/api/nutrition/stables-workload${date ? `?date=${date}` : ''}`),
  createNutritionPlan: (horseId, payload) => api(`/api/horses/${horseId}/nutrition-plan`, { method: 'POST', body: JSON.stringify(payload) }),
  deleteNutritionPlan: (horseId) => api(`/api/horses/${horseId}/nutrition-plan`, { method: 'DELETE' }),

  // FR15 Care Tasks
  getCareTasks: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v) })
    return api(`/api/care-tasks?${q}`)
  },
  getCareTask: (id) => api(`/api/care-tasks/${id}`),
  createCareTask: (payload) => api('/api/care-tasks', { method: 'POST', body: JSON.stringify(payload) }),
  completeCareTask: (id, payload) => api(`/api/care-tasks/${id}/complete`, { method: 'POST', body: payload instanceof FormData ? payload : JSON.stringify(payload || {}) }),
  cancelCareTask: (id) => api(`/api/care-tasks/${id}/cancel`, { method: 'POST' }),

  // FR16 Incidents
  createIncident: (payload) => api('/api/incidents', { method: 'POST', body: JSON.stringify(payload) }),
  getIncidents: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v) })
    return api(`/api/incidents?${q}`)
  },
  getIncident: (id) => api(`/api/incidents/${id}`),
  updateIncidentStatus: (id, payload) => api(`/api/incidents/${id}/status`, { method: 'PUT', body: JSON.stringify(payload) }),

  // FR17 Inventory & Replenishment
  getInventoryItems: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v) })
    return api(`/api/inventory/items?${q}`)
  },
  getInventoryItem: (id) => api(`/api/inventory/items/${id}`),
  createInventoryItem: (payload) => api('/api/inventory/items', { method: 'POST', body: JSON.stringify(payload) }),
  updateInventoryItem: (id, payload) => api(`/api/inventory/items/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deleteInventoryItem: (id) => api(`/api/inventory/items/${id}`, { method: 'DELETE' }),
  updateStock: (id, payload) => api(`/api/inventory/items/${id}/stock`, { method: 'PUT', body: JSON.stringify(payload) }),
  recordUsage: (id, payload) => api(`/api/inventory/items/${id}/usage`, { method: 'POST', body: JSON.stringify(payload) }),
  getUsage: (limit = 20) => api(`/api/inventory/usage?limit=${limit}`),
  getReplenishments: (status) => api(`/api/inventory/replenishments${status ? `?status=${status}` : ''}`),
  getReplenishment: (id) => api(`/api/inventory/replenishments/${id}`),
  createReplenishment: (payload) => api('/api/inventory/replenishments', { method: 'POST', body: JSON.stringify(payload) }),
  reviewReplenishment: (id, payload) => api(`/api/inventory/replenishments/${id}/review`, { method: 'POST', body: JSON.stringify(payload) }),
  receiveReplenishment: (id, payload) => api(`/api/inventory/replenishments/${id}/receive`, { method: 'POST', body: JSON.stringify(payload) }),
}

export const inventoryApi = stableCareApi

export const financeApi = {
  getTransactions: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v) })
    return api(`/api/financial-transactions?${q}`)
  },
  getCount: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v) })
    return api(`/api/financial-transactions/count?${q}`)
  },
  getSummary: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v) })
    return api(`/api/financial-transactions/summary?${q}`)
  },
  createTransaction: (payload) => api('/api/financial-transactions', { method: 'POST', body: JSON.stringify(payload) }),
  exportCsv: async (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v) })
    const auth = getAuth()
    const response = await fetch(`${API_URL}/api/financial-transactions/export?${q}`, {
      headers: auth?.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {}
    })
    if (!response.ok) throw new Error('Không thể tải file CSV')
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `financial-transactions-${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }
}

export const reportingApi = {
  getOwners: () => api('/api/owner-reports/owners'),
  getReports: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v) })
    return api(`/api/owner-reports?${q}`)
  },
  getReport: (id) => api(`/api/owner-reports/${id}`),
  generateReport: (payload) => api('/api/owner-reports', { method: 'POST', body: JSON.stringify(payload) }),
  downloadJson: async (id) => {
    const auth = getAuth()
    const response = await fetch(`${API_URL}/api/owner-reports/${id}/download`, {
      headers: auth?.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {}
    })
    if (!response.ok) throw new Error('Không thể tải file JSON')
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `owner-report-${id}.json`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  },
  downloadCsv: async (id) => {
    const auth = getAuth()
    const response = await fetch(`${API_URL}/api/owner-reports/${id}/export-csv`, {
      headers: auth?.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {}
    })
    if (!response.ok) throw new Error('Không thể tải file CSV')
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `owner-report-${id}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }
}

