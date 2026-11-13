const CLOUD_API_URL = 'https://horse-project-4qa4.onrender.com'
const LOCAL_API_URL = 'http://127.0.0.1:8080'

export function canConfigureApiUrl() {
  if (typeof window === 'undefined') return false
  return window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
}

export function getApiUrl() {
  const envUrl = (import.meta.env.VITE_API_URL || '').trim().replace(/\/$/, '')

  // A deployed frontend must never inherit a stale localhost/custom URL from
  // this browser. VITE_API_URL remains the explicit production override.
  if (typeof window !== 'undefined' && !canConfigureApiUrl()) {
    return envUrl || CLOUD_API_URL
  }

  if (canConfigureApiUrl()) {
    const custom = (localStorage.getItem('racehorse.api_url') || '').trim()
    if (custom) return custom.replace(/\/$/, '')
  }
  if (envUrl) return envUrl
  return LOCAL_API_URL
}

export function setCustomApiUrl(url) {
  if (typeof window !== 'undefined') {
    if (!canConfigureApiUrl()) {
      localStorage.removeItem('racehorse.api_url')
      return
    }
    const trimmed = (url || '').trim().replace(/\/$/, '')
    if (trimmed) {
      localStorage.setItem('racehorse.api_url', trimmed)
    } else {
      localStorage.removeItem('racehorse.api_url')
    }
  }
}

const STORAGE_KEY = 'racehorse.auth'

export const localApiUrl = getApiUrl()

export async function apiFetch(path, options = {}) {
  const baseUrl = getApiUrl()
  const isLoopback = baseUrl.includes('127.0.0.1') || baseUrl.includes('localhost')
  try {
    return await fetch(`${baseUrl}${path}`, {
      cache: 'no-store',
      ...options,
      ...(isLoopback ? { targetAddressSpace: 'loopback' } : {}),
    })
  } catch (error) {
    if (error instanceof TypeError) {
      if (isLoopback) {
        throw new Error(`Không kết nối được backend cục bộ tại ${baseUrl}. Hãy chạy backend tại 127.0.0.1:8080 hoặc cấu hình URL máy chủ Render.`)
      }
      throw new Error(`Không thể kết nối đến máy chủ backend tại ${baseUrl}. Vui lòng kiểm tra lại dịch vụ Render (có thể đang khởi động sau thời gian sleep) hoặc kiểm tra mạng.`)
    }
    throw error
  }
}

export async function checkLocalBackend() {
  let response
  try {
    const timeoutMs = getApiUrl().endsWith('.onrender.com') ? 75000 : 15000
    response = await apiFetch('/actuator/health', { signal: AbortSignal.timeout(timeoutMs) })
  } catch (error) {
    if (error.name === 'TimeoutError' || error.name === 'AbortError') {
      throw new Error('Hết thời gian kiểm tra. Render gói miễn phí có thể cần 30-50s để khởi động lại, vui lòng thử lại sau.')
    }
    throw error
  }
  const contentType = response.headers.get('content-type') || ''
  if (!response.ok || !contentType.includes('json')) throw new Error('Địa chỉ API chưa trả về trạng thái backend hợp lệ.')
  const health = await response.json()
  if (health.status !== 'UP') throw new Error('Backend đang chạy nhưng chưa sẵn sàng. Hãy kiểm tra kết nối cơ sở dữ liệu.')
  return health
}

export function getAuth() {
  try { return JSON.parse(sessionStorage.getItem(STORAGE_KEY)) } catch { return null }
}

export function saveAuth(auth) { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(auth)) }
export function clearAuth() {
  sessionStorage.removeItem(STORAGE_KEY)
  if (typeof window !== 'undefined') {
    try {
      for (let i = sessionStorage.length - 1; i >= 0; i--) {
        const key = sessionStorage.key(i)
        if (key && key.startsWith('racehorse.dismissed_expiry_warning_')) {
          sessionStorage.removeItem(key)
        }
      }
    } catch {
      // ignore storage errors
    }
  }
}

async function parseResponse(response) {
  if (response.status === 204) return null
  const type = response.headers.get('content-type') || ''
  const body = type.includes('application/json') ? await response.json() : await response.text()
  if (response.ok && type.includes('text/html')) {
    throw new Error('Địa chỉ API đang trả về trang web thay vì backend. Hãy kiểm tra cấu hình kết nối.')
  }
  if (!response.ok) {
    const validationErrors = body && typeof body === 'object' ? body.validationErrors : null
    const validationMessage = validationErrors && typeof validationErrors === 'object'
      ? Object.entries(validationErrors).filter(([, value]) => typeof value === 'string')
        .map(([field, value]) => `${field}: ${value}`).join('; ')
      : ''
    const message = typeof body === 'string'
      ? body
      : typeof body?.message === 'string'
        ? body.message
        : typeof body?.detail === 'string'
          ? body.detail
          : typeof body?.title === 'string'
            ? body.title
            : validationMessage
    const error = new Error(validationMessage || message || `Yêu cầu thất bại (${response.status})`)
    error.status = response.status
    error.validationErrors = validationErrors
    throw error
  }
  return body
}

async function refreshAccessToken() {
  const auth = getAuth()
  if (!auth?.refreshToken) throw new Error('Phiên đăng nhập đã hết hạn')
  const response = await apiFetch('/api/auth/refresh', {
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
  const response = await apiFetch(path, { ...options, headers })
  if (response.status === 401 && retry && auth?.refreshToken && path !== '/api/auth/refresh') {
    try {
      const token = await refreshAccessToken()
      headers.set('Authorization', `Bearer ${token}`)
      return parseResponse(await apiFetch(path, { ...options, headers }))
    } catch (error) { clearAuth(); throw error }
  }
  return parseResponse(response)
}

export async function apiBlob(path, options = {}, retry = true) {
  const auth = getAuth()
  const headers = new Headers(options.headers || {})
  if (auth?.accessToken) headers.set('Authorization', `Bearer ${auth.accessToken}`)
  const response = await apiFetch(path, { ...options, headers })
  if (response.status === 401 && retry && auth?.refreshToken && path !== '/api/auth/refresh') {
    try {
      const token = await refreshAccessToken()
      headers.set('Authorization', `Bearer ${token}`)
      const retryResponse = await apiFetch(path, { ...options, headers })
      if (!retryResponse.ok) {
        throw new Error(retryResponse.status === 403 ? 'Bạn không có quyền truy cập ảnh này' : `Yêu cầu thất bại (${retryResponse.status})`)
      }
      return retryResponse.blob()
    } catch (error) { clearAuth(); throw error }
  }
  if (!response.ok) {
    if (response.status === 403) throw new Error('Bạn không có quyền xem ảnh của nhiệm vụ này (403 Forbidden)')
    if (response.status === 404) throw new Error('Nhiệm vụ này không có ảnh đính kèm (404 Not Found)')
    throw new Error(`Yêu cầu thất bại (${response.status})`)
  }
  return response.blob()
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

  // Intake & Stall Assignment / Transfer
  getPendingWaitingRoom: () => api('/api/horse-intake/waiting-room/pending'),
  admitToWaitingRoom: (horseId) => api(`/api/horse-intake/${horseId}/waiting-room`, { method: 'POST' }),
  getPendingStall: () => api('/api/horse-intake/stall/pending'),
  assignStall: (stableId, stallId, horseId) => api(`/api/stables/${stableId}/stalls/${stallId}/assign`, { method: 'POST', body: JSON.stringify({ horseId }) }),
  unassignStall: (assignmentId) => api(`/api/stables/assignments/${assignmentId}/end`, { method: 'POST' }),
  transferHorse: (horseId, payload) => api(`/api/stables/horses/${horseId}/transfer`, { method: 'POST', body: JSON.stringify(payload) }),

  // FR14 Nutrition
  getActiveNutritionPlan: (horseId, date) => api(`/api/horses/${horseId}/nutrition-plan${date ? `?date=${date}` : ''}`),
  getNutritionHistory: (horseId) => api(`/api/horses/${horseId}/nutrition-plan/history`),
  getDailyFeedingChecklist: (horseId, date) => api(`/api/horses/${horseId}/nutrition-plan/daily-checklist${date ? `?date=${date}` : ''}`),
  confirmDailyFeedingMeal: (horseId, payload) => api(`/api/horses/${horseId}/daily-feeding/confirm`, { method: 'POST', body: JSON.stringify(payload) }),
  getDailyFeedingHistory: (horseId, params) => {
    const q = params ? new URLSearchParams(params).toString() : ''
    return api(`/api/horses/${horseId}/daily-feeding/history${q ? `?${q}` : ''}`)
  },
  getStandardPlans: () => api('/api/nutrition/standard-plans'),
  getStandardPlan: (planCode) => api(`/api/nutrition/standard-plans/${encodeURIComponent(planCode)}`),
  createStandardPlan: (payload) => api('/api/nutrition/standard-plans', { method: 'POST', body: JSON.stringify(payload) }),
  updateStandardPlan: (planCode, payload) => api(`/api/nutrition/standard-plans/${encodeURIComponent(planCode)}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deleteStandardPlan: (planCode) => api(`/api/nutrition/standard-plans/${encodeURIComponent(planCode)}`, { method: 'DELETE' }),
  getFeedingTemplateOptions: (horseId) => api(`/api/horses/${horseId}/nutrition-plan/template-options`),
  applyFeedingTemplate: (horseId, payload) => api(`/api/horses/${horseId}/nutrition-plan/apply-template`, { method: 'POST', body: JSON.stringify(payload) }),
  getHorsesActivePlans: (date) => api(`/api/nutrition/horses-active${date ? `?date=${date}` : ''}`),
  getStablesWorkload: (date) => api(`/api/nutrition/stables-workload${date ? `?date=${date}` : ''}`),
  getStablesQuickFeed: (date) => api(`/api/nutrition/stables-quick-feed${date ? `?date=${date}` : ''}`),
  createNutritionPlan: (horseId, payload) => api(`/api/horses/${horseId}/nutrition-plan`, { method: 'POST', body: JSON.stringify(payload) }),
  deleteNutritionPlan: (horseId) => api(`/api/horses/${horseId}/nutrition-plan`, { method: 'DELETE' }),
  getRoutineBackfillPreview: () => api('/api/stables/routines/backfill-preview'),
  executeRoutineBackfill: () => api('/api/stables/routines/backfill-execute', { method: 'POST' }),

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
  getCareTaskPhotoBlob: (id) => apiBlob(`/api/care-tasks/${id}/photo`),

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

  // Training Preparation for Stable Staff
  getMyTrainingSchedules: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v) })
    return api(`/api/training-schedules/mine${q.toString() ? `?${q}` : ''}`)
  },
  markTrainingReady: (scheduleId) => api(`/api/training-schedules/${scheduleId}/ready`, { method: 'PATCH' }),
  // Care Routine Definitions (Task Catalog)
  getActiveCareRoutines: () => api('/api/care-routine-definitions/active'),
  getReserveCareRoutines: () => api('/api/care-routine-definitions/reserve'),
  getCareRoutine: (id) => api(`/api/care-routine-definitions/${id}`),
  createCareRoutine: (payload) => api('/api/care-routine-definitions', { method: 'POST', body: JSON.stringify(payload) }),
  updateCareRoutine: (id, payload) => api(`/api/care-routine-definitions/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  transferCareRoutine: (id, active) => api(`/api/care-routine-definitions/${id}/transfer`, { method: 'POST', body: JSON.stringify({ active }) }),
  moveCareRoutineToActive: (id) => api(`/api/care-routine-definitions/${id}/move-to-active`, { method: 'POST' }),
  moveCareRoutineToReserve: (id) => api(`/api/care-routine-definitions/${id}/move-to-reserve`, { method: 'POST' }),
  deleteCareRoutine: (id) => api(`/api/care-routine-definitions/${id}`, { method: 'DELETE' }),
  reorderCareRoutines: (orderedIds) => api('/api/care-routine-definitions/reorder', { method: 'PUT', body: JSON.stringify({ orderedIds }) }),
}

export const careRoutineApi = {
  getActive: () => api('/api/care-routine-definitions/active'),
  getReserve: () => api('/api/care-routine-definitions/reserve'),
  get: (id) => api(`/api/care-routine-definitions/${id}`),
  create: (payload) => api('/api/care-routine-definitions', { method: 'POST', body: JSON.stringify(payload) }),
  update: (id, payload) => api(`/api/care-routine-definitions/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  transfer: (id, active) => api(`/api/care-routine-definitions/${id}/transfer`, { method: 'POST', body: JSON.stringify({ active }) }),
  moveToActive: (id) => api(`/api/care-routine-definitions/${id}/move-to-active`, { method: 'POST' }),
  moveToReserve: (id) => api(`/api/care-routine-definitions/${id}/move-to-reserve`, { method: 'POST' }),
  delete: (id) => api(`/api/care-routine-definitions/${id}`, { method: 'DELETE' }),
  reorder: (orderedIds) => api('/api/care-routine-definitions/reorder', { method: 'PUT', body: JSON.stringify({ orderedIds }) }),
}

export const servicePackageApi = {
  getActive: () => api('/api/service-packages/active'),
  getReserve: () => api('/api/service-packages/reserve'),
  getCatalog: () => api('/api/service-packages/catalog'),
  get: (id) => api(`/api/service-packages/${id}`),
  create: (payload) => api('/api/service-packages', { method: 'POST', body: JSON.stringify(payload) }),
  update: (id, payload) => api(`/api/service-packages/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  transfer: (id, active) => api(`/api/service-packages/${id}/transfer`, { method: 'POST', body: JSON.stringify({ active }) }),
  moveToActive: (id) => api(`/api/service-packages/${id}/move-to-active`, { method: 'POST' }),
  moveToReserve: (id) => api(`/api/service-packages/${id}/move-to-reserve`, { method: 'POST' }),
  delete: (id) => api(`/api/service-packages/${id}`, { method: 'DELETE' }),
  reorder: (orderedIds) => api('/api/service-packages/reorder', { method: 'PUT', body: JSON.stringify({ orderedIds }) }),
}

export const broadcastApi = {
  getActiveBroadcasts: () => api('/api/notifications/broadcasts'),
  dismissBroadcast: (id) => api(`/api/notifications/broadcasts/${id}/dismiss`, { method: 'POST' }),
}

export const walletAlertApi = {
  getDiscontinuedExpirySummary: () => api('/api/wallet/discontinued-expiry-summary'),
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
    const response = await apiFetch(`/api/financial-transactions/export?${q}`, {
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
  getMyExpenses: (from, to) => api(`/api/owner-reports/my-expenses?${new URLSearchParams({ ...(from ? { from } : {}), ...(to ? { to } : {}) })}`),
  getReports: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v) })
    return api(`/api/owner-reports?${q}`)
  },
  getReport: (id) => api(`/api/owner-reports/${id}`),
  generateReport: (payload) => api('/api/owner-reports', { method: 'POST', body: JSON.stringify(payload) }),
  downloadJson: async (id) => {
    const auth = getAuth()
    const response = await apiFetch(`/api/owner-reports/${id}/download`, {
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
    const response = await apiFetch(`/api/owner-reports/${id}/export-csv`, {
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

