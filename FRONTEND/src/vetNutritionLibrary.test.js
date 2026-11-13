import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

const frontendDir = path.resolve(process.cwd(), process.cwd().endsWith('FRONTEND') ? '.' : 'FRONTEND')

test('1. Veterinarian navigation exposes shared ration library in sidebar', () => {
  const appPath = path.resolve(frontendDir, 'src/App.jsx')
  const content = fs.readFileSync(appPath, 'utf-8')

  // Verify /vet/nutrition is in isVet nav items
  assert.ok(
    content.includes("to: '/vet/nutrition'"),
    'App.jsx must include route /vet/nutrition for Veterinarian navigation'
  )
  assert.ok(
    content.includes("label: 'Thư viện khẩu phần chuẩn'"),
    'App.jsx must label /vet/nutrition as "Thư viện khẩu phần chuẩn"'
  )

  // Verify non-vets (isStaff, club manager) do NOT have /vet/nutrition in their nav lists
  const staffNavStart = content.indexOf('const navItems = isStaff ? [')
  const isVetStart = content.indexOf('] : isVet ? [', staffNavStart)
  const defaultNavStart = content.indexOf('] : [', isVetStart)

  const staffNavContent = content.substring(staffNavStart, isVetStart)
  assert.ok(
    !staffNavContent.includes('/vet/nutrition'),
    'Stable Staff navigation must NOT include /vet/nutrition'
  )

  const defaultNavContent = content.substring(defaultNavStart)
  assert.ok(
    !defaultNavContent.includes('/vet/nutrition'),
    'Club Manager / Owner navigation must NOT include /vet/nutrition'
  )
})

test('2. VetPage renders dedicated VetNutritionLibraryView for activeTab=nutrition', () => {
  const vetPagePath = path.resolve(frontendDir, 'src/VetPage.jsx')
  const content = fs.readFileSync(vetPagePath, 'utf-8')

  // Verify import
  assert.ok(
    content.includes("import VetNutritionLibraryView from './VetNutritionLibraryView'"),
    'VetPage must import VetNutritionLibraryView'
  )

  // Verify activeTab === 'nutrition' renders VetNutritionLibraryView
  assert.ok(
    content.includes("activeTab === 'nutrition' && <VetNutritionLibraryView"),
    'VetPage must render VetNutritionLibraryView when activeTab is nutrition'
  )

  // Verify legacy redirection to /stable-care/nutrition is replaced
  assert.ok(
    !content.includes("activeTab === 'nutrition' && <Navigate to=\"/stable-care/nutrition\" replace />"),
    'VetPage must no longer redirect activeTab=nutrition to /stable-care/nutrition'
  )
})

test('3. vetApi and api clients define shared standard plan CRUD methods', () => {
  const vetApiPath = path.resolve(frontendDir, 'src/vetApi.js')
  const vetApiContent = fs.readFileSync(vetApiPath, 'utf-8')

  assert.ok(vetApiContent.includes("getStandardPlans: () => api('/api/nutrition/standard-plans')"))
  assert.ok(vetApiContent.includes("getStandardPlan: (planCode) => api(`/api/nutrition/standard-plans/${encodeURIComponent(planCode)}`)"))
  assert.ok(vetApiContent.includes("createStandardPlan: (payload) =>"))
  assert.ok(vetApiContent.includes("updateStandardPlan: (planCode, payload) =>"))
  assert.ok(vetApiContent.includes("deleteStandardPlan: (planCode) =>"))

  const apiPath = path.resolve(frontendDir, 'src/api.js')
  const apiContent = fs.readFileSync(apiPath, 'utf-8')
  assert.ok(apiContent.includes("createStandardPlan: (payload) => api('/api/nutrition/standard-plans'"))
  assert.ok(apiContent.includes("updateStandardPlan: (planCode, payload) => api(`/api/nutrition/standard-plans/"))
  assert.ok(apiContent.includes("deleteStandardPlan: (planCode) => api(`/api/nutrition/standard-plans/"))
})

test('4. VetNutritionLibraryView supports listing, metrics, and meal composition viewing', () => {
  const viewPath = path.resolve(frontendDir, 'src/VetNutritionLibraryView.jsx')
  const content = fs.readFileSync(viewPath, 'utf-8')

  // Verify component presence and key data rendering
  assert.ok(content.includes('export default function VetNutritionLibraryView'))
  assert.ok(content.includes('vetApi.getStandardPlans()'), 'Must load standard plans from backend')
  assert.ok(content.includes('plan.planCode'), 'Must display planCode')
  assert.ok(content.includes('plan.planName'), 'Must display planName')
  assert.ok(content.includes('plan.targetGroup'), 'Must display targetGroup')
  assert.ok(content.includes('plan.description'), 'Must display description')

  // Verify meal composition and metrics
  assert.ok(content.includes('concentrateKg'), 'Must calculate or display concentrateKg')
  assert.ok(content.includes('forageKg'), 'Must calculate or display forageKg')
  assert.ok(content.includes('feedingTime'), 'Must display meal feedingTime')
  assert.ok(content.includes('supplements'), 'Must display supplements')
})

test('5. Create/Edit Form enforces required fields and immutable planCode on edit', () => {
  const viewPath = path.resolve(frontendDir, 'src/VetNutritionLibraryView.jsx')
  const content = fs.readFileSync(viewPath, 'utf-8')

  assert.ok(content.includes('function StandardPlanFormModal'), 'Must define form modal')

  // In create mode: planCode is input; in edit mode: planCode is disabled
  assert.ok(content.includes('isCreate ?'), 'Must differentiate between create and edit mode')
  assert.ok(content.includes('disabled'), 'planCode must be disabled when editing')
  assert.ok(content.includes('Mã định danh bất biến'), 'Must inform user that planCode is immutable')

  // Meal adding and removing
  assert.ok(content.includes('addMeal'), 'Must allow adding new meals')
  assert.ok(content.includes('removeMeal'), 'Must allow removing meals')

  // Validation
  assert.ok(content.includes('Mã khẩu phần mẫu không được để trống'), 'Must validate planCode presence')
  assert.ok(content.includes('Tên khẩu phần mẫu không được để trống'), 'Must validate planName presence')
  assert.ok(content.includes('Khẩu phần mẫu phải có ít nhất một bữa ăn'), 'Must validate non-empty meals')
  assert.ok(content.includes('Lượng thức ăn tinh'), 'Must validate concentrateKg non-negative')
  assert.ok(content.includes('Lượng thức ăn thô xanh'), 'Must validate forageKg non-negative')
})

test('6. Delete Confirmation Modal enforces explicit acknowledgement and warns about unassignment', () => {
  const viewPath = path.resolve(frontendDir, 'src/VetNutritionLibraryView.jsx')
  const content = fs.readFileSync(viewPath, 'utf-8')

  assert.ok(content.includes('function DeletePlanConfirmModal'), 'Must define delete modal')
  assert.ok(content.includes('vetApi.deleteStandardPlan'), 'Must invoke delete endpoint')

  // Verify required warning statements
  assert.ok(content.includes('Xóa vĩnh viễn'), 'Must warn about hard delete')
  assert.ok(content.includes('ĐÃ HỦY (CANCELLED)'), 'Must warn that active plans are cancelled')
  assert.ok(content.includes('CHƯA GÁN KHẨU PHẦN (UNASSIGNED)'), 'Must warn horses become unassigned')
  assert.ok(content.includes('Bảo tồn lịch sử'), 'Must confirm historical records remain preserved')
  assert.ok(content.includes('không tự động chọn khẩu phần thay thế'), 'Must state no auto-replacement')

  // Checkbox acknowledgement requirement
  assert.ok(content.includes('disabled={!acknowledged || busy}'), 'Delete button must be disabled until user acknowledges')
})

test('7. StableCarePage preserves standard plan viewing with dynamic count and Vet link', () => {
  const stablePagePath = path.resolve(frontendDir, 'src/StableCarePage.jsx')
  const content = fs.readFileSync(stablePagePath, 'utf-8')

  // Dynamic template count
  assert.ok(content.includes('{standardPlans.length} mẫu tiêu chuẩn'), 'Must display dynamic count of standard plans')

  // Vet link to library management
  assert.ok(content.includes('href="/vet/nutrition"'), 'Must link to /vet/nutrition for veterinarians')
  assert.ok(content.includes('Quản lý thư viện'), 'Must provide link to manage library')
})
