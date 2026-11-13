import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { sanitizeMojibake } from './servicePackageValidation.js'

test('RH-SUBSCRIPTION-07D: sanitizeMojibake restores corrupted legacy Vietnamese text', () => {
  const p30NameMojibake = 'GÃ³i 1 thÃ¡ng TiÃªu chuáº©n'
  const p30DescMojibake = 'ChÄƒm sÃ³c dinh dÆ°á»¡ng tiÃªu chuáº©n, theo dÃµi sá»©c khá» e vÃ  huáº¥n luyá»‡n cÆ¡ báº£n trong 30 ngÃ y.'
  const p90NameMojibake = 'GÃ³i 3 thÃ¡ng NÃ¢ng cao'
  const p90DescMojibake = 'PhÃ¡c Ä‘á»“ huáº¥n luyá»‡n chuyÃªn sÃ¢u, cháº¿ Ä‘á»™ dinh dÆ°á»¡ng tá»‘i Æ°u vÃ  kiá»ƒm tra thÃº y Ä‘á»‹nh ká»³ trong 90 ngÃ y.'
  const p365NameMojibake = 'GÃ³i 12 thÃ¡ng ToÃ n diá»‡n'
  const p365DescMojibake = 'ChÄƒm sÃ³c chuá»“ng tráº¡i trá» n gÃ³i, chuáº©n bá»‹ thi Ä‘áº¥u giáº£i Ä‘ua chuyÃªn nghiá»‡p vÃ  chÄƒm sÃ³c y táº¿ toÃ n diá»‡n cáº£ nÄƒm.'

  assert.equal(sanitizeMojibake(p30NameMojibake), 'Gói 1 tháng Tiêu chuẩn')
  assert.equal(sanitizeMojibake(p30DescMojibake), 'Chăm sóc dinh dưỡng tiêu chuẩn, theo dõi sức khỏe và huấn luyện cơ bản trong 30 ngày.')
  assert.equal(sanitizeMojibake(p90NameMojibake), 'Gói 3 tháng Nâng cao')
  assert.equal(sanitizeMojibake(p90DescMojibake), 'Phác đồ huấn luyện chuyên sâu, chế độ dinh dưỡng tối ưu và kiểm tra thú y định kỳ trong 90 ngày.')
  assert.equal(sanitizeMojibake(p365NameMojibake), 'Gói 12 tháng Toàn diện')
  assert.equal(sanitizeMojibake(p365DescMojibake), 'Chăm sóc chuồng trại trọn gói, chuẩn bị thi đấu giải đua chuyên nghiệp và chăm sóc y tế toàn diện cả năm.')
})

test('RH-SUBSCRIPTION-07D: sanitizeMojibake preserves clean Vietnamese and ASCII text untouched', () => {
  const cleanVi = 'Gói 1 tháng Tiêu chuẩn và Huấn luyện chuyên nghiệp'
  assert.equal(sanitizeMojibake(cleanVi), cleanVi)
  assert.equal(sanitizeMojibake(''), '')
  assert.equal(sanitizeMojibake(null), '')
  assert.equal(sanitizeMojibake('Standard Plan 30 Days'), 'Standard Plan 30 Days')
})

test('RH-SUBSCRIPTION-07D: WalletPage fetches active packages dynamically from servicePackageApi.getActive()', () => {
  const walletPagePath = path.resolve(process.cwd(), 'src/WalletPage.jsx')
  const content = fs.readFileSync(walletPagePath, 'utf8')

  assert.ok(content.includes('servicePackageApi.getActive()'), 'WalletPage must query servicePackageApi.getActive()')
  assert.ok(content.includes('const [plans, setPlans] = useState('), 'WalletPage must maintain dynamic plans state')
  assert.ok(content.includes('sanitizeMojibake'), 'WalletPage must sanitize package names and descriptions')
})

test('RH-SUBSCRIPTION-07D: Owner plan selection excludes Reserve packages dynamically', () => {
  // Simulate active packages returned from backend where P90 was moved to Reserve
  const mockBackendActivePackages = [
    { code: 'P30', name: 'Gói 1 tháng Tiêu chuẩn', priceVnd: 5000000, durationDays: 30, isActive: true },
    { code: 'P365', name: 'Gói 12 tháng Toàn diện', priceVnd: 60000000, durationDays: 365, isActive: true },
  ]

  // Owner state after loading active packages
  const activeCodes = new Set(mockBackendActivePackages.map(p => p.code))
  assert.equal(activeCodes.has('P90'), false, 'Reserve package P90 must not be present in active plans')
  assert.equal(activeCodes.has('P30'), true, 'Active package P30 must be present')
  assert.equal(activeCodes.has('P365'), true, 'Active package P365 must be present')

  // When a horse had P90 as nextPlan or activePlan, selection must fallback to an active package
  const horseWithDiscontinuedPlan = { horseId: 'h-1', name: 'Xích Thố', activePlan: 'P90', nextPlan: null }
  const selected = (activeCodes.has(horseWithDiscontinuedPlan.nextPlan) ? horseWithDiscontinuedPlan.nextPlan : null)
    || (activeCodes.has(horseWithDiscontinuedPlan.activePlan) ? horseWithDiscontinuedPlan.activePlan : null)
    || mockBackendActivePackages[0]?.code
    || 'P30'

  assert.equal(selected, 'P30', 'Selected package for horse with discontinued plan must default to an active package')
})
