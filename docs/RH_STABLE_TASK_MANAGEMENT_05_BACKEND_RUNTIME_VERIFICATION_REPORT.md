# TASK RH-STABLE-TASK-MANAGEMENT-05: Backend Runtime Verification & Controlled Restart Report

**Date:** 2026-11-11  
**Author:** Antigravity  
**Task ID:** `RH-STABLE-TASK-MANAGEMENT-05`  
**Repository (Product Source):** `C:\DEV\RACE HORSE\HORSE-PROJECT` (Branch: `develop`)  
**Repository (Workflow & Mirror):** `C:\DEV\clone-horse` (Branch: `main`)  
**Deliverable Document:** `docs/RH_STABLE_TASK_MANAGEMENT_05_BACKEND_RUNTIME_VERIFICATION_REPORT.md`  
**Remote Up-Report:** `thangnx-mc/clone-horse/up-report/RH_STABLE_TASK_MANAGEMENT_05_BACKEND_RUNTIME_VERIFICATION_REPORT.md`

---

## 1. Executive Summary

| Attribute | State | Details |
|---|---|---|
| **Task Status** | **COMPLETE** | Forensic inspection, controlled restart, API verification, and deadlock investigation fully completed |
| **Backend Restart** | **SUCCESS** | Graceful shutdown of stale PID 14304; launched PID 28604 in 8.57s |
| **API Verification** | **PASS** | `GET /active` (200 OK), `GET /reserve` (200 OK for Club Manager, 403 for Stable Staff), 401 for unauth |
| **Product Source Modified** | **NO** | Zero modifications to Java or React product code |
| **Database Modified** | **NO** | Zero DDL migrations, DML writes, or manual seed changes |
| **Frontend Restart** | **NO** | Frontend Vite server (PID 24368 on port 5173) remained continuously online |
| **Report Push** | **SUCCESS** | Pushed to `thangnx-mc/clone-horse` under `up-report/` |

---

## 2. Pre-Restart Forensic Inspection

### 2.1 Process Details (Before Restart)
- **Spring Boot Java Process:**
  - **PID:** `14304`
  - **Executable:** `C:\Program Files\Java\jdk-25.0.3\bin\java.exe`
  - **Parent Process ID:** `13752` (`java.exe` Maven Classworlds Launcher)
  - **Root Parent PID:** `17628` (`cmd.exe /c ""C:\Program Files\apache-maven-3.9.15\bin\mvn.cmd" -s maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=local"`)
  - **Working Directory:** `C:\DEV\RACE HORSE\HORSE-PROJECT\BACKEND`
  - **Listening Port:** `8080` (127.0.0.1)
  - **Process Start Time:** `11/11/2026 2:22:46 AM` (UTC+7)
  - **Originating Task:** `TASK RH-LOCAL-01` (`task-28317`)

### 2.2 Proof of Project Ownership
- Working directory matches `C:\DEV\RACE HORSE\HORSE-PROJECT\BACKEND`.
- Command line matches Maven launcher pointing to `C:\DEV\RACE HORSE\HORSE-PROJECT\BACKEND`.
- Database JDBC URL connects to local SQL Server `racehorse_local`.

### 2.3 Root Cause of the 404 / 500 `NoResourceFoundException`
- `CareRoutineDefinitionController.java` was created on `11/11/2026 8:32:43 AM` during `TASK RH-STABLE-TASK-MANAGEMENT-02`.
- Running JVM PID 14304 was launched on `11/11/2026 2:22:46 AM` — more than **6 hours before** the catalog Controller existed.
- Spring MVC threw `NoResourceFoundException: No static resource api/care-routine-definitions/reserve for request '/api/care-routine-definitions/reserve'` because the endpoint was completely absent from the running ApplicationContext.

---

## 3. Controlled Backend Restart Execution

### 3.1 Shutdown Procedure
1. Cancelled daemon task `ecbb197c-b243-4488-83a8-978084d3087e/task-28317` via `manage_task(Action='kill')`.
2. Verified process termination: PIDs 14304, 13752, and 17628 exited cleanly.
3. Verified port release: TCP port 8080 became completely available.

### 3.2 Startup Execution
1. Re-verified required Cloudinary user environment variables (`CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` all present and non-empty).
2. Launched background daemon task `task-29374` using the authorized startup command:
   ```powershell
   cd "C:\DEV\RACE HORSE\HORSE-PROJECT\BACKEND"
   mvn -s maven-settings.xml spring-boot:run "-Dspring-boot.run.profiles=local" *>&1 | Tee-Object -FilePath "C:\Users\nguye\.gemini\antigravity\brain\ecbb197c-b243-4488-83a8-978084d3087e\logs\backend.log"
   ```
3. Process initialized:
   - **New PID:** `28604`
   - **Creation Date:** `11/11/2026 9:41:47 AM` (UTC+7)
   - **Startup Duration:** 8.568 seconds
   - **Tomcat Status:** Started on port 8080 with context path `/`
   - **Active Profile:** `local`

---

## 4. Post-Restart API & Authorization Verification

All verification tests were conducted strictly using read-only `GET` HTTP requests:

### 4.1 Backend Readiness & Health
- **Endpoint:** `GET http://localhost:8080/actuator/health`
- **HTTP Status:** `200 OK`
- **Response Payload:** `{"groups":["liveness","readiness"],"status":"UP"}`

### 4.2 Endpoint & Role Boundary Test Matrix
| Request | Endpoint | Authenticated Role | HTTP Status | Response / Behavior |
|:---:|---|---|:---:|---|
| **1** | `/api/care-routine-definitions/active` | Unauthenticated | **401 Unauthorized** | Correctly blocked by Spring Security |
| **2** | `/api/care-routine-definitions/reserve` | Unauthenticated | **401 Unauthorized** | Correctly blocked by Spring Security |
| **3** | `/api/care-routine-definitions/active` | `CLUB_MANAGER` (`admin@gmail.com`) | **200 OK** | Returned 5 active definitions (`ROUTINE_MORNING_STABLE_CHECK`, `ROUTINE_WATER_TROUGH_CHECK`, `ROUTINE_MORNING_MUCKING`, `ROUTINE_MIDDAY_FORAGE`, `ROUTINE_EVENING_BEDDING_CHECK`) |
| **4** | `/api/care-routine-definitions/reserve` | `CLUB_MANAGER` (`admin@gmail.com`) | **200 OK** | Returned empty array `[]` (0 reserve definitions currently in library) |
| **5** | `/api/care-routine-definitions/active` | `STABLE_STAFF` (`staff@gmail.com`) | **200 OK** | Returned 5 active definitions identically to Manager view |
| **6** | `/api/care-routine-definitions/reserve` | `STABLE_STAFF` (`staff@gmail.com`) | **403 Forbidden** | Correctly forbidden by `@PreAuthorize("hasRole('CLUB_MANAGER')")` |
| **7** | `/api/care-tasks?date=2026-11-11` | `STABLE_STAFF` (`staff@gmail.com`) | **200 OK** | Returned 5 daily routine task instances for today with status `PENDING` |

### 4.3 Resolution of `NoResourceFoundException`
- `GET /api/care-routine-definitions/reserve` returned **200 OK** for Club Manager.
- `GET /api/care-routine-definitions/active` returned **200 OK** for Club Manager and Stable Staff.
- The `NoResourceFoundException` is **100% RESOLVED**.

---

## 5. SQL Server Deadlock Investigation

### 5.1 Evidence Recorded
In `backend.log` (lines 5687–5705) prior to restart:
```
org.springframework.dao.CannotAcquireLockException: PreparedStatementCallback; SQL [INSERT INTO dbo.care_tasks (
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
]; Transaction (Process ID 60) was deadlocked on lock resources with another process and has been chosen as the deadlock victim. Rerun the transaction.
    at com.racehorse.caretask.service.CareTaskService.ensureRoutineInstancesForDate(CareTaskService.java:56)
    at com.racehorse.caretask.service.CareTaskService.listTasks(CareTaskService.java:286)
```

### 5.2 Technical Analysis
- **Trigger:** Multiple concurrent browser requests calling `GET /api/care-tasks?date=YYYY-MM-DD` simultaneously when tasks for that date have not yet been generated.
- **Lock Contention:**
  1. Transaction A and Transaction B concurrently execute `INSERT ... SELECT ... WHERE NOT EXISTS ...`.
  2. Both execute the `NOT EXISTS` subquery against `dbo.care_tasks` acquiring Shared (S) locks on key/page resources.
  3. Finding no existing rows, both attempt to insert new rows, requesting Exclusive (X) locks on the same page/key range.
  4. Each transaction waits for the other to release its S lock before granting the X lock, forming a cyclic dependency.
  5. SQL Server deadlock monitor terminates one transaction (Error 1205).
- **Try-Catch Gap:** In `CareTaskService.ensureRoutineInstancesForDate`, the method catches `DataIntegrityViolationException` (unique index collision), but SQL Server deadlocks throw Spring's `CannotAcquireLockException`, which propagates unhandled to the web tier as HTTP 500.
- **Post-Restart State:** Single-client read-only queries executed successfully (`GET /api/care-tasks?date=2026-11-11` returned 200 OK with 5 tasks). Once tasks are generated for a date, subsequent queries perform 0 inserts and do not encounter lock contention.
- **Strict Boundary Compliance:** Zero modifications to SQL or Java code were made in this task.

---

## 6. Recommendations for Follow-Up

To permanently eliminate concurrent deadlock vulnerability in `CareTaskService.ensureRoutineInstancesForDate`, a narrowly scoped source task should implement one or more of:
1. **Catch `CannotAcquireLockException`:** Add `catch (CannotAcquireLockException | DataIntegrityViolationException ex)` in `ensureRoutineInstancesForDate` with idempotent recovery (since another transaction won and generated the rows).
2. **Locking Hints:** Apply `WITH (UPDLOCK, HOLDLOCK)` on the `NOT EXISTS` check or execute generation inside a mutex/application lock.
3. **Database Indexing:** Ensure index on `dbo.care_tasks (scheduled_date, routine_definition_id)` supports granular row-level locking.
