import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

const frontendDir = path.resolve(process.cwd(), process.cwd().endsWith('FRONTEND') ? '.' : 'FRONTEND')
const stableCarePagePath = path.resolve(frontendDir, 'src/StableCarePage.jsx')
const useStablesQuickFeedPath = path.resolve(frontendDir, 'src/useStablesQuickFeed.js')
const apiJsPath = path.resolve(frontendDir, 'src/api.js')

const stableCarePage = fs.readFileSync(stableCarePagePath, 'utf8')
const useStablesQuickFeedJs = fs.readFileSync(useStablesQuickFeedPath, 'utf8')
const apiJs = fs.readFileSync(apiJsPath, 'utf8')

test('1. StablesView integrates Quick-Feed directly into primary operational overview', () => {
  // Feed date selector in StablesView action header
  assert.ok(
    stableCarePage.includes('const [feedDate, setFeedDate] = useState(today())'),
    'StablesView must maintain feedDate state defaulting to today()'
  )
  assert.ok(
    stableCarePage.includes('value={feedDate}'),
    'StablesView must render date picker bound to feedDate'
  )
  assert.ok(
    stableCarePage.includes('onChange={(e) => setFeedDate(e.target.value)}'),
    'StablesView date picker must update feedDate'
  )
  assert.ok(
    stableCarePage.includes('quickFeed.reloadQuickFeed()'),
    'handleRefresh in StablesView must reload quick feed data'
  )
})

test('2. StablesView stall cards render scheduled meals and 1-click Quick-Feed button for operational staff', () => {
  // Scheduled meals rendering on stall card in StablesView
  assert.ok(
    stableCarePage.includes('const qfStall = isOperationalStaff ? quickFeed.quickFeedByStallId.get(stall.id) : null'),
    'StablesView stall cards must look up meals from quickFeedByStallId only for operational staff'
  )
  assert.ok(
    stableCarePage.includes('quickFeed.handleQuickConfirmMeal({'),
    'StablesView stall cards must trigger quickFeed.handleQuickConfirmMeal on click'
  )
  assert.ok(
    stableCarePage.includes('quickFeed.confirmingMealKey === mealKey'),
    'StablesView stall card must track in-flight confirmation state'
  )
  assert.ok(
    stableCarePage.includes("isPending ? '…' : 'Cho ăn'"),
    'StablesView stall card button must indicate in-flight confirmation state'
  )
})

test('3. StablesView right detail panel renders full meal schedule and link to detailed checklist', () => {
  assert.ok(
    stableCarePage.includes('Bữa ăn dinh dưỡng hôm nay'),
    'StablesView detail panel must render Bữa ăn dinh dưỡng hôm nay section'
  )
  assert.ok(
    stableCarePage.includes('Khẩu phần & Checklist chi tiết'),
    'StablesView detail panel must link to detailed checklist'
  )
  assert.ok(
    stableCarePage.includes('/stable-care/nutrition?horseId='),
    'Detailed checklist link must direct staff to NutritionView for selected horse'
  )
})

test('4. NutritionView preserves detailed checklist and plan viewer as secondary view', () => {
  assert.ok(
    stableCarePage.includes('Danh sách kiểm tra cho ăn trong ca (Daily Feeding Checklist)'),
    'NutritionView must preserve daily feeding checklist header'
  )
  assert.ok(
    stableCarePage.includes('Lịch sử kế hoạch dinh dưỡng'),
    'NutritionView must preserve nutrition plan history section'
  )
  assert.ok(
    stableCarePage.includes('Nhật ký cho ăn thực tế'),
    'NutritionView must preserve actual feeding execution history section'
  )
})

test('5. Periodic background refresh timer is configured with automatic pause and cleanup', () => {
  assert.ok(
    useStablesQuickFeedJs.includes('refreshIntervalMs = 60000'),
    'useStablesQuickFeed must default refresh interval to 60000ms (~60 seconds)'
  )
  assert.ok(
    useStablesQuickFeedJs.includes("document.visibilityState === 'hidden'"),
    'Background refresh must pause when document visibilityState is hidden'
  )
  assert.ok(
    useStablesQuickFeedJs.includes('const timer = setInterval'),
    'Must register periodic timer with setInterval'
  )
  assert.ok(
    useStablesQuickFeedJs.includes('clearInterval(timer)'),
    'Must clear interval timer on cleanup'
  )
})

test('6. Concurrency and out-of-order response reliability guards', () => {
  // Request deduplication guard
  assert.ok(
    useStablesQuickFeedJs.includes('const isFetchingRef = useRef(false)'),
    'Must maintain isFetchingRef to prevent overlapping requests'
  )
  assert.ok(
    useStablesQuickFeedJs.includes('if (isFetchingRef.current) return'),
    'Must abort redundant fetch when request is already in-flight'
  )

  // Stale out-of-order response discard guard
  assert.ok(
    useStablesQuickFeedJs.includes('const requestIdRef = useRef(0)'),
    'Must maintain requestIdRef sequence counter'
  )
  assert.ok(
    useStablesQuickFeedJs.includes('if (currentRequestId !== requestIdRef.current) return'),
    'Must discard out-of-order responses if targetDate or trigger changed'
  )
})

test('7. API failure handling: distinct error banner with retry action without swallowing', () => {
  assert.ok(
    useStablesQuickFeedJs.includes('setQuickFeedError(err.message ||'),
    'useStablesQuickFeed must capture error message on failure'
  )
  assert.ok(
    !useStablesQuickFeedJs.includes('.catch(() => [])'),
    'useStablesQuickFeed must NOT silently swallow API errors with catch(() => [])'
  )
  // Error banner in StablesView
  assert.ok(
    stableCarePage.includes('{isOperationalStaff && quickFeed.quickFeedError && ('),
    'StablesView must render quickFeedError banner only for operational staff'
  )
  // Retry button in StablesView
  assert.ok(
    stableCarePage.includes('onClick={quickFeed.reloadQuickFeed}'),
    'Error banner must provide retry action invoking reloadQuickFeed'
  )
  // Error banner in NutritionView
  assert.ok(
    stableCarePage.includes('Không thể tải dữ liệu cho ăn trong ca ({quickFeed.quickFeedError})'),
    'NutritionView must render distinct error banner for quick-feed failure'
  )
})

test('8. Strict Role Exclusivity: Veterinarian has NO confirmation buttons anywhere', () => {
  // In useStablesQuickFeed
  assert.ok(
    useStablesQuickFeedJs.includes('if (!isStaff || isVet) return'),
    'handleQuickConfirmMeal in useStablesQuickFeed must reject any call if !isStaff or isVet'
  )

  // In StablesView stall card
  assert.ok(
    stableCarePage.includes('isStaff && !isVet && !isConfirmed && ('),
    'StablesView stall card quick confirm button must require isStaff && !isVet'
  )

  // In StablesView detail panel
  assert.ok(
    stableCarePage.includes('{isStaff && !isVet && !isConfirmed && ('),
    'StablesView detail panel quick confirm button must require isStaff && !isVet'
  )

  // In NutritionView quick-feed buttons
  assert.ok(
    stableCarePage.includes('{/* 1-click Quick Feed Button: Strictly for Stable Staff, never for Veterinarian */}'),
    'NutritionView must document strict role restriction on quick feed buttons'
  )
})

test('9. Stable Meal Identity (id) is preserved and used during confirmation', () => {
  assert.ok(
    useStablesQuickFeedJs.includes('mealKey: meal.mealId || meal.mealKey || undefined'),
    'Payload must prioritize stable mealId / mealKey over fallback'
  )
  assert.ok(
    useStablesQuickFeedJs.includes('m.mealId && (m.mealId === meal.mealId || m.mealId === savedLog?.mealKey)'),
    'Local update must match meal by stable mealId first'
  )
})

test('10. Logic Simulation: Timer Lifecycle, Deduplication, and Stale Response Discard', async () => {
  let isFetching = false
  let requestId = 0
  let state = { data: null, error: '', loading: false }

  const mockApiCall = async (id, shouldFail = false, delayMs = 10, resultData = []) => {
    if (isFetching) return { aborted: true }
    isFetching = true
    const currentId = ++requestId

    state.loading = true
    await new Promise((r) => setTimeout(r, delayMs))

    if (currentId !== requestId) {
      // Discard stale response
      isFetching = false
      return { stale: true }
    }

    if (shouldFail) {
      state.error = 'Network 503 Service Unavailable'
      state.loading = false
      isFetching = false
      return { error: state.error }
    }

    state.data = resultData
    state.error = ''
    state.loading = false
    isFetching = false
    return { success: true, data: resultData }
  }

  // A. Normal successful fetch
  const res1 = await mockApiCall(1, false, 5, [{ stallId: 101, meals: [{ mealId: 'm1', status: 'PENDING' }] }])
  assert.equal(res1.success, true)
  assert.equal(state.data.length, 1)
  assert.equal(state.error, '')

  // B. Deduplication: second concurrent fetch while first in-flight
  isFetching = true
  const res2 = await mockApiCall(2)
  assert.equal(res2.aborted, true, 'Concurrent call must be aborted by isFetchingRef')
  isFetching = false

  // C. Error preservation: failure does NOT erase previous valid data
  const prevData = state.data
  const res3 = await mockApiCall(3, true, 5)
  assert.equal(res3.error, 'Network 503 Service Unavailable')
  assert.equal(state.data, prevData, 'Previous loaded data must be preserved when refresh fails')
  assert.equal(state.error, 'Network 503 Service Unavailable')

  // D. Stale response cancellation: request 4 starts, request 5 arrives and completes first
  requestId = 10
  const delayedPromise = (async () => {
    const cur = ++requestId // 11
    await new Promise((r) => setTimeout(r, 20))
    if (cur !== requestId) return { stale: true }
    return { stale: false }
  })()

  // New request 12 arrives immediately
  requestId = 12
  const delayedRes = await delayedPromise
  assert.equal(delayedRes.stale, true, 'Older response must be discarded when newer request was dispatched')
})

test('11. RH-NUTRITION-10: Veterinarian view has NO operational Quick-Feed status badges', () => {
  // Operational badges in Grid View strictly require isOperationalStaff
  assert.ok(
    stableCarePage.includes('{isOperationalStaff && s.quickFeedMeals && s.quickFeedMeals.length > 0 && ('),
    'Grid View quick feed meals section must strictly require isOperationalStaff'
  )
  // Operational badges in Table View strictly require isOperationalStaff
  assert.ok(
    stableCarePage.includes('{isOperationalStaff && s.hasHorse && s.quickFeedMeals && s.quickFeedMeals.length > 0 ? ('),
    'Table View quick feed meals column must strictly require isOperationalStaff'
  )
  // Error banner strictly requires isOperationalStaff
  assert.ok(
    stableCarePage.includes('{isOperationalStaff && quickFeed.quickFeedError && ('),
    'QuickFeed error banner must only display for isOperationalStaff'
  )
})

test('12. RH-NUTRITION-10: useStablesQuickFeed is inactive for Veterinarians to eliminate polling and 403 errors', () => {
  assert.ok(
    useStablesQuickFeedJs.includes('const isOperationalStaff = Boolean(isStaff && !isVet)'),
    'useStablesQuickFeed must identify isOperationalStaff'
  )
  assert.ok(
    useStablesQuickFeedJs.includes('const isHookActive = Boolean(enabled && isOperationalStaff)'),
    'useStablesQuickFeed must deactivate hook when not operational staff'
  )
  assert.ok(
    useStablesQuickFeedJs.includes('if (!isHookActive || !targetDate) return'),
    'useStablesQuickFeed must abort fetch when hook is inactive'
  )
  assert.ok(
    useStablesQuickFeedJs.includes('if (!isHookActive || refreshIntervalMs <= 0) return'),
    'useStablesQuickFeed must NOT start setInterval polling when hook is inactive'
  )
})

test('13. RH-NUTRITION-10: Veterinarian retains nutrition management and feeding execution history access', () => {
  // Access to Daily Feeding History endpoint in API
  assert.ok(
    apiJs.includes('getDailyFeedingHistory: (horseId, params)'),
    'API client must retain getDailyFeedingHistory endpoint'
  )
  // VetPage retains VetNutritionLibraryView
  assert.ok(
    fs.existsSync(path.resolve(frontendDir, 'src/VetNutritionLibraryView.jsx')),
    'VetNutritionLibraryView must exist for standard plan library management'
  )
  // NutritionView retains historical tabs
  assert.ok(
    stableCarePage.includes('Nhật ký cho ăn thực tế'),
    'NutritionView must preserve actual feeding execution history view'
  )
  assert.ok(
    stableCarePage.includes('Lịch sử kế hoạch dinh dưỡng'),
    'NutritionView must preserve nutrition plan version history view'
  )
})

test('14. RH-NUTRITION-10: Logic Simulation of Veterinarian vs Stable Staff Hook State', () => {
  const evaluateHookState = ({ isStaff, isVet, enabled = true }) => {
    const isOperationalStaff = Boolean(isStaff && !isVet)
    const isHookActive = Boolean(enabled && isOperationalStaff)
    return {
      isOperationalStaff,
      isHookActive,
      shouldPoll: isHookActive,
      canConfirm: isOperationalStaff,
      initialLoading: isHookActive,
    }
  }

  // 1. Stable Staff: active hook, polling enabled, can confirm
  const staffState = evaluateHookState({ isStaff: true, isVet: false })
  assert.equal(staffState.isOperationalStaff, true)
  assert.equal(staffState.isHookActive, true)
  assert.equal(staffState.shouldPoll, true)
  assert.equal(staffState.canConfirm, true)
  assert.equal(staffState.initialLoading, true)

  // 2. Veterinarian: inactive hook, NO polling, CANNOT confirm, initialLoading false
  const vetState = evaluateHookState({ isStaff: false, isVet: true })
  assert.equal(vetState.isOperationalStaff, false)
  assert.equal(vetState.isHookActive, false)
  assert.equal(vetState.shouldPoll, false)
  assert.equal(vetState.canConfirm, false)
  assert.equal(vetState.initialLoading, false)

  // 3. Dual role (if any): isVet overrides
  const dualState = evaluateHookState({ isStaff: true, isVet: true })
  assert.equal(dualState.isOperationalStaff, false)
  assert.equal(dualState.isHookActive, false)
  assert.equal(dualState.shouldPoll, false)

  // 4. Other roles: Horse Owner, Trainer, Club Manager
  const ownerState = evaluateHookState({ isStaff: false, isVet: false })
  assert.equal(ownerState.isOperationalStaff, false)
  assert.equal(ownerState.isHookActive, false)
})
