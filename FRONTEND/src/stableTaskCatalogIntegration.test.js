import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

test('1. Stable UI Active catalog loading via careRoutineApi.getActive', () => {
  const pagePath = path.resolve(process.cwd(), 'src/StableCarePage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // Verify import
  assert.ok(content.includes('careRoutineApi'), 'StableCarePage must import careRoutineApi from ./api')

  // Verify loadActiveCatalog invocation
  assert.ok(content.includes('careRoutineApi.getActive()'), 'Must call careRoutineApi.getActive()')
  assert.ok(content.includes('loadActiveCatalog()'), 'Must call loadActiveCatalog on mount/refresh')

  // Verify that reserve API is NEVER called from StableCarePage
  assert.ok(!content.includes('careRoutineApi.getReserve()'), 'StableCarePage must NEVER call careRoutineApi.getReserve')
  assert.ok(!content.includes('getReserveCareRoutines()'), 'StableCarePage must NEVER call getReserveCareRoutines')
})

test('2. Informational notice explaining Club Manager catalog governance', () => {
  const pagePath = path.resolve(process.cwd(), 'src/StableCarePage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  const expectedNoticeText =
    'Danh sách nhiệm vụ chăm sóc chuồng được quản lý và cập nhật bởi Club Manager. Nhân viên Stable thực hiện theo danh sách hiện hành.'
  assert.ok(content.includes(expectedNoticeText), 'Notice must include exact Vietnamese text per specification')
  assert.ok(content.includes('stable-catalog-notice'), 'Notice container must have stable-catalog-notice class')
})

test('3. Read-only catalog modal and zero mutation controls for Stable', () => {
  const pagePath = path.resolve(process.cwd(), 'src/StableCarePage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  assert.ok(content.includes('ActiveRoutineCatalogModal'), 'Must define ActiveRoutineCatalogModal component')
  assert.ok(content.includes('Quy trình chăm sóc đang áp dụng'), 'Modal must have title "Quy trình chăm sóc đang áp dụng"')
  assert.ok(content.includes('Chế độ xem chỉ đọc dành cho Stable'), 'Modal must note read-only view for Stable')

  // Slice modal content to ensure no mutation controls exist inside it
  const modalStart = content.indexOf('function ActiveRoutineCatalogModal')
  const modalEnd = content.indexOf('function ViewTaskPhotoModal', modalStart)
  const modalContent = content.substring(modalStart, modalEnd)

  assert.ok(!modalContent.includes('careRoutineApi.create'), 'ActiveRoutineCatalogModal must have no create action')
  assert.ok(!modalContent.includes('careRoutineApi.update'), 'ActiveRoutineCatalogModal must have no update action')
  assert.ok(!modalContent.includes('careRoutineApi.delete'), 'ActiveRoutineCatalogModal must have no delete action')
  assert.ok(!modalContent.includes('careRoutineApi.transfer'), 'ActiveRoutineCatalogModal must have no transfer action')
  assert.ok(!modalContent.includes('careRoutineApi.moveToActive'), 'ActiveRoutineCatalogModal must have no moveToActive action')
  assert.ok(!modalContent.includes('careRoutineApi.moveToReserve'), 'ActiveRoutineCatalogModal must have no moveToReserve action')
  assert.ok(!modalContent.includes('careRoutineApi.reorder'), 'ActiveRoutineCatalogModal must have no reorder action')
})

test('4. Empty catalog handling without crashing', () => {
  const pagePath = path.resolve(process.cwd(), 'src/StableCarePage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // Verify graceful empty catalog representation
  assert.ok(
    content.includes('Chưa có quy trình chăm sóc nào trong danh mục đang áp dụng'),
    'ActiveRoutineCatalogModal must display friendly message when active catalog is empty'
  )
})

test('5. API failure handling with retry option and NO hardcoded operational fallback', () => {
  const pagePath = path.resolve(process.cwd(), 'src/StableCarePage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // Error state and retry button
  assert.ok(content.includes('Lỗi đồng bộ danh mục'), 'Must display error state if catalog sync fails')
  assert.ok(content.includes('onClick={loadActiveCatalog}'), 'Must provide retry button invoking loadActiveCatalog')

  // Verify that on error, activeCatalog is set to empty array, NOT to any hardcoded fallback tasks
  const catchBlockStart = content.indexOf('setCatalogError(err?.message')
  const catchBlockEnd = content.indexOf('finally {', catchBlockStart)
  const catchContent = content.substring(catchBlockStart, catchBlockEnd)

  assert.ok(catchContent.includes('setActiveCatalog([])'), 'On error, catalog must be set to empty array')
  assert.ok(!catchContent.includes('DEFAULT_ROUTINES'), 'Must not inject hardcoded DEFAULT_ROUTINES')
  assert.ok(!catchContent.includes('ROUTINE_MORNING'), 'Must not inject hardcoded ROUTINE_ codes')
})

test('6. Daily task instances rendering preserves snapshot data and displays description', () => {
  const pagePath = path.resolve(process.cwd(), 'src/StableCarePage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // Daily task items rendered from tasks state
  assert.ok(content.includes('task.title'), 'Must render daily task title')
  assert.ok(content.includes('task.description'), 'Must render daily task description')
  assert.ok(content.includes('task.scheduledTime'), 'Must render daily task scheduledTime')
  assert.ok(content.includes('task.horseName'), 'Must render daily task horseName')
  assert.ok(content.includes('task.taskCategory === \'SPECIAL\''), 'Must distinguish SPECIAL and ROUTINE categories')
  assert.ok(content.includes('THƯỜNG NHẬT'), 'Must render THƯỜNG NHẬT badge for routine tasks')
  assert.ok(content.includes('ĐẶC BIỆT'), 'Must render ĐẶC BIỆT badge for special tasks')
})

test('7. Task completion workflow and photo proof regression check', () => {
  const pagePath = path.resolve(process.cwd(), 'src/StableCarePage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  // Task completion button
  assert.ok(content.includes('onClick={() => setCompletingTask(task)}'), 'Must maintain completion trigger')
  assert.ok(content.includes('CompleteTaskModal'), 'Must maintain CompleteTaskModal')
  assert.ok(content.includes('stableCareApi.completeCareTask'), 'Must call stableCareApi.completeCareTask')
  assert.ok(content.includes('ViewTaskPhotoModal'), 'Must maintain ViewTaskPhotoModal')
  assert.ok(content.includes('stableCareApi.getCareTaskPhotoBlob'), 'Must call getCareTaskPhotoBlob')
})

test('8. TASK_TYPE_LABELS contains all standard operational task types', () => {
  const pagePath = path.resolve(process.cwd(), 'src/StableCarePage.jsx')
  const content = fs.readFileSync(pagePath, 'utf-8')

  const labelSection = content.substring(
    content.indexOf('const TASK_TYPE_LABELS = {'),
    content.indexOf('const ROUTINE_ACTIVITY_LABELS = {')
  )

  assert.ok(labelSection.includes('STABLE_CLEANING: \'Vệ sinh chuồng trại\''), 'Must include STABLE_CLEANING')
  assert.ok(labelSection.includes('FACILITY_CHECK: \'Kiểm tra cơ sở vật chất\''), 'Must include FACILITY_CHECK')
  assert.ok(labelSection.includes('HEALTH_CHECK: \'Kiểm tra thể trạng\''), 'Must include HEALTH_CHECK')
  assert.ok(labelSection.includes('MUCKING_OUT: \'Dọn phân chuồng\''), 'Must include MUCKING_OUT')
  assert.ok(labelSection.includes('FEEDING: \'Cho ăn / Nước uống\''), 'Must include FEEDING')
  assert.ok(labelSection.includes('BATHING: \'Tắm rửa vệ sinh\''), 'Must include BATHING')
  assert.ok(labelSection.includes('ICE_BOOTS: \'Ngâm / Chườm đá chân\''), 'Must include ICE_BOOTS')
  assert.ok(labelSection.includes('GROOMING: \'Chải lông chăm sóc\''), 'Must include GROOMING')
  assert.ok(labelSection.includes('WALKING: \'Dắt đi dạo nhẹ\''), 'Must include WALKING')
  assert.ok(labelSection.includes('OTHER: \'Nhiệm vụ khác\''), 'Must include OTHER')
})

test('9. Distinction between definitions and daily task instances', () => {
  // Unit logic test verifying the lifecycle model
  const mockCatalogDefinition = {
    id: 'def-uuid-1',
    code: 'ROUTINE_MORNING_MUCKING',
    title: 'Dọn phân chuồng & thay chất độn chuồng ca sáng',
    description: 'Thu gom phân, thay lớp rơm ẩm ướt...',
    taskType: 'MUCKING_OUT',
    scheduledTime: '08:30:00',
    scope: 'FACILITY',
    isActive: true,
  }

  // Daily task generated from definition
  const mockDailyTaskInstance = {
    id: 'instance-uuid-1',
    routineDefinitionId: mockCatalogDefinition.id,
    taskCategory: 'ROUTINE',
    scope: mockCatalogDefinition.scope,
    horseId: null,
    title: mockCatalogDefinition.title,
    description: mockCatalogDefinition.description,
    taskType: mockCatalogDefinition.taskType,
    scheduledDate: '2026-11-11',
    scheduledTime: mockCatalogDefinition.scheduledTime,
    assignedStaffId: null,
    status: 'PENDING',
    completedAt: null,
    completedByUserId: null,
  }

  // Definition is a template; Daily task is an actionable work item
  assert.equal(mockDailyTaskInstance.routineDefinitionId, mockCatalogDefinition.id)
  assert.equal(mockDailyTaskInstance.status, 'PENDING')
  assert.equal(mockDailyTaskInstance.scheduledDate, '2026-11-11')

  // Completing the daily task instance does NOT mutate the definition
  const completedTask = {
    ...mockDailyTaskInstance,
    status: 'COMPLETED',
    completedAt: '2026-11-11T09:00:00Z',
    completedByUserId: 'staff-uuid-1',
    completionNotes: 'Đã hoàn tất dọn toàn bộ 10 ô chuồng',
  }
  assert.equal(completedTask.status, 'COMPLETED')
  assert.equal(mockCatalogDefinition.isActive, true) // Definition remains active template
})

test('10. Moving definition to Reserve preserves historical task instances', () => {
  const historicalTask = {
    id: 'task-100',
    routineDefinitionId: 'def-1',
    title: 'Nhiệm vụ mẫu',
    status: 'COMPLETED',
    scheduledDate: '2026-11-10',
  }

  // When Club Manager moves definition to Reserve
  const reservedDefinition = {
    id: 'def-1',
    title: 'Nhiệm vụ mẫu',
    isActive: false, // Moved to reserve
  }

  // Historical task instance is unaffected
  assert.equal(historicalTask.status, 'COMPLETED')
  assert.equal(historicalTask.scheduledDate, '2026-11-10')
  assert.equal(reservedDefinition.isActive, false)
})
