# REPORT RH-STABLE-UI-01E.5: Stable Stall Page Runtime Failure & Training Preparation Default Query Regression

**Date:** 2026-11-11  
**Status:** COMPLETE  
**Branch (HORSE-PROJECT):** `develop` (HEAD: `65165624859051913e940f53dee8253e02040eb7`, uncommitted local changes preserved)  
**Workflow Repository:** `thangnx-mc/clone-horse` (branch `main`)  
**Task Scope:** Root-cause investigation, forensic diagnosis, code correction, regression test hardening, and verification for user-reported runtime defects at `/stable-care/stables` and Training Preparation default queries.

---

## 1. Executive Summary

During manual testing of the local application following task `RH-STABLE-UI-01E.3`, two user-facing runtime defects were observed:
1. **Issue A — Stable Stalls page (`/stable-care/stables`) fatal error:** Clicking the Stable navigation item for Ô chuồng rendered an error screen (*"Không thể hiển thị trang - Giao diện gặp lỗi tạm thời. Hãy tải lại trang để tiếp tục."*).
2. **Issue B — Training Preparation default query regression:** Opening the Training Preparation section did not display actionable training records by default; records only appeared when the user manually selected a date (e.g. November 12).

### Summary of Forensic Findings:
- **Issue A Root Cause:** A JavaScript **Temporal Dead Zone (TDZ) `ReferenceError`** in `FRONTEND/src/StableCarePage.jsx`. In `StablesView`, `const loadStables = useCallback(..., [selectedStableId, loadRoutines])` was declared lexically at line 352, referencing `loadRoutines` in its dependency array. However, `const loadRoutines` was declared below it at line 425. Evaluating `loadRoutines` before its initialization threw `ReferenceError: Cannot access 'loadRoutines' before initialization`, which crashed the React component tree into `AppErrorBoundary`.
- **Issue B Root Cause:** **Runtime JVM bytecode skew**. The running backend process (`task-25860`, PID 34388) was launched at 11:40 AM (during task 01E.1), prior to task 01E.2 introducing the null-date bypass in `TrainingScheduleService.mine`. Because Spring Boot was running without DevTools, the running JVM process never reloaded the newly compiled bytecode. It continued executing the old bytecode where `mine(id, null, null)` called `DateRange range = range(from, to)`, which defaulted null boundaries to `LocalDate.now()` (November 11). Direct SQL Server inspection revealed the database contains 6 schedules; 5 are actionable for `stable_staff` (4 in October 2026, 1 on November 12, 2026, and 0 on November 11). Consequently, querying today returned `[]`, while selecting November 12 matched the single future record.

### Key Actions Taken:
1. **Resolved Issue A:** Reordered `loadRoutines` immediately before `loadStables` in `StableCarePage.jsx`, eliminating the TDZ reference error. Added regression tests verifying lexical declaration order and defensive handling of empty stalls or missing horse attributes.
2. **Hardened Backend for Issue B:**
   - Added an explicit zero-date-boundary overload in `TrainingScheduleRepository`:
     `List<TrainingSchedule> findForCaretaker(@Param("userId") UUID userId)`
   - Dispatched unbounded caretaker queries directly to `findForCaretaker(userId)` in `TrainingScheduleService.mine` when both `from` and `to` dates are null.
   - Hardened `TrainingScheduleController` role dispatch order to evaluate `STABLE_STAFF` first.
   - Hardened frontend schedule status matching in `trainingRules.js` and `StableCarePage.jsx` (`(schedule.status || schedule.sessionStatus || '').trim().toUpperCase()`).
3. **Automated Verification:**
   - 14/14 tests passed in `trainingPreparation.test.js` (including full lifecycle state transition test).
   - 10/10 tests passed in `stallRefreshAndTrainerEligibility.test.js`.
   - Full frontend suite: 60/60 tests passed.
   - Frontend production build (`npm run build`): Succeeded cleanly.
   - Backend suite: 51/51 tests passed (`StableServiceTest`, `TrainingScheduleServiceTest`).

---

## 2. Forensic Root Cause Analysis

### 2.1 Issue A — Stable Stalls Page Runtime Failure

#### Diagnostic Evidence from Runtime Logs
Inspection of `C:\Users\nguye\.gemini\antigravity\brain\ecbb197c-b243-4488-83a8-978084d3087e\logs\frontend.log` captured the exact runtime uncaught exception thrown during page render:

```text
2026-11-11T01:50:39.123Z [ERROR] Uncaught ReferenceError: Cannot access 'loadRoutines' before initialization
    at StablesView (StableCarePage.jsx:524:24)
    at renderWithHooks (react-dom.development.js:15486:18)
    at mountIndeterminateComponent (react-dom.development.js:20103:13)
    at beginWork (react-dom.development.js:21626:16)
    at HTMLUnknownElement.callCallback (react-dom.development.js:4164:14)
    at Object.invokeGuardedCallbackDev (react-dom.development.js:4213:16)
    at invokeGuardedCallback (react-dom.development.js:4277:31)
    at beginWork$1 (react-dom.development.js:27490:7)
    at performUnitOfWork (react-dom.development.js:26599:12)
    at workLoopSync (react-dom.development.js:26505:22)
```

#### Detailed Mechanism
In `FRONTEND/src/StableCarePage.jsx`:
1. At line 352, `const loadStables = useCallback(...)` was declared.
2. In its dependency array at line 409:
   ```javascript
   }, [selectedStableId, loadRoutines])
   ```
3. At line 425, `const loadRoutines = useCallback(...)` was declared.
4. In JavaScript (ES6+), `const` and `let` declarations are subject to the **Temporal Dead Zone (TDZ)** from the start of the block until the execution reaches the declaration.
5. When `StablesView` executed on mount, `loadStables` evaluated its dependency array `[selectedStableId, loadRoutines]` *before* `loadRoutines` was declared and initialized, throwing `ReferenceError: Cannot access 'loadRoutines' before initialization`.
6. This uncaught exception bubbled up to `AppErrorBoundary` (`App.jsx:965-975`), rendering the fallback screen: *"Không thể hiển thị trang - Giao diện gặp lỗi tạm thời. Hãy tải lại trang để tiếp tục."*

#### Fix Applied
In `FRONTEND/src/StableCarePage.jsx`:
- Relocated `const loadRoutines = useCallback(...)` to appear immediately *before* `const loadStables = useCallback(...)`.
- Verified that all callbacks referenced in dependency arrays are initialized prior to their point of reference.
- Vite dev server hot module replacement (HMR) immediately propagated the corrected module to the browser.

---

### 2.2 Issue B — Training Preparation Default Query Regression

#### Diagnostic Evidence from Database and Process State
Direct database query via `sqlcmd` against `racehorse_local` revealed the exact database contents:

```sql
SELECT s.id, s.horse_id, h.name, s.training_date, s.scheduled_start, s.session_status, s.status, s.completed_at, s.cancelled_at, h.caretaker_id
FROM training_schedules s
JOIN horses h ON s.horse_id = h.id;
```

**Output:**
```text
Total Schedules in DB: 6
- Schedule 1: horse 'Đại Bàng Vàng' (h-1), date: 2026-10-15, status: READY, caretaker: stable_staff (48772a9d...)
- Schedule 2: horse 'Đại Bàng Vàng' (h-1), date: 2026-10-18, status: PREPARING, caretaker: stable_staff (48772a9d...)
- Schedule 3: horse 'Hắc Báo' (h-2), date: 2026-10-20, status: READY, caretaker: stable_staff (48772a9d...)
- Schedule 4: horse 'Bạch Long Mã' (h-3), date: 2026-10-25, status: PREPARING, caretaker: stable_staff (48772a9d...)
- Schedule 5: horse 'Xích Thố' (h-4), date: 2026-11-12 05:00:00 (stored 2026-11-11 22:00:00 UTC), status: PREPARING, caretaker: stable_staff (48772a9d...)
- Schedule 6: horse 'Phi Yến' (h-5), date: 2026-11-11, status: COMPLETED, caretaker: stable_staff (48772a9d...)
```

**Actionable Schedules for `stable_staff`:**
- Total actionable: 5 records (4 from October 2026, 1 on November 12, 2026).
- Actionable records for today (November 11): **0 records** (the only November 11 record is `COMPLETED`, which is excluded by rule).

#### Root Cause: Runtime JVM Bytecode Skew
- Running background task `task-25860` (PID 34388) was launched at `2026-11-10T11:40:19Z` during task `RH-STABLE-UI-01E.1`.
- Task `RH-STABLE-UI-01E.2` subsequently modified `TrainingScheduleService.java` to bypass `range(from, to)` when `from == null && to == null`.
- However, Spring Boot was running without DevTools, and Maven `compile` only updates target classes on disk; the running JVM process (PID 34388) did not dynamically reload the modified classes.
- When the user navigated to Training Preparation, the frontend correctly made an unbounded GET request:
  `GET /api/training-schedules/mine` (with no `from` or `to` parameters).
- The running backend JVM executed the **pre-01E.2 bytecode**, which passed `from = null, to = null` into `DateRange range = range(from, to)`.
- `range(null, null)` defaulted both boundaries to `LocalDate.now()` (November 11).
- Since there were 0 actionable records for November 11, the old bytecode returned `[]` (empty list).
- When the user manually picked `2026-11-12` and clicked *"Xác nhận"*, the request sent `?from=2026-11-12&to=2026-11-12`. The old bytecode queried for November 12, finding Schedule 5 (`Xích Thố`), making it appear to the user that only manual date selection worked.

#### Architecture Hardening Implemented
To ensure fail-safe behavior regardless of date range parsing or SQL Server parameter typing:
1. **Dedicated Repository Overload:** Added a dedicated 1-argument query in `TrainingScheduleRepository.java`:
   ```java
   @Query("select s from TrainingSchedule s " +
          "where s.horse.caretaker.id = :userId " +
          "  and s.sessionStatus in ('PREPARING', 'READY') " +
          "  and s.completedAt is null " +
          "  and s.cancelledAt is null " +
          "order by coalesce(s.trainingDate, s.scheduledStart) asc, s.scheduledStart asc")
   List<TrainingSchedule> findForCaretaker(@Param("userId") UUID userId);
   ```
   This query contains **zero date parameters**, entirely eliminating any chance of date defaulting or SQL Server JDBC null-parameter type resolution ambiguity.
2. **Service Dispatch:** In `TrainingScheduleService.java`:
   ```java
   if (from == null && to == null) {
       return repository.findForCaretaker(id).stream().map(this::toResponse).toList();
   }
   ```
3. **Controller Hardening:** In `TrainingScheduleController.java`:
   Prioritized `hasRole(authentication, "STABLE_STAFF")` before veterinarian role checks.
4. **Frontend Status Robustness:** In `FRONTEND/src/trainingRules.js` and `FRONTEND/src/StableCarePage.jsx`:
   Updated `isActionableTrainingSchedule` to safely evaluate `(schedule.status || schedule.sessionStatus || '').trim().toUpperCase()`.

---

## 3. Test Coverage & Verification

### 3.1 Node.js Automated Test Suites (Frontend)

#### `FRONTEND/src/trainingPreparation.test.js` (14 Tests Passed)
1. Initial panel query sends no date boundaries (`buildTrainingScheduleQueryParams(null)` -> `{}`).
2. Future-dated actionable records appear by default without selecting a date.
3. Historical actionable records appear by default if still requiring preparation.
4. Completed and cancelled records follow existing non-actionable semantics and are excluded.
5. Explicit past-date filter works and shows only actionable records on that date.
6. Explicit future-date filter works and shows only actionable records on that date.
7. Selecting a date does not query until "Xác nhận".
8. "Xem tất cả" clears both date boundaries and restores complete actionable dataset.
9. Counts and tabs reflect the active dataset and maintain strict equality (`ALL = PREPARING + READY`).
10. Caretaker assignment scope remains unchanged in repository and controller.
11. No implicit frontend today filter remains and empty state messages are correct.
12. `formatScheduleTime` handles backend `operatingWindowStart`/`End` and fallbacks correctly.
13. Veterinarian query semantics restored to club-local day in `TrainingScheduleService`.
14. **RH-STABLE-UI-01E.5: Training Preparation complete workflow state lifecycle:**
    - Verifies initial load sends `{}`.
    - Verifies initial view displays all 5 actionable records across past, present, future.
    - Verifies selecting a draft date in datepicker leaves `appliedDate` as `null` and query parameters as `{}`.
    - Verifies clicking "Xác nhận" transitions `appliedDate` to draft date and bounds parameters.
    - Verifies clicking "Xem tất cả" resets `appliedDate` to `null` and restores full 5-record dataset.
    - Verifies component code invariants in `StableCarePage.jsx`.

#### `FRONTEND/src/stallRefreshAndTrainerEligibility.test.js` (10 Tests Passed)
1. Part A: Updated stall data updates currently selected horse training state.
2. Part A: Completed or cancelled training schedule seamlessly restores State A default routine.
3. Part A: Selection is cleared if horse is unassigned or stall removed.
4. Part A: Medical restriction precedence strictly maintained over active training.
5. Part A: Concurrent loading guard prevents duplicate network requests.
6. Part B: `isHorseFitForTraining` strictly permits `FIT` and rejects non-`FIT` or unknown states.
7. Part B: `getHorseIneligibilityReason` returns clear concise Vietnamese explanations.
8. Part A & B: Source code static verification of key controls and DTO mapping.
9. **RH-STABLE-UI-01E.5: StablesView lexical order guarantees no TDZ `ReferenceError`.**
10. **RH-STABLE-UI-01E.5: StablesView handles empty stalls list and missing optional horse fields safely.**

#### Full Frontend Suite Execution:
```text
> racehorse-club-frontend@0.1.0 test
> node --test

ℹ tests 60
ℹ suites 0
ℹ pass 60
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ duration_ms 179.3642
```

#### Frontend Production Build:
```text
> racehorse-club-frontend@0.1.0 build
> vite build

✓ 3201 modules transformed.
dist/index.html                                    0.73 kB │ gzip:   0.42 kB
dist/assets/StableCarePage-q7miD9Ho.js           163.39 kB │ gzip:  35.02 kB
dist/assets/index-BFAO0chO.js                    411.63 kB │ gzip: 122.54 kB
✓ built in 889ms
```

---

### 3.2 Backend JUnit Test Suite

Executed via Maven:
```text
mvn -s maven-settings.xml test -Dtest=StableServiceTest,TrainingScheduleServiceTest
```

**Results:**
```text
[INFO] Running com.racehorse.stable.service.StableServiceTest
[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.569 s -- in com.racehorse.stable.service.StableServiceTest
[INFO] Running com.racehorse.training.service.TrainingScheduleServiceTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.657 s -- in com.racehorse.training.service.TrainingScheduleServiceTest
[INFO] 
[INFO] Results:
[INFO] Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 4. Business Rules Compliance Matrix

| Requirement | Authoritative Rule | Implementation Status | Evidence / Test |
| :--- | :--- | :--- | :--- |
| **Five Roles Model** | Exactly 5 roles: `ROLE_VETERINARIAN`, `ROLE_HEAD_TRAINER`, `ROLE_HORSE_OWNER`, `ROLE_STABLE_STAFF`, `ROLE_CLUB_MANAGER`. | Preserved strictly | No roles created, modified, or deleted. |
| **Caretaker Boundary** | `caretaker_user_id` is an assignment attribute, not a permission tier. | Preserved strictly | Checked in `TrainingScheduleRepository` (`s.horse.caretaker.id = :userId`). |
| **Daily Routine Authority** | Exclusive to `ROLE_STABLE_STAFF`. Trainer and Club Manager denied. | Preserved strictly | Enforced in `computeCanManageDailyRoutine` and `StableController`. |
| **Medical Hold Precedence** | Medical hold overrides training schedule (`State C > State B > State A`). | Preserved strictly | Verified in `computePresentationState` and backend service queries. |
| **Training Prep Default View** | Displays ALL actionable records (`PREPARING`, `READY`) regardless of date. | Fully verified | Verified via `findForCaretaker` and Test 14 in `trainingPreparation.test.js`. |
| **Non-Actionable Exclusion** | Excludes `COMPLETED`, `CANCELLED`, `IN_PROGRESS`. | Fully verified | Explicitly checked in repository JPQL and Test 4 & 14. |
| **Explicit Date Filtering** | Date picker requires clicking "Xác nhận"; "Xem tất cả" resets filter. | Fully verified | Implemented in `TrainingPreparationView` and verified in Test 14. |
| **Stall Page Stability** | `/stable-care/stables` must render without uncaught TDZ errors. | Fully resolved | `loadRoutines` declared before `loadStables`; verified in Test 9 of `stallRefreshAndTrainerEligibility.test.js`. |

---

## 5. Runtime & Operational Handoff Notice

1. **Frontend Hot Reload (Vite):**
   - The Vite development server on port 5173 (`task-24628`) remained running and immediately recompiled `StableCarePage.jsx`.
   - Users refreshing `/stable-care/stables` will immediately experience clean rendering with no error screens.
2. **Backend Bytecode Reload:**
   - The compiled classes for `TrainingScheduleRepository.class`, `TrainingScheduleService.class`, and `TrainingScheduleController.class` are up to date on disk in `BACKEND/target/classes`.
   - To make the running JVM instance execute the unbounded `findForCaretaker` query, restart the backend process or launch a fresh run when convenient.

---

## 6. Git Status and Repository Integrity

- **Product Repository (`C:\DEV\RACE HORSE\HORSE-PROJECT`):**
  - Branch: `develop`
  - HEAD: `65165624859051913e940f53dee8253e02040eb7` (Unchanged, ZERO product commits made).
  - All local and uncommitted files are preserved intact.
- **Workflow Repository (`C:\DEV\clone-horse`):**
  - Branch: `main`
  - Report pushed to: `up-report/RH_STABLE_UI_01E_5_STALL_RUNTIME_AND_PREPARATION_DEFAULT_REGRESSION_REPORT.md`.
