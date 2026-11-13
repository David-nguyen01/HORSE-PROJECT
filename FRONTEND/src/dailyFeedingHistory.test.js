import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

const frontendDir = path.resolve(process.cwd(), process.cwd().endsWith('FRONTEND') ? '.' : 'FRONTEND')
const apiJsPath = path.resolve(frontendDir, 'src/api.js')
const vetApiJsPath = path.resolve(frontendDir, 'src/vetApi.js')
const stableCarePagePath = path.resolve(frontendDir, 'src/StableCarePage.jsx')

const apiJs = fs.readFileSync(apiJsPath, 'utf8')
const vetApiJs = fs.readFileSync(vetApiJsPath, 'utf8')
const stableCarePage = fs.readFileSync(stableCarePagePath, 'utf8')

test('1. API clients export getDailyFeedingHistory for both Stable Staff and Veterinarian', () => {
  assert.ok(
    apiJs.includes('getDailyFeedingHistory: (horseId, params) =>'),
    'api.js (stableCareApi) must export getDailyFeedingHistory'
  )
  assert.ok(
    apiJs.includes('/api/horses/${horseId}/daily-feeding/history'),
    'api.js must route history requests to /api/horses/{horseId}/daily-feeding/history'
  )
  assert.ok(
    vetApiJs.includes('getDailyFeedingHistory: (horseId, params) =>'),
    'vetApi.js must export getDailyFeedingHistory'
  )
  assert.ok(
    vetApiJs.includes('/api/horses/${horseId}/daily-feeding/history'),
    'vetApi.js must route history requests to /api/horses/{horseId}/daily-feeding/history'
  )
})

test('2. Nutrition history button toggles history section for horse', () => {
  assert.ok(
    stableCarePage.includes("showHistory ? 'Ẩn lịch sử & nhật ký' : 'Lịch sử & Nhật ký cho ăn'"),
    "History toggle button must toggle between 'Ẩn lịch sử & nhật ký' and 'Lịch sử & Nhật ký cho ăn'"
  )
  assert.ok(
    stableCarePage.includes('onClick={toggleHistory}'),
    'Must provide onClick={toggleHistory}'
  )
})

test('3. Visual dual-tab separation between Feeding Execution History and Nutrition Plan History', () => {
  assert.ok(
    stableCarePage.includes("const [historyTab, setHistoryTab] = useState('feeding')"),
    "StableCarePage must maintain historyTab state defaulting to 'feeding'"
  )
  assert.ok(
    stableCarePage.includes('Nhật ký cho ăn thực tế'),
    "Must have tab label 'Nhật ký cho ăn thực tế'"
  )
  assert.ok(
    stableCarePage.includes('Lịch sử kế hoạch dinh dưỡng'),
    "Must have tab label 'Lịch sử kế hoạch dinh dưỡng'"
  )
  assert.ok(
    stableCarePage.includes("historyTab === 'feeding'"),
    'Must conditionally render feeding execution history section'
  )
  assert.ok(
    stableCarePage.includes("historyTab === 'plan'"),
    'Must conditionally render prescription plan history section'
  )
})

test('4. Feeding execution history displays snapshot details (concentrate, forage, supplements, planVersion)', () => {
  assert.ok(
    stableCarePage.includes('const snapshot = item.mealSnapshot || {}'),
    'Must extract immutable snapshot from item.mealSnapshot'
  )
  assert.ok(
    stableCarePage.includes('snapshot.concentrateKg'),
    'Must display concentrateKg from snapshot'
  )
  assert.ok(
    stableCarePage.includes('snapshot.forageKg'),
    'Must display forageKg from snapshot'
  )
  assert.ok(
    stableCarePage.includes('snapshot.supplements'),
    'Must display supplements from snapshot'
  )
  assert.ok(
    stableCarePage.includes('Kế hoạch v{item.planVersion}'),
    'Must display planVersion associated with feeding log'
  )
  assert.ok(
    stableCarePage.includes('item.confirmedByName'),
    'Must display staff name who confirmed the meal'
  )
  assert.ok(
    stableCarePage.includes('formatVNDateTime(item.confirmedAt)'),
    'Must format confirmedAt timestamp'
  )
  assert.ok(
    stableCarePage.includes('formatTimeHHmm(item.feedingTime)'),
    'Must format scheduled feedingTime'
  )
  assert.ok(
    stableCarePage.includes('formatVNDate(item.feedingDate)'),
    'Must format feedingDate'
  )
})

test('5. Date range filtering controls and presets are supported', () => {
  assert.ok(
    stableCarePage.includes('handleDatePreset(7)'),
    'Must support 7-day quick preset'
  )
  assert.ok(
    stableCarePage.includes('handleDatePreset(14)'),
    'Must support 14-day quick preset'
  )
  assert.ok(
    stableCarePage.includes('handleDatePreset(30)'),
    'Must support 30-day quick preset'
  )
  assert.ok(
    stableCarePage.includes("handleDatePreset('all')"),
    'Must support All-time preset'
  )
  assert.ok(
    stableCarePage.includes('setFeedingStartDate(e.target.value)'),
    'Must allow custom start date input'
  )
  assert.ok(
    stableCarePage.includes('setFeedingEndDate(e.target.value)'),
    'Must allow custom end date input'
  )
  assert.ok(
    stableCarePage.includes('setFeedingReloadTrigger'),
    'Must provide refresh button triggering reload'
  )
})

test('6. Loading, error, and empty states are cleanly handled', () => {
  assert.ok(
    stableCarePage.includes('feedingHistoryLoading ?'),
    'Must handle feedingHistoryLoading state'
  )
  assert.ok(
    stableCarePage.includes('Đang tải nhật ký cho ăn thực tế…'),
    'Must display loading text while fetching'
  )
  assert.ok(
    stableCarePage.includes('feedingHistoryError ?'),
    'Must handle feedingHistoryError state'
  )
  assert.ok(
    stableCarePage.includes('Chưa có nhật ký cho ăn thực tế'),
    'Must handle empty feeding history state'
  )
})

test('7. Feeding history query has race-condition and cancellation guard', () => {
  const match = stableCarePage.match(
    /\/\/\s*3\.\s*Load feeding execution history[\s\S]*?return\s*\(\)\s*=>\s*\{[\s\S]*?cancelled\s*=\s*true[\s\S]*?\}/
  )
  assert.ok(
    match,
    'Feeding history useEffect must contain cancellation guard (cancelled = true on cleanup)'
  )
})

test('8. Meal confirmation prepends saved log to feedingHistory immediately', () => {
  assert.ok(
    stableCarePage.includes('setFeedingHistory((prev) => [savedLog, ...(prev || [])])'),
    'handleConfirmMeal must immediately prepend savedLog to feedingHistory state'
  )
})
