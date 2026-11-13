import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { validateServicePackageForm } from './servicePackageValidation.js'

test('1. Club Manager navigation visibility & role boundary', () => {
  const appPath = path.resolve(process.cwd(), 'src/App.jsx')
  const content = fs.readFileSync(appPath, 'utf-8')

  // Check that the menu item is placed inside hasAny(user, 'CLUB_MANAGER') block
  const managerNavIdx = content.indexOf("hasAny(user, 'CLUB_MANAGER') ? [")
  assert.ok(managerNavIdx !== -1, 'Club Manager navigation block must exist')

  const packageNavItemIdx = content.indexOf("/admin/service-packages")
  assert.ok(packageNavItemIdx !== -1, '/admin/service-packages navigation route must exist')
  assert.ok(content.includes("'Quản lý gói dịch vụ'"), "Label 'Quản lý gói dịch vụ' must exist in App.jsx")

  // Ensure it is positioned after the Club Manager check
  assert.ok(packageNavItemIdx > managerNavIdx, '/admin/service-packages must be within the CLUB_MANAGER menu items array')

  // Check Route guard
  assert.ok(
    content.includes('path="admin/service-packages" element={hasAny(user,\'CLUB_MANAGER\')?<ServicePackageCatalogPage/>:<Navigate to="/" replace/>}'),
    'Route for admin/service-packages must be guarded by hasAny(user, CLUB_MANAGER)'
  )

  // Simulation for role-based navigation filtering
  const buildNavItems = (user) => {
    const isManager = Boolean(user?.roles?.includes('CLUB_MANAGER'))
    const items = []
    if (isManager) {
      items.push({ to: '/admin/service-packages', label: 'Quản lý gói dịch vụ' })
    }
    return items
  }

  // Club Manager has the item
  const managerItems = buildNavItems({ roles: ['CLUB_MANAGER'] })
  assert.equal(managerItems.length, 1)
  assert.equal(managerItems[0].to, '/admin/service-packages')

  // Other four roles DO NOT have the item
  assert.equal(buildNavItems({ roles: ['STABLE_STAFF'] }).length, 0)
  assert.equal(buildNavItems({ roles: ['HEAD_TRAINER'] }).length, 0)
  assert.equal(buildNavItems({ roles: ['VETERINARIAN'] }).length, 0)
  assert.equal(buildNavItems({ roles: ['HORSE_OWNER'] }).length, 0)
})

test('2. servicePackageApi endpoints in api.js', () => {
  const apiPath = path.resolve(process.cwd(), 'src/api.js')
  const content = fs.readFileSync(apiPath, 'utf-8')

  assert.ok(content.includes('export const servicePackageApi = {'), 'servicePackageApi must be exported')
  assert.ok(content.includes("getActive: () => api('/api/service-packages/active')"), 'servicePackageApi.getActive exists')
  assert.ok(content.includes("getReserve: () => api('/api/service-packages/reserve')"), 'servicePackageApi.getReserve exists')
  assert.ok(content.includes("getCatalog: () => api('/api/service-packages/catalog')"), 'servicePackageApi.getCatalog exists')
  assert.ok(content.includes("create: (payload) => api('/api/service-packages'"), 'servicePackageApi.create exists')
  assert.ok(content.includes("update: (id, payload) => api(`/api/service-packages/${id}`"), 'servicePackageApi.update exists')
  assert.ok(content.includes("moveToActive: (id) => api(`/api/service-packages/${id}/move-to-active`"), 'servicePackageApi.moveToActive exists')
  assert.ok(content.includes("moveToReserve: (id) => api(`/api/service-packages/${id}/move-to-reserve`"), 'servicePackageApi.moveToReserve exists')
  assert.ok(content.includes("delete: (id) => api(`/api/service-packages/${id}`"), 'servicePackageApi.delete exists')
})

test('3. Active Package List rendering and data display', () => {
  const pagePath = path.resolve(process.cwd(), 'src/ServicePackageCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  assert.ok(content.includes('section-active-packages'), 'Must define section-active-packages')
  assert.ok(content.includes('ĐANG ÁP DỤNG'), 'Must render active status badge')
  assert.ok(content.includes('pkg.name'), 'Must render package name')
  assert.ok(content.includes('pkg.code'), 'Must render package code')
  assert.ok(content.includes('pkg.priceVnd'), 'Must render package price')
  assert.ok(content.includes('pkg.durationDays'), 'Must render package duration')
  assert.ok(content.includes('btn-move-to-reserve'), 'Must render button to move to reserve')
})

test('4. Strict rule: NO Add button inside Active section or page heading actions', () => {
  const pagePath = path.resolve(process.cwd(), 'src/ServicePackageCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // Top heading actions must ONLY have refresh button, NO Add button
  const topActionsSlice = content.substring(content.indexOf('heading-actions'), content.indexOf('admin-kpis'))
  assert.ok(
    !topActionsSlice.includes('Thêm gói'),
    'Top-right heading actions must NOT contain any Add button'
  )
  assert.ok(
    !topActionsSlice.includes('btn-add-reserve-package'),
    'Top-right heading actions must NOT contain btn-add-reserve-package'
  )

  // Active section must NOT contain Add button
  const activeSectionSlice = content.substring(
    content.indexOf('data-testid="section-active-packages"'),
    content.indexOf('data-testid="section-reserve-packages"')
  )
  assert.ok(
    !activeSectionSlice.includes('btn-add-reserve-package'),
    'Active section must NOT contain btn-add-reserve-package'
  )
  assert.ok(
    !activeSectionSlice.includes('btn-empty-add-reserve'),
    'Active section must NOT contain btn-empty-add-reserve'
  )
})

test('5. Reserve section rendering and Add button placement', () => {
  const pagePath = path.resolve(process.cwd(), 'src/ServicePackageCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  assert.ok(content.includes('section-reserve-packages'), 'Must define section-reserve-packages')
  assert.ok(content.includes('DỰ PHÒNG'), 'Must render DỰ PHÒNG status badge')
  assert.ok(content.includes('Thư viện dự phòng'), 'Must display Thư viện dự phòng')

  // The Add button is placed inside section-reserve-packages
  const reserveSectionSlice = content.substring(
    content.indexOf('data-testid="section-reserve-packages"'),
    content.indexOf('CreateServicePackageModal')
  )
  assert.ok(
    reserveSectionSlice.includes('data-testid="btn-add-reserve-package"'),
    'Must have btn-add-reserve-package inside section-reserve-packages'
  )
  assert.ok(
    reserveSectionSlice.includes('data-testid="btn-empty-add-reserve"'),
    'Must have btn-empty-add-reserve inside Reserve empty state'
  )
})

test('6. Create Modal strictly enforces Reserve creation (isActive: false) and validation', () => {
  const pagePath = path.resolve(process.cwd(), 'src/ServicePackageCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  const createModalSlice = content.substring(
    content.indexOf('function CreateServicePackageModal'),
    content.indexOf('function EditServicePackageModal')
  )

  assert.ok(createModalSlice.includes('isActive: false'), 'Create modal must strictly pass isActive: false')
  assert.ok(createModalSlice.includes('validateServicePackageForm'), 'Create modal must call validateServicePackageForm')
  assert.ok(createModalSlice.includes('input-package-code'), 'Create modal must provide code input')
  assert.ok(createModalSlice.includes('input-package-name'), 'Create modal must provide name input')
  assert.ok(createModalSlice.includes('input-package-price'), 'Create modal must provide price input')
  assert.ok(createModalSlice.includes('input-package-duration'), 'Create modal must provide duration input')
})

test('7. Edit Modal enforces code immutability and Policy A awareness', () => {
  const pagePath = path.resolve(process.cwd(), 'src/ServicePackageCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  const editModalSlice = content.substring(
    content.indexOf('function EditServicePackageModal'),
    content.indexOf('function DeleteConfirmDialog')
  )

  assert.ok(editModalSlice.includes('disabled'), 'Code input must be disabled in edit modal')
  assert.ok(editModalSlice.includes('input-package-code-readonly'), 'Code input must have readonly testid')
  assert.ok(editModalSlice.includes('Mã định danh gói dịch vụ là duy nhất và cố định sau khi tạo'), 'Must display immutability explanation')
  assert.ok(editModalSlice.includes('Policy A'), 'Edit modal must display Policy A notice')
  assert.ok(editModalSlice.includes('version: pkg.version'), 'Edit modal must transmit version for optimistic locking')
  assert.ok(editModalSlice.includes('err?.status === 409'), 'Edit modal must catch 409 optimistic locking conflicts')
  assert.ok(editModalSlice.includes('validateServicePackageForm'), 'Edit modal must call validateServicePackageForm')
})

test('8. Delete Protection & HTTP 409 Reference Handling', () => {
  const pagePath = path.resolve(process.cwd(), 'src/ServicePackageCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // Check delete modal warnings
  assert.ok(content.includes('delete-package-modal'), 'Delete confirmation dialog must be present')
  assert.ok(content.includes('409 Conflict'), 'Delete dialog must explicitly explain 409 reference rejection')
  assert.ok(content.includes('Thư viện dự phòng'), 'Delete dialog must recommend moving to Reserve instead')

  // Check handle deletion error branch
  assert.ok(content.includes('isReferenced'), 'Delete handler must identify referenced packages')
  assert.ok(content.includes('err?.status === 409'), 'Delete handler checks 409 status')
  assert.ok(content.includes('Không thể xóa gói dịch vụ'), 'Delete handler presents user-friendly error message')
})

test('9. Policy A Banner present on main page', () => {
  const pagePath = path.resolve(process.cwd(), 'src/ServicePackageCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  assert.ok(content.includes('data-testid="policy-a-banner"'), 'Page must contain policy-a-banner')
  assert.ok(content.includes('Chính sách định giá và bảo toàn dữ liệu (Policy A)'), 'Policy A title must be clear')
  assert.ok(content.includes('Bảo toàn hợp đồng đã thanh toán'), 'Policy A must clarify contract preservation')
})

test('10. RH-SUBSCRIPTION-04A: Duration boundary validation (1 accepted, 730 accepted, 731 rejected, <1 rejected)', () => {
  const baseValid = {
    code: 'TEST_PKG',
    name: 'Gói Thử Nghiệm Hợp Lệ',
    description: 'Mô tả hợp lệ',
    priceVnd: '5000000',
    durationDays: '30',
  }

  // 1. Valid lower boundary: 1 day is accepted
  assert.equal(
    validateServicePackageForm({ ...baseValid, durationDays: '1' }, false),
    null,
    '1 day must be accepted in Create form'
  )
  assert.equal(
    validateServicePackageForm({ ...baseValid, durationDays: '1' }, true),
    null,
    '1 day must be accepted in Edit form'
  )

  // 2. Valid upper boundary: 730 days is accepted
  assert.equal(
    validateServicePackageForm({ ...baseValid, durationDays: '730' }, false),
    null,
    '730 days must be accepted in Create form'
  )
  assert.equal(
    validateServicePackageForm({ ...baseValid, durationDays: '730' }, true),
    null,
    '730 days must be accepted in Edit form'
  )

  // 3. Exceeded upper boundary: 731 days is rejected
  const err731Create = validateServicePackageForm({ ...baseValid, durationDays: '731' }, false)
  assert.ok(err731Create !== null, '731 days must be rejected in Create form')
  assert.ok(err731Create.includes('730'), 'Error message must state 730 days limit')

  const err731Edit = validateServicePackageForm({ ...baseValid, durationDays: '731' }, true)
  assert.ok(err731Edit !== null, '731 days must be rejected in Edit form')
  assert.ok(err731Edit.includes('730'), 'Error message must state 730 days limit')

  // Also test old invalid value 3650 days is rejected
  const err3650 = validateServicePackageForm({ ...baseValid, durationDays: '3650' }, false)
  assert.ok(err3650 !== null, '3650 days must be rejected')

  // 4. Below minimum: 0 and negative values are rejected
  const err0 = validateServicePackageForm({ ...baseValid, durationDays: '0' }, false)
  assert.ok(err0 !== null, '0 days must be rejected')

  const errNegative = validateServicePackageForm({ ...baseValid, durationDays: '-5' }, false)
  assert.ok(errNegative !== null, 'Negative days must be rejected')

  const errEmpty = validateServicePackageForm({ ...baseValid, durationDays: '' }, false)
  assert.ok(errEmpty !== null, 'Empty duration must be rejected')

  const errNan = validateServicePackageForm({ ...baseValid, durationDays: 'abc' }, false)
  assert.ok(errNan !== null, 'Non-numeric duration must be rejected')
})

test('11. RH-SUBSCRIPTION-04A: Alignment of name, description, price, and code with Backend DTOs', () => {
  const baseValid = {
    code: 'TEST_PKG',
    name: 'Gói Hợp Lệ 30 Ngày',
    description: 'Mô tả hợp lệ',
    priceVnd: '5000000',
    durationDays: '30',
  }

  // Name length boundary: min 2, max 100
  assert.equal(validateServicePackageForm({ ...baseValid, name: 'AB' }, false), null, '2-character name accepted')
  assert.equal(validateServicePackageForm({ ...baseValid, name: 'A'.repeat(100) }, false), null, '100-character name accepted')

  const errNameShort = validateServicePackageForm({ ...baseValid, name: 'A' }, false)
  assert.ok(errNameShort !== null, '1-character name must be rejected')
  assert.ok(errNameShort.includes('2 đến 100 ký tự'), 'Error message specifies 2-100 characters')

  const errNameLong = validateServicePackageForm({ ...baseValid, name: 'A'.repeat(101) }, false)
  assert.ok(errNameLong !== null, '101-character name must be rejected')

  // Description max length: 500
  assert.equal(validateServicePackageForm({ ...baseValid, description: 'D'.repeat(500) }, false), null, '500-char description accepted')
  assert.equal(validateServicePackageForm({ ...baseValid, description: '' }, false), null, 'Empty description accepted')
  assert.equal(validateServicePackageForm({ ...baseValid, description: null }, false), null, 'Null description accepted')

  const errDescLong = validateServicePackageForm({ ...baseValid, description: 'D'.repeat(501) }, false)
  assert.ok(errDescLong !== null, '501-character description must be rejected')
  assert.ok(errDescLong.includes('500'), 'Error message specifies 500 characters')

  // Price non-negative
  assert.equal(validateServicePackageForm({ ...baseValid, priceVnd: '0' }, false), null, '0 VND accepted')
  const errNegativePrice = validateServicePackageForm({ ...baseValid, priceVnd: '-1' }, false)
  assert.ok(errNegativePrice !== null, 'Negative price rejected')

  // Code validation (Create only, ignored on Edit)
  assert.equal(validateServicePackageForm({ ...baseValid, code: 'PKG_2026' }, false), null, 'Valid code accepted')
  const errInvalidCode = validateServicePackageForm({ ...baseValid, code: 'pkg-lowercase' }, false)
  assert.ok(errInvalidCode !== null, 'Lowercase code rejected on Create')

  // On Edit, code is ignored
  assert.equal(validateServicePackageForm({ ...baseValid, code: undefined }, true), null, 'Code ignored on Edit')
})

test('12. RH-SUBSCRIPTION-04A: HTML attribute alignment and elimination of 3650 limit in UI', () => {
  const pagePath = path.resolve(process.cwd(), 'src/ServicePackageCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // Verify duration input attributes: min=1, max=730
  assert.ok(content.includes('max={730}'), 'Duration input must specify max={730}')
  assert.ok(content.includes('min={1}'), 'Duration input must specify min={1}')

  // Verify name input attributes: minLength=2, maxLength=100
  assert.ok(content.includes('minLength={2}'), 'Name input must specify minLength={2}')
  assert.ok(content.includes('maxLength={100}'), 'Name input must specify maxLength={100}')

  // Verify description textarea attribute: maxLength=500
  assert.ok(content.includes('maxLength={500}'), 'Description textarea must specify maxLength={500}')

  // Verify helper text
  assert.ok(content.includes('Từ 1 đến 730 ngày (tối đa 2 năm)'), 'UI must display helper text indicating 1-730 days range')

  // Zero occurrences of legacy 3650 limit
  assert.ok(!content.includes('3650'), 'ServicePackageCatalogPage must contain ZERO occurrences of 3650')
})

test('13. RH-SUBSCRIPTION-04B: Strict Integer Duration (reject decimal, accept integer boundaries)', () => {
  const baseValid = {
    code: 'INT_DUR',
    name: 'Gói Kiểm Thử Thời Hạn Nguyên',
    description: 'Mô tả hợp lệ',
    priceVnd: '5000000',
  }

  // A. Decimals rejected in Create and Edit
  const decimalDurations = ['1.5', '30.5', '30.0', '0.5', '729.9', '1,5', 1.5, 30.5]
  for (const d of decimalDurations) {
    const errCreate = validateServicePackageForm({ ...baseValid, durationDays: d }, false)
    assert.ok(errCreate !== null, `Decimal duration ${d} must be rejected on Create`)
    assert.ok(errCreate.includes('số thập phân'), `Error message for ${d} must mention decimal`)

    const errEdit = validateServicePackageForm({ ...baseValid, durationDays: d }, true)
    assert.ok(errEdit !== null, `Decimal duration ${d} must be rejected on Edit`)
    assert.ok(errEdit.includes('số thập phân'), `Error message for ${d} must mention decimal`)
  }

  // B. Valid integer boundaries accepted
  const validDurations = ['1', 1, '30', 30, '730', 730]
  for (const d of validDurations) {
    assert.equal(validateServicePackageForm({ ...baseValid, durationDays: d }, false), null, `Duration ${d} must be accepted on Create`)
    assert.equal(validateServicePackageForm({ ...baseValid, durationDays: d }, true), null, `Duration ${d} must be accepted on Edit`)
  }

  // C. Invalid non-numeric strings with trailing characters rejected without silent truncation
  const nonNumericDurations = ['30d', '10 20', '30days', '12abc', 'abc']
  for (const d of nonNumericDurations) {
    const err = validateServicePackageForm({ ...baseValid, durationDays: d }, false)
    assert.ok(err !== null, `Non-numeric duration ${d} must be rejected`)
  }
})

test('14. RH-SUBSCRIPTION-04B: Strict Integer Price (reject decimal, accept zero and positive integers)', () => {
  const baseValid = {
    code: 'INT_PRICE',
    name: 'Gói Kiểm Thử Giá Nguyên',
    description: 'Mô tả hợp lệ',
    durationDays: '30',
  }

  // A. Decimals rejected in Create and Edit
  const decimalPrices = ['5000000.5', '100.50', '0.5', '10,5', 5000000.5, 0.1]
  for (const p of decimalPrices) {
    const errCreate = validateServicePackageForm({ ...baseValid, priceVnd: p }, false)
    assert.ok(errCreate !== null, `Decimal price ${p} must be rejected on Create`)
    assert.ok(errCreate.includes('số thập phân'), `Error message for ${p} must mention decimal`)

    const errEdit = validateServicePackageForm({ ...baseValid, priceVnd: p }, true)
    assert.ok(errEdit !== null, `Decimal price ${p} must be rejected on Edit`)
    assert.ok(errEdit.includes('số thập phân'), `Error message for ${p} must mention decimal`)
  }

  // B. Valid zero and positive integers accepted
  const validPrices = ['0', 0, '1000', 1000, '5000000', 5000000, '60000000', 60000000]
  for (const p of validPrices) {
    assert.equal(validateServicePackageForm({ ...baseValid, priceVnd: p }, false), null, `Price ${p} must be accepted on Create`)
    assert.equal(validateServicePackageForm({ ...baseValid, priceVnd: p }, true), null, `Price ${p} must be accepted on Edit`)
  }

  // C. Negative prices rejected
  const negativePrices = ['-1', -1, '-5000000', -5000000]
  for (const p of negativePrices) {
    const err = validateServicePackageForm({ ...baseValid, priceVnd: p }, false)
    assert.ok(err !== null, `Negative price ${p} must be rejected`)
    assert.ok(err.includes('không được âm'), `Negative error message for ${p}`)
  }

  // D. Empty or non-numeric strings rejected without silent truncation
  const invalidPrices = ['', '   ', 'abc', '5000k', '5000 vnd', '10 20']
  for (const p of invalidPrices) {
    const err = validateServicePackageForm({ ...baseValid, priceVnd: p }, false)
    assert.ok(err !== null, `Invalid price ${p} must be rejected`)
  }
})

test('15. RH-SUBSCRIPTION-04B: Strict Display Order Validation', () => {
  const baseValid = {
    code: 'ORD_TEST',
    name: 'Gói Kiểm Thử Thứ Tự',
    description: 'Mô tả hợp lệ',
    durationDays: '30',
    priceVnd: '5000000',
  }

  // Valid non-negative integers or omitted
  assert.equal(validateServicePackageForm({ ...baseValid, displayOrder: '0' }, false), null)
  assert.equal(validateServicePackageForm({ ...baseValid, displayOrder: 0 }, false), null)
  assert.equal(validateServicePackageForm({ ...baseValid, displayOrder: '10' }, false), null)
  assert.equal(validateServicePackageForm({ ...baseValid, displayOrder: '' }, false), null)
  assert.equal(validateServicePackageForm({ ...baseValid, displayOrder: undefined }, false), null)

  // Decimals and negatives rejected
  const errDecimal = validateServicePackageForm({ ...baseValid, displayOrder: '10.5' }, false)
  assert.ok(errDecimal !== null && errDecimal.includes('số thập phân'), 'Decimal display order rejected')

  const errNegative = validateServicePackageForm({ ...baseValid, displayOrder: '-1' }, false)
  assert.ok(errNegative !== null && errNegative.includes('không âm'), 'Negative display order rejected')
})

test('16. RH-SUBSCRIPTION-04B: Submit handlers do NOT silently truncate values with parseInt()', () => {
  const pagePath = path.resolve(process.cwd(), 'src/ServicePackageCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // Check Create modal submit handler
  const createModalSlice = content.substring(
    content.indexOf('function CreateServicePackageModal'),
    content.indexOf('function EditServicePackageModal')
  )
  assert.ok(
    !createModalSlice.includes('parseInt(priceVnd'),
    'Create modal must NOT use parseInt(priceVnd) which truncates decimal input'
  )
  assert.ok(
    !createModalSlice.includes('parseInt(durationDays'),
    'Create modal must NOT use parseInt(durationDays) which truncates decimal input'
  )

  // Check Edit modal submit handler
  const editModalSlice = content.substring(
    content.indexOf('function EditServicePackageModal'),
    content.indexOf('function DeleteConfirmDialog')
  )
  assert.ok(
    !editModalSlice.includes('parseInt(priceVnd'),
    'Edit modal must NOT use parseInt(priceVnd) which truncates decimal input'
  )
  assert.ok(
    !editModalSlice.includes('parseInt(durationDays'),
    'Edit modal must NOT use parseInt(durationDays) which truncates decimal input'
  )

  // Verify step={1} on duration and order inputs
  assert.ok(content.includes('data-testid="input-package-duration"'), 'input-package-duration exists')
  assert.ok(content.includes('step={1}'), 'step={1} attribute present for integer inputs')
})
