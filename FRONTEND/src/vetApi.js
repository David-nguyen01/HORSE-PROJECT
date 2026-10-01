import { api } from './api'

export const vetApi = {
  // FR08 Barn Health Map
  getBarnHealthMap: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') q.set(k, v)
    })
    const qs = q.toString()
    return api(`/api/health/barn-map${qs ? `?${qs}` : ''}`)
  },

  // General Horses & Stables
  getHorses: (params = {}) => {
    const q = new URLSearchParams({ size: '100', ...params })
    return api(`/api/horses?${q.toString()}`)
  },
  getHorse: (id) => api(`/api/horses/${id}`),
  getHorseHealthStatus: (id) => api(`/api/horses/${id}/health-status`),
  updateHorseHealthStatus: (id, payload) =>
    api(`/api/horses/${id}/health-status`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  getStables: (includeInactive = false) => api(`/api/stables?includeInactive=${includeInactive}`),

  // FR09 Medical Examination & Treatment Records
  getHorseMedicalExams: (horseId) => api(`/api/horses/${horseId}/medical-examinations`),
  getMedicalExamDetail: (examId) => api(`/api/medical-examinations/${examId}`),
  createMedicalExam: (horseId, payload) =>
    api(`/api/horses/${horseId}/medical-examinations`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  addMedicalExamAddendum: (examId, payload) =>
    api(`/api/medical-examinations/${examId}/addenda`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  // FR10 Injury Tracking
  getHorseInjuries: (horseId, status) =>
    api(`/api/horses/${horseId}/injuries${status ? `?status=${encodeURIComponent(status)}` : ''}`),
  getInjuryDetail: (injuryId) => api(`/api/injuries/${injuryId}`),
  createInjury: (horseId, payload) =>
    api(`/api/horses/${horseId}/injuries`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  addInjuryRecoveryUpdate: (injuryId, payload) =>
    api(`/api/injuries/${injuryId}/recovery-updates`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  // FR11 Training Locks
  getCurrentTrainingLock: (horseId) => api(`/api/horses/${horseId}/training-locks/current`),
  getTrainingLockHistory: (horseId) => api(`/api/horses/${horseId}/training-locks`),
  createTrainingLock: (horseId, payload) =>
    api(`/api/horses/${horseId}/training-locks`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  releaseTrainingLock: (lockId, payload) =>
    api(`/api/training-locks/${lockId}/release`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  // FR12 Preventive Care
  getDuePreventiveCare: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') q.set(k, v)
    })
    const qs = q.toString()
    return api(`/api/preventive-care/due${qs ? `?${qs}` : ''}`)
  },
  getHorsePreventiveCare: (horseId, params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') q.set(k, v)
    })
    const qs = q.toString()
    return api(`/api/horses/${horseId}/preventive-care${qs ? `?${qs}` : ''}`)
  },
  createPreventiveCare: (horseId, payload) =>
    api(`/api/horses/${horseId}/preventive-care`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  updatePreventiveCare: (id, payload) =>
    api(`/api/preventive-care/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  completePreventiveCare: (id, payload) =>
    api(`/api/preventive-care/${id}/complete`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  cancelPreventiveCare: (id) =>
    api(`/api/preventive-care/${id}`, {
      method: 'DELETE',
    }),

  // Incidents (reported from stable staff)
  getIncidents: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') q.set(k, v)
    })
    const qs = q.toString()
    return api(`/api/incidents${qs ? `?${qs}` : ''}`)
  },
}
