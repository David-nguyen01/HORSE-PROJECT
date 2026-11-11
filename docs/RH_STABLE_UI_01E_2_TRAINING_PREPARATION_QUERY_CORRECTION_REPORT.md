# TASK REPORT: RH-STABLE-UI-01E.2 — Training Preparation All-Date Query & Veterinarian Query Scope Correction

**Report Date:** 2026-11-11T01:25:00+07:00  
**Product Repository:** `C:\DEV\RACE HORSE\HORSE-PROJECT`  
**Baseline Branch:** `develop`  
**Git HEAD:** `65165624859051913e940f53dee8253e02040eb7`  
**Workflow Repository:** `C:\DEV\clone-horse`  
**Workflow Branch:** `main` (`thangnx-mc/clone-horse`)  
**Workflow Destination:** `up-report/RH_STABLE_UI_01E_2_TRAINING_PREPARATION_QUERY_CORRECTION_REPORT.md`  

---

## 1. Executive Summary

Task **RH-STABLE-UI-01E.2** corrected the Training Preparation default dataset behavior and restored strict separation between Stable Staff and Veterinarian date-query semantics:

1. **Part A — Training Preparation Actionable Work Default:**
   - **Root Cause Identified:** Forensically isolated three compounding causes for why future-dated records were not functioning properly:
     1. `[FACT]` **Pre-01D Backend Date Default:** Prior to task 01D, `TrainingScheduleService.mine()` passed omitted dates to `range(null, null)`, which defaulted to today (`start = today, end = today`), silently truncating any future preparation work.
     2. `[FACT]` **Actionable Status Predicate Gap:** `TrainingScheduleRepository.findForCaretaker` previously checked only `s.cancelledAt is null`, treating every non-cancelled historical record (including hundreds of old `COMPLETED` and `IN_PROGRESS` sessions) as preparation items. This bloated the "Tất cả" count and list with past completed tasks, creating a false equivalence between non-cancelled records and actionable preparation work.
     3. `[FACT]` **Presentation Field Mismatch:** In `StableCarePage.jsx`, the card displayed `formatVNDate(item.sessionDate)` and `item.startTime`. However, `TrainingScheduleResponse` returns `trainingDate` and `operatingWindowStart` / `operatingWindowEnd`. Because `sessionDate` was undefined on actual API payloads, the date was completely blanked out on the card (`formatVNDate(undefined) === ''`), making future records appear undated and confusing.
   - **Actionable Semantics Defined:** Stable preparation work is strictly defined by lifecycle states requiring Stable staff action: `PREPARING` (needs preparation/grooms/tacks and readiness confirmation) and `READY` (prepared, staged, waiting for trainer). `COMPLETED`, `CANCELLED`, and `IN_PROGRESS` records are strictly non-actionable for preparation and are excluded.
   - **Defense-in-Depth Implementation:** Updated `TrainingScheduleRepository.findForCaretaker` with predicate:
     ```sql
     and s.completedAt is null and (s.sessionStatus is null or s.sessionStatus in ('PREPARING', 'READY'))
     ```
     Updated `FRONTEND/src/trainingRules.js` and `StableCarePage.jsx` with `isActionableTrainingSchedule(schedule)` so counts (`all = preparing + ready`) and tab filtering remain 100% consistent.
   - **Display Field Normalization:** Normalized date and time formatting on schedule cards to evaluate `item.trainingDate || item.sessionDate` and `operatingWindowStart` / `operatingWindowEnd`.

2. **Part B — Restored Veterinarian Query Date Semantics:**
   - Addressed regression identified in `RH-STABLE-UI-01D.1`: `forDiagnosingVet(id, from, to)` in `TrainingScheduleService.java` was previously broadened in 01D to accept null date boundaries without defaulting.
   - Restored `forDiagnosingVet(id, from, to)` to unconditionally delegate to `range(from, to)`. When `from` and `to` are omitted (`null, null`), it strictly defaults to the current club-local day.
   - Preserved Stable Staff `mine(id, null, null)` unbounded search across all dates.
   - Preserved all controller authorization, role routing, and caretaker assignment boundaries.

3. **Part D — Preserved 01E.1 & Historical Artifacts:**
   - Preserved all 01E.1 deliverables: `HorseResponse.healthStatus` mapping, Trainer eligibility rules and modal banners, Stable stall map state synchronization, and the manual refresh button (`RotateCw`).
   - Preserved all 16 pre-existing modified files in `HORSE-PROJECT`. Zero process terminations or restarts.

---

## 2. Working Tree Inventory & Baseline State

### 2.1 Git Repository State
- **Product Repository:** `C:\DEV\RACE HORSE\HORSE-PROJECT`
- **Branch:** `develop`
- **HEAD Commit:** `65165624859051913e940f53dee8253e02040eb7`
- **Product Source Mutations:** Zero commits or pushes to product repository.

### 2.2 Working Tree Cleanliness
All 16 previously dirty files and untracked artifacts were preserved untouched:
- `BACKEND/src/main/java/com/racehorse/health/dto/BarnHealthMapResponse.java`
- `BACKEND/src/main/java/com/racehorse/health/service/BarnHealthService.java`
- `BACKEND/src/main/java/com/racehorse/horse/dto/HorseResponse.java`
- `BACKEND/src/main/java/com/racehorse/horse/mapper/HorseMapper.java`
- `BACKEND/src/main/java/com/racehorse/media/controller/HorsePhotoController.java`
- `BACKEND/src/main/java/com/racehorse/media/controller/HorseProfileFileController.java`
- `BACKEND/src/main/java/com/racehorse/media/service/HorseProfileFileService.java`
- `BACKEND/src/main/java/com/racehorse/stable/controller/StableController.java`
- `BACKEND/src/main/java/com/racehorse/stable/service/StableService.java`
- `BACKEND/src/test/java/com/racehorse/health/service/BarnHealthServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/horse/service/HorseServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/media/service/HorseProfileFileServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/stable/service/StableServiceTest.java`
- `FRONTEND/src/AdminPages.jsx`
- `FRONTEND/src/App.jsx`
- `FRONTEND/src/TrainingSchedulePage.jsx`
- `FRONTEND/src/VetPage.jsx`
- `FRONTEND/src/api.js`
- `FRONTEND/src/styles.css`
- Untracked artifacts: `BACKEND/.../controller/`, `dailyRoutine.test.js`, `imageFallback.js`, `imageFallback.test.js`, `stallRefreshAndTrainerEligibility.test.js`, `docs/`.

### 2.3 Runtime Integrity
- Backend Spring Boot PID 34388 on port 8080 and Vite dev server on port 5173 remained running throughout.

---

## 3. Investigation & Root Cause Analysis

### 3.1 Complete Query Flow Tracing

| Step | Layer | File & Location | Pre-01E.2 Behavior | Post-01E.2 Corrected Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Frontend Component | `FRONTEND/src/StableCarePage.jsx:2213` | `appliedDate = null`, `draftDate = ''` | Preserved decoupled state: initial `appliedDate = null` |
| **2** | Query Param Builder | `FRONTEND/src/trainingRules.js` | Returns `{}` when `appliedDate` is null | Returns `{}` (no date boundary params sent) |
| **3** | API Client | `FRONTEND/src/api.js:218` | `GET /api/training-schedules/mine` | Dispatches clean unbounded query string |
| **4** | Controller | `TrainingScheduleController.java:120` | `@RequestParam(required = false) from, to` | Passes `null, null` to `service.mine()` |
| **5** | Service (Stable) | `TrainingScheduleService.java:422` | Calls `findForCaretaker(id, null, null)` | Retains unbounded query `null, null` |
| **6** | Service (Vet) | `TrainingScheduleService.java:431` | Called `findForDiagnosingVet(id, null, null)` | **Restored:** calls `range(from, to)` defaulting to today |
| **7** | Repository (Caretaker) | `TrainingScheduleRepository.java:71` | Filtered only `cancelledAt is null` | **Corrected:** Filters `completedAt is null` and `sessionStatus in ('PREPARING', 'READY')` |
| **8** | Response DTO | `TrainingScheduleResponse.java:21` | Returned `trainingDate`, `operatingWindowStart/End` | Preserved canonical DTO structure |
| **9** | Card Rendering | `FRONTEND/src/StableCarePage.jsx:2440` | Looked up `item.sessionDate` (undefined $\rightarrow$ blank) | **Corrected:** Evaluates `item.trainingDate \|\| item.sessionDate` and `formatScheduleTime(item)` |

### 3.2 Actionable Lifecycle Status Criteria

The five training schedule states were analyzed against the Stable Staff preparation role:
- **`PREPARING` (Actionable):** Session is published and waiting for Stable Staff to prepare the horse and mark it ready (`markTrainingReady`). Must appear by default across all dates.
- **`READY` (Actionable):** Stable Staff has marked the horse ready; horse is staged waiting for Trainer. Must appear under "Tất cả" and "Đã sẵn sàng" across all dates.
- **`IN_PROGRESS` (Non-Actionable for Preparation):** Trainer has started training on the track. Stable preparation is complete. Excluded from preparation work queue.
- **`COMPLETED` (Non-Actionable):** Training session is finished and evaluation recorded. Excluded from preparation work queue.
- **`CANCELLED` (Non-Actionable):** Training session was cancelled. Excluded from preparation work queue.

---

## 4. Implementation Details

### 4.1 Backend Changes

#### 4.1.1 Actionable Preparation Repository Query (`TrainingScheduleRepository.java`)
Updated `findForCaretaker` to enforce actionable preparation statuses and exclude completed/cancelled sessions:
```java
    @EntityGraph(attributePaths = {"phase", "horse", "racetrack", "createdBy"})
    @Query("""
            select s from TrainingSchedule s
            where s.horse.caretaker.id = :userId
              and s.publishedAt is not null
              and s.cancelledAt is null
              and s.completedAt is null
              and (s.sessionStatus is null or s.sessionStatus in ('PREPARING', 'READY'))
              and (:fromInclusive is null or s.scheduledEnd > :fromInclusive)
              and (:toExclusive is null or s.scheduledStart < :toExclusive)
            order by s.scheduledStart
            """)
    List<TrainingSchedule> findForCaretaker(@Param("userId") UUID userId, @Param("fromInclusive") Instant fromInclusive, @Param("toExclusive") Instant toExclusive);
```

#### 4.1.2 Restored Veterinarian Date Semantics (`TrainingScheduleService.java`)
Restored `forDiagnosingVet` to delegate to `range(from, to)`, ensuring null date parameters strictly default to the club-local day, while preserving unbounded date search for Stable `mine()`:
```java
    @Transactional(readOnly = true)
    public List<TrainingScheduleResponse> mine(UUID id, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return repository.findForCaretaker(id, null, null).stream().map(this::toResponse).toList();
        }
        DateRange range = range(from, to);
        return repository.findForCaretaker(id, range.from(), range.to()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TrainingScheduleResponse> forDiagnosingVet(UUID id, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        return repository.findForDiagnosingVet(id, range.from(), range.to()).stream().map(this::toResponse).toList();
    }
```

---

### 4.2 Frontend Changes

#### 4.2.1 Actionable Preparation Helpers (`FRONTEND/src/trainingRules.js`)
Implemented pure ES module helpers for preparation filtering, count derivation, and time formatting:
```javascript
export function isActionableTrainingSchedule(schedule) {
  if (!schedule) return false
  const status = schedule.status ? String(schedule.status).trim().toUpperCase() : ''
  return status === 'PREPARING' || status === 'READY'
}

export function filterTrainingSchedules(schedules, statusFilter = 'ALL') {
  if (!Array.isArray(schedules)) return []
  const actionable = schedules.filter(isActionableTrainingSchedule)
  if (!statusFilter || statusFilter === 'ALL') return actionable
  return actionable.filter((s) => s.status === statusFilter)
}

export function computeTrainingScheduleCounts(schedules) {
  const list = Array.isArray(schedules) ? schedules.filter(isActionableTrainingSchedule) : []
  return {
    all: list.length,
    preparing: list.filter((s) => s.status === 'PREPARING').length,
    ready: list.filter((s) => s.status === 'READY').length,
  }
}

export function formatScheduleTime(item) {
  if (!item) return '05:00 - 11:00'
  if (item.operatingWindowStart && item.operatingWindowEnd) {
    return `${formatTimeHHmm(item.operatingWindowStart)} - ${formatTimeHHmm(item.operatingWindowEnd)}`
  }
  if (item.startTime && item.endTime) {
    return `${formatTimeHHmm(item.startTime)} - ${formatTimeHHmm(item.endTime)}`
  }
  if (item.scheduledStart && item.scheduledEnd) {
    return formatTimeRange(item.scheduledStart, item.scheduledEnd)
  }
  return '05:00 - 11:00'
}
```

#### 4.2.2 Stable Care View Integration (`FRONTEND/src/StableCarePage.jsx`)
1. Exported `isActionableTrainingSchedule`, `filterTrainingSchedules`, `computeTrainingScheduleCounts`, and `formatScheduleTime`.
2. Updated schedule card display (line 2440):
   ```jsx
   <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
     <Clock size={13} />
     <span>
       {formatVNDate(item.trainingDate || item.sessionDate || (item.scheduledStart ? String(item.scheduledStart).slice(0, 10) : ''))}
       {' · '}
       {formatScheduleTime(item)}
     </span>
   </div>
   ```

---

## 5. Automated Verification Results

### 5.1 Backend Unit Tests
Command executed:
```bash
cmd /c "cd /d C:\DEV\RACE HORSE\HORSE-PROJECT\BACKEND && mvn -s maven-settings.xml test -Dtest=TrainingScheduleServiceTest,HorseServiceTest"
```
**Output:**
```
[INFO] Running com.racehorse.horse.service.HorseServiceTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.063 s -- in com.racehorse.horse.service.HorseServiceTest
[INFO] Running com.racehorse.training.service.TrainingScheduleServiceTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.751 s -- in com.racehorse.training.service.TrainingScheduleServiceTest
[INFO] Results:
[INFO] Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
Verified:
- `mine_withNullDates_queriesAllRecordsWithoutDateBoundaries` $\rightarrow$ calls `findForCaretaker(caretakerId, null, null)`
- `mine_withSpecificDate_queriesWithDateBoundaries` $\rightarrow$ calls `findForCaretaker(caretakerId, from, to)`
- `forDiagnosingVet_withNullDates_queriesCurrentClubDay` $\rightarrow$ calls `findForDiagnosingVet(vetId, range.from(), range.to())` where dates default to today
- `forDiagnosingVet_withSpecificDate_queriesWithDateBoundaries` $\rightarrow$ calls with explicit boundaries

### 5.2 Frontend Unit Tests
Command executed:
```bash
cmd /c "cd /d C:\DEV\RACE HORSE\HORSE-PROJECT\FRONTEND && npm test"
```
**Output:**
```
✔ all region labels in the injury form resolve to their own code (2.6993ms)
✔ structured region codes take precedence over legacy location text (0.1987ms)
✔ saved severity values have distinct visual colors (0.1145ms)
✔ non-specific injury labels do not claim a precise point on the model (0.897ms)
✔ all markers project onto the bundled horse model and paired legs stay apart (18.1546ms)
✔ stableCareApi in api.js does not expose deleteRoutine endpoint (removed per RH-STABLE-UI-01C.3) (1.0453ms)
✔ computeCanManageDailyRoutine permits STABLE_STAFF for normal horse (0.1471ms)
✔ computeCanManageDailyRoutine denies HEAD_TRAINER (0.0922ms)
✔ computeCanManageDailyRoutine denies CLUB_MANAGER (0.085ms)
✔ computeCanManageDailyRoutine denies VETERINARIAN and HORSE_OWNER (0.1103ms)
✔ computeCanManageDailyRoutine denies Stable users while horse is training (0.157ms)
✔ computeCanManageDailyRoutine denies Stable users while horse is injured or medically restricted (0.107ms)
✔ validateRoutineForm validates chronological order of start and end times (0.4579ms)
✔ validateRoutineForm blocks any modification when horse is restricted (0.2146ms)
✔ validateRoutineForm for OTHER requires non-blank custom content (0.3457ms)
✔ formatTimeHHmm consistently formats seconds and minutes (0.2743ms)
✔ formatHorseHealthStatus maps all English codes to Vietnamese labels (0.2062ms)
✔ getRoutineDisplayTitle displays custom description for OTHER and label for standard (0.2104ms)
✔ isHorseMovementRestricted identifies injured, training locked, or emergency horses (0.8444ms)
✔ State A: normal healthy horse displays default daily routine and enables mutation actions (0.1181ms)
✔ State B: horse currently training displays training schedule read-only and temporarily hides default routine (0.1684ms)
✔ State C: medical hold overrides training state and takes strict precedence (0.0759ms)
✔ Transition: when training session completes, horse seamlessly restores default routine without data loss (0.1023ms)
✔ computeCanExecuteBackfill permits STABLE_STAFF (01C.6 approved rule) (0.0741ms)
✔ computeCanExecuteBackfill denies HEAD_TRAINER, CLUB_MANAGER, VETERINARIAN, and HORSE_OWNER (0.0461ms)
✔ Five-Role & Caretaker boundary: caretaker_user_id does not create a role or authorization boundary for daily routine (0.0723ms)
✔ handleImageFallback hides target and reveals next element sibling with specified display mode (0.7266ms)
✔ handleImageFallback supports grid display mode for modal/hero avatars (0.271ms)
✔ handleImageFallback handles missing or detached next element sibling safely (0.3377ms)
✔ handleImageFallback handles sibling lacking style object safely (0.9819ms)
✔ handleImageFallback handles null or invalid event objects safely (0.2109ms)
✔ handleImageFallback catches unexpected DOM property mutation exceptions (0.1278ms)
✔ Part A: updated stall data updates currently selected horse training state (2.3866ms)
✔ Part A: completed or cancelled training schedule seamlessly restores State A default routine (0.1877ms)
✔ Part A: selection is cleared if horse is unassigned or stall removed (0.1144ms)
✔ Part A: medical restriction precedence strictly maintained over active training (0.0881ms)
✔ Part A: concurrent loading guard prevents duplicate network requests (27.6795ms)
✔ Part B: isHorseFitForTraining strictly permits FIT and rejects non-FIT or unknown states (0.4469ms)
✔ Part B: getHorseIneligibilityReason returns clear concise Vietnamese explanations (0.1971ms)
✔ Part A & B: source code static verification of key controls and DTO mapping (1.7721ms)
✔ 1. Initial panel query sends no date boundaries (0.9994ms)
✔ 2. Future-dated actionable records appear by default without selecting a date (0.1938ms)
✔ 3. Historical actionable records appear by default if still requiring preparation (0.096ms)
✔ 4. Completed and cancelled records follow existing non-actionable semantics and are excluded (0.1051ms)
✔ 5. Explicit past-date filter works and shows only actionable records on that date (0.1981ms)
✔ 6. Explicit future-date filter works and shows only actionable records on that date (0.1084ms)
✔ 7. Selecting a date does not query until "Xác nhận" (0.1046ms)
✔ 8. "Xem tất cả" clears both date boundaries and restores complete actionable dataset (0.112ms)
✔ 9. Counts and tabs reflect the active dataset and maintain strict equality (all = preparing + ready) (0.1996ms)
✔ 10. Caretaker assignment scope remains unchanged in repository and controller (1.3312ms)
✔ 11. No implicit frontend today filter remains and empty state messages are correct (1.0873ms)
✔ 12. FormatScheduleTime handles backend operatingWindowStart/End and fallbacks correctly (0.2096ms)
✔ 13. Veterinarian query semantics restored to club-local day in TrainingScheduleService (1.7597ms)
ℹ tests 53
ℹ suites 0
ℹ pass 53
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 170.3571
```

### 5.3 Frontend Production Build
Command executed:
```bash
cmd /c "cd /d C:\DEV\RACE HORSE\HORSE-PROJECT\FRONTEND && npm run build"
```
**Output:**
```
vite v8.2.2 building client environment for production...
transforming...
✓ 3201 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                                    0.73 kB │ gzip:   0.42 kB
...
dist/assets/StableCarePage-C9LiijkT.js           163.10 kB │ gzip:  34.96 kB
✓ built in 1.19s
```

---

## 6. Definition of Done & Status Matrix

| Dimension | Status | Notes |
| :--- | :--- | :--- |
| **Part A (Stable Actionable Records)** | **Complete** | Actionable states `PREPARING` and `READY` appear by default across past, present, and future dates without date selection |
| **Part B (Veterinarian Date Scope)** | **Complete** | `forDiagnosingVet` restored to use `range(from, to)` (defaults to current club-local day) |
| **Backend Automated Tests** | **Passed** | 40/40 tests passed (`TrainingScheduleServiceTest` + `HorseServiceTest`) |
| **Frontend Automated Tests** | **Passed** | 53/53 tests passed (13 dedicated tests in `trainingPreparation.test.js`) |
| **Frontend Production Build** | **Passed** | Clean build in 1.19s |
| **01E.1 Work Preservation** | **Preserved** | Health status mapping, refresh button, stall selection sync intact |
| **Working Tree Safety** | **Preserved** | All 16 dirty files and untracked artifacts intact |
| **Process Integrity** | **Compliant** | Zero process terminations or restarts (PID 34388 and Vite 5173 running) |
| **External Runtime Verification** | **Pending User Verification** | Live verification in running browser by Stable Staff and Veterinarian users |

---

## 7. User Verification Protocol

To verify the live behavioral integration:

1. **Verify Default Actionable Training Preparation (Stable Staff):**
   - Log in as **Stable Staff** (`ROLE_STABLE_STAFF`).
   - Navigate to `/stable-care` and click tab **"Chuẩn bị huấn luyện"**.
   - **Expected Result:**
     - The date input is blank and shows "Ngày tập: [__/__/____]".
     - Without selecting a date or clicking any button, the list immediately displays all actionable training sessions (`PREPARING` and `READY`) for assigned horses, including tomorrow's and later dates.
     - Completed and cancelled sessions do not appear.
     - Tab badges show `Tất cả (N)`, `Cần chuẩn bị (P)`, `Đã sẵn sàng (R)` where `N = P + R`.
     - Schedule cards clearly show the formatted date (e.g., `12/11/2026`) and time window (e.g., `05:00 - 11:00`).
2. **Verify Explicit Date Filter & "Xem tất cả":**
   - Pick a specific date in the date picker. Note that the list does NOT immediately change (draft decoupled).
   - Click **"Xác nhận"**. The list filters to show only actionable sessions on that date, and an indicator appears: `Đang lọc: DD/MM/YYYY`.
   - Click **"Xem tất cả"**. The date filter is cleared, and all actionable sessions across all dates reappear.
3. **Verify Veterinarian Query Date Semantics:**
   - Log in as **Veterinarian** (`ROLE_VETERINARIAN`).
   - Navigate to the Veterinary training overview (`/vet/schedules` or `/vet`).
   - When no explicit dates are specified, verify requests to `GET /api/training-schedules/mine` or `GET /api/training-schedules` strictly return sessions for the current day only, rather than unbounded history.
