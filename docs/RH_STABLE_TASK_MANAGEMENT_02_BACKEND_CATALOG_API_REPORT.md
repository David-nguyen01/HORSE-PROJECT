# TASK RH-STABLE-TASK-MANAGEMENT-02: Backend Task Catalog Management Report

**Date:** 2026-11-11  
**Author:** Antigravity  
**Task ID:** `RH-STABLE-TASK-MANAGEMENT-02`  
**Repository (Product Source):** `C:\DEV\RACE HORSE\HORSE-PROJECT` (Branch: `develop`)  
**Repository (Workflow & Mirror):** `C:\DEV\clone-horse` (Branch: `main`)  
**Deliverable Document:** `docs/RH_STABLE_TASK_MANAGEMENT_02_BACKEND_CATALOG_API_REPORT.md`  

---

## 1. Executive Summary

This task implements the Backend domain model, service logic, and REST APIs for Club Manager-managed Stable task definitions, adhering strictly to the approved business rules established during architecture discovery `RH-STABLE-TASK-MANAGEMENT-01`:

1. **Two Operational Collections:**
   - **Active List (`is_active = true`):** Definitions published for daily task generation. Editable, movable to Reserve, and permanently deletable. Direct creation in the Active List is **strictly prohibited**.
   - **Reserve Task Library (`is_active = false`):** Repository of inactive task definitions. **New definitions must be created directly here**. Editable, movable to Active List, and permanently deletable.
2. **Transfer Semantics:**
   - Explicit state transition modifying `is_active` between true and false.
   - Idempotent and deterministic; never creates duplicate definitions.
   - Does not delete, modify, or corrupt historical task instances.
3. **Safe Physical Deletion (Zero Schema Migrations):**
   - Permanent deletion physically deletes the catalog definition row from `dbo.care_routine_definitions`.
   - Safely unlinks referencing historical task instances in `dbo.care_tasks` by setting `routine_definition_id = NULL` in the same transaction.
   - Completely preserves historical `care_tasks` snapshot data (title, description, task_type, scheduled_date, scheduled_time, scope, status, completion notes, photo URLs, timestamps).
   - Zero database schema alterations or migrations required (`routine_definition_id` is already nullable in `DATABASE/05_health_and_care.sql`).
4. **Stable Read-Only Integration:**
   - Stable Staff can read the published Active List (`GET /api/care-routine-definitions/active`).
   - Stable Staff is strictly denied access to the Reserve Library and all mutation endpoints (403 Forbidden).
   - Daily routine task generation (`CareTaskService.ensureRoutineInstancesForDate`) enforces `WHERE d.is_active = 1`, ensuring reserve definitions are never generated as daily tasks.

---

## 2. APIs Implemented

The following REST endpoints were implemented under `/api/care-routine-definitions`:

| Method | Endpoint | Authorized Roles / Authorities | Description | Business Rules & Invariants |
|---|---|---|---|---|
| `GET` | `/api/care-routine-definitions/active` | `PERM_STABLE_CARE_VIEW` (Club Manager, Stable Staff) | Retrieve all active definitions published for daily task generation | Ordered by `sort_order ASC, scheduled_time ASC, created_at ASC`. Filter: `is_active = 1`. |
| `GET` | `/api/care-routine-definitions/reserve` | `ROLE_CLUB_MANAGER` and `PERM_STABLE_CARE_VIEW` | Retrieve all inactive definitions in the Reserve Task Library | Ordered by `sort_order ASC, scheduled_time ASC, created_at ASC`. Filter: `is_active = 0`. Forbidden to Stable Staff. |
| `GET` | `/api/care-routine-definitions/{id}` | `PERM_STABLE_CARE_VIEW` | Retrieve single definition by ID | If definition is in Reserve (`is_active = 0`), caller must be `ROLE_CLUB_MANAGER`; non-managers receive 403 Forbidden. |
| `POST` | `/api/care-routine-definitions` | `ROLE_CLUB_MANAGER` and `PERM_STABLE_CARE_EDIT` | Create new task definition in Reserve Library | Setting `isActive = true` is rejected with `BusinessException`. Auto-generates unique code `ROUTINE_<SLUG>_<UUID8>` if blank. Always persists with `is_active = 0`. |
| `PUT` | `/api/care-routine-definitions/{id}` | `ROLE_CLUB_MANAGER` and `PERM_STABLE_CARE_EDIT` | Edit existing definition (Active or Reserve) | Updates `title`, `description`, `task_type`, `scheduled_time`, `scope`, `sort_order`, `updated_at`. Preserves existing `is_active` status. Does not rewrite historical tasks. |
| `POST` | `/api/care-routine-definitions/{id}/transfer` | `ROLE_CLUB_MANAGER` and `PERM_STABLE_CARE_EDIT` | Move definition between Active and Reserve | Accepts body `TransferCareRoutineRequest(Boolean active)`. Idempotent if already in target state. |
| `POST` | `/api/care-routine-definitions/{id}/move-to-active` | `ROLE_CLUB_MANAGER` and `PERM_STABLE_CARE_EDIT` | Convenience endpoint to activate definition | Moves definition from Reserve to Active (`is_active = 1`). |
| `POST` | `/api/care-routine-definitions/{id}/move-to-reserve` | `ROLE_CLUB_MANAGER` and `PERM_STABLE_CARE_EDIT` | Convenience endpoint to reserve definition | Moves definition from Active to Reserve (`is_active = 0`). |
| `DELETE` | `/api/care-routine-definitions/{id}` | `ROLE_CLUB_MANAGER` and `PERM_STABLE_CARE_EDIT` | Permanently delete definition from catalog | Unlinks historical `care_tasks` (`routine_definition_id = NULL`) and executes physical DELETE from `dbo.care_routine_definitions`. |
| `PUT` | `/api/care-routine-definitions/reorder` | `ROLE_CLUB_MANAGER` and `PERM_STABLE_CARE_EDIT` | Reorder Active List definitions | Accepts `ReorderCareRoutinesRequest(List<UUID> orderedIds)`. Updates `sort_order` sequentially (`(index + 1) * 10`). |

---

## 3. Authorization Evidence

Authorization is implemented strictly in accordance with existing Spring Security conventions:

- **Method Security:** Enabled via `@EnableMethodSecurity`.
- **Role & Authority Model:**
  - `ROLE_CLUB_MANAGER`: Full management rights over task catalog (read active, read reserve, create in reserve, update, transfer, reorder, delete).
  - `ROLE_STABLE_STAFF`: Read-only access to published Active List (`GET /active` and `GET /{id}` for active tasks). Strictly forbidden from reserve definitions and all mutation endpoints (returns HTTP 403 Forbidden).
  - `ROLE_HEAD_TRAINER`, `ROLE_VETERINARIAN`, `ROLE_HORSE_OWNER`: Strictly denied access to reserve endpoints and definition mutations (returns HTTP 403 Forbidden).
- **Unauthenticated Requests:** Blocked by Spring Security filter chain (HTTP 401/403).

### Automated Security Test Evidence
From `CareRoutineDefinitionControllerSecurityTest`:
- `unauthenticatedRequestToActiveListReturns4xx`: Passed.
- `clubManagerCanAccessActiveAndReserveLists`: Passed.
- `clubManagerCanCreateDefinitionInReserve`: Passed.
- `clubManagerCanPerformMutations`: Passed.
- `stableStaffCanReadActiveList`: Passed.
- `stableStaffCannotAccessReserveListForbidden`: Passed (403 Forbidden).
- `stableStaffCannotMutateDefinitionsForbidden`: Passed (POST, PUT, TRANSFER, MOVE-TO-ACTIVE, MOVE-TO-RESERVE, REORDER, DELETE all return 403 Forbidden).
- `stableStaffCanReadActiveDefinitionByIdButDeniedReserveDefinition`: Passed (Active returns 200, Reserve returns 403 Forbidden).
- `horseOwnerDeniedAllEndpointsForbidden`: Passed (403 Forbidden).
- `headTrainerDeniedAllCatalogEndpointsForbidden`: Passed (403 Forbidden).

---

## 4. Active vs. Reserve Collection Lifecycle

### 4.1 Collection Membership
The database column `dbo.care_routine_definitions.is_active BIT NOT NULL` serves as the single source of truth for collection membership:
- `is_active = 1`: **Active List (On List)**. Published and eligible for daily routine task generation.
- `is_active = 0`: **Reserve Task Library**. Drafts, seasonal routines, or temporarily paused tasks.

### 4.2 Creation Boundary
```java
if (Boolean.TRUE.equals(req.isActive())) {
    throw new BusinessException("Định nghĩa nhiệm vụ mới chỉ được phép tạo trong Thư viện dự phòng (Reserve Library)");
}
```
Attempting to pass `isActive = true` during definition creation throws a `BusinessException` with HTTP 400 Bad Request. All newly created definitions enter the Reserve Library with `is_active = 0`.

### 4.3 Transfer Semantics
- Explicit transition invoked via `/transfer`, `/move-to-active`, or `/move-to-reserve`.
- Idempotent: If a definition is already Active and `/move-to-active` is called, no SQL update is triggered and the existing definition is returned safely.
- Never duplicates the catalog row.
- Never creates, deletes, or modifies historical task instances.

---

## 5. Safe Physical Deletion & Historical Task Integrity

### 5.1 The Deletion Challenge
Table `dbo.care_tasks` maintains a foreign key constraint:
```sql
CONSTRAINT fk_care_tasks_routine FOREIGN KEY (routine_definition_id) REFERENCES dbo.care_routine_definitions(id)
```
Because the foreign key does not define `ON DELETE CASCADE` or `ON DELETE SET NULL`, a naive `DELETE FROM dbo.care_routine_definitions WHERE id = ?` on a definition with existing historical daily tasks triggers a SQL Server FK violation (error 547).

### 5.2 Safe Solution Without Schema Migration
In `CareRoutineDefinitionService.delete(UUID id, UUID actorId)`:
1. Validate definition existence (`get(id)`).
2. Unlink referencing historical task instances in `dbo.care_tasks` by setting `routine_definition_id = NULL`:
   ```sql
   UPDATE dbo.care_tasks SET routine_definition_id = NULL WHERE routine_definition_id = ?
   ```
3. Physically delete the definition from the catalog:
   ```sql
   DELETE FROM dbo.care_routine_definitions WHERE id = ?
   ```

### 5.3 Integrity Verification Evidence
- **Task Row Preservation:** `dbo.care_tasks` rows are never deleted.
- **Snapshot Immutability:** All task execution data (`task_category`, `scope`, `title`, `description`, `task_type`, `scheduled_date`, `scheduled_time`, `assigned_staff_id`, `status`, `completed_at`, `completed_by_user_id`, `completed_by_name`, `completion_notes`, `photo_url`, `photo_bytes`) remain 100% intact.
- **Query Compatibility:** `CareTaskService.baseSelect()` performs `LEFT JOIN dbo.horses h ON h.id = t.horse_id` and does not join `care_routine_definitions`. Daily task lists, filters, completion flows, and photo attachments continue to function without degradation when `routine_definition_id` is `NULL`.
- **Filtered Unique Index Safety:** The index `uq_care_tasks_facility_routine_instance` specifies `WHERE routine_definition_id IS NOT NULL AND horse_id IS NULL`. Setting `routine_definition_id = NULL` removes the row from the filtered index, preventing index collisions or constraint violations.

---

## 6. Daily Task Generation Compatibility

In `CareTaskService.ensureRoutineInstancesForDate(LocalDate date)`:
```sql
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
```
- **Active Definitions:** Automatically generated as `PENDING` routine tasks for the specified date.
- **Reserve Definitions:** Strictly excluded by `WHERE d.is_active = 1`.
- **Transferred Definitions:**
  - When moved from Reserve to Active, subsequent calls to `ensureRoutineInstancesForDate` will instantiate the routine for that date without affecting past dates.
  - When moved from Active to Reserve, subsequent calls will cease instantiating the routine, leaving existing instances untouched.

---

## 7. Initial Content Plan (Part D)

### 7.1 Existing Active List (Preserved)
The 5 standard routine definitions seeded in `DATABASE/09_seed_reference_data.sql` are preserved:
1. `ROUTINE_MORNING_STABLE_CHECK`: Kiểm tra tổng quát chuồng sáng (06:00).
2. `ROUTINE_WATER_TROUGH_CHECK`: Kiểm tra & làm sạch máng nước tự động (06:30).
3. `ROUTINE_MORNING_MUCKING`: Dọn vệ sinh & thay chất độn chuồng (07:00).
4. `ROUTINE_MIDDAY_FORAGE`: Bổ sung cỏ khô & kiểm tra giữa ngày (11:30).
5. `ROUTINE_EVENING_BEDDING_CHECK`: Bổ sung rơm lót & kiểm tra an toàn chuồng tối (17:30).

### 7.2 Proposed Reserve Task Library Definitions
Per business specification, 4 reserve definitions are designed for future activation:
1. **Kiểm tra và vệ sinh dụng cụ chăm sóc ngựa** (`ROUTINE_TOOL_CLEANING_CHECK`)
   - Task Type: `FACILITY_CHECK` / `CLEANING`
   - Scope: `FACILITY`
   - Scheduled Time: `08:00:00`
   - Active: `false` (Reserve)
2. **Kiểm tra tình trạng cửa, khóa và vách ngăn chuồng** (`ROUTINE_BARN_DOOR_LOCK_INSPECTION`)
   - Task Type: `FACILITY_CHECK`
   - Scope: `FACILITY`
   - Scheduled Time: `09:30:00`
   - Active: `false` (Reserve)
3. **Kiểm tra khu vực lưu trữ thức ăn và vệ sinh kho** (`ROUTINE_FEED_STORAGE_INSPECTION`)
   - Task Type: `INVENTORY_CHECK` / `FACILITY_CHECK`
   - Scope: `FACILITY`
   - Scheduled Time: `14:00:00`
   - Active: `false` (Reserve)
4. **Kiểm tra và vệ sinh khu vực thoát nước chuồng trại** (`ROUTINE_DRAINAGE_CLEANING_CHECK`)
   - Task Type: `FACILITY_CHECK` / `CLEANING`
   - Scope: `FACILITY`
   - Scheduled Time: `16:00:00`
   - Active: `false` (Reserve)

*Note: Per strict boundaries, no database seed script was executed against local or live databases during this task. The migration SQL below is prepared for future deployment.*

```sql
-- Migration Plan for Proposed Reserve Definitions (Pending User Approval):
INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order)
VALUES 
(NEWID(), 'ROUTINE_TOOL_CLEANING_CHECK', N'Kiểm tra và vệ sinh dụng cụ chăm sóc ngựa', N'Vệ sinh bàn chải, lược chải lông, móc móng và khử trùng dụng cụ chăm sóc hàng ngày.', 'FACILITY_CHECK', '08:00:00', 'FACILITY', 0, 10),
(NEWID(), 'ROUTINE_BARN_DOOR_LOCK_INSPECTION', N'Kiểm tra tình trạng cửa, khóa và vách ngăn chuồng', N'Kiểm tra then cài, bản lề cửa chuồng, bu-lông vách ngăn đảm bảo an toàn cho ngựa.', 'FACILITY_CHECK', '09:30:00', 'FACILITY', 0, 20),
(NEWID(), 'ROUTINE_FEED_STORAGE_INSPECTION', N'Kiểm tra khu vực lưu trữ thức ăn và vệ sinh kho', N'Kiểm tra độ ẩm kho cám, bao bì thức ăn viên, pallet cỏ khô và dọn sạch sàn kho thức ăn.', 'FACILITY_CHECK', '14:00:00', 'FACILITY', 0, 30),
(NEWID(), 'ROUTINE_DRAINAGE_CLEANING_CHECK', N'Kiểm tra và vệ sinh khu vực thoát nước chuồng trại', N'Khơi thông rãnh thoát nước, loại bỏ rơm rác ứ đọng và vệ sinh hố ga khu vực chuồng.', 'FACILITY_CHECK', '16:00:00', 'FACILITY', 0, 40);
```

---

## 8. Test Execution Results

All unit and security tests were executed using Maven:
`mvn -s maven-settings.xml test "-Dtest=CareRoutineDefinitionServiceTest,CareRoutineDefinitionControllerSecurityTest,CareTaskServiceTest,CareTaskControllerSecurityTest"`

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.racehorse.caretask.controller.CareRoutineDefinitionControllerSecurityTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.401 s -- in com.racehorse.caretask.controller.CareRoutineDefinitionControllerSecurityTest
[INFO] Running com.racehorse.caretask.controller.CareTaskControllerSecurityTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.324 s -- in com.racehorse.caretask.controller.CareTaskControllerSecurityTest
[INFO] Running com.racehorse.caretask.service.CareRoutineDefinitionServiceTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.132 s -- in com.racehorse.caretask.service.CareRoutineDefinitionServiceTest
[INFO] Running com.racehorse.caretask.service.CareTaskServiceTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.200 s -- in com.racehorse.caretask.service.CareTaskServiceTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  20.219 s
[INFO] Finished at: 2026-11-11T08:39:01+07:00
[INFO] ------------------------------------------------------------------------
```

---

## 9. Schema & Migration Impact Analysis

- **Database Alterations:** ZERO schema changes or migrations were executed or required.
- **Foreign Key Status:** `dbo.care_tasks.routine_definition_id` is natively `NULLABLE`. Setting it to `NULL` prior to definition deletion satisfies all relational integrity requirements.
- **Index Impact:** Filtered unique index `uq_care_tasks_facility_routine_instance` excludes `NULL` routine definitions, guaranteeing zero constraint collisions upon deletion.
- **Historical Reporting Impact:** Zero impact. Task completion audits and historical logs continue to read all snapshot fields directly from `dbo.care_tasks`.

---

## 10. Summary of Files Created

### Source Code
- `BACKEND/src/main/java/com/racehorse/caretask/dto/CareRoutineDefinitionResponse.java`
- `BACKEND/src/main/java/com/racehorse/caretask/dto/CreateCareRoutineDefinitionRequest.java`
- `BACKEND/src/main/java/com/racehorse/caretask/dto/UpdateCareRoutineDefinitionRequest.java`
- `BACKEND/src/main/java/com/racehorse/caretask/dto/TransferCareRoutineRequest.java`
- `BACKEND/src/main/java/com/racehorse/caretask/dto/ReorderCareRoutinesRequest.java`
- `BACKEND/src/main/java/com/racehorse/caretask/service/CareRoutineDefinitionService.java`
- `BACKEND/src/main/java/com/racehorse/caretask/controller/CareRoutineDefinitionController.java`

### Test Code
- `BACKEND/src/test/java/com/racehorse/caretask/service/CareRoutineDefinitionServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/caretask/controller/CareRoutineDefinitionControllerSecurityTest.java`

### Documentation Deliverables
- `docs/RH_STABLE_TASK_MANAGEMENT_02_BACKEND_CATALOG_API_REPORT.md` (Product repo)
- `up-report/RH_STABLE_TASK_MANAGEMENT_02_BACKEND_CATALOG_API_REPORT.md` (Workflow repo `thangnx-mc/clone-horse`)
