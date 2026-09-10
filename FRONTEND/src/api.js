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

export const pageContent = (page) => page?.content || []
