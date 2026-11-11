# TASK REPORT: RH-STABLE-UI-01E.1 — Stable Stall State Refresh & Trainer Horse Eligibility Visibility

**Report Date:** 2026-11-11T01:10:00+07:00  
**Product Repository:** `C:\DEV\RACE HORSE\HORSE-PROJECT`  
**Baseline Branch:** `develop`  
**Git HEAD:** `65165624859051913e940f53dee8253e02040eb7`  
**Workflow Repository:** `C:\DEV\clone-horse`  
**Workflow Branch:** `main` (`thangnx-mc/clone-horse`)  
**Workflow Destination:** `up-report/RH_STABLE_UI_01E_1_STABLE_REFRESH_TRAINER_ELIGIBILITY_REPORT.md`  

---

## 1. Executive Summary

Task **RH-STABLE-UI-01E.1** implemented two targeted, non-breaking improvements identified in audit `RH_STABLE_UI_01E_TRAINING_ROUTINE_STATUS_INTEGRATION_AUDIT_REPORT.md`:

1. **Part A — Stable Stall Map State Synchronization & Manual Refresh:**
   - **Problem:** In `FRONTEND/src/StableCarePage.jsx`, the selected horse (`selectedHorse`) remained pinned to React state at the moment the user clicked a stall. When a Trainer created, started, or completed a training schedule in another tab or session, re-fetching stall data (`loadStables()`) updated the map cards but did not synchronize the currently inspected horse, leaving stale daily routine or training displays.
   - **Solution:** Updated `loadStables()` so that upon receiving fresh stall data, it re-matches `selectedHorse` against current stall assignments. If the horse remains assigned, it updates `selectedHorse` with the latest `isTrainingActive`, `trainingSchedule`, and health data, immediately triggering daily routine synchronization. If the horse or stall was unassigned, it safely clears the selection. Added a prominent manual refresh button (**"Làm mới"** with `RotateCw` icon) in the Stall Map header guarded by an in-flight ref (`isFetchingRef`) to prevent duplicate concurrent network requests.
   - **3-State Hierarchy Preserved:** Strict priority remains intact: **State C (Medical Hold / Stall Rest)** $\rightarrow$ **State B (Today's Training Schedule)** $\rightarrow$ **State A (Default Daily Routine)**.

2. **Part B — Trainer Horse Eligibility Visibility:**
   - **Problem:** Trainers attempting to create a training plan or schedule for a non-FIT or injured horse encountered raw backend exceptions only upon form submission because `HorseResponse` omitted the horse's internal health status (`healthStatus`), and the frontend lacked visibility into veterinary fitness.
   - **Solution:**
     - In `BACKEND/src/main/java/com/racehorse/horse/dto/HorseResponse.java`, exposed `String healthStatus` while preserving a 24-parameter backward-compatible constructor for zero-breakage compatibility across existing callers.
     - In `BACKEND/src/main/java/com/racehorse/horse/mapper/HorseMapper.java`, mapped `horse.getHealthStatus()` into `HorseResponse`.
     - In `FRONTEND/src/trainingRules.js`, implemented `isHorseFitForTraining(horse)` (strictly `healthStatus === 'FIT' && status !== 'INJURED'`) and `getHorseIneligibilityReason(horse)` returning clear Vietnamese diagnostic messages.
     - In `FRONTEND/src/TrainingSchedulePage.jsx`, disabled ineligible horses in `CreatePlanModal` and `CreateScheduleModal` with concise reasons (e.g., `Chấn thương`, `Đang cách ly`, `Cần theo dõi y tế`, `Chưa có trạng thái FIT`), displayed contextual warning banners, blocked submission with user-friendly notices, and annotated the calendar filter dropdown.

---

## 2. Working Tree Inventory & Safety Compliance

### 2.1 Git Repository State
- **Product Repository:** `C:\DEV\RACE HORSE\HORSE-PROJECT`
- **Current Branch:** `develop`
- **HEAD Commit:** `65165624859051913e940f53dee8253e02040eb7`
- **Mutating Git Commands:** Zero git mutations executed in product repository.

### 2.2 Working Tree Cleanliness & Preservation
All 16 previously dirty files and uncommitted test/documentation artifacts were strictly preserved:
- `BACKEND/src/main/java/com/racehorse/health/dto/BarnHealthMapResponse.java`
- `BACKEND/src/main/java/com/racehorse/health/service/BarnHealthService.java`
- `BACKEND/src/main/java/com/racehorse/media/controller/HorsePhotoController.java`
- `BACKEND/src/main/java/com/racehorse/media/controller/HorseProfileFileController.java`
- `BACKEND/src/main/java/com/racehorse/media/service/HorseProfileFileService.java`
- `BACKEND/src/main/java/com/racehorse/stable/controller/StableController.java`
- `BACKEND/src/main/java/com/racehorse/stable/service/StableService.java`
- `BACKEND/src/main/java/com/racehorse/training/repository/TrainingScheduleRepository.java`
- `BACKEND/src/main/java/com/racehorse/training/service/TrainingScheduleService.java`
- `BACKEND/src/test/java/com/racehorse/health/service/BarnHealthServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/media/service/HorseProfileFileServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/stable/service/StableServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/training/service/TrainingScheduleServiceTest.java`
- `FRONTEND/src/AdminPages.jsx`
- `FRONTEND/src/App.jsx`
- `FRONTEND/src/VetPage.jsx`
- `FRONTEND/src/api.js`
- `FRONTEND/src/styles.css`
- Untracked artifacts: `BACKEND/.../controller/`, `FRONTEND/.../dailyRoutine.test.js`, `imageFallback.js`, `imageFallback.test.js`, `trainingPreparation.test.js`, `docs/`.

### 2.3 Process & Runtime Integrity
- **Backend Spring Boot:** PID 34388 on port 8080 remained continuously running. Zero process kills or restarts.
- **Frontend Vite Server:** Port 5173 remained continuously running.
- **Database:** Zero SQL mutations, migrations, or DDL changes.

---

## 3. Implementation Details

### 3.1 Part A — Stable Stall Map State Synchronization (`FRONTEND/src/StableCarePage.jsx`)

1. **Selection Reference & In-Flight Concurrency Guard:**
   ```javascript
   const selectedHorseRef = useRef(selectedHorse)
   selectedHorseRef.current = selectedHorse
   const isFetchingRef = useRef(false)
   ```
2. **Synchronized Selection Re-matching in `loadStables()`:**
   When fresh stall data is fetched, the handler searches across all stables and stalls for the current `selectedHorseRef.current.id`:
   - If a matching assignment is found, `selectedHorse` is updated with the latest attributes (`isTrainingActive`, `trainingSchedule`, `horseHealthStatus`, `emergencyActive`, `trainingLocked`), and `loadRoutines(updatedHorse.id)` is refreshed.
   - If the horse was moved or unassigned, `selectedHorse` is reset to `null` and routine panels are cleared.
   ```javascript
   const currentSelected = selectedHorseRef.current
   if (currentSelected?.id) {
     let matchingStall = null
     for (const s of stablesList) {
       const found = (s.stalls || []).find((st) => st.currentAssignment?.horseId === currentSelected.id)
       if (found) {
         matchingStall = found
         break
       }
     }

     if (matchingStall?.currentAssignment) {
       const updatedHorse = {
         id: matchingStall.currentAssignment.horseId,
         code: matchingStall.currentAssignment.horseCode,
         name: matchingStall.currentAssignment.horseName,
         stallId: matchingStall.id,
         stallCode: matchingStall.code,
         healthStatus: matchingStall.currentAssignment.horseHealthStatus,
         assignedFrom: matchingStall.currentAssignment.assignedFrom,
         trainingLocked: matchingStall.currentAssignment.trainingLocked,
         trainingLockReason: matchingStall.currentAssignment.trainingLockReason,
         emergencyActive: matchingStall.currentAssignment.emergencyActive,
         photoUrl: matchingStall.currentAssignment.photoUrl || null,
         isTrainingActive: Boolean(matchingStall.currentAssignment.isTrainingActive),
         trainingSchedule: matchingStall.currentAssignment.trainingSchedule || null,
       }
       setSelectedHorse(updatedHorse)
       loadRoutines(updatedHorse.id)
     } else {
       setSelectedHorse(null)
       setRoutines([])
       setRoutineNotice('')
       setRoutineError('')
     }
   }
   ```
3. **Manual Refresh Handler:**
   ```javascript
   const handleRefresh = useCallback(async () => {
     if (loading || isFetchingRef.current) return
     await Promise.all([
       loadStables(),
       loadPendingCounts(),
     ])
   }, [loading, loadStables, loadPendingCounts])
   ```
4. **Header UI Control:**
   Added button beside "Tiếp nhận & Xếp chuồng":
   ```jsx
   <button
     type="button"
     className="button secondary"
     style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', minHeight: '36px' }}
     onClick={handleRefresh}
     disabled={loading}
     title="Làm mới sơ đồ ô chuồng và trạng thái ngựa"
   >
     <RotateCw size={15} /> {loading ? 'Đang tải…' : 'Làm mới'}
   </button>
   ```

---

### 3.2 Part B — Trainer Horse Eligibility Visibility

#### 3.2.1 Backend DTO & Mapper Update
1. **`BACKEND/src/main/java/com/racehorse/horse/dto/HorseResponse.java`**:
   - Added `String healthStatus` component to `HorseResponse` record.
   - Added 24-parameter backward-compatible canonical constructor chaining to the 25-parameter record:
   ```java
   public HorseResponse(
           UUID id, String code, String name, String microchipPassportNumber, LocalDate birthDate,
           HorseGender gender, String colorMarkings, String breed, ParentSummary sire, ParentSummary dam,
           UserSummary currentTrainer, UserSummary caretaker, UserSummary diagnosingVet,
           List<OwnerSummary> owners, String stableLocation, HorseStatus status, Instant archivedAt,
           Instant createdAt, Instant updatedAt, String paymentStatus, String intakeStatus,
           Boolean serviceOverride, String overrideReason, LocalDate paidUntil) {
       this(id, code, name, microchipPassportNumber, birthDate, gender, colorMarkings, breed, sire, dam,
               currentTrainer, caretaker, diagnosingVet, owners, stableLocation, status, null,
               archivedAt, createdAt, updatedAt, paymentStatus, intakeStatus, serviceOverride,
               overrideReason, paidUntil);
   }
   ```
2. **`BACKEND/src/main/java/com/racehorse/horse/mapper/HorseMapper.java`**:
   - Passed `horse.getHealthStatus()` in `toResponse(Horse horse, List<Ownership> ownerships)`.
3. **`BACKEND/src/test/java/com/racehorse/horse/service/HorseServiceTest.java`**:
   - Added unit test `@Test void toResponseIncludesInternalHealthStatus()` verifying mapping of `healthStatus` for both `FIT` and `INJURED`.

#### 3.2.2 Frontend Shared Eligibility Rule (`FRONTEND/src/trainingRules.js`)
Added authoritative helper functions evaluating canonical English uppercase statuses:
```javascript
export function isHorseFitForTraining(horse) {
  if (!horse) return false
  const health = horse.healthStatus ? String(horse.healthStatus).trim().toUpperCase() : ''
  const status = horse.status ? String(horse.status).trim().toUpperCase() : ''
  return health === 'FIT' && status !== 'INJURED'
}

export function getHorseIneligibilityReason(horse) {
  if (!horse) return 'Không xác định'
  const status = horse.status ? String(horse.status).trim().toUpperCase() : ''
  const health = horse.healthStatus ? String(horse.healthStatus).trim().toUpperCase() : ''
  if (status === 'INJURED' || health === 'INJURED') return 'Chấn thương'
  if (health === 'QUARANTINED') return 'Đang cách ly'
  if (health === 'MONITORING' || health === 'MONITOR') return 'Cần theo dõi y tế'
  if (health === 'RESTING') return 'Nghỉ ngơi'
  if (health === 'RECOVERING') return 'Đang hồi phục'
  if (health === 'SICK' || health === 'UNDER_TREATMENT') return 'Đang điều trị'
  if (health === 'RESTRICTED') return 'Hạn chế y tế'
  if (!health) return 'Chưa có trạng thái FIT'
  if (health !== 'FIT') return `Trạng thái: ${health}`
  return ''
}
```

#### 3.2.3 Trainer Scheduling Interface (`FRONTEND/src/TrainingSchedulePage.jsx`)
1. **Top Calendar Filter Dropdown:**
   Annotates horses that are not fit with their reason:
   ```jsx
   {horses.map(h => {
     const fit = isHorseFitForTraining(h)
     const tag = !fit ? ` [${getHorseIneligibilityReason(h)}]` : ''
     return <option key={h.id} value={h.id}>{h.code} · {h.name}{tag}</option>
   })}
   ```
2. **`CreatePlanModal` (Plan Creation):**
   - Disables option tags for non-FIT horses: `<option disabled={!fit}>`.
   - Displays clear warning banner if an ineligible horse is selected:
     ```jsx
     {form.horseId && !isHorseFitForTraining(horses.find(h => h.id === form.horseId)) && (
       <p className="notice error full">
         Ngựa không đủ điều kiện lập kế hoạch huấn luyện ({getHorseIneligibilityReason(horses.find(h => h.id === form.horseId))}). Ngựa phải hoàn tất khám và có trạng thái FIT.
       </p>
     )}
     ```
   - Disables the submit button if the selected horse is not FIT: `disabled={... || !isHorseFitForTraining(horses.find(horse => horse.id === form.horseId))}`.
   - Throws validation error preventing API request if submitted programmatically.
3. **`CreateScheduleModal` (Schedule Creation):**
   - Disables option tags for non-FIT horses: `<option disabled={!fit}>`.
   - Displays clear warning banner if an ineligible horse is selected:
     ```jsx
     {selectedHorse && !isHorseFitForTraining(horses.find(h => h.id === selectedHorse)) && (
       <div className="notice error full">
         Ngựa không đủ điều kiện tạo buổi tập: {getHorseIneligibilityReason(horses.find(h => h.id === selectedHorse))}. Ngựa phải hoàn tất khám và có trạng thái FIT.
       </div>
     )}
     ```
   - Disables the submit button if the selected horse is not FIT: `disabled={... || !isHorseFitForTraining(horses.find(h => h.id === selectedHorse))}`.
   - Pre-submission validation guard sets error and aborts request:
     ```javascript
     const selectedHorseObj = horses.find(h => h.id === selectedHorse)
     if (!isHorseFitForTraining(selectedHorseObj)) {
       setError(`Ngựa không đủ điều kiện tạo buổi tập: ${getHorseIneligibilityReason(selectedHorseObj)}. Ngựa phải hoàn tất khám và có trạng thái FIT.`)
       return
     }
     ```

---

## 4. Automated Verification Results

### 4.1 Backend Unit Tests
Command executed:
```bash
cmd /c "cd /d C:\DEV\RACE HORSE\HORSE-PROJECT\BACKEND && mvn -s maven-settings.xml test -Dtest=HorseServiceTest"
```
**Output:**
```
[INFO] Running com.racehorse.horse.service.HorseServiceTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.197 s -- in com.racehorse.horse.service.HorseServiceTest
[INFO] Results:
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 4.2 Frontend Unit Tests
Command executed:
```bash
cmd /c "cd /d C:\DEV\RACE HORSE\HORSE-PROJECT\FRONTEND && npm test"
```
**Output:**
```
✔ all region labels in the injury form resolve to their own code (2.7651ms)
✔ structured region codes take precedence over legacy location text (0.1884ms)
✔ saved severity values have distinct visual colors (0.1102ms)
✔ non-specific injury labels do not claim a precise point on the model (0.9085ms)
✔ all markers project onto the bundled horse model and paired legs stay apart (15.9524ms)
✔ stableCareApi in api.js does not expose deleteRoutine endpoint (removed per RH-STABLE-UI-01C.3) (1.9523ms)
✔ computeCanManageDailyRoutine permits STABLE_STAFF for normal horse (0.2594ms)
✔ computeCanManageDailyRoutine denies HEAD_TRAINER (0.1922ms)
✔ computeCanManageDailyRoutine denies CLUB_MANAGER (0.2304ms)
✔ computeCanManageDailyRoutine denies VETERINARIAN and HORSE_OWNER (0.2404ms)
✔ computeCanManageDailyRoutine denies Stable users while horse is training (0.2661ms)
✔ computeCanManageDailyRoutine denies Stable users while horse is injured or medically restricted (0.2272ms)
✔ validateRoutineForm validates chronological order of start and end times (0.3871ms)
✔ validateRoutineForm blocks any modification when horse is restricted (0.1956ms)
✔ validateRoutineForm for OTHER requires non-blank custom content (0.2944ms)
✔ formatTimeHHmm consistently formats seconds and minutes (0.2503ms)
✔ formatHorseHealthStatus maps all English codes to Vietnamese labels (0.1761ms)
✔ getRoutineDisplayTitle displays custom description for OTHER and label for standard (0.1767ms)
✔ isHorseMovementRestricted identifies injured, training locked, or emergency horses (1.2022ms)
✔ State A: normal healthy horse displays default daily routine and enables mutation actions (0.2246ms)
✔ State B: horse currently training displays training schedule read-only and temporarily hides default routine (0.2943ms)
✔ State C: medical hold overrides training state and takes strict precedence (0.1745ms)
✔ Transition: when training session completes, horse seamlessly restores default routine without data loss (0.1911ms)
✔ computeCanExecuteBackfill permits STABLE_STAFF (01C.6 approved rule) (0.1377ms)
✔ computeCanExecuteBackfill denies HEAD_TRAINER, CLUB_MANAGER, VETERINARIAN, and HORSE_OWNER (0.1503ms)
✔ Five-Role & Caretaker boundary: caretaker_user_id does not create a role or authorization boundary for daily routine (0.1454ms)
✔ handleImageFallback hides target and reveals next element sibling with specified display mode (0.6183ms)
✔ handleImageFallback supports grid display mode for modal/hero avatars (0.1275ms)
✔ handleImageFallback handles missing or detached next element sibling safely (0.166ms)
✔ handleImageFallback handles sibling lacking style object safely (0.8519ms)
✔ handleImageFallback handles null or invalid event objects safely (0.3015ms)
✔ handleImageFallback catches unexpected DOM property mutation exceptions (0.2593ms)
✔ Part A: updated stall data updates currently selected horse training state (2.2258ms)
✔ Part A: completed or cancelled training schedule seamlessly restores State A default routine (0.1775ms)
✔ Part A: selection is cleared if horse is unassigned or stall removed (0.0877ms)
✔ Part A: medical restriction precedence strictly maintained over active training (0.0882ms)
✔ Part A: concurrent loading guard prevents duplicate network requests (26.9206ms)
✔ Part B: isHorseFitForTraining strictly permits FIT and rejects non-FIT or unknown states (0.2772ms)
✔ Part B: getHorseIneligibilityReason returns clear concise Vietnamese explanations (0.1678ms)
✔ Part A & B: source code static verification of key controls and DTO mapping (2.6749ms)
✔ 1. Initial panel entry retrieves/displays all available records without date filter (1.9348ms)
✔ 2. Date selection alone does not apply filtering (draft decoupled from applied) (0.122ms)
✔ 3. Clicking "Xác nhận" applies the selected date filter (0.1736ms)
✔ 4. Clearing the date filter restores all records (0.1059ms)
✔ 5. Switching status tabs preserves the applied date filter (0.1244ms)
✔ 6. Counts match the currently active dataset (0.1237ms)
✔ 7. Empty results for a selected date and all-records are displayed correctly in Vietnamese (0.1855ms)
✔ 8. Existing authorization and record visibility remain unchanged (0.3109ms)
✔ 9. StableCarePage source verification for explicit confirmation button and decoupled states (1.0724ms)
ℹ tests 49
ℹ suites 0
ℹ pass 49
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 164.6305
```

### 4.3 Frontend Production Build
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
dist/assets/TrainingSchedulePage-CtcB3ANm.js      59.54 kB │ gzip:  13.80 kB
dist/assets/StableCarePage-8xqIr7e4.js           162.55 kB │ gzip:  34.74 kB
✓ built in 947ms
```

---

## 5. Definition of Done & Status Matrix

| Dimension | Status | Notes |
| :--- | :--- | :--- |
| **Part A Implementation** | **Complete** | In-flight guarded `loadStables()` synchronizes `selectedHorse` across state transitions; "Làm mới" button added |
| **Part B Implementation** | **Complete** | `HorseResponse` includes `healthStatus`; Trainer modals proactively disable non-FIT horses with VN reasons |
| **Backend Automated Tests** | **Passed** | `HorseServiceTest` 20/20 passed |
| **Frontend Automated Tests** | **Passed** | 49/49 unit tests passed (including 8 new tests in `stallRefreshAndTrainerEligibility.test.js`) |
| **Frontend Build** | **Passed** | Vite production build clean in 947ms |
| **Runtime & Process Safety** | **Compliant** | Zero process terminations; backend PID 34388 and Vite 5173 kept running |
| **Working Tree Safety** | **Compliant** | All pre-existing 16 dirty files preserved untouched |
| **Git Boundary** | **Compliant** | Zero git mutations in `HORSE-PROJECT`; report pushed exclusively to `clone-horse` |
| **External Runtime Verification** | **Requires User Verification** | Live multi-tab interaction between Trainer scheduling and Stable stall refresh |

---

## 6. User Verification Protocol

To verify the live behavioral integration in a running browser environment:

1. **Verify Trainer Horse Ineligibility Handling:**
   - Log in as **Head Trainer** (`ROLE_HEAD_TRAINER`).
   - Navigate to `/training/schedules`.
   - Open **"Lập kế hoạch mới"** (`CreatePlanModal`):
     - Click **"Chọn ngựa"**. Verify horses with status other than `FIT` (or marked `INJURED`) appear disabled with explanatory text such as `(Không đủ ĐK: Chấn thương)` or `(Không đủ ĐK: Đang cách ly)`.
     - Attempting to submit without a FIT horse is blocked and disables the submit button.
   - Open **"Xếp lịch tập luyện"** (`CreateScheduleModal`):
     - Verify non-FIT horses are disabled in the selection list.
2. **Verify Stable Stall Map Refresh & Routine Transition:**
   - In browser tab 1, log in as **Stable Staff** (`ROLE_STABLE_STAFF`) and open `/stable-care/stables`.
   - Select stall with horse **Ngọc Long** (`H13`). Note that it displays **Lịch trình hàng ngày mặc định** (State A).
   - In browser tab 2, log in as **Head Trainer** and create a training schedule for **Ngọc Long** for today.
   - Return to tab 1 (Stable Care) without switching stalls. Click the **"Làm mới"** button in the header.
   - **Expected Result:** The button shows "Đang tải…", the stall map updates, and the routine panel immediately transitions to **Lịch tập luyện hôm nay** (State B), displaying the task name, session status, and time range. Default routine editing is hidden/locked.
3. **Verify Training Completion Transition:**
   - In tab 2 (Trainer), mark the training schedule as completed.
   - In tab 1 (Stable Staff), click **"Làm mới"**.
   - **Expected Result:** The routine panel immediately reverts to **Lịch trình hàng ngày mặc định** (State A) with all default slots intact.
