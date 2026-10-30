import { api } from './api'

export const vetApi = {
  // Barn Health Map
  getBarnHealthMap: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') q.set(k, v)
    })
    const qs = q.toString()
    return api(`/api/health/barn-map${qs ? `?${qs}` : ''}`)
  },

  // General Horses & Stables
  getEligibleHorses: () => api('/api/horses/eligible-for-medical'),
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

  // Medical Examination & Treatment Records
  getHorseMedicalExams: (horseId) => api(`/api/horses/${horseId}/medical-examinations`),
  getMedicalExamDetail: (examId) => api(`/api/medical-examinations/${examId}`),
  createMedicalExam: (horseId, payload) =>
    api(`/api/horses/${horseId}/medical-examinations`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  deleteMedicalExam: (examId) =>
    api(`/api/medical-examinations/${examId}`, {
      method: 'DELETE',
    }),
  addMedicalExamAddendum: (examId, payload) =>
    api(`/api/medical-examinations/${examId}/addenda`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  deleteMedicalExamAddendum: (addendumId) =>
    api(`/api/medical-examinations/addenda/${addendumId}`, {
      method: 'DELETE',
    }),

  // Injury Tracking
  getHorseInjuries: (horseId, status) =>
    api(`/api/horses/${horseId}/injuries${status ? `?status=${encodeURIComponent(status)}` : ''}`),
  getInjuryDetail: (injuryId) => api(`/api/injuries/${injuryId}`),
  createInjury: (horseId, payload) =>
    api(`/api/horses/${horseId}/injuries`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  deleteInjury: (injuryId) =>
    api(`/api/injuries/${injuryId}`, {
      method: 'DELETE',
    }),
  addInjuryRecoveryUpdate: (injuryId, payload) =>
    api(`/api/injuries/${injuryId}/recovery-updates`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  // Shared waiting room: first veterinarian to receive an unclaimed horse takes the case.
  getMedicalQueue: () => api('/api/medical/queue'),
  getMedicalQueueOverview: () => api('/api/medical/queue/overview'),
  getMyAssignments: () => api('/api/medical/queue/my-assignments'),
  receiveMedicalQueue: (horseId) =>
    api(`/api/medical/queue/${horseId}/receive`, {
      method: 'POST',
    }),
  completeMedicalQueue: (horseId) =>
    api(`/api/medical/queue/${horseId}/complete`, {
      method: 'POST',
    }),
  getMedicalFollowUps: () => api('/api/medical/follow-ups'),
  requestFollowUpHandover: (id, reason, mode) =>
    api(`/api/medical/follow-ups/${id}/handover`, {
      method: 'POST',
      body: JSON.stringify({ reason, mode }),
    }),
  acceptFollowUpHandover: (id) =>
    api(`/api/medical/follow-ups/${id}/accept`, { method: 'POST' }),
  cancelFollowUpHandover: (id) =>
    api(`/api/medical/follow-ups/${id}/handover/cancel`, { method: 'POST' }),

  // Nutrition Plan
  getHorseNutritionPlan: (horseId) => api(`/api/horses/${horseId}/nutrition-plan`),
  createNutritionPlan: (horseId, payload) =>
    api(`/api/horses/${horseId}/nutrition-plan`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  deleteNutritionPlan: (horseId) =>
    api(`/api/horses/${horseId}/nutrition-plan`, {
      method: 'DELETE',
    }),

  // Training Locks
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

  // Preventive Care
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
  acknowledgeIncident: (incidentId, notes = '') =>
    api(`/api/incidents/${incidentId}/acknowledge`, {
      method: 'POST',
      body: JSON.stringify({ notes }),
    }),
  claimEmergency: (incidentId) =>
    api(`/api/incidents/${incidentId}/claim-emergency`, { method: 'POST' }),
  dismissIncident: (incidentId, notes = '') =>
    api(`/api/incidents/${incidentId}/dismiss`, {
      method: 'POST',
      body: JSON.stringify({ notes }),
    }),

  // Vet Training Assessment
  getTrainingSessionsByDate: (date) =>
    api(`/api/training-schedules?date=${date}`),
  submitVetAssessment: (scheduleId, payload) =>
    api(`/api/training-evaluations/${scheduleId}/vet-assessment`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  getVetAssessment: (scheduleId) =>
    api(`/api/training-evaluations/${scheduleId}/vet-assessment`),
  getVetAssessmentsByDate: (date) =>
    api(`/api/training-evaluations/vet-assessments?date=${date}`),

  // Horse Detailed Profile
  getHorseTrainingPlans: (horseId) =>
    api(`/api/horses/${horseId}/training-plans`),
  getHorseWeights: (horseId) =>
    api(`/api/horses/${horseId}/weights`),
  getHorseRaceReadiness: (horseId) =>
    api(`/api/horses/${horseId}/race-readiness`),

  // Sessions of horses assigned to the diagnosing veterinarian
  getMyTrainingSessions: (from, to) => {
    const q = new URLSearchParams()
    if (from) q.set('from', from)
    if (to)   q.set('to', to)
    const qs = q.toString()
    return api(`/api/training-schedules/mine${qs ? `?${qs}` : ''}`)
  },
}
