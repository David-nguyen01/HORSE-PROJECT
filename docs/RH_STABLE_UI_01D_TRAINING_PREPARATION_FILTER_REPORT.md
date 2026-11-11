# TASK REPORT: RH-STABLE-UI-01D — Training Preparation Panel: Default All Records & Explicit Date Filtering

**Report Date:** 2026-11-10T23:40:00+07:00  
**Product Repository:** `C:\DEV\RACE HORSE\HORSE-PROJECT`  
**Baseline Branch:** `develop`  
**Git HEAD:** `65165624859051913e940f53dee8253e02040eb7`  
**Workflow Repository:** `C:\DEV\clone-horse`  
**Workflow Branch:** `main` (`thangnx-mc/clone-horse`)  
**Workflow Destination:** `up-report/RH_STABLE_UI_01D_TRAINING_PREPARATION_FILTER_REPORT.md`  

---

## 1. Executive Summary

Task **RH-STABLE-UI-01D** implemented the required behaviors for the Stable Staff Training Preparation panel (`TrainingPreparationView` in `/stable-care/stables`):
1. **Default Mode (All Available Records):** Opening the panel now displays all available published training preparation records for the staff member's assigned horses by default, instead of silently restricting results to today's date.
2. **Decoupled Date State & Explicit Confirmation:** Date selection in the date picker updates only a separate draft state (`draftDate`). Records remain unchanged until the user explicitly clicks the **"Xác nhận"** button.
3. **Filter Reset Action:** Added a clear/reset action (**"Xem tất cả"**) that clears the applied filter and returns the panel to all available records.
4. **Dynamic Status Tabs & Counts:** Status tabs ("Tất cả", "Cần chuẩn bị", "Đã sẵn sàng") and their badge counts dynamically derive from the currently active dataset (either all available records or the confirmed single-date dataset).
5. **Tab Switching Preserves Filter:** Switching tabs alters only the status filter criterion without resetting or altering the active applied date.
6. **Contextual Vietnamese Empty States:** Empty states clearly differentiate between a date-specific empty result (`Không có buổi tập nào trong ngày DD/MM/YYYY`) and an overall empty dataset (`Không có buổi tập nào cần chuẩn bị`).
7. **Strict Authorization & Process Safety:** The authoritative 5-role model and existing `@PreAuthorize` security policies remain 100% unchanged. Background processes (backend and frontend) were never stopped or restarted.

---

## 2. Working Tree Inventory & Baseline State

### 2.1 Git HEAD
- `develop` at `65165624859051913e940f53dee8253e02040eb7`.

### 2.2 Pre-existing Dirty Working Tree
All 16 previously modified files and untracked test/report artifacts were preserved intact:
- `BACKEND/src/main/java/com/racehorse/health/dto/BarnHealthMapResponse.java`
- `BACKEND/src/main/java/com/racehorse/health/service/BarnHealthService.java`
- `BACKEND/src/main/java/com/racehorse/media/controller/HorsePhotoController.java`
- `BACKEND/src/main/java/com/racehorse/media/controller/HorseProfileFileController.java`
- `BACKEND/src/main/java/com/racehorse/media/service/HorseProfileFileService.java`
- `BACKEND/src/main/java/com/racehorse/stable/controller/StableController.java`
- `BACKEND/src/main/java/com/racehorse/stable/service/StableService.java`
- `BACKEND/src/test/java/com/racehorse/health/service/BarnHealthServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/media/service/HorseProfileFileServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/stable/service/StableServiceTest.java`
- `FRONTEND/src/AdminPages.jsx`
- `FRONTEND/src/App.jsx`
- `FRONTEND/src/VetPage.jsx`
- `FRONTEND/src/api.js`
- `FRONTEND/src/styles.css`
- Untracked artifacts: `BACKEND/.../controller/`, `FRONTEND/.../dailyRoutine.test.js`, `imageFallback.js`, `imageFallback.test.js`, `docs/`.

---

## 3. Investigation & Existing API/Backend Behavior

### 3.1 Initial Forensic Inspection
1. **Frontend Component:** `TrainingPreparationView` in `FRONTEND/src/StableCarePage.jsx`:
   - Previously initialized `selectedDate` to `today()`.
   - On every mount and whenever `selectedDate` changed, immediately dispatched `getMyTrainingSchedules({ from: selectedDate, to: selectedDate })`.
   - Provided no confirmation button; changing the `<input type="date">` directly fired network requests.
   - Provided no reset button to view all preparation records across dates.
2. **Backend Service & Repository:**
   - Endpoint: `GET /api/training-schedules/mine` in `TrainingScheduleController.java`.
   - Controller delegated to `TrainingScheduleService.mine(userId, from, to)`.
   - In `TrainingScheduleService.java`:
     ```java
     private DateRange range(LocalDate from, LocalDate to) {
         LocalDate start = from == null ? LocalDate.now(clock.withZone(CLUB_ZONE)) : from;
         LocalDate end = to == null ? start : to;
         ...
     }
     ```
     `[FACT]` When `from` and `to` were null, `range(null, null)` silently defaulted to `start = today` and `end = today`.
   - In `TrainingScheduleRepository.java`:
     `findForCaretaker` contained `and s.scheduledStart < :toExclusive and s.scheduledEnd > :fromInclusive`, which threw errors if null bounds were supplied.

---

## 4. Implementation Details

### 4.1 Backend Scope (Narrowly Scoped API Support)
1. **`TrainingScheduleRepository.java`**:
   Updated `findForCaretaker` and `findForDiagnosingVet` to support optional boundary parameters using standard JPQL null-coalescing predicates:
   ```java
   @EntityGraph(attributePaths = {"phase", "horse", "racetrack", "createdBy"})
   @Query("""
           select s from TrainingSchedule s
           where s.horse.caretaker.id = :userId
             and s.publishedAt is not null
             and s.cancelledAt is null
             and (:fromInclusive is null or s.scheduledEnd > :fromInclusive)
             and (:toExclusive is null or s.scheduledStart < :toExclusive)
           order by s.scheduledStart
           """)
   List<TrainingSchedule> findForCaretaker(@Param("userId") UUID userId, @Param("fromInclusive") Instant fromInclusive, @Param("toExclusive") Instant toExclusive);
   ```
2. **`TrainingScheduleService.java`**:
   Updated `mine` and `forDiagnosingVet` to pass `null, null` directly when both `from` and `to` are null:
   ```java
   @Transactional(readOnly = true)
   public List<TrainingScheduleResponse> mine(UUID id, LocalDate from, LocalDate to) {
       if (from == null && to == null) {
           return repository.findForCaretaker(id, null, null).stream().map(this::toResponse).toList();
       }
       DateRange range = range(from, to);
       return repository.findForCaretaker(id, range.from(), range.to()).stream().map(this::toResponse).toList();
   }
   ```
3. **Authorization Unchanged:**
   - `@PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('STABLE_STAFF','VETERINARIAN')")` untouched.
   - Staff members can only view published, non-cancelled training schedules for horses where `horse.caretaker.id == userId`.

### 4.2 Frontend Scope (`FRONTEND/src/StableCarePage.jsx`)
1. **State Decoupling:**
   - `appliedDate` (initial state `null`): The confirmed date filter. When `null`, panel queries all records (`params = {}`).
   - `draftDate` (initial state `''`): Uncommitted date in the `<input type="date">`.
2. **Explicit Confirmation & Reset Toolbar:**
   - Date input bound to `draftDate`: `value={draftDate} onChange={(e) => setDraftDate(e.target.value)}`.
   - "Hôm nay" button: Sets `draftDate` to `today()` without immediately triggering a query.
   - **"Xác nhận"** button: Primary button disabled if `!draftDate`. On click, sets `appliedDate = draftDate`, triggering `loadSchedules`.
   - **"Xem tất cả"** button: Rendered when `appliedDate !== null`. On click, clears filter (`draftDate = ''`, `appliedDate = null`), restoring all records.
   - Active filter indicator: Shows `Đang lọc: DD/MM/YYYY` when a date is applied.
3. **Dynamic Counts & Tabs:**
   - Tab counts `Tất cả ({counts.all})`, `Cần chuẩn bị ({counts.preparing})`, `Đã sẵn sàng ({counts.ready})` derive strictly from `schedules` of the current view.
   - Switching tabs only updates `statusFilter` and preserves `appliedDate`.
4. **Contextual Vietnamese Empty State:**
   - If `appliedDate` is set: `"Không có buổi tập nào trong ngày DD/MM/YYYY"`.
   - If `appliedDate` is `null`: `"Không có buổi tập nào cần chuẩn bị"`.

---

## 5. Verification & Test Results

### 5.1 Automated Backend Tests
Ran JUnit test suite:
- Command: `mvn -s BACKEND/maven-settings.xml -f BACKEND/pom.xml test -Dtest="TrainingScheduleServiceTest,TrainingSessionAccessTest"`
- `TrainingScheduleServiceTest`: **19/19 passed** (including new tests: `mine_withNullDates_queriesAllRecordsWithoutDateBoundaries`, `mine_withSpecificDate_queriesWithDateBoundaries`, and `forDiagnosingVet_withNullDates_queriesAllRecordsWithoutDateBoundaries`).
- `TrainingSessionAccessTest`: **9/9 passed** (confirming zero regressions in controller security policies).
- **Total:** 28 tests, 0 failures, 0 errors.

### 5.2 Automated Frontend Tests
Ran Node test runner:
- Command: `npm.cmd test --prefix FRONTEND`
- Created dedicated test suite: `FRONTEND/src/trainingPreparation.test.js`:
  1. Initial panel entry retrieves/displays all available records without date filter (`buildTrainingScheduleQueryParams(null) == {}`).
  2. Date selection alone does not apply filtering (`draftDate` decoupled from `appliedDate`).
  3. Clicking "Xác nhận" applies the selected date filter.
  4. Clearing the date filter restores all records.
  5. Switching status tabs preserves the applied date filter.
  6. Counts match the currently active dataset.
  7. Empty results for a selected date and all-records are displayed correctly in Vietnamese.
  8. Existing authorization and record visibility remain unchanged.
  9. StableCarePage source verification for explicit confirmation button and decoupled states.
- **Total:** 41 tests, 0 failures, 0 errors.

### 5.3 Frontend Production Build
- Command: `npm.cmd run build --prefix FRONTEND`
- Result: **SUCCESS** (3,201 modules transformed, built in 836ms without error).

### 5.4 Manual Browser Verification Boundary
`[FACT]` Automated unit, integration, and security tests were fully executed and passed. Live browser interaction was not performed directly via HTTP to respect the strict safety rule prohibiting live HTTP calls and process restarts during task execution.

---

## 6. Exact Files Modified & Created

### Product Source Files
| File | Action | Purpose |
|---|---|---|
| `BACKEND/src/main/java/com/racehorse/training/repository/TrainingScheduleRepository.java` | Modified | Added null safety to `fromInclusive`/`toExclusive` queries |
| `BACKEND/src/main/java/com/racehorse/training/service/TrainingScheduleService.java` | Modified | Handle `null, null` dates in `mine` & `forDiagnosingVet` |
| `BACKEND/src/test/java/com/racehorse/training/service/TrainingScheduleServiceTest.java` | Modified | Unit tests for null-date all-records and date-bounded queries |
| `FRONTEND/src/StableCarePage.jsx` | Modified | State decoupling, "Xác nhận" button, "Xem tất cả", empty states, count helpers |
| `FRONTEND/src/trainingPreparation.test.js` | Created | Automated test coverage for 01D requirements |

---

## 7. Workflow Report Git Push Status

- Destination Repository: `C:\DEV\clone-horse`
- Branch: `main`
- Commit: Added report `up-report/RH_STABLE_UI_01D_TRAINING_PREPARATION_FILTER_REPORT.md`
- Push Result: SUCCESS
