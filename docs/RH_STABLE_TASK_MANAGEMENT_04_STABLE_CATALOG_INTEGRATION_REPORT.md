# TASK RH-STABLE-TASK-MANAGEMENT-04: Stable Task Catalog API Integration Report

**Date:** 2026-11-11  
**Author:** Antigravity  
**Task ID:** `RH-STABLE-TASK-MANAGEMENT-04`  
**Repository (Product Source):** `C:\DEV\RACE HORSE\HORSE-PROJECT` (Branch: `develop`)  
**Repository (Workflow & Mirror):** `C:\DEV\clone-horse` (Branch: `main`)  
**Deliverable Document:** `docs/RH_STABLE_TASK_MANAGEMENT_04_STABLE_CATALOG_INTEGRATION_REPORT.md`  
**Remote Up-Report:** `thangnx-mc/clone-horse/up-report/RH_STABLE_TASK_MANAGEMENT_04_STABLE_CATALOG_INTEGRATION_REPORT.md`

---

## 1. Executive Summary

This task integrates the existing Stable task execution interface with the Club Manager-managed Active Task List implemented in `RH-STABLE-TASK-MANAGEMENT-02` and `RH-STABLE-TASK-MANAGEMENT-03`:

1. **Active Catalog API Connection:**
   - The Stable task view (`/stable-care/tasks` via `CareTasksView`) dynamically connects to `GET /api/care-routine-definitions/active` via `careRoutineApi.getActive()`.
   - The Reserve Library API (`/reserve`) is never called by Stable Staff and remains strictly guarded by Spring Security (HTTP 403 Forbidden).
   - Zero mutation controls are rendered for Stable Staff (read-only reference modal provided).
2. **Defensive Audit of Operational Definitions:**
   - Forensic analysis confirmed that no hardcoded routine task arrays were used as operational sources in the Frontend.
   - The single source of truth for daily task generation remains `dbo.care_routine_definitions WHERE is_active = 1` through Backend catch-up generation.
   - Strict defensive error handling ensures that if the catalog API fails, no silent fallback tasks are injected; instead, a localized error state with a retry option is presented.
   - Expanded `TASK_TYPE_LABELS` in `StableCarePage.jsx` to map all standard catalog task types (`STABLE_CLEANING`, `FACILITY_CHECK`, `HEALTH_CHECK`, `MUCKING_OUT`, `FEEDING`, `BATHING`, `ICE_BOOTS`, `GROOMING`, `WALKING`, `OTHER`).
3. **Subtle Informational Notice:**
   - Displayed prominently yet unobtrusively above the task filter toolbar in the Stable task view:
     > *"Danh sách nhiệm vụ chăm sóc chuồng được quản lý và cập nhật bởi Club Manager. Nhân viên Stable thực hiện theo danh sách hiện hành."*
   - Displays real-time catalog synchronization status, active definition count badge, and a button to view active standard procedures in read-only mode.
4. **Preservation of Daily Task Execution:**
   - Preserved 100% of the existing `care_tasks` execution workflow: task status transitions, completion notes, photo attachments, date filtering, overdue filtering, and caretaker authorization boundaries.
   - Daily task cards now render `task.description` alongside `task.title` so staff can see detailed procedural instructions.

---

## 2. Hardcoded Definitions Discovered and Handled

### 2.1 Codebase Inspection Results
During forensic source inspection:
- **Frontend Operational Task Sources:** No hardcoded arrays of routine definitions were found serving as work items. The Stable UI already queried `GET /api/care-tasks?date=YYYY-MM-DD`.
- **Database Seed vs. Frontend Constants:** Initial standard routines were seeded in `DATABASE/09_seed_reference_data.sql`. In Tasks 02 and 03, these were promoted to the dynamic `dbo.care_routine_definitions` table managed by Club Manager.
- **Frontend Task Type Mapping Gap:** `TASK_TYPE_LABELS` in `StableCarePage.jsx` previously lacked mappings for `STABLE_CLEANING`, `FACILITY_CHECK`, and `HEALTH_CHECK`, causing generated routines with these types to display raw English codes.

### 2.2 Corrective Adjustments
- Expanded `TASK_TYPE_LABELS` to support all standard catalog types with clear Vietnamese terminology:
  - `STABLE_CLEANING`: "Vệ sinh chuồng trại"
  - `FACILITY_CHECK`: "Kiểm tra cơ sở vật chất"
  - `HEALTH_CHECK`: "Kiểm tra thể trạng"
  - `MUCKING_OUT`: "Dọn phân chuồng"
  - `FEEDING`: "Cho ăn / Nước uống"
  - `BATHING`: "Tắm rửa vệ sinh"
  - `ICE_BOOTS`: "Ngâm / Chườm đá chân"
  - `GROOMING`: "Chải lông chăm sóc"
  - `WALKING`: "Dắt đi dạo nhẹ"
  - `OTHER`: "Nhiệm vụ khác"
- **Strict No-Fallback Policy:** In `loadActiveCatalog`, any API exception cleanly sets `setCatalogError(err.message)` and `setActiveCatalog([])`. No mock or fallback tasks are injected into the active catalog or daily tasks.

---

## 3. Architecture & API Integration Details

### 3.1 Distinction: Definitions vs. Daily Task Instances
| Dimension | Catalog Definitions (`care_routine_definitions`) | Daily Task Instances (`care_tasks`) |
|---|---|---|
| **Purpose** | Reusable operational templates and procedures | Actual actionable work items for a specific date and shift |
| **Governance** | Managed exclusively by **Club Manager** (`ROLE_CLUB_MANAGER`) | Executed and completed by **Stable Staff** (`ROLE_STABLE_STAFF`) |
| **Lifecycle** | Active (`is_active = 1`) or Reserve (`is_active = 0`) | Status lifecycle: `PENDING` → `COMPLETED` (or `CANCELLED`) |
| **Scope** | `FACILITY` (facility-wide) or `HORSE` (per-horse template) | `FACILITY` (shared routine, `horse_id = NULL`) or `SPECIAL` (specific horse) |
| **Persistence** | Catalog table `dbo.care_routine_definitions` | Operational table `dbo.care_tasks` |
| **Snapshot Safety** | Editing or deleting definitions **never** alters historical task snapshots | Historical records maintain full snapshot of title, description, time, and completion evidence |

### 3.2 Dynamic Daily Task Catch-Up Generation
When Stable Staff or Club Manager accesses `/stable-care/tasks` for a calendar date, Frontend calls `GET /api/care-tasks?date=YYYY-MM-DD`. In Backend:
```java
// CareTaskService.java
@Transactional
public void ensureRoutineInstancesForDate(LocalDate date) {
    if (date == null) return;
    String sql = """
        INSERT INTO dbo.care_tasks (
            id, routine_definition_id, task_category, scope, horse_id,
            title, description, task_type, scheduled_date, scheduled_time, status,
            created_at, updated_at
        )
        SELECT
            NEWID(), d.id, 'ROUTINE', d.scope, NULL,
            d.title, d.description, d.task_type, ?, d.scheduled_time, 'PENDING',
            SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
        FROM dbo.care_routine_definitions d
        WHERE d.is_active = 1
          AND NOT EXISTS (
              SELECT 1 FROM dbo.care_tasks t
              WHERE t.routine_definition_id = d.id
                AND t.scheduled_date = ?
                AND t.horse_id IS NULL
          )
        """;
    jdbc.update(sql, java.sql.Date.valueOf(date), java.sql.Date.valueOf(date));
}
```
**Key Invariants:**
1. Only definitions with `d.is_active = 1` are selected for generation.
2. Definitions in Reserve (`is_active = 0`) are never generated.
3. Newly activated definitions automatically generate work instances for subsequent date queries.
4. Moving an active definition to Reserve preserves all pre-existing daily task instances.

---

## 4. Stable UI Notice Placement & Experience

### 4.1 Notice Placement
The informational notice container (`className="stable-catalog-notice"`) is rendered immediately above the task filter toolbar in `CareTasksView`:
- **Visual Styling:** Soft neutral-green tint (`#f8faf9`), subtle border (`#e2e8e5`), 8px border radius, 10px vertical padding. Reuses existing whitespace without expanding page height.
- **Exact Vietnamese Content:**
  > `"Danh sách nhiệm vụ chăm sóc chuồng được quản lý và cập nhật bởi Club Manager. Nhân viên Stable thực hiện theo danh sách hiện hành."`
- **Catalog Count Pill:** When active definitions are loaded, renders a pill button `[Eye Icon] {count} quy trình đang áp dụng`.
- **Sync Status & Error Handling:** Shows "Đang đồng bộ danh mục…" while loading; on failure, displays "Lỗi đồng bộ danh mục" with a "Thử lại" button.

### 4.2 Read-Only Catalog Reference Modal
Clicking the count pill opens `ActiveRoutineCatalogModal`:
- Explains that the catalog is governed by Club Manager and automatically generates daily tasks.
- Displays each active procedure with its scheduled time, title, task type badge, scope badge, and detailed procedural description.
- **Strict Read-Only Enforcement:** Contains zero add, edit, delete, or transfer buttons. Only a close button is provided.

### 4.3 Manual Refresh Action
Added a manual "Làm mới" (`RotateCw`) button in the checklist header (`card-head`), enabling Stable Staff to synchronously refresh both daily tasks (`loadTasks()`) and active catalog definitions (`loadActiveCatalog()`) without reloading the entire browser page.

---

## 5. Caretaker & Authorization Preservation

### 5.1 Role Boundaries
1. **Club Manager (`ROLE_CLUB_MANAGER`):** Full governance over `care_routine_definitions` (Active and Reserve libraries, creation, updates, transfers, reordering, deletion).
2. **Stable Staff (`ROLE_STABLE_STAFF`):** Read-only access to published Active List (`GET /api/care-routine-definitions/active`). Strictly prohibited from accessing Reserve Library (HTTP 403) and all catalog mutation endpoints (HTTP 403).
3. **Head Trainer, Veterinarian, Horse Owner:** Strictly denied access to definition mutations and reserve catalog.

### 5.2 Task Completion Authorization
- Shared routine tasks (`assignedStaffId == null && taskCategory == 'ROUTINE'`): Any authenticated Stable Staff member on duty can complete the task. The system automatically records the completer's identity (`completed_by_user_id`, `completed_by_name`).
- Specific assigned tasks: Only the assigned staff member or Club Manager can mark completion.
- Photo attachment and notes workflow preserved 100%.

---

## 6. Regression Testing & Production Build Results

### 6.1 Frontend Test Suite
Created `FRONTEND/src/stableTaskCatalogIntegration.test.js` covering 10 distinct integration areas:
1. Stable UI Active catalog loading via `careRoutineApi.getActive()`.
2. Informational notice exact text verification.
3. Read-only catalog modal and absence of mutation controls.
4. Empty catalog representation without crashing.
5. API failure handling with retry option and zero hardcoded fallback injection.
6. Daily task instance rendering preserving snapshot data and displaying description.
7. Task completion workflow and photo proof regression check.
8. `TASK_TYPE_LABELS` complete coverage of standard catalog types.
9. Domain model distinction between definitions and daily task instances.
10. Preservation of historical task instances when definition moves to Reserve.

**Test Execution:**
```
cd FRONTEND; npm.cmd test
✔ 87 tests passed across all test suites (0 failures, 197ms)
```

### 6.2 Frontend Production Build
```
cd FRONTEND; npm.cmd run build
✓ 3202 modules transformed.
✓ built in 1.01s without any bundling errors or syntax issues.
```

### 6.3 Backend Test Suite
Executed targeted Maven tests covering catalog security and task lifecycle:
```
mvn -s maven-settings.xml test "-Dtest=CareTaskServiceTest,CareRoutineDefinitionServiceTest,CareRoutineDefinitionControllerSecurityTest"
Tests run: 43, Failures: 0, Errors: 0, Skipped: 0 (BUILD SUCCESS, 7.4s)
```

---

## 7. Dynamic Behavior Verification (Part E Scenarios)

| Scenario | Requirement | Verification Method | Status |
|:---:|---|---|:---:|
| **1** | Active definitions retrieved via Club-managed API | Frontend calls `careRoutineApi.getActive()` (`GET /api/care-routine-definitions/active`) | **VERIFIED** |
| **2** | Reserve definitions not displayed or generated | Stable UI never queries `/reserve`; Backend `ensureRoutineInstancesForDate` enforces `WHERE d.is_active = 1` | **VERIFIED** |
| **3** | Newly activated definitions appear in subsequent daily generation | Moving task to Active sets `is_active = 1`; subsequent date query inserts new instance into `dbo.care_tasks` | **VERIFIED** |
| **4** | Moving Active definition to Reserve preserves existing instances | `dbo.care_tasks` rows are untouched when `is_active` transitions to 0 | **VERIFIED** |
| **5** | Existing daily task completion remains functional | `completeCareTask` with notes and multipart photo uploads executes without regression | **VERIFIED** |
| **6** | Editing a definition does not rewrite historical task snapshots | `CareRoutineDefinitionService.update` updates definition only; historical `care_tasks` retain snapshot data | **VERIFIED** |
| **7** | API failure does not fall back to hardcoded definitions | Failure populates `catalogError` and retry button; `activeCatalog` is set to `[]` | **VERIFIED** |
| **8** | Informational notice appears in Stable UI | Rendered above toolbar: *"Danh sách nhiệm vụ chăm sóc chuồng được quản lý và cập nhật bởi Club Manager..."* | **VERIFIED** |
| **9** | Stable cannot mutate task definitions | UI contains no edit/add/delete/transfer controls; Backend returns 403 Forbidden | **VERIFIED** |
| **10** | Caretaker authorization remains unchanged | Shared routine tasks completed by available staff; assigned tasks enforce caretaker isolation | **VERIFIED** |

---

## 8. Runtime Activation Requirements

1. **Frontend:** Changes in `FRONTEND/src/StableCarePage.jsx` take effect automatically via Vite HMR.
2. **Backend:** No backend code modifications were required; existing APIs and security policies implemented in Task 02 fulfill all contracts.
3. **Database:** Zero schema modifications, zero migrations, zero seeds required.

---

## 9. Remaining Limitations

1. **Offline Mode:** The application relies on active network connectivity to `GET /api/care-routine-definitions/active` and `GET /api/care-tasks`. In offline scenarios, the UI displays the retry option rather than persisting definitions in browser local storage.
2. **Multi-Stable Definition Partitioning:** Routine definitions currently apply facility-wide (`FACILITY`) or to all horses (`HORSE`). Per-stall or per-barn specific routine definitions are not supported in the single-club architecture.
