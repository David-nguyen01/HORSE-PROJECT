import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

const frontendDir = path.resolve(process.cwd(), process.cwd().endsWith('FRONTEND') ? '.' : 'FRONTEND')
const apiJsPath = path.resolve(frontendDir, 'src/api.js')
const vetApiJsPath = path.resolve(frontendDir, 'src/vetApi.js')
const stableCarePagePath = path.resolve(frontendDir, 'src/StableCarePage.jsx')
const useStablesQuickFeedPath = path.resolve(frontendDir, 'src/useStablesQuickFeed.js')

const apiJs = fs.readFileSync(apiJsPath, 'utf8')
const vetApiJs = fs.readFileSync(vetApiJsPath, 'utf8')
const stableCarePage = fs.readFileSync(stableCarePagePath, 'utf8')
const useStablesQuickFeedJs = fs.readFileSync(useStablesQuickFeedPath, 'utf8')

test('1. api.js and vetApi.js export getStablesQuickFeed endpoint', () => {
  assert.ok(
    apiJs.includes('getStablesQuickFeed: (date) => api(`/api/nutrition/stables-quick-feed'),
    'api.js (stableCareApi) must export getStablesQuickFeed targeting /api/nutrition/stables-quick-feed'
  )
  assert.ok(
    vetApiJs.includes('getStablesQuickFeed: (date) =>'),
    'vetApi.js must export getStablesQuickFeed'
  )
  assert.ok(
    vetApiJs.includes('/api/nutrition/stables-quick-feed'),
    'vetApi.js must route quick-feed requests to /api/nutrition/stables-quick-feed'
  )
})

test('2. useStablesQuickFeed fetches quick-feed data on targetDate change with reliability guards', () => {
  assert.ok(
    useStablesQuickFeedJs.includes('const [quickFeedData, setQuickFeedData] = useState'),
    'useStablesQuickFeed must declare quickFeedData state'
  )
  assert.ok(
    useStablesQuickFeedJs.includes('stableCareApi.getStablesQuickFeed(targetDate)'),
    'useStablesQuickFeed must call stableCareApi.getStablesQuickFeed with targetDate'
  )
  assert.ok(
    useStablesQuickFeedJs.includes('setQuickFeedData(Array.isArray(data) ? data : [])'),
    'useStablesQuickFeed must store quick-feed items into quickFeedData'
  )
  assert.ok(
    stableCarePage.includes('const quickFeed = useStablesQuickFeed'),
    'StableCarePage must integrate useStablesQuickFeed'
  )
})

test('3. Stalls are enriched with quickFeedMeals by stallId for operational staff', () => {
  assert.ok(
    useStablesQuickFeedJs.includes('quickFeedByStallId = useMemo'),
    'Must construct quickFeedByStallId lookup map'
  )
  assert.ok(
    stableCarePage.includes('quickFeedMeals: isOperationalStaff ? (quickFeed.quickFeedByStallId.get(s.stallId)?.meals || []) : []'),
    'Must enrich stall objects with quickFeedMeals from quickFeedByStallId for operational staff'
  )
})

test('4. Stall cards in Grid View render scheduled meals with pure derived status badges', () => {
  assert.ok(
    stableCarePage.includes("meal.status === 'CONFIRMED'"),
    'Must evaluate CONFIRMED status'
  )
  assert.ok(
    stableCarePage.includes("meal.status === 'OVERDUE'"),
    'Must evaluate OVERDUE status'
  )
  assert.ok(
    stableCarePage.includes('Đã cho ăn'),
    'Must display Đã cho ăn badge for confirmed meals'
  )
  assert.ok(
    stableCarePage.includes('Quá giờ'),
    'Must display Quá giờ badge for overdue meals'
  )
  assert.ok(
    stableCarePage.includes('Chờ cho ăn'),
    'Must display Chờ cho ăn badge for pending meals'
  )
})

test('5. Confirmed meals display confirmation metadata (staff name and timestamp)', () => {
  assert.ok(
    stableCarePage.includes('meal.confirmedByName'),
    'Must render staff name who confirmed the meal'
  )
  assert.ok(
    stableCarePage.includes('formatVNDateTime(meal.confirmedAt)'),
    'Must format confirmedAt timestamp with formatVNDateTime'
  )
})

test('6. Stable Staff has 1-click Quick Feed confirm button without navigating to checklist', () => {
  assert.ok(
    stableCarePage.includes('handleQuickConfirmMeal = useCallback'),
    'Must define handleQuickConfirmMeal handler'
  )
  assert.ok(
    stableCarePage.includes('isStaff && !isVet && !isConfirmed'),
    'Quick-feed confirmation button must strictly require isStaff && !isVet && !isConfirmed'
  )
  assert.ok(
    stableCarePage.includes('handleQuickConfirmMeal(s, meal)'),
    'Stall card must invoke handleQuickConfirmMeal with stall and meal'
  )
})

test('7. Veterinarian view is strictly read-only with NO confirmation actions', () => {
  // In handleQuickConfirmMeal guard
  assert.ok(
    stableCarePage.includes('if (!isStaff || isVet) return'),
    'handleQuickConfirmMeal must immediately return if not staff or if isVet'
  )
  // In checklist button rendering
  assert.ok(
    stableCarePage.includes('{isStaff && !isVet && ('),
    'Checklist confirm button must only render for isStaff && !isVet'
  )
  assert.ok(
    stableCarePage.includes('handleConfirmMeal'),
    'handleConfirmMeal must be defined'
  )
})

test('8. Duplicate in-flight clicks are guarded and button shows pending state', () => {
  assert.ok(
    stableCarePage.includes('if (confirmingMealKey === key || meal.confirmed) return'),
    'Must guard against duplicate clicks when request is in-flight or meal already confirmed'
  )
  assert.ok(
    stableCarePage.includes("disabled={isPending}"),
    'Button must be disabled when isPending is true'
  )
  assert.ok(
    stableCarePage.includes("isPending ? 'Đang gửi…' : 'Cho ăn'"),
    "Button must show 'Đang gửi…' while pending"
  )
})

test('9. Error handling displays dismissible error banner and preserves unconfirmed state', () => {
  assert.ok(
    stableCarePage.includes("setConfirmError(err.message || 'Không thể xác nhận cho ăn. Vui lòng thử lại.')"),
    'Must set error message on quick-feed confirmation failure'
  )
  assert.ok(
    stableCarePage.includes("onClick={() => setConfirmError('')}"),
    'Must provide dismissal button for confirmError banner'
  )
})

test('10. Table View renders meal progress and Quick-Feed buttons in Bữa ăn trong ca column', () => {
  assert.ok(
    stableCarePage.includes('<span>Bữa ăn trong ca</span>'),
    'Table header must include Bữa ăn trong ca column'
  )
  assert.ok(
    stableCarePage.includes('s.hasHorse && s.quickFeedMeals && s.quickFeedMeals.length > 0'),
    'Table row must conditionally render quick feed meals when present'
  )
})

test('11. Empty stalls and unassigned horses remain clearly distinct', () => {
  assert.ok(
    stableCarePage.includes('Chưa có ngựa'),
    'Empty stall must show Chưa có ngựa placeholder'
  )
  assert.ok(
    stableCarePage.includes('[Không có khẩu phần]'),
    'Empty stall must show [Không có khẩu phần]'
  )
  assert.ok(
    stableCarePage.includes('Ô chuồng trống'),
    'Empty stall action must show Ô chuồng trống'
  )
  assert.ok(
    stableCarePage.includes('+ Khẩu phần'),
    'Horse without plan must offer + Khẩu phần template assignment button'
  )
})

test('12. Bidirectional sync between Quick-Feed overview and detailed Checklist', () => {
  // Confirming from quick-feed updates checklist
  assert.ok(
    stableCarePage.includes('if (selectedStall?.horseId === stall.horseId)'),
    'handleQuickConfirmMeal must sync checklist if current stall is selected'
  )
  // Confirming from checklist updates quickFeedData via syncConfirmedMeal
  assert.ok(
    stableCarePage.includes('quickFeed.syncConfirmedMeal(selectedStall.stallId'),
    'handleConfirmMeal must call quickFeed.syncConfirmedMeal for selected stall'
  )
  assert.ok(
    useStablesQuickFeedJs.includes('if (item.stallId !== stallId) return item'),
    'syncConfirmedMeal must update matching stall meals'
  )
})

test('13. Logic Simulation: Quick-Feed Status Transitions & Role Guard', () => {
  // Simulate stall quick feed item
  const mockStallItem = {
    stableId: 1,
    stableName: 'Chuồng A',
    stallId: 101,
    stallCode: 'A-01',
    horseId: 50,
    horseName: 'Thần Gió',
    hasPlan: true,
    meals: [
      {
        mealId: 'm-1',
        mealName: 'Bữa sáng',
        feedingTime: '07:00',
        status: 'OVERDUE',
        confirmed: false,
      },
      {
        mealId: 'm-2',
        mealName: 'Bữa trưa',
        feedingTime: '12:00',
        status: 'PENDING',
        confirmed: false,
      },
    ],
  }

  // 1. Initial status checks
  assert.equal(mockStallItem.meals[0].status, 'OVERDUE')
  assert.equal(mockStallItem.meals[0].confirmed, false)
  assert.equal(mockStallItem.meals[1].status, 'PENDING')
  assert.equal(mockStallItem.meals[1].confirmed, false)

  // 2. Role authorization simulation
  const staffCanConfirm = (isStaff, isVet, confirmed) => isStaff && !isVet && !confirmed
  assert.equal(staffCanConfirm(true, false, false), true, 'Stable Staff can confirm unconfirmed meal')
  assert.equal(staffCanConfirm(false, true, false), false, 'Veterinarian cannot confirm meal')
  assert.equal(staffCanConfirm(false, false, false), false, 'Owner/Trainer cannot confirm meal')
  assert.equal(staffCanConfirm(true, false, true), false, 'Cannot confirm already confirmed meal')

  // 3. Successful confirmation state update
  const updatedMeals = mockStallItem.meals.map((m) => {
    if (m.mealId === 'm-1') {
      return {
        ...m,
        confirmed: true,
        status: 'CONFIRMED',
        confirmedAt: '2026-11-13T10:30:00Z',
        confirmedByName: 'Nguyễn Văn Nhân Viên',
      }
    }
    return m
  })

  assert.equal(updatedMeals[0].confirmed, true)
  assert.equal(updatedMeals[0].status, 'CONFIRMED')
  assert.equal(updatedMeals[0].confirmedByName, 'Nguyễn Văn Nhân Viên')
  assert.equal(staffCanConfirm(true, false, updatedMeals[0].confirmed), false, 'Confirmed meal has no confirm button')
})
