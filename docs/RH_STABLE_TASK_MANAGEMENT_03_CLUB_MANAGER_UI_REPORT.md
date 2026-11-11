# TASK RH-STABLE-TASK-MANAGEMENT-03: Club Manager Task Catalog UI Report

**Date:** 2026-11-11  
**Author:** Antigravity  
**Task ID:** `RH-STABLE-TASK-MANAGEMENT-03`  
**Repository (Product Source):** `C:\DEV\RACE HORSE\HORSE-PROJECT` (Branch: `develop`)  
**Repository (Workflow & Mirror):** `C:\DEV\clone-horse` (Branch: `main`)  
**Deliverable Document:** `docs/RH_STABLE_TASK_MANAGEMENT_03_CLUB_MANAGER_UI_REPORT.md`  

---

## 1. Executive Summary

This task implements the user interface for Club Manager to manage the Stable task catalog, consuming the backend domain and REST APIs completed in `RH-STABLE-TASK-MANAGEMENT-02`:

1. **Dedicated Navigation & Route Guarding:**
   - Added new menu item **"Quản lý nhiệm vụ Stable"** under Club Manager navigation (`/admin/care-tasks`).
   - Accessible exclusively to `ROLE_CLUB_MANAGER`. All other four roles (`STABLE_STAFF`, `HEAD_TRAINER`, `VETERINARIAN`, `HORSE_OWNER`) are redirected to `/`.
2. **Two Distinct Catalog Sections (Tabs):**
   - **Danh sách đang áp dụng (On List):** Displays published active tasks (`is_active = true`) generating daily routine tasks. Provides View, Edit, Move to Reserve, and Permanent Delete. **Strictly disallows task creation (no Add button).**
   - **Kho nhiệm vụ dự trữ (Reserve Library):** Displays reserve tasks (`is_active = false`). Provides Add Task, View, Edit, Move to On List, and Permanent Delete.
3. **Explicit Transfer Workflows:**
   - "Chuyển vào dự trữ" (Move to Reserve) and "Áp dụng vào lịch" (Move to Active) provide clear, explicit transitions between collections with instantaneous feedback.
4. **Safe Permanent Deletion Confirmation:**
   - Permanent delete modal with comprehensive warning: explains physical catalog removal while assuring users that historical care tasks and staff completion audit records are preserved 100%.
5. **Form Validation & Ergonomics:**
   - Full validation of Vietnamese inputs, task types, daily scheduled times (`HH:mm`), scope (`FACILITY` vs `HORSE`), and sort order.
   - Disallows creating active tasks directly: newly created definitions are created into the Reserve Library.

---

## 2. Route & Navigation Changes

### 2.1 Sidebar Navigation Entry
In `FRONTEND/src/App.jsx`:
```jsx
...(hasAny(user, 'CLUB_MANAGER') ? [
  { to: '/races', label: 'Quản lý giải đua', icon: Award },
  { to: '/admin/finance-reports', label: 'Tài chính & Báo cáo', icon: DollarSign },
  { to: '/admin/stables', label: 'Chuồng trại', icon: Warehouse },
  { to: '/admin/care-tasks', label: 'Quản lý nhiệm vụ Stable', icon: ClipboardList },
  { to: '/admin/inventory-catalog', label: 'Vật tư & Kho hàng', icon: Package },
  ...
] : [])
```
- Visible exclusively to users with `ROLE_CLUB_MANAGER`.
- Positioned alongside facility and operational management items (`Chuồng trại`, `Vật tư & Kho hàng`).

### 2.2 Route Guard
In `FRONTEND/src/App.jsx`:
```jsx
<Route
  path="admin/care-tasks"
  element={hasAny(user, 'CLUB_MANAGER') ? <TaskCatalogPage /> : <Navigate to="/" replace />}
/>
```
- Protects the route from direct URL access by non-manager roles.
- Non-manager users (`STABLE_STAFF`, `HEAD_TRAINER`, `VETERINARIAN`, `HORSE_OWNER`) are immediately redirected to `/`.

---

## 3. Components Implemented

### 3.1 `TaskCatalogPage` (`FRONTEND/src/TaskCatalogPage.jsx`)
- **Header:** Features standard Racehorse page heading with eyebrow `QUẢN TRỊ QUY TRÌNH CHĂM SÓC`, title `Quản lý nhiệm vụ Stable`, explanatory description, manual refresh button, and dynamic action buttons:
  - When viewing `RESERVE` tab: displays `<button className="button primary"><Plus size={16}/> Thêm nhiệm vụ dự trữ</button>`.
  - When viewing `ACTIVE` tab: **strictly no Add Task button**.
- **KPI Summary Strip:** Displays counts and clickable cards for "Đang áp dụng (On List)", "Kho nhiệm vụ dự trữ (Reserve)", and "Tổng quy trình chuồng".
- **Tabs (`.flow4-tabs`):** Switches between Active List and Reserve Library with real-time record count badges.
- **Toolbar:** Real-time search by title, code, or description, plus dropdown filter by task type (`STABLE_CLEANING`, `FACILITY_CHECK`, `FEEDING`, `HEALTH_CHECK`, `MUCKING_OUT`, `BATHING`, `GROOMING`, `ICE_BOOTS`, `WALKING`, `OTHER`).
- **Notice Banners:** Success notifications with auto-dismiss (4.5s) and error alerts with dismiss buttons.

### 3.2 `TaskCard`
- Time pill with clock icon (e.g. `06:00`).
- Color-coded status badge (`ĐANG ÁP DỤNG` in green vs `DỰ TRỮ` in amber).
- Task Type badge and Scope badge (`Khu vực chung / Toàn chuồng` vs `Từng cá thể ngựa`).
- Full task title, system code (`ROUTINE_...`), sort order, and description instructions.
- Contextual actions:
  - On Active card: "Chuyển vào dự trữ", "Sửa", "Xóa".
  - On Reserve card: "Áp dụng vào lịch", "Sửa", "Xóa".

### 3.3 `TaskDefinitionModal`
- Clean modal dialog for creating or editing definitions.
- In Create mode:
  - Title: "Tạo nhiệm vụ dự trữ mới".
  - Notice explaining that new tasks are saved directly into the Reserve Library.
  - Submits with `isActive: false` (strictly preventing active creation).
- In Edit mode:
  - Title: "Chỉnh sửa quy trình chăm sóc".
  - Displays read-only system code.
  - Preserves existing `isActive` status.
- Fields validated: Tiêu đề (*), Loại nhiệm vụ (*), Giờ thực hiện hàng ngày (*), Phạm vi áp dụng, Thứ tự hiển thị, Mô tả chi tiết & hướng dẫn thực hiện.
- Busy/saving state disables inputs and buttons to prevent duplicate submissions.

### 3.4 `DeleteConfirmDialog`
- Explicit confirmation modal explaining physical removal of definition from catalog.
- Highlights data preservation guarantee: historical care tasks, completion notes, photos, and audit logs remain 100% intact.
- Action buttons: "Hủy bỏ" and "Xác nhận xóa vĩnh viễn" (with danger button styling).

---

## 4. Backend API Contracts Consumed

All endpoints communicate with `/api/care-routine-definitions` via `careRoutineApi` in `FRONTEND/src/api.js`:

| Action | API Endpoint | Request Body | Handled Response |
|---|---|---|---|
| Fetch Active List | `GET /api/care-routine-definitions/active` | None | `List<CareRoutineDefinitionResponse>` |
| Fetch Reserve List | `GET /api/care-routine-definitions/reserve` | None | `List<CareRoutineDefinitionResponse>` |
| Create in Reserve | `POST /api/care-routine-definitions` | `CreateCareRoutineDefinitionRequest` (`title`, `description`, `taskType`, `scheduledTime`, `scope`, `sortOrder`, `isActive: false`) | `CareRoutineDefinitionResponse` (201 Created) |
| Edit Definition | `PUT /api/care-routine-definitions/{id}` | `UpdateCareRoutineDefinitionRequest` (`title`, `description`, `taskType`, `scheduledTime`, `scope`, `sortOrder`) | `CareRoutineDefinitionResponse` |
| Move to Active | `POST /api/care-routine-definitions/{id}/move-to-active` | None | `CareRoutineDefinitionResponse` |
| Move to Reserve | `POST /api/care-routine-definitions/{id}/move-to-reserve` | None | `CareRoutineDefinitionResponse` |
| Permanent Delete | `DELETE /api/care-routine-definitions/{id}` | None | 204 No Content |
| Reorder (Active) | `PUT /api/care-routine-definitions/reorder` | `ReorderCareRoutinesRequest` (`orderedIds`) | `List<CareRoutineDefinitionResponse>` |

---

## 5. Authorization & Role Boundaries

- **Club Manager:** Full access to view Active/Reserve, create definitions in Reserve, edit, transfer between collections, and permanently delete.
- **Stable Staff:** No access to task management UI. Sidebar hides "Quản lý nhiệm vụ Stable". Navigating to `/admin/care-tasks` redirects to `/`. Daily task execution on `/stable-care/tasks` remains strictly preserved and unchanged.
- **Head Trainer, Veterinarian, Horse Owner:** No access to task management UI. Sidebar hides the menu item. Route redirects to `/`.

---

## 6. Test & Build Execution Results

### 6.1 Frontend Test Suite (`FRONTEND/src/taskCatalog.test.js`)
All 11 required test areas from Part G passed successfully:
```
✔ 1. Club Manager navigation visibility & role boundary (4.811ms)
✔ 2. On List (Active) rendering and data display (1.2466ms)
✔ 3. Reserve Library rendering and data display (0.5571ms)
✔ 4. Strict rule: NO Add button in On List (Active tab) (0.4899ms)
✔ 5. Add button available in Reserve Library tab (0.3504ms)
✔ 6. Edit task in both lists preserves isActive state (0.3849ms)
✔ 7. Transfer in both directions (Active -> Reserve and Reserve -> Active) (1.7562ms)
✔ 8. Delete confirmation dialog and data integrity explanation (0.6838ms)
✔ 9. Failed mutation error handling without crash (0.6677ms)
✔ 10. Empty and loading states for both tabs (0.756ms)
✔ 11. Unauthorized role cannot access the page (route guard) (1.5879ms)

Total Frontend Test Results: 77 passed, 0 failed, 0 skipped (duration: ~265ms)
```

### 6.2 Production Build Verification (`npm.cmd run build`)
```
vite v8.2.2 building client environment for production...
transforming...
✓ 3202 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                                    0.73 kB │ gzip:   0.42 kB
dist/assets/index-ChtB4gFY.css                    91.77 kB │ gzip:  19.04 kB
dist/assets/index-BkXKLUqR.js                    434.73 kB │ gzip: 127.68 kB
dist/assets/VetPage-CiawjDrC.js                1,180.55 kB │ gzip: 311.96 kB
✓ built in 1.31s
```
Zero build errors, zero chunk resolution errors, zero syntax warnings.

### 6.3 Backend Verification
Backend regression test suite re-verified with 47 tests passed (0 failures):
```
[INFO] Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 7. Product Source Preservation Evidence

- `git status` in `C:\DEV\RACE HORSE\HORSE-PROJECT` confirms that all pre-existing dirty, staged, and untracked files from previous tasks remain completely intact.
- Zero git commits or pushes were executed against `HORSE-PROJECT`.
- No database migrations, seeds, or writes were executed.
- No local backend or frontend processes were restarted.

---

## 8. Remaining Integration Requirements for Stable

1. **Stable Staff Daily Task Generation:** Stable Staff daily routine view already retrieves tasks generated from `dbo.care_routine_definitions WHERE is_active = 1`. Any definition activated by Club Manager via the new UI will automatically participate in daily routine instance generation for subsequent days.
2. **Initial Reserve Seeding (Part D of Task 02):** The four proposed reserve definitions can be activated into the system once formal database seed authorization is granted.
