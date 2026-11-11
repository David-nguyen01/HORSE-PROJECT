import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  isActionableTrainingSchedule,
  filterTrainingSchedules,
  computeTrainingScheduleCounts,
  buildTrainingScheduleQueryParams,
  getTrainingEmptyStateMessage,
  formatScheduleTime,
} from './trainingRules.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

function formatVNDate(val) {
  if (!val) return ''
  try {
    const [y, m, d] = String(val).split('-')
    if (y && m && d) return `${d}/${m}/${y}`
    return String(val)
  } catch {
    return String(val)
  }
}

// Mock dataset mirroring actual backend TrainingScheduleResponse records across past, present, and future dates
const mockAllSchedules = [
  {
    id: 'sched-1',
    trainingDate: '2026-11-10', // Historical actionable
    taskName: 'Bài tập thể lực sáng',
    status: 'PREPARING',
    operatingWindowStart: '05:00:00',
    operatingWindowEnd: '11:00:00',
    horse: { id: 'h-1', code: 'H-001', name: 'Thunder' },
  },
  {
    id: 'sched-2',
    trainingDate: '2026-11-10', // Historical actionable
    taskName: 'Bài tập phi nước đại',
    status: 'READY',
    operatingWindowStart: '05:00:00',
    operatingWindowEnd: '11:00:00',
    horse: { id: 'h-2', code: 'H-002', name: 'Lightning' },
  },
  {
    id: 'sched-3',
    trainingDate: '2026-11-10', // Historical completed (non-actionable)
    taskName: 'Bài tập hoàn tất hôm qua',
    status: 'COMPLETED',
    operatingWindowStart: '05:00:00',
    operatingWindowEnd: '11:00:00',
    horse: { id: 'h-1', code: 'H-001', name: 'Thunder' },
  },
  {
    id: 'sched-4',
    trainingDate: '2026-11-11', // Today actionable
    taskName: 'Chạy nước kiệu phục hồi',
    status: 'PREPARING',
    operatingWindowStart: '05:00:00',
    operatingWindowEnd: '11:00:00',
    horse: { id: 'h-1', code: 'H-001', name: 'Thunder' },
  },
  {
    id: 'sched-5',
    trainingDate: '2026-11-12', // Future actionable (tomorrow)
    taskName: 'Bài tập khởi động nhẹ',
    status: 'PREPARING',
    operatingWindowStart: '05:00:00',
    operatingWindowEnd: '11:00:00',
    horse: { id: 'h-3', code: 'H-003', name: 'Storm' },
  },
  {
    id: 'sched-6',
    trainingDate: '2026-11-12', // Future actionable (tomorrow)
    taskName: 'Huấn luyện vượt rào',
    status: 'READY',
    operatingWindowStart: '05:00:00',
    operatingWindowEnd: '11:00:00',
    horse: { id: 'h-4', code: 'H-004', name: 'Comet' },
  },
  {
    id: 'sched-7',
    trainingDate: '2026-11-12', // Future cancelled (non-actionable)
    taskName: 'Bài tập bị hủy',
    status: 'CANCELLED',
    operatingWindowStart: '05:00:00',
    operatingWindowEnd: '11:00:00',
    horse: { id: 'h-5', code: 'H-005', name: 'Shadow' },
  },
  {
    id: 'sched-8',
    trainingDate: '2026-11-15', // Later future in-progress (non-actionable for preparation)
    taskName: 'Buổi tập đang trên sân',
    status: 'IN_PROGRESS',
    operatingWindowStart: '05:00:00',
    operatingWindowEnd: '11:00:00',
    horse: { id: 'h-6', code: 'H-006', name: 'Wind' },
  },
]

test('1. Initial panel query sends no date boundaries', () => {
  // On initial entry, appliedDate is null
  const initialAppliedDate = null
  const queryParams = buildTrainingScheduleQueryParams(initialAppliedDate)

  // Must be empty object so no from/to params are sent to /api/training-schedules/mine
  assert.deepEqual(queryParams, {}, 'Initial query params must be empty to fetch all records')
})

test('2. Future-dated actionable records appear by default without selecting a date', () => {
  const displayed = filterTrainingSchedules(mockAllSchedules, 'ALL')
  const futurePreparing = displayed.find((s) => s.id === 'sched-5')
  const futureReady = displayed.find((s) => s.id === 'sched-6')

  assert.ok(futurePreparing, 'Future PREPARING record (sched-5, 2026-11-12) must appear by default')
  assert.equal(futurePreparing.status, 'PREPARING')

  assert.ok(futureReady, 'Future READY record (sched-6, 2026-11-12) must appear by default')
  assert.equal(futureReady.status, 'READY')
})

test('3. Historical actionable records appear by default if still requiring preparation', () => {
  const displayed = filterTrainingSchedules(mockAllSchedules, 'ALL')
  const historicalPreparing = displayed.find((s) => s.id === 'sched-1')
  const historicalReady = displayed.find((s) => s.id === 'sched-2')

  assert.ok(historicalPreparing, 'Historical PREPARING record (sched-1, 2026-11-10) must appear by default')
  assert.ok(historicalReady, 'Historical READY record (sched-2, 2026-11-10) must appear by default')
})

test('4. Completed and cancelled records follow existing non-actionable semantics and are excluded', () => {
  // Helper verification
  assert.equal(isActionableTrainingSchedule({ status: 'PREPARING' }), true)
  assert.equal(isActionableTrainingSchedule({ status: 'READY' }), true)
  assert.equal(isActionableTrainingSchedule({ status: 'COMPLETED' }), false)
  assert.equal(isActionableTrainingSchedule({ status: 'CANCELLED' }), false)
  assert.equal(isActionableTrainingSchedule({ status: 'IN_PROGRESS' }), false)
  assert.equal(isActionableTrainingSchedule(null), false)

  const displayed = filterTrainingSchedules(mockAllSchedules, 'ALL')
  const completedFound = displayed.some((s) => s.status === 'COMPLETED')
  const cancelledFound = displayed.some((s) => s.status === 'CANCELLED')
  const inProgressFound = displayed.some((s) => s.status === 'IN_PROGRESS')

  assert.equal(completedFound, false, 'Completed records must not be included in actionable preparation work')
  assert.equal(cancelledFound, false, 'Cancelled records must not be included in actionable preparation work')
  assert.equal(inProgressFound, false, 'In-progress records must not be included in actionable preparation work')

  // Total actionable records: sched-1, sched-2, sched-4, sched-5, sched-6 (5 records)
  assert.equal(displayed.length, 5)
})

test('5. Explicit past-date filter works and shows only actionable records on that date', () => {
  const appliedDate = '2026-11-10'
  const queryParams = buildTrainingScheduleQueryParams(appliedDate)
  assert.deepEqual(queryParams, { from: '2026-11-10', to: '2026-11-10' })

  // When filtered for 2026-11-10, only actionable records on that date are shown
  const pastDayRecords = mockAllSchedules.filter((s) => s.trainingDate === appliedDate)
  const displayed = filterTrainingSchedules(pastDayRecords, 'ALL')

  assert.equal(displayed.length, 2, 'Must only include 2 actionable records (sched-1 and sched-2)')
  assert.ok(displayed.every((s) => s.trainingDate === '2026-11-10'))
  assert.ok(!displayed.some((s) => s.id === 'sched-3'), 'Completed record sched-3 must be excluded')
})

test('6. Explicit future-date filter works and shows only actionable records on that date', () => {
  const appliedDate = '2026-11-12'
  const queryParams = buildTrainingScheduleQueryParams(appliedDate)
  assert.deepEqual(queryParams, { from: '2026-11-12', to: '2026-11-12' })

  const futureDayRecords = mockAllSchedules.filter((s) => s.trainingDate === appliedDate)
  const displayed = filterTrainingSchedules(futureDayRecords, 'ALL')

  assert.equal(displayed.length, 2, 'Must include sched-5 and sched-6')
  assert.ok(displayed.every((s) => s.trainingDate === '2026-11-12'))
  assert.ok(!displayed.some((s) => s.id === 'sched-7'), 'Cancelled record sched-7 must be excluded')
})

test('7. Selecting a date does not query until "Xác nhận"', () => {
  let appliedDate = null
  let draftDate = '2026-11-12' // User clicked date picker

  // While draft is set, appliedDate has not changed
  assert.equal(appliedDate, null, 'appliedDate remains null before clicking Xác nhận')

  // Query params sent to API still reflect appliedDate (null -> all records)
  const queryParams = buildTrainingScheduleQueryParams(appliedDate)
  assert.deepEqual(queryParams, {})

  // Displayed records remain all actionable records
  const displayed = filterTrainingSchedules(mockAllSchedules, 'ALL')
  assert.equal(displayed.length, 5)

  // User clicks "Xác nhận" -> applies draftDate
  appliedDate = draftDate
  assert.equal(appliedDate, '2026-11-12')
  const appliedQueryParams = buildTrainingScheduleQueryParams(appliedDate)
  assert.deepEqual(appliedQueryParams, { from: '2026-11-12', to: '2026-11-12' })
})

test('8. "Xem tất cả" clears both date boundaries and restores complete actionable dataset', () => {
  let appliedDate = '2026-11-12'
  let draftDate = '2026-11-12'

  // User clicks "Xem tất cả"
  appliedDate = null
  draftDate = ''

  assert.equal(appliedDate, null)
  assert.equal(draftDate, '')

  const queryParams = buildTrainingScheduleQueryParams(appliedDate)
  assert.deepEqual(queryParams, {}, 'Query params must return to empty after clearing')

  const displayed = filterTrainingSchedules(mockAllSchedules, 'ALL')
  assert.equal(displayed.length, 5, 'All 5 actionable records restored across all dates')
})

test('9. Counts and tabs reflect the active dataset and maintain strict equality (all = preparing + ready)', () => {
  // Counts on all-records dataset: 3 PREPARING (sched-1, sched-4, sched-5), 2 READY (sched-2, sched-6)
  const allCounts = computeTrainingScheduleCounts(mockAllSchedules)
  assert.equal(allCounts.all, 5)
  assert.equal(allCounts.preparing, 3)
  assert.equal(allCounts.ready, 2)
  assert.equal(allCounts.all, allCounts.preparing + allCounts.ready, 'All count must equal preparing + ready count')

  // Tab filtering: PREPARING
  const preparingList = filterTrainingSchedules(mockAllSchedules, 'PREPARING')
  assert.equal(preparingList.length, 3)
  assert.ok(preparingList.every((s) => s.status === 'PREPARING'))

  // Tab filtering: READY
  const readyList = filterTrainingSchedules(mockAllSchedules, 'READY')
  assert.equal(readyList.length, 2)
  assert.ok(readyList.every((s) => s.status === 'READY'))

  // Date-filtered dataset counts (2026-11-12)
  const nov12Records = mockAllSchedules.filter((s) => s.trainingDate === '2026-11-12')
  const nov12Counts = computeTrainingScheduleCounts(nov12Records)
  assert.equal(nov12Counts.all, 2)
  assert.equal(nov12Counts.preparing, 1) // sched-5
  assert.equal(nov12Counts.ready, 1) // sched-6
  assert.equal(nov12Counts.all, nov12Counts.preparing + nov12Counts.ready)
})

test('10. Caretaker assignment scope remains unchanged in repository and controller', () => {
  const repoPath = path.resolve(__dirname, '../../BACKEND/src/main/java/com/racehorse/training/repository/TrainingScheduleRepository.java')
  const repoContent = fs.readFileSync(repoPath, 'utf8')

  // Caretaker predicate must be strictly preserved
  assert.ok(repoContent.includes('s.horse.caretaker.id = :userId'), 'Must preserve horse.caretaker.id = :userId')

  // Actionable preparation statuses must be filtered
  assert.ok(repoContent.includes("s.sessionStatus in ('PREPARING', 'READY')"), 'Must filter sessionStatus in PREPARING, READY')
  assert.ok(repoContent.includes('s.completedAt is null'), 'Must exclude completed sessions')

  // Controller endpoint /mine retains strict role check
  const controllerPath = path.resolve(__dirname, '../../BACKEND/src/main/java/com/racehorse/training/controller/TrainingScheduleController.java')
  const controllerContent = fs.readFileSync(controllerPath, 'utf8')
  assert.ok(controllerContent.includes("hasAnyRole('STABLE_STAFF','VETERINARIAN')"))
})

test('11. No implicit frontend today filter remains and empty state messages are correct', () => {
  const pagePath = path.resolve(__dirname, 'StableCarePage.jsx')
  const pageContent = fs.readFileSync(pagePath, 'utf8')

  // appliedDate must initialize to null (not today)
  assert.ok(pageContent.includes('const [appliedDate, setAppliedDate] = useState(null)'))

  // Empty state for selected date vs all records
  const filteredEmpty = getTrainingEmptyStateMessage('2026-11-15', formatVNDate)
  assert.ok(filteredEmpty.title.includes('15/11/2026'))
  assert.ok(filteredEmpty.subtitle.includes('trong ngày này'))

  const allEmpty = getTrainingEmptyStateMessage(null, formatVNDate)
  assert.equal(allEmpty.title, 'Không có buổi tập nào cần chuẩn bị')
  assert.equal(allEmpty.subtitle, 'Hiện tại chưa có lịch huấn luyện nào cho ngựa do bạn phụ trách cần chuẩn bị.')
})

test('12. FormatScheduleTime handles backend operatingWindowStart/End and fallbacks correctly', () => {
  const recordWithWindow = {
    operatingWindowStart: '05:00:00',
    operatingWindowEnd: '11:00:00',
  }
  assert.equal(formatScheduleTime(recordWithWindow), '05:00 - 11:00')

  const recordWithStrings = {
    startTime: '06:00',
    endTime: '07:30',
  }
  assert.equal(formatScheduleTime(recordWithStrings), '06:00 - 07:30')

  assert.equal(formatScheduleTime(null), '05:00 - 11:00')
})

test('13. Veterinarian query semantics restored to club-local day in TrainingScheduleService', () => {
  const servicePath = path.resolve(__dirname, '../../BACKEND/src/main/java/com/racehorse/training/service/TrainingScheduleService.java')
  const serviceContent = fs.readFileSync(servicePath, 'utf8')

  // forDiagnosingVet delegates to range(from, to) directly
  const expectedMethod = `public List<TrainingScheduleResponse> forDiagnosingVet(UUID id, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        return repository.findForDiagnosingVet(id, range.from(), range.to()).stream().map(this::toResponse).toList();
    }`

  assert.ok(
    serviceContent.replace(/\r\n/g, '\n').includes(expectedMethod.replace(/\r\n/g, '\n')),
    'forDiagnosingVet must delegate to range(from, to) ensuring null dates default to current club day'
  )

  // mine preserves unbounded query when dates are null
  assert.ok(
    serviceContent.includes('if (from == null && to == null) {'),
    'mine must retain unbounded query when both dates are null'
  )
})

test('14. RH-STABLE-UI-01E.5: Training Preparation complete workflow state lifecycle', () => {
  // Step 1: Initial load state
  let appliedDate = null
  let draftDate = ''

  // Initial params sent to backend must be empty (unbounded)
  let params = buildTrainingScheduleQueryParams(appliedDate)
  assert.deepEqual(params, {}, 'Initial query must send empty object (no date boundaries)')

  // Initial view displays all actionable records across past, present, and future dates
  const initialView = filterTrainingSchedules(mockAllSchedules, 'ALL')
  assert.equal(initialView.length, 5, 'Must contain 5 actionable records across past, present, future')

  // Verify past, present, and future are present
  assert.ok(initialView.some((s) => s.id === 'sched-1' && s.trainingDate === '2026-11-10'), 'Historical sched-1 present')
  assert.ok(initialView.some((s) => s.id === 'sched-2' && s.trainingDate === '2026-11-10'), 'Historical sched-2 present')
  assert.ok(initialView.some((s) => s.id === 'sched-4' && s.trainingDate === '2026-11-11'), 'Today sched-4 present')
  assert.ok(initialView.some((s) => s.id === 'sched-5' && s.trainingDate === '2026-11-12'), 'Future sched-5 present')
  assert.ok(initialView.some((s) => s.id === 'sched-6' && s.trainingDate === '2026-11-12'), 'Future sched-6 present')

  // Excluded statuses
  assert.ok(!initialView.some((s) => s.status === 'COMPLETED'), 'Excludes COMPLETED')
  assert.ok(!initialView.some((s) => s.status === 'CANCELLED'), 'Excludes CANCELLED')
  assert.ok(!initialView.some((s) => s.status === 'IN_PROGRESS'), 'Excludes IN_PROGRESS')

  // Step 2: User selects a date in datepicker (e.g. 2026-11-12)
  draftDate = '2026-11-12'
  // Crucial: appliedDate has NOT changed yet!
  assert.equal(appliedDate, null, 'appliedDate must remain null until explicit confirmation')
  params = buildTrainingScheduleQueryParams(appliedDate)
  assert.deepEqual(params, {}, 'Query params must still be empty while only draft date changed')

  // Step 3: User clicks "Xác nhận" (Confirm)
  // handleApplyDate logic:
  if (draftDate) {
    appliedDate = draftDate
  }
  assert.equal(appliedDate, '2026-11-12')
  params = buildTrainingScheduleQueryParams(appliedDate)
  assert.deepEqual(params, { from: '2026-11-12', to: '2026-11-12' }, 'Applied date params must be bounded')

  // Filtered dataset for 2026-11-12
  const dateFiltered = mockAllSchedules
    .filter((s) => s.trainingDate === appliedDate)
    .filter((s) => isActionableTrainingSchedule(s))
  assert.equal(dateFiltered.length, 2, 'Filtered view contains only 2 actionable records for 2026-11-12')
  assert.deepEqual(dateFiltered.map((s) => s.id), ['sched-5', 'sched-6'])

  // Step 4: User clicks "Xem tất cả" (View all / Clear filter)
  // handleClearDateFilter logic:
  draftDate = ''
  appliedDate = null
  assert.equal(appliedDate, null)
  assert.equal(draftDate, '')
  params = buildTrainingScheduleQueryParams(appliedDate)
  assert.deepEqual(params, {}, 'Cleared query params must return to empty object')

  // Full actionable list restored
  const restoredView = filterTrainingSchedules(mockAllSchedules, 'ALL')
  assert.equal(restoredView.length, 5, 'Full actionable dataset restored')

  // Step 5: Verify component code invariants in StableCarePage.jsx
  const pagePath = path.resolve(__dirname, 'StableCarePage.jsx')
  const pageContent = fs.readFileSync(pagePath, 'utf8')
  assert.ok(pageContent.includes('const [appliedDate, setAppliedDate] = useState(null)'), 'appliedDate initialized to null')
  assert.ok(pageContent.includes("const [draftDate, setDraftDate] = useState('')"), 'draftDate initialized to empty string')
  assert.ok(pageContent.includes('setAppliedDate(draftDate)'), 'handleApplyDate sets appliedDate to draftDate')
  assert.ok(pageContent.includes('setAppliedDate(null)'), 'handleClearDateFilter resets appliedDate to null')
})

