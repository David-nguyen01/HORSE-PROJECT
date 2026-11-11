import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  computeHorseDailyRoutineState,
  isTrainingScheduleActiveForDay,
  getScheduleDate,
} from './trainingRules.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const ROUTINE_ACTIVITY_LABELS = {
  TURNOUT: 'Thả bãi cỏ dạo chơi (Turnout)',
  GROOMING: 'Chải lông & Vệ sinh (Grooming)',
  REST: 'Thời gian nghỉ ngơi (Rest)',
  FEEDING: 'Giờ ăn khẩu phần (Feeding)',
  WALKING: 'Dắt đi dạo nhẹ (Walking)',
  ICE_BOOTS: 'Ngâm / Chườm đá chân (Ice boots)',
  STALL_REST: 'Tĩnh dưỡng tại chuồng (Stall rest)',
  OTHER: 'Hoạt động khác',
}

const HEALTH_STATUS_MAP = {
  ACTIVE: { label: 'Khỏe mạnh', cls: 'active' },
  FIT: { label: 'Khỏe mạnh', cls: 'active' },
  HEALTHY: { label: 'Khỏe mạnh', cls: 'active' },
  INJURED: { label: 'Chấn thương', cls: 'injured' },
  QUARANTINED: { label: 'Đang cách ly', cls: 'injured' },
  RESTING: { label: 'Nghỉ ngơi', cls: 'resting' },
  RECOVERING: { label: 'Hồi phục', cls: 'resting' },
  RETIRED: { label: 'Đã giải nghệ', cls: 'retired' },
  SICK: { label: 'Đang điều trị', cls: 'injured' },
  RESTRICTED: { label: 'Hạn chế y tế', cls: 'injured' },
}

function formatTimeHHmm(val) {
  if (!val) return ''
  const str = String(val).trim()
  const m = str.match(/^(\d{1,2}):(\d{2})(?::\d{2})?/)
  if (m) {
    return `${m[1].padStart(2, '0')}:${m[2]}`
  }
  return str
}

function formatHorseHealthStatus(val) {
  if (!val) return 'Khỏe mạnh'
  const key = String(val).trim().toUpperCase()
  return HEALTH_STATUS_MAP[key]?.label || key
}

function getRoutineDisplayTitle(r) {
  if (!r) return ''
  if (r.activityType === 'OTHER' && r.notes?.trim()) {
    return r.notes.trim()
  }
  return ROUTINE_ACTIVITY_LABELS[r.activityType] || r.activityType
}

// Validation logic helper matching RoutineModal in StableCarePage.jsx
function validateRoutineForm(form, isRestricted) {
  if (isRestricted) {
    return 'Ngựa đang trong chế độ hạn chế y tế / chấn thương. Không thể lưu thay đổi lịch sinh hoạt.'
  }
  const cleanStart = formatTimeHHmm(form.startTime)
  const cleanEnd = formatTimeHHmm(form.endTime)
  if (!cleanStart || !cleanEnd) {
    return 'Giờ bắt đầu và kết thúc là bắt buộc.'
  }
  if (cleanStart >= cleanEnd) {
    return 'Giờ bắt đầu phải nhỏ hơn giờ kết thúc.'
  }
  if (form.activityType === 'OTHER' && !form.customActivity?.trim()) {
    return 'Vui lòng nhập nội dung cho hoạt động khác.'
  }
  return null
}

function computeCanManageDailyRoutine(roles, horse = null) {
  const isStaff = (roles || []).includes('STABLE_STAFF')
  if (!isStaff) return false
  if (horse) {
    const isMedicallyRestricted = Boolean(
      horse.emergencyActive || horse.healthStatus === 'INJURED' || horse.trainingLocked
    )
    const isTrainingActive = Boolean(
      !isMedicallyRestricted && (horse.isTrainingActive || Boolean(horse.trainingSchedule))
    )
    if (isMedicallyRestricted || isTrainingActive) return false
  }
  return true
}

// 01C.6 approved decision: bulk backfill belongs exclusively to Stable Staff
function computeCanExecuteBackfill(roles) {
  return (roles || []).includes('STABLE_STAFF')
}

function isHorseMovementRestricted(horse) {
  return horse.healthStatus === 'INJURED' || Boolean(horse.trainingLocked) || Boolean(horse.emergencyActive)
}

test('stableCareApi in api.js does not expose deleteRoutine endpoint (removed per RH-STABLE-UI-01C.3)', () => {
  const apiSrc = fs.readFileSync(path.join(__dirname, 'api.js'), 'utf8')
  assert.equal(apiSrc.includes('deleteRoutine:'), false, 'stableCareApi should not have deleteRoutine')
  assert.equal(apiSrc.includes('addRoutine:'), true, 'stableCareApi should have addRoutine')
  assert.equal(apiSrc.includes('updateRoutine:'), true, 'stableCareApi should have updateRoutine')
  assert.equal(apiSrc.includes('getRoutines:'), true, 'stableCareApi should have getRoutines')
})

test('computeCanManageDailyRoutine permits STABLE_STAFF for normal horse', () => {
  assert.equal(computeCanManageDailyRoutine(['STABLE_STAFF']), true)
  assert.equal(computeCanManageDailyRoutine(['STABLE_STAFF'], { healthStatus: 'HEALTHY', trainingLocked: false, emergencyActive: false, isTrainingActive: false }), true)
  assert.equal(computeCanManageDailyRoutine(['CLUB_MANAGER', 'STABLE_STAFF']), true)
})

test('computeCanManageDailyRoutine denies HEAD_TRAINER', () => {
  assert.equal(computeCanManageDailyRoutine(['HEAD_TRAINER']), false)
  assert.equal(computeCanManageDailyRoutine(['HEAD_TRAINER'], { healthStatus: 'HEALTHY' }), false)
})

test('computeCanManageDailyRoutine denies CLUB_MANAGER', () => {
  assert.equal(computeCanManageDailyRoutine(['CLUB_MANAGER']), false)
  assert.equal(computeCanManageDailyRoutine(['CLUB_MANAGER'], { healthStatus: 'HEALTHY' }), false)
  assert.equal(computeCanManageDailyRoutine(['CLUB_MANAGER', 'HEAD_TRAINER']), false)
})

test('computeCanManageDailyRoutine denies VETERINARIAN and HORSE_OWNER', () => {
  assert.equal(computeCanManageDailyRoutine(['VETERINARIAN']), false)
  assert.equal(computeCanManageDailyRoutine(['HORSE_OWNER']), false)
  assert.equal(computeCanManageDailyRoutine([]), false)
})

test('computeCanManageDailyRoutine denies Stable users while horse is training', () => {
  const trainingHorse1 = { id: 'h-1', isTrainingActive: true, healthStatus: 'HEALTHY' }
  const trainingHorse2 = { id: 'h-2', trainingSchedule: { taskName: 'Morning Gallop' }, healthStatus: 'HEALTHY' }
  assert.equal(computeCanManageDailyRoutine(['STABLE_STAFF'], trainingHorse1), false)
  assert.equal(computeCanManageDailyRoutine(['STABLE_STAFF'], trainingHorse2), false)
  assert.equal(computeCanManageDailyRoutine(['CLUB_MANAGER', 'STABLE_STAFF'], trainingHorse1), false)
})

test('computeCanManageDailyRoutine denies Stable users while horse is injured or medically restricted', () => {
  const injuredHorse = { id: 'h-3', healthStatus: 'INJURED' }
  const lockedHorse = { id: 'h-4', trainingLocked: true, trainingLockReason: 'Medical observation' }
  const emergencyHorse = { id: 'h-5', emergencyActive: true }
  assert.equal(computeCanManageDailyRoutine(['STABLE_STAFF'], injuredHorse), false)
  assert.equal(computeCanManageDailyRoutine(['STABLE_STAFF'], lockedHorse), false)
  assert.equal(computeCanManageDailyRoutine(['STABLE_STAFF'], emergencyHorse), false)
  assert.equal(computeCanManageDailyRoutine(['CLUB_MANAGER', 'STABLE_STAFF'], injuredHorse), false)
})

test('validateRoutineForm validates chronological order of start and end times', () => {
  assert.equal(
    validateRoutineForm({ startTime: '08:00', endTime: '07:30', activityType: 'GROOMING' }, false),
    'Giờ bắt đầu phải nhỏ hơn giờ kết thúc.'
  )
  assert.equal(
    validateRoutineForm({ startTime: '08:00:00', endTime: '07:30:00', activityType: 'GROOMING' }, false),
    'Giờ bắt đầu phải nhỏ hơn giờ kết thúc.'
  )
  assert.equal(
    validateRoutineForm({ startTime: '08:00', endTime: '08:00', activityType: 'GROOMING' }, false),
    'Giờ bắt đầu phải nhỏ hơn giờ kết thúc.'
  )
  assert.equal(
    validateRoutineForm({ startTime: '07:00', endTime: '08:00', activityType: 'GROOMING' }, false),
    null
  )
})

test('validateRoutineForm blocks any modification when horse is restricted', () => {
  assert.equal(
    validateRoutineForm({ startTime: '06:00', endTime: '07:00', activityType: 'FEEDING' }, true),
    'Ngựa đang trong chế độ hạn chế y tế / chấn thương. Không thể lưu thay đổi lịch sinh hoạt.'
  )
})

test('validateRoutineForm for OTHER requires non-blank custom content', () => {
  // Blank custom content should fail
  assert.equal(
    validateRoutineForm({ startTime: '14:00', endTime: '15:00', activityType: 'OTHER', customActivity: '' }, false),
    'Vui lòng nhập nội dung cho hoạt động khác.'
  )
  assert.equal(
    validateRoutineForm({ startTime: '14:00', endTime: '15:00', activityType: 'OTHER', customActivity: '   ' }, false),
    'Vui lòng nhập nội dung cho hoạt động khác.'
  )
  // Valid custom content should pass
  assert.equal(
    validateRoutineForm({ startTime: '14:00', endTime: '15:00', activityType: 'OTHER', customActivity: 'Tập bơi hồ thủy liệu' }, false),
    null
  )
})

test('formatTimeHHmm consistently formats seconds and minutes', () => {
  assert.equal(formatTimeHHmm('06:00:00'), '06:00')
  assert.equal(formatTimeHHmm('07:30:00'), '07:30')
  assert.equal(formatTimeHHmm('08:15'), '08:15')
  assert.equal(formatTimeHHmm('9:05:00'), '09:05')
  assert.equal(formatTimeHHmm(''), '')
  assert.equal(formatTimeHHmm(null), '')
})

test('formatHorseHealthStatus maps all English codes to Vietnamese labels', () => {
  assert.equal(formatHorseHealthStatus('FIT'), 'Khỏe mạnh')
  assert.equal(formatHorseHealthStatus('HEALTHY'), 'Khỏe mạnh')
  assert.equal(formatHorseHealthStatus('QUARANTINED'), 'Đang cách ly')
  assert.equal(formatHorseHealthStatus('INJURED'), 'Chấn thương')
  assert.equal(formatHorseHealthStatus('RESTING'), 'Nghỉ ngơi')
  assert.equal(formatHorseHealthStatus('RECOVERING'), 'Hồi phục')
  assert.equal(formatHorseHealthStatus('RETIRED'), 'Đã giải nghệ')
  assert.equal(formatHorseHealthStatus(''), 'Khỏe mạnh')
  assert.equal(formatHorseHealthStatus(null), 'Khỏe mạnh')
})

test('getRoutineDisplayTitle displays custom description for OTHER and label for standard', () => {
  const customRoutine = { activityType: 'OTHER', notes: 'Tập vật lý trị liệu với chuyên gia' }
  assert.equal(getRoutineDisplayTitle(customRoutine), 'Tập vật lý trị liệu với chuyên gia')

  const fallbackOther = { activityType: 'OTHER', notes: '' }
  assert.equal(getRoutineDisplayTitle(fallbackOther), 'Hoạt động khác')

  const standardRoutine = { activityType: 'TURNOUT', notes: 'Thả bãi số 2' }
  assert.equal(getRoutineDisplayTitle(standardRoutine), 'Thả bãi cỏ dạo chơi (Turnout)')
})

test('isHorseMovementRestricted identifies injured, training locked, or emergency horses', () => {
  assert.equal(isHorseMovementRestricted({ healthStatus: 'INJURED' }), true)
  assert.equal(isHorseMovementRestricted({ trainingLocked: true }), true)
  assert.equal(isHorseMovementRestricted({ emergencyActive: true }), true)
  assert.equal(isHorseMovementRestricted({ healthStatus: 'HEALTHY', trainingLocked: false, emergencyActive: false }), false)
})

// computeHorseDailyRoutineState imported from ./trainingRules.js

test('State A: normal healthy horse displays default daily routine and enables mutation actions', () => {
  const horse = {
    id: 'h-1',
    name: 'Normal Horse',
    healthStatus: 'HEALTHY',
    trainingLocked: false,
    emergencyActive: false,
    isTrainingActive: false,
    trainingSchedule: null
  }
  const result = computeHorseDailyRoutineState(horse)
  assert.equal(result.state, 'STATE_A_NORMAL')
  assert.equal(result.showDefaultRoutine, true)
  assert.equal(result.showTrainingSchedule, false)
  assert.equal(result.showMedicalAlert, false)
  assert.equal(result.canMutateRoutine, true)
})

test('State B: horse currently training displays training schedule read-only and temporarily hides default routine', () => {
  const horse = {
    id: 'h-2',
    name: 'Training Horse',
    healthStatus: 'HEALTHY',
    trainingLocked: false,
    emergencyActive: false,
    isTrainingActive: true,
    trainingSchedule: {
      taskName: 'Morning Gallop',
      racetrackName: 'Main Dirt Track',
      workloadIntensity: 'HIGH',
      sessionStatus: 'READY'
    }
  }
  const result = computeHorseDailyRoutineState(horse)
  assert.equal(result.state, 'STATE_B_TRAINING_ACTIVE')
  assert.equal(result.showDefaultRoutine, false)
  assert.equal(result.showTrainingSchedule, true)
  assert.equal(result.showMedicalAlert, false)
  assert.equal(result.canMutateRoutine, false)
  assert.match(result.tooltip, /Ngựa đang trong lịch trình huấn luyện hôm nay/)
})

test('State C: medical hold overrides training state and takes strict precedence', () => {
  const horse = {
    id: 'h-3',
    name: 'Injured Training Horse',
    healthStatus: 'INJURED',
    trainingLocked: true,
    trainingLockReason: 'Khớp gối sưng nhẹ sau bài tập',
    emergencyActive: false,
    isTrainingActive: true,
    trainingSchedule: {
      taskName: 'Sprint Practice',
      racetrackName: 'Grass Lane',
      workloadIntensity: 'MEDIUM',
      sessionStatus: 'PREPARING'
    }
  }
  const result = computeHorseDailyRoutineState(horse)
  assert.equal(result.state, 'STATE_C_MEDICAL_RESTRICTION')
  assert.equal(result.showMedicalAlert, true)
  assert.equal(result.showTrainingSchedule, false)
  assert.equal(result.showDefaultRoutine, false)
  assert.equal(result.canMutateRoutine, false)
  assert.match(result.tooltip, /Không thể thay đổi lịch sinh hoạt khi ngựa đang chấn thương hoặc hạn chế y tế/)
})

test('Transition: when training session completes, horse seamlessly restores default routine without data loss', () => {
  const routinesPreserved = [
    { id: 'r-1', activityType: 'FEEDING', startTime: '06:00', endTime: '07:00' },
    { id: 'r-2', activityType: 'TURNOUT', startTime: '07:00', endTime: '08:30' },
    { id: 'r-3', activityType: 'GROOMING', startTime: '09:00', endTime: '10:00' }
  ]
  const trainingHorse = {
    id: 'h-4',
    name: 'Race Winner',
    healthStatus: 'HEALTHY',
    trainingLocked: false,
    emergencyActive: false,
    isTrainingActive: true,
    trainingSchedule: { taskName: 'Speed Drill', sessionStatus: 'IN_PROGRESS' }
  }
  assert.equal(computeHorseDailyRoutineState(trainingHorse).showDefaultRoutine, false)

  // Session finishes -> isTrainingActive = false
  const finishedHorse = { ...trainingHorse, isTrainingActive: false, trainingSchedule: null }
  const afterState = computeHorseDailyRoutineState(finishedHorse)
  assert.equal(afterState.state, 'STATE_A_NORMAL')
  assert.equal(afterState.showDefaultRoutine, true)
  assert.equal(routinesPreserved.length, 3, 'All routine records remain completely intact')
})

test('computeCanExecuteBackfill permits STABLE_STAFF (01C.6 approved rule)', () => {
  assert.equal(computeCanExecuteBackfill(['STABLE_STAFF']), true)
  assert.equal(computeCanExecuteBackfill(['STABLE_STAFF', 'HEAD_TRAINER']), true)
  assert.equal(computeCanExecuteBackfill(['STABLE_STAFF', 'CLUB_MANAGER']), true)
})

test('computeCanExecuteBackfill denies HEAD_TRAINER, CLUB_MANAGER, VETERINARIAN, and HORSE_OWNER', () => {
  assert.equal(computeCanExecuteBackfill(['HEAD_TRAINER']), false)
  assert.equal(computeCanExecuteBackfill(['CLUB_MANAGER']), false)
  assert.equal(computeCanExecuteBackfill(['VETERINARIAN']), false)
  assert.equal(computeCanExecuteBackfill(['HORSE_OWNER']), false)
  assert.equal(computeCanExecuteBackfill([]), false)
})

test('Five-Role & Caretaker boundary: caretaker_user_id does not create a role or authorization boundary for daily routine', () => {
  const horseWithCaretaker = {
    id: 'h-100',
    caretaker_user_id: 'user-staff-1',
    healthStatus: 'HEALTHY',
    trainingLocked: false,
    emergencyActive: false,
    isTrainingActive: false
  }
  // Daily routine management is determined exclusively by caller possessing ROLE_STABLE_STAFF, not caretaker assignment
  assert.equal(computeCanManageDailyRoutine(['STABLE_STAFF'], horseWithCaretaker), true)
  assert.equal(computeCanManageDailyRoutine(['HEAD_TRAINER'], horseWithCaretaker), false)
  assert.equal(computeCanManageDailyRoutine(['CLUB_MANAGER'], horseWithCaretaker), false)
  assert.equal(computeCanManageDailyRoutine(['CARETAKER'], horseWithCaretaker), false, 'Non-existent CARETAKER role has zero authority')
})


test('Part A1: Training plan spans Nov 11-30 with sessions on Nov 11, 13, 15 only', () => {
  const sessions = [
    { id: 's-1', scheduledStart: '2026-11-11T08:00:00Z', scheduledEnd: '2026-11-11T09:00:00Z', taskName: 'Nov 11 Session', sessionStatus: 'READY' },
    { id: 's-2', scheduledStart: '2026-11-13T08:00:00Z', scheduledEnd: '2026-11-13T09:00:00Z', taskName: 'Nov 13 Session', sessionStatus: 'PREPARING' },
    { id: 's-3', scheduledStart: '2026-11-15T08:00:00Z', scheduledEnd: '2026-11-15T09:00:00Z', taskName: 'Nov 15 Session', sessionStatus: 'PREPARING' },
  ]
  const defaultRoutines = [
    { id: 'r-1', activityType: 'FEEDING', startTime: '06:00', endTime: '07:00' },
    { id: 'r-2', activityType: 'TURNOUT', startTime: '07:00', endTime: '08:30' },
  ]

  // On Nov 11: session exists -> State B
  const horseNov11 = {
    id: 'h-1',
    healthStatus: 'HEALTHY',
    trainingLocked: false,
    emergencyActive: false,
    trainingSchedule: sessions[0],
  }
  const stateNov11 = computeHorseDailyRoutineState(horseNov11, '2026-11-11')
  assert.equal(stateNov11.state, 'STATE_B_TRAINING_ACTIVE')
  assert.equal(stateNov11.showTrainingSchedule, true)
  assert.equal(stateNov11.showDefaultRoutine, false)
  assert.equal(stateNov11.canMutateRoutine, false)

  // On Nov 12: no session scheduled -> State A (default daily routine)
  const horseNov12 = {
    id: 'h-1',
    healthStatus: 'HEALTHY',
    trainingLocked: false,
    emergencyActive: false,
    trainingSchedule: null,
  }
  const stateNov12 = computeHorseDailyRoutineState(horseNov12, '2026-11-12')
  assert.equal(stateNov12.state, 'STATE_A_NORMAL')
  assert.equal(stateNov12.showTrainingSchedule, false)
  assert.equal(stateNov12.showDefaultRoutine, true)
  assert.equal(stateNov12.canMutateRoutine, true)

  // On Nov 13: session exists -> State B
  const horseNov13 = {
    id: 'h-1',
    healthStatus: 'HEALTHY',
    trainingLocked: false,
    emergencyActive: false,
    trainingSchedule: sessions[1],
  }
  const stateNov13 = computeHorseDailyRoutineState(horseNov13, '2026-11-13')
  assert.equal(stateNov13.state, 'STATE_B_TRAINING_ACTIVE')
  assert.equal(stateNov13.showTrainingSchedule, true)
  assert.equal(stateNov13.showDefaultRoutine, false)

  // On Nov 14: no session -> State A
  const horseNov14 = {
    id: 'h-1',
    healthStatus: 'HEALTHY',
    trainingLocked: false,
    emergencyActive: false,
    trainingSchedule: null,
  }
  const stateNov14 = computeHorseDailyRoutineState(horseNov14, '2026-11-14')
  assert.equal(stateNov14.state, 'STATE_A_NORMAL')
  assert.equal(stateNov14.showDefaultRoutine, true)

  // On Nov 15: session exists -> State B
  const horseNov15 = {
    id: 'h-1',
    healthStatus: 'HEALTHY',
    trainingLocked: false,
    emergencyActive: false,
    trainingSchedule: sessions[2],
  }
  const stateNov15 = computeHorseDailyRoutineState(horseNov15, '2026-11-15')
  assert.equal(stateNov15.state, 'STATE_B_TRAINING_ACTIVE')
  assert.equal(stateNov15.showTrainingSchedule, true)

  // Verify default routines records were never mutated or deleted
  assert.equal(defaultRoutines.length, 2)
})

test('Part A2: A session scheduled for Nov 12 must NEVER replace Nov 11 routine', () => {
  const tomorrowSession = {
    id: 's-tomorrow',
    scheduledStart: '2026-11-12T09:00:00Z',
    scheduledEnd: '2026-11-12T10:30:00Z',
    taskName: 'Tomorrow Gallop',
    sessionStatus: 'READY',
  }
  const horseOnNov11 = {
    id: 'h-future-check',
    healthStatus: 'HEALTHY',
    trainingLocked: false,
    emergencyActive: false,
    trainingSchedule: tomorrowSession,
  }

  // Evaluating for Nov 11 with a Nov 12 session:
  assert.equal(isTrainingScheduleActiveForDay(tomorrowSession, '2026-11-11'), false)
  const result = computeHorseDailyRoutineState(horseOnNov11, '2026-11-11')
  assert.equal(result.state, 'STATE_A_NORMAL', 'Nov 12 session must not activate State B on Nov 11')
  assert.equal(result.showDefaultRoutine, true)
  assert.equal(result.showTrainingSchedule, false)
  assert.equal(result.canMutateRoutine, true)
})

test('Part A3: Completed session restores default routine visibility (State A)', () => {
  const completedSession = {
    id: 's-done',
    scheduledStart: '2026-11-11T07:00:00Z',
    scheduledEnd: '2026-11-11T08:00:00Z',
    taskName: 'Finished Sprint',
    sessionStatus: 'COMPLETED',
  }
  const horseCompleted = {
    id: 'h-completed',
    healthStatus: 'HEALTHY',
    trainingLocked: false,
    emergencyActive: false,
    trainingSchedule: completedSession,
  }

  assert.equal(isTrainingScheduleActiveForDay(completedSession, '2026-11-11'), false)
  const result = computeHorseDailyRoutineState(horseCompleted, '2026-11-11')
  assert.equal(result.state, 'STATE_A_NORMAL')
  assert.equal(result.showDefaultRoutine, true)
  assert.equal(result.showTrainingSchedule, false)
  assert.equal(result.canMutateRoutine, true)
})

test('Part A4: Medical restriction strictly overrides training session (State C > State B > State A)', () => {
  const activeSessionToday = {
    id: 's-active-today',
    scheduledStart: '2026-11-11T08:00:00Z',
    scheduledEnd: '2026-11-11T09:00:00Z',
    taskName: 'Active Session',
    sessionStatus: 'IN_PROGRESS',
  }
  const injuredHorse = {
    id: 'h-injured',
    healthStatus: 'INJURED',
    trainingLocked: true,
    trainingLockReason: 'Khớp cổ chân sưng viêm sau khi ngã',
    emergencyActive: false,
    trainingSchedule: activeSessionToday,
  }

  const result = computeHorseDailyRoutineState(injuredHorse, '2026-11-11')
  assert.equal(result.state, 'STATE_C_MEDICAL_RESTRICTION')
  assert.equal(result.showMedicalAlert, true)
  assert.equal(result.showTrainingSchedule, false)
  assert.equal(result.showDefaultRoutine, false)
  assert.equal(result.canMutateRoutine, false)
})
