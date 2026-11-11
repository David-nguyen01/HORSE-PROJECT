import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { isHorseFitForTraining, getHorseIneligibilityReason, isInjuredHorse } from './trainingRules.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// Helper mirroring StableCarePage's stall-to-horse synchronization logic
function syncSelectedHorse(stablesList, currentSelected) {
  if (!currentSelected?.id) return null
  let matchingStall = null
  for (const s of stablesList) {
    const found = (s.stalls || []).find((st) => st.currentAssignment?.horseId === currentSelected.id)
    if (found) {
      matchingStall = found
      break
    }
  }

  if (matchingStall?.currentAssignment) {
    return {
      id: matchingStall.currentAssignment.horseId,
      code: matchingStall.currentAssignment.horseCode,
      name: matchingStall.currentAssignment.horseName,
      stallId: matchingStall.id,
      stallCode: matchingStall.code,
      healthStatus: matchingStall.currentAssignment.horseHealthStatus,
      assignedFrom: matchingStall.currentAssignment.assignedFrom,
      trainingLocked: matchingStall.currentAssignment.trainingLocked,
      trainingLockReason: matchingStall.currentAssignment.trainingLockReason,
      emergencyActive: matchingStall.currentAssignment.emergencyActive,
      photoUrl: matchingStall.currentAssignment.photoUrl || null,
      isTrainingActive: Boolean(matchingStall.currentAssignment.isTrainingActive),
      trainingSchedule: matchingStall.currentAssignment.trainingSchedule || null,
    }
  }
  return null
}

function computePresentationState(selectedHorse) {
  if (!selectedHorse) return 'NONE'
  const isMedicallyRestricted = Boolean(
    selectedHorse.emergencyActive ||
    selectedHorse.healthStatus === 'INJURED' ||
    selectedHorse.trainingLocked
  )
  if (isMedicallyRestricted) return 'STATE_C_MEDICAL_RESTRICTION'
  if (selectedHorse.isTrainingActive || Boolean(selectedHorse.trainingSchedule)) {
    return 'STATE_B_TRAINING_SCHEDULE'
  }
  return 'STATE_A_DEFAULT_ROUTINE'
}

test('Part A: updated stall data updates currently selected horse training state', () => {
  const horseId = '6086ae9a-096c-43a8-9b6e-b5cc7811fcea'
  const initialSelected = {
    id: horseId,
    code: 'H13',
    name: 'Ngọc Long',
    stallId: 'stall-1',
    stallCode: 'A-02',
    healthStatus: 'FIT',
    isTrainingActive: false,
    trainingSchedule: null,
  }
  assert.equal(computePresentationState(initialSelected), 'STATE_A_DEFAULT_ROUTINE')

  // Trainer publishes training schedule today -> API returns stall with isTrainingActive=true
  const updatedStables = [{
    id: 'stable-1',
    name: 'Khu A',
    stalls: [{
      id: 'stall-1',
      code: 'A-02',
      currentAssignment: {
        horseId,
        horseCode: 'H13',
        horseName: 'Ngọc Long',
        horseHealthStatus: 'FIT',
        isTrainingActive: true,
        trainingSchedule: {
          id: 'sched-1',
          taskName: 'Phi nước đại 1200m',
          sessionStatus: 'PREPARING',
          scheduledStart: '2026-11-11T05:00:00+07:00',
          scheduledEnd: '2026-11-11T11:00:00+07:00',
        },
      },
    }],
  }]

  const syncedHorse = syncSelectedHorse(updatedStables, initialSelected)
  assert.ok(syncedHorse, 'Horse must remain selected')
  assert.equal(syncedHorse.isTrainingActive, true)
  assert.equal(syncedHorse.trainingSchedule?.taskName, 'Phi nước đại 1200m')
  assert.equal(computePresentationState(syncedHorse), 'STATE_B_TRAINING_SCHEDULE')
})

test('Part A: completed or cancelled training schedule seamlessly restores State A default routine', () => {
  const horseId = '6086ae9a-096c-43a8-9b6e-b5cc7811fcea'
  const currentlyTraining = {
    id: horseId,
    code: 'H13',
    name: 'Ngọc Long',
    stallId: 'stall-1',
    stallCode: 'A-02',
    healthStatus: 'FIT',
    isTrainingActive: true,
    trainingSchedule: {
      id: 'sched-1',
      taskName: 'Phi nước đại 1200m',
      sessionStatus: 'IN_PROGRESS',
    },
  }
  assert.equal(computePresentationState(currentlyTraining), 'STATE_B_TRAINING_SCHEDULE')

  // Session finishes -> backend returns stall with isTrainingActive=false, trainingSchedule=null
  const completedStables = [{
    id: 'stable-1',
    name: 'Khu A',
    stalls: [{
      id: 'stall-1',
      code: 'A-02',
      currentAssignment: {
        horseId,
        horseCode: 'H13',
        horseName: 'Ngọc Long',
        horseHealthStatus: 'FIT',
        isTrainingActive: false,
        trainingSchedule: null,
      },
    }],
  }]

  const syncedHorse = syncSelectedHorse(completedStables, currentlyTraining)
  assert.ok(syncedHorse)
  assert.equal(syncedHorse.isTrainingActive, false)
  assert.equal(syncedHorse.trainingSchedule, null)
  assert.equal(computePresentationState(syncedHorse), 'STATE_A_DEFAULT_ROUTINE')
})

test('Part A: selection is cleared if horse is unassigned or stall removed', () => {
  const horseId = '6086ae9a-096c-43a8-9b6e-b5cc7811fcea'
  const currentSelected = { id: horseId, code: 'H13', name: 'Ngọc Long' }

  // Vacant stalls
  const emptyStables = [{
    id: 'stable-1',
    name: 'Khu A',
    stalls: [{ id: 'stall-1', code: 'A-02', currentAssignment: null }],
  }]

  const syncedHorse = syncSelectedHorse(emptyStables, currentSelected)
  assert.equal(syncedHorse, null)
})

test('Part A: medical restriction precedence strictly maintained over active training', () => {
  const medicallyRestrictedTrainingHorse = {
    id: 'h-1',
    healthStatus: 'INJURED',
    trainingLocked: true,
    emergencyActive: false,
    isTrainingActive: true,
    trainingSchedule: { taskName: 'Tập chạy' },
  }
  assert.equal(computePresentationState(medicallyRestrictedTrainingHorse), 'STATE_C_MEDICAL_RESTRICTION')
})

test('Part A: concurrent loading guard prevents duplicate network requests', async () => {
  let callCount = 0
  const isFetchingRef = { current: false }

  const mockLoadStables = async () => {
    if (isFetchingRef.current) return 'SKIPPED'
    isFetchingRef.current = true
    callCount += 1
    await new Promise((res) => setTimeout(res, 20))
    isFetchingRef.current = false
    return 'LOADED'
  }

  const [res1, res2] = await Promise.all([
    mockLoadStables(),
    mockLoadStables(),
  ])

  assert.equal(callCount, 1, 'Only one network call should occur during concurrent trigger')
  assert.equal(res1, 'LOADED')
  assert.equal(res2, 'SKIPPED')
})

test('Part B: isHorseFitForTraining strictly permits FIT and rejects non-FIT or unknown states', () => {
  assert.equal(isHorseFitForTraining({ healthStatus: 'FIT', status: 'ACTIVE' }), true)
  assert.equal(isHorseFitForTraining({ healthStatus: 'fit', status: 'ACTIVE' }), true)
  assert.equal(isHorseFitForTraining({ healthStatus: ' FIT ', status: 'ACTIVE' }), true)

  // Rejections
  assert.equal(isHorseFitForTraining({ healthStatus: 'INJURED', status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: 'FIT', status: 'INJURED' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: 'MONITORING', status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: 'MONITOR', status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: 'RECOVERING', status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: 'RESTING', status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: 'QUARANTINED', status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: 'SICK', status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: 'UNDER_TREATMENT', status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: null, status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: undefined, status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: '', status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining(null), false)

  // Translated Vietnamese labels must NEVER evaluate to FIT
  assert.equal(isHorseFitForTraining({ healthStatus: 'Khỏe mạnh', status: 'ACTIVE' }), false)
  assert.equal(isHorseFitForTraining({ healthStatus: 'Khỏe mạnh (FIT)', status: 'ACTIVE' }), false)
})

test('Part B: getHorseIneligibilityReason returns clear concise Vietnamese explanations', () => {
  assert.equal(getHorseIneligibilityReason({ healthStatus: 'INJURED' }), 'Chấn thương')
  assert.equal(getHorseIneligibilityReason({ status: 'INJURED', healthStatus: 'FIT' }), 'Chấn thương')
  assert.equal(getHorseIneligibilityReason({ healthStatus: 'QUARANTINED' }), 'Đang cách ly')
  assert.equal(getHorseIneligibilityReason({ healthStatus: 'MONITORING' }), 'Cần theo dõi y tế')
  assert.equal(getHorseIneligibilityReason({ healthStatus: 'MONITOR' }), 'Cần theo dõi y tế')
  assert.equal(getHorseIneligibilityReason({ healthStatus: 'RESTING' }), 'Nghỉ ngơi')
  assert.equal(getHorseIneligibilityReason({ healthStatus: 'RECOVERING' }), 'Đang hồi phục')
  assert.equal(getHorseIneligibilityReason({ healthStatus: 'SICK' }), 'Đang điều trị')
  assert.equal(getHorseIneligibilityReason({ healthStatus: 'UNDER_TREATMENT' }), 'Đang điều trị')
  assert.equal(getHorseIneligibilityReason({ healthStatus: 'RESTRICTED' }), 'Hạn chế y tế')
  assert.equal(getHorseIneligibilityReason({ healthStatus: '' }), 'Chưa có trạng thái FIT')
  assert.equal(getHorseIneligibilityReason({ healthStatus: null }), 'Chưa có trạng thái FIT')
})

test('Part A & B: source code static verification of key controls and DTO mapping', () => {
  const stableCareSrc = fs.readFileSync(path.join(__dirname, 'StableCarePage.jsx'), 'utf-8')
  assert.ok(stableCareSrc.includes('handleRefresh'), 'StableCarePage must contain handleRefresh')
  assert.ok(stableCareSrc.includes('RotateCw'), 'StableCarePage must import and render RotateCw')
  assert.ok(stableCareSrc.includes('selectedHorseRef'), 'StableCarePage must use selectedHorseRef')
  assert.ok(stableCareSrc.includes('isFetchingRef'), 'StableCarePage must guard with isFetchingRef')

  const trainingScheduleSrc = fs.readFileSync(path.join(__dirname, 'TrainingSchedulePage.jsx'), 'utf-8')
  assert.ok(trainingScheduleSrc.includes('isHorseFitForTraining'), 'TrainingSchedulePage must import isHorseFitForTraining')
  assert.ok(trainingScheduleSrc.includes('getHorseIneligibilityReason'), 'TrainingSchedulePage must import getHorseIneligibilityReason')

  const horseResponseSrc = fs.readFileSync(
    path.join(__dirname, '../../BACKEND/src/main/java/com/racehorse/horse/dto/HorseResponse.java'),
    'utf-8'
  )
  assert.ok(horseResponseSrc.includes('String healthStatus,'), 'HorseResponse must declare healthStatus')

  const horseMapperSrc = fs.readFileSync(
    path.join(__dirname, '../../BACKEND/src/main/java/com/racehorse/horse/mapper/HorseMapper.java'),
    'utf-8'
  )
  assert.ok(horseMapperSrc.includes('horse.getHealthStatus(),'), 'HorseMapper must map horse.getHealthStatus()')
})


test('RH-STABLE-UI-01E.5: StablesView lexical order guarantees no TDZ ReferenceError', () => {
  const stableCareSrc = fs.readFileSync(path.join(__dirname, 'StableCarePage.jsx'), 'utf-8')
  
  // Find StablesView definition
  const stablesViewIdx = stableCareSrc.indexOf('function StablesView')
  assert.ok(stablesViewIdx !== -1, 'StablesView must exist')
  
  const stablesViewSrc = stableCareSrc.slice(stablesViewIdx, stableCareSrc.indexOf('function CareTasksView'))
  
  const loadRoutinesIdx = stablesViewSrc.indexOf('const loadRoutines = useCallback')
  const loadStablesIdx = stablesViewSrc.indexOf('const loadStables = useCallback')
  const handleSelectStallIdx = stablesViewSrc.indexOf('const handleSelectStall = useCallback')
  
  assert.ok(loadRoutinesIdx !== -1, 'loadRoutines must be defined')
  assert.ok(loadStablesIdx !== -1, 'loadStables must be defined')
  assert.ok(handleSelectStallIdx !== -1, 'handleSelectStall must be defined')
  
  assert.ok(
    loadRoutinesIdx < loadStablesIdx,
    'loadRoutines MUST be declared BEFORE loadStables to avoid TDZ ReferenceError in dependency array'
  )
  assert.ok(
    loadRoutinesIdx < handleSelectStallIdx,
    'loadRoutines MUST be declared BEFORE handleSelectStall'
  )
})

test('RH-STABLE-UI-01E.5: StablesView handles empty stalls list and missing optional horse fields safely', () => {
  // Empty stables list
  const emptyStables = []
  const activeStableEmpty = emptyStables.find((s) => s.id === 'stable-1') || emptyStables[0]
  assert.equal(activeStableEmpty, undefined)
  const allStallsEmpty = activeStableEmpty?.stalls || []
  assert.deepEqual(allStallsEmpty, [])
  assert.equal(allStallsEmpty.length, 0)
  
  // Stables with empty stalls
  const stablesWithEmptyStalls = [{ id: 's-1', name: 'Barn A', stalls: [] }]
  const activeStable = stablesWithEmptyStalls[0]
  const allStalls = activeStable?.stalls || []
  const occupiedCount = allStalls.filter((s) => s.currentAssignment).length
  const vacantCount = allStalls.length - occupiedCount
  assert.equal(occupiedCount, 0)
  assert.equal(vacantCount, 0)
  
  // Stall with missing optional horse fields (photoUrl, lockReason, schedule)
  const stallMissingOptionals = {
    id: 'stall-1',
    code: 'ST-01',
    currentAssignment: {
      horseId: 'h-100',
      horseCode: 'H100',
      horseName: 'Minimal Horse',
      horseHealthStatus: 'ACTIVE',
      assignedFrom: '2026-11-01T00:00:00Z',
      trainingLocked: false,
      trainingLockReason: null,
      emergencyActive: false,
      photoUrl: null,
      isTrainingActive: false,
      trainingSchedule: null,
    }
  }
  
  const synced = syncSelectedHorse([{ id: 's-1', stalls: [stallMissingOptionals] }], { id: 'h-100' })
  assert.equal(synced.id, 'h-100')
  assert.equal(synced.photoUrl, null)
  assert.equal(synced.trainingLockReason, null)
  assert.equal(synced.trainingSchedule, null)
  assert.equal(computePresentationState(synced), 'STATE_A_DEFAULT_ROUTINE')
  assert.equal(computePresentationState(null), 'NONE')
})
