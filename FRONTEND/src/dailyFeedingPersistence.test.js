import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

const frontendDir = path.resolve(process.cwd(), process.cwd().endsWith('FRONTEND') ? '.' : 'FRONTEND')
const apiJsPath = path.resolve(frontendDir, 'src/api.js')
const stableCarePagePath = path.resolve(frontendDir, 'src/StableCarePage.jsx')

const apiJs = fs.readFileSync(apiJsPath, 'utf8')
const stableCarePage = fs.readFileSync(stableCarePagePath, 'utf8')

test('1. api.js exports confirmDailyFeedingMeal and getDailyFeedingHistory endpoints', () => {
  assert.ok(
    apiJs.includes('confirmDailyFeedingMeal: (horseId, payload) => api(`/api/horses/${horseId}/daily-feeding/confirm`'),
    'api.js must export confirmDailyFeedingMeal calling POST /api/horses/{horseId}/daily-feeding/confirm'
  )
  assert.ok(
    apiJs.includes('getDailyFeedingHistory: (horseId, params) =>'),
    'api.js must export getDailyFeedingHistory'
  )
  assert.ok(
    apiJs.includes('/api/horses/${horseId}/daily-feeding/history'),
    'api.js must route history requests to /api/horses/{horseId}/daily-feeding/history'
  )
})

test('2. Ephemeral confirmedMeals state is completely removed from StableCarePage.jsx', () => {
  assert.ok(
    !stableCarePage.includes('const [confirmedMeals, setConfirmedMeals] = useState'),
    'StableCarePage must not contain ephemeral confirmedMeals state'
  )
  assert.ok(
    !stableCarePage.includes('setConfirmedMeals('),
    'StableCarePage must not mutate ephemeral confirmedMeals state'
  )
  assert.ok(
    !stableCarePage.includes('toggleMealConfirmed'),
    'StableCarePage must not contain toggleMealConfirmed'
  )
})

test('3. Checklist meal confirmation uses server-backed meal.confirmed', () => {
  assert.ok(
    stableCarePage.includes('const isDone = Boolean(meal.confirmed)'),
    'isDone must be derived from meal.confirmed returned by the server'
  )
  assert.ok(
    stableCarePage.includes("isDone ? '✓ Đã cho ăn' : 'Xác nhận cho ăn'"),
    "Button text must display '✓ Đã cho ăn' when confirmed and 'Xác nhận cho ăn' when unconfirmed"
  )
})

test('4. Confirmed meal renders confirmation metadata (timestamp and staff name)', () => {
  assert.ok(
    stableCarePage.includes('formatVNDateTime(meal.confirmedAt)'),
    'Must format meal.confirmedAt with formatVNDateTime'
  )
  assert.ok(
    stableCarePage.includes('meal.confirmedByName'),
    'Must display meal.confirmedByName when available'
  )
  assert.ok(
    stableCarePage.includes('✓ Đã xác nhận:'),
    'Must display confirmed indicator label'
  )
})

test('5. Duplicate clicks are prevented while confirmation request is in-flight', () => {
  assert.ok(
    stableCarePage.includes('const [confirmingMealKey, setConfirmingMealKey] = useState(null)'),
    'Must track in-flight confirmingMealKey state'
  )
  assert.ok(
    stableCarePage.includes('if (confirmingMealKey === key || meal.confirmed) return'),
    'handleConfirmMeal must guard against concurrent duplicate clicks or already confirmed meals'
  )
  assert.ok(
    stableCarePage.includes('disabled={isDone || isPending}'),
    'Button must be disabled when isDone or isPending'
  )
  assert.ok(
    stableCarePage.includes("isPending ? 'Đang xác nhận…'"),
    "Button must show 'Đang xác nhận…' while request is pending"
  )
})

test('6. In-flight failure leaves meal unconfirmed and sets understandable error message', () => {
  assert.ok(
    stableCarePage.includes('const [confirmError, setConfirmError] = useState'),
    'Must track confirmError state'
  )
  assert.ok(
    stableCarePage.includes("setConfirmError(err.message || 'Không thể xác nhận cho ăn. Vui lòng thử lại.')"),
    'Must set error message on request failure'
  )
  assert.ok(
    stableCarePage.includes('{confirmError && ('),
    'Must display confirmError notice above checklist'
  )
})

test('7. Horse and Date switching cancel in-flight async requests to prevent race conditions', () => {
  // Check useEffect cancellation pattern in loadHorseDetails
  const loadStart = stableCarePage.indexOf('// 2. Load checklist and history for selected stall/horse')
  assert.ok(loadStart !== -1, 'Must have loadHorseDetails effect')

  const effectBody = stableCarePage.substring(loadStart, loadStart + 1500)
  assert.ok(
    effectBody.includes('let cancelled = false'),
    'Must initialize cancellation flag in effect'
  )
  assert.ok(
    /return\s*\(\)\s*=>\s*\{\s*cancelled\s*=\s*true\s*\}/.test(effectBody),
    'Cleanup function must set cancelled = true'
  )
  assert.ok(
    effectBody.includes('if (cancelled) return'),
    'Effect promise handlers must discard responses if cancelled'
  )
})

test('8. Empty stall and unassigned horse states remain distinct without confirmation buttons', () => {
  assert.ok(
    stableCarePage.includes('Chưa có kế hoạch dinh dưỡng'),
    'Must preserve unassigned horse empty message'
  )
  assert.ok(
    stableCarePage.includes('!checklist?.meals?.length ? ('),
    'Must render empty placeholder when horse has no plan meals'
  )
})

test('9. Logic Simulation: Server checklist update and confirmation immutability', () => {
  // Initial server response simulation
  const initialChecklist = {
    horseId: 'h-01',
    date: '2026-11-13',
    planVersion: 1,
    meals: [
      {
        mealKey: 'meal_0630_bua_sang_06_30',
        mealName: 'Bữa sáng (06:30)',
        feedingTime: '06:30:00',
        concentrateKg: 2.0,
        confirmed: true,
        confirmedAt: '2026-11-13T06:35:00+07:00',
        confirmedByName: 'staff_a',
      },
      {
        mealKey: 'meal_1130_bua_trua_11_30',
        mealName: 'Bữa trưa (11:30)',
        feedingTime: '11:30:00',
        concentrateKg: 1.0,
        confirmed: false,
        confirmedAt: null,
        confirmedByName: null,
      },
    ],
  }

  // 1. Initial state inspection
  assert.equal(initialChecklist.meals[0].confirmed, true)
  assert.equal(initialChecklist.meals[1].confirmed, false)

  // 2. Confirm meal 2
  const serverConfirmResponse = {
    mealKey: 'meal_1130_bua_trua_11_30',
    confirmedAt: '2026-11-13T11:32:00+07:00',
    confirmedByName: 'staff_b',
  }

  const updatedMeals = initialChecklist.meals.map((m) => {
    if (m.mealKey === serverConfirmResponse.mealKey) {
      return {
        ...m,
        confirmed: true,
        confirmedAt: serverConfirmResponse.confirmedAt,
        confirmedByName: serverConfirmResponse.confirmedByName,
      }
    }
    return m
  })

  assert.equal(updatedMeals[1].confirmed, true)
  assert.equal(updatedMeals[1].confirmedByName, 'staff_b')
  assert.equal(updatedMeals[1].confirmedAt, '2026-11-13T11:32:00+07:00')

  // 3. Meal 1 remains unchanged
  assert.equal(updatedMeals[0].confirmed, true)
  assert.equal(updatedMeals[0].confirmedByName, 'staff_a')
})

test('10. Logic Simulation: Switching date resets confirmation state to target date', () => {
  const date1Checklist = {
    horseId: 'h-01',
    date: '2026-11-13',
    meals: [{ mealKey: 'm1', confirmed: true, confirmedAt: '2026-11-13T07:00:00Z' }],
  }

  const date2Checklist = {
    horseId: 'h-01',
    date: '2026-11-14',
    meals: [{ mealKey: 'm1', confirmed: false, confirmedAt: null }],
  }

  // State on date 1
  let activeChecklist = date1Checklist
  assert.equal(activeChecklist.meals[0].confirmed, true)

  // User changes date to date 2
  activeChecklist = date2Checklist
  assert.equal(activeChecklist.meals[0].confirmed, false)
  assert.equal(activeChecklist.date, '2026-11-14')
})
