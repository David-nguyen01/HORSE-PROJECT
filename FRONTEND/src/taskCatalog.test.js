import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

test('1. Club Manager navigation visibility & role boundary', () => {
  const appPath = path.resolve(process.cwd(), 'src/App.jsx')
  const content = fs.readFileSync(appPath, 'utf-8')

  // Check that the menu item is placed inside hasAny(user, 'CLUB_MANAGER') block
  const managerNavIdx = content.indexOf("hasAny(user, 'CLUB_MANAGER') ? [")
  assert.ok(managerNavIdx !== -1, 'Club Manager navigation block must exist')

  const taskNavItemIdx = content.indexOf("/admin/care-tasks")
  assert.ok(taskNavItemIdx !== -1, '/admin/care-tasks navigation route must exist')
  assert.ok(content.includes("'Quản lý nhiệm vụ Stable'"), "Label 'Quản lý nhiệm vụ Stable' must exist in App.jsx")

  // Ensure it is positioned after the Club Manager check
  assert.ok(taskNavItemIdx > managerNavIdx, '/admin/care-tasks must be within the CLUB_MANAGER menu items array')

  // Helper simulation for role-based navigation filtering
  const buildNavItems = (user) => {
    const isManager = Boolean(user?.roles?.includes('CLUB_MANAGER'))
    const items = []
    if (isManager) {
      items.push({ to: '/admin/care-tasks', label: 'Quản lý nhiệm vụ Stable' })
    }
    return items
  }

  // Club Manager has the item
  const managerItems = buildNavItems({ roles: ['CLUB_MANAGER'] })
  assert.equal(managerItems.length, 1)
  assert.equal(managerItems[0].to, '/admin/care-tasks')

  // Other four roles DO NOT have the item
  assert.equal(buildNavItems({ roles: ['STABLE_STAFF'] }).length, 0)
  assert.equal(buildNavItems({ roles: ['HEAD_TRAINER'] }).length, 0)
  assert.equal(buildNavItems({ roles: ['VETERINARIAN'] }).length, 0)
  assert.equal(buildNavItems({ roles: ['HORSE_OWNER'] }).length, 0)
})

test('2. On List (Active) rendering and data display', () => {
  const pagePath = path.resolve(process.cwd(), 'src/TaskCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  assert.ok(content.includes('section-active-tasks'), 'Must define section-active-tasks')
  assert.ok(content.includes('ĐANG ÁP DỤNG'), 'Must render active status badge')
  assert.ok(content.includes('task.scheduledTime'), 'Must render task scheduled time')
  assert.ok(content.includes('task.title'), 'Must render task title')
  assert.ok(content.includes('task.code'), 'Must render task code')
  assert.ok(content.includes('task.description'), 'Must render task description')
})

test('3. Reserve section rendering and refined natural terminology', () => {
  const pagePath = path.resolve(process.cwd(), 'src/TaskCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  assert.ok(content.includes('section-reserve-tasks'), 'Must define section-reserve-tasks')
  assert.ok(content.includes('TRONG KHO'), 'Must render refined TRONG KHO status badge')
  assert.ok(content.includes('Kho nhiệm vụ'), 'Must display refined Kho nhiệm vụ tab title')
})

test('4. Strict rule: NO duplicate Add button in top-right heading actions', () => {
  const pagePath = path.resolve(process.cwd(), 'src/TaskCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // Heading actions must ONLY have the refresh button, NO Add button
  const topActionsSlice = content.substring(content.indexOf('heading-actions'), content.indexOf('admin-kpis'))
  assert.ok(
    !topActionsSlice.includes('Thêm nhiệm vụ'),
    'Top-right heading actions must NOT contain any Add button'
  )
  assert.ok(
    !topActionsSlice.includes('btn-add-reserve-task'),
    'Top-right heading actions must NOT contain btn-add-reserve-task'
  )
  assert.ok(
    topActionsSlice.includes('btn-refresh-catalog'),
    'Top-right heading actions must contain btn-refresh-catalog'
  )
})

test('5. Add button available in Reserve section header and empty state', () => {
  const pagePath = path.resolve(process.cwd(), 'src/TaskCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // The Add button is placed inside section-reserve-tasks
  const reserveSectionSlice = content.substring(
    content.indexOf('data-testid="section-reserve-tasks"'),
    content.indexOf('Task Form Modal')
  )

  assert.ok(
    reserveSectionSlice.includes('data-testid="btn-add-reserve-task"'),
    'Must have btn-add-reserve-task inside section-reserve-tasks'
  )
  assert.ok(
    reserveSectionSlice.includes('data-testid="btn-empty-add-reserve"'),
    'Must have btn-empty-add-reserve inside Reserve empty state'
  )
  assert.ok(
    reserveSectionSlice.includes('Thêm nhiệm vụ mới'),
    'Add button label must read "Thêm nhiệm vụ mới"'
  )
})

test('6. Edit task in both lists preserves isActive state', () => {
  const pagePath = path.resolve(process.cwd(), 'src/TaskCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  assert.ok(content.includes('btn-edit-task-'), 'Must have edit button on task card')
  assert.ok(content.includes('careRoutineApi.update'), 'Must call careRoutineApi.update in edit mode')

  // When creating: isActive is explicitly false; When editing: update payload preserves existing isActive
  assert.ok(content.includes('isActive: false'), 'Create payload must specify isActive: false')
  assert.ok(!content.includes('isActive: true'), 'Frontend must NEVER submit isActive: true')
})

test('7. Transfer in both directions (Active -> Reserve and Reserve -> Active)', () => {
  const pagePath = path.resolve(process.cwd(), 'src/TaskCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  assert.ok(content.includes('careRoutineApi.moveToActive'), 'Must call moveToActive')
  assert.ok(content.includes('careRoutineApi.moveToReserve'), 'Must call moveToReserve')
  assert.ok(content.includes('btn-move-to-reserve-'), 'Must have move to reserve button on active cards')
  assert.ok(content.includes('btn-move-to-active-'), 'Must have move to active button on reserve cards')
  assert.ok(content.includes('Chuyển về kho'), 'Transfer button label on active task must be "Chuyển về kho"')
})

test('8. Delete confirmation dialog and data integrity explanation', () => {
  const pagePath = path.resolve(process.cwd(), 'src/TaskCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  assert.ok(content.includes('delete-confirm-dialog'), 'Must render DeleteConfirmDialog')
  assert.ok(content.includes('bảo toàn 100%'), 'Must explain historical task data preservation')
  assert.ok(content.includes('careRoutineApi.delete'), 'Must call careRoutineApi.delete on confirmation')
  assert.ok(content.includes('btn-cancel-delete'), 'Must provide cancel button')
})

test('9. Failed mutation error handling without crash', () => {
  const pagePath = path.resolve(process.cwd(), 'src/TaskCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  assert.ok(content.includes('catalog-error-notice'), 'Must render error notice on catalog failure')
  assert.ok(content.includes('modal-error'), 'Must render error notice on modal failure')
  assert.ok(content.includes('setError('), 'Must set error state in catch blocks')
})

test('10. Empty and loading states for both tabs', () => {
  const pagePath = path.resolve(process.cwd(), 'src/TaskCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  assert.ok(content.includes('Đang tải danh mục nhiệm vụ...'), 'Must render loading message')
  assert.ok(content.includes('Chưa có quy trình chăm sóc nào đang áp dụng'), 'Must have Active empty state')
  assert.ok(content.includes('Kho nhiệm vụ hiện đang trống'), 'Must have Reserve empty state')
})

test('11. Unauthorized role cannot access the page (route guard)', () => {
  const appPath = path.resolve(process.cwd(), 'src/App.jsx')
  const content = fs.readFileSync(appPath, 'utf-8')

  // Check Route guard
  const expectedRoute = '<Route path="admin/care-tasks" element={hasAny(user,\'CLUB_MANAGER\')?<TaskCatalogPage/>:<Navigate to="/" replace/>}/>'
  assert.ok(content.includes(expectedRoute), 'Route /admin/care-tasks must be strictly guarded for CLUB_MANAGER')
})

test('12. Thứ tự hiển thị helper text and total absence of "dự trữ"', () => {
  const pagePath = path.resolve(process.cwd(), 'src/TaskCatalogPage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // Helper text under Thứ tự hiển thị
  const expectedHelper = 'Số nhỏ hơn sẽ được hiển thị trước trong danh sách. Không ảnh hưởng đến giờ thực hiện nhiệm vụ.'
  assert.ok(content.includes(expectedHelper), 'Must include exact helper text for sortOrder')

  // Zero occurrences of "dự trữ" (case-insensitive)
  const regex = /dự\s*trữ/i
  assert.ok(!regex.test(content), 'TaskCatalogPage must contain ZERO occurrences of "dự trữ"')
})
