# RH-STABLE-UI-01C.6 — Daily Routine Backfill Authority, Custom Activity Input & Stable UI Consistency Report

**Task ID:** RH-STABLE-UI-01C.6  
**Date:** 2026-11-10  
**Environment:** Local Development (Windows 11, PowerShell, JDK 21, Node.js v20)  
**Product Repository:** `C:\DEV\RACE HORSE\HORSE-PROJECT` (Branch: `develop`, HEAD: `65165624859051913e940f53dee8253e02040eb7`)  
**Workflow Repository:** `C:\DEV\clone-horse` (Branch: `main`)

---

## 1. Executive Summary

This report documents the implementation and verification for task **RH-STABLE-UI-01C.6**, delivering the approved Product Owner business rules and UI refinements for the Stable Care module (`/stable-care/stables`).

### Key Deliverables:
1. `[FACT]` **Bulk Routine Backfill Execution Restricted to Stable Staff:**
   - Per the approved Product Owner decision, `POST /api/stables/routines/backfill-execute` is now executable exclusively by `ROLE_STABLE_STAFF` with `PERM_STABLE_CARE_EDIT`.
   - `ROLE_HEAD_TRAINER`, `ROLE_CLUB_MANAGER`, `ROLE_VETERINARIAN`, and `ROLE_HORSE_OWNER` are denied execution with HTTP 403 Forbidden.
   - `GET /api/stables/routines/backfill-preview` retains its pre-existing read authorization (`PERM_STABLE_CARE_VIEW`).
   - The frontend trigger button (`Khởi tạo lịch mặc định`) is now visible only to authorized Stable Staff.
2. `[FACT]` **Read-Only Training Schedule Display in Stable View:**
   - Active training schedules created by Trainers are displayed as strictly read-only informational cards directly inside the horse's daily routine panel in `StablesView`.
   - The data is sourced safely from the existing `StableService.AssignmentView` projection (`isTrainingActive` and `trainingSchedule`) returned by `GET /api/stables`.
   - Stable Staff cannot mutate, publish, or delete training schedules. Zero training APIs or endpoints were modified or broadened.
   - Medical holds and clinical restrictions maintain absolute precedence over active training. Default daily routine records remain completely preserved and automatically reappear when training concludes.
3. `[FACT]` **Custom Activity Content Input for "Hoạt động khác" (OTHER):**
   - When Stable Staff selects "Hoạt động khác", the edit modal displays a dedicated input field: `Nội dung hoạt động khác *`.
   - Custom content is persisted in the existing `notes` field of `module_records.data_json` without requiring any database schema alteration or table migration.
   - When reopening the edit modal for an existing custom activity, the saved description is restored into the input field.
   - The Daily Routine view list and modal dropdown display the saved custom activity description instead of the generic "Hoạt động khác" label.
   - Both frontend and backend validate that custom content cannot be blank when `OTHER` is selected (backend throws `BusinessException("Vui lòng nhập nội dung cho hoạt động khác")`).
   - Switching away to another activity type (e.g. `TURNOUT`) maintains separate state and does not contaminate standard instructions with custom activity text.
4. `[FACT]` **Standardized `HH:mm` Time Formatting:**
   - All user-facing time displays in Stable Care (activity dropdown, routine list items, modal inputs) are standardized to `HH:mm` format (e.g., `06:00 - 07:00` instead of `06:00:00 - 07:00:00`).
   - Underlying database and API time contracts remain preserved.
5. `[FACT]` **Consistent Vietnamese Horse-Status Localization:**
   - English health status codes (`FIT`, `HEALTHY`, `QUARANTINED`, `INJURED`, `RECOVERING`, `RESTING`, `RETIRED`) are standardized to clear Vietnamese labels (`Khỏe mạnh`, `Đang cách ly`, `Chấn thương`, `Hồi phục`, `Nghỉ ngơi`, `Đã giải nghệ`).
   - Hardcoded English warnings (e.g., `(QUARANTINED)`) were replaced with natural Vietnamese phrasing across stall allocation and transfer workflows.
   - Underlying database enum/string values remain completely untouched.
6. `[FACT]` **Automated Test Validation:**
   - **Backend Tests:** 57/57 passed (`StableControllerSecurityTest`: 26 tests, `StableServiceTest`: 31 tests, 0 failures, 0 skipped).
   - **Frontend Tests:** 27/27 passed (`dailyRoutine.test.js`: 21 tests, `imageFallback.test.js`: 6 tests, 0 failures).
   - **Production Build:** Vite production build succeeded in 1.11s with 0 errors.
7. `[FACT]` **Operational Safety:**
   - Zero process disruptions (Spring Boot backend PID 34388 and Vite dev server remained active).
   - Zero product repository commits or pushes.

---

## 2. Product Baseline & Working-Tree Status

### 2.1 Git Lineage
- **Product Repository:** `C:\DEV\RACE HORSE\HORSE-PROJECT`
- **Active Branch:** `develop`
- **Baseline HEAD:** `65165624859051913e940f53dee8253e02040eb7`
- **Workflow Repository:** `C:\DEV\clone-horse` (`main`)

### 2.2 Product Working-Tree Inventory (`git status --short`)

| File Path | Status | Task Relation | Changes in 01C.6 |
|---|---|---|---|
| `BACKEND/src/main/java/com/racehorse/health/dto/BarnHealthMapResponse.java` | Modified | Pre-existing | Preserved untouched |
| `BACKEND/src/main/java/com/racehorse/health/service/BarnHealthService.java` | Modified | Pre-existing | Preserved untouched |
| `BACKEND/src/main/java/com/racehorse/media/controller/HorsePhotoController.java` | Modified | Pre-existing | Preserved untouched |
| `BACKEND/src/main/java/com/racehorse/media/controller/HorseProfileFileController.java` | Modified | Pre-existing | Preserved untouched |
| `BACKEND/src/main/java/com/racehorse/media/service/HorseProfileFileService.java` | Modified | Pre-existing | Preserved untouched |
| `BACKEND/src/main/java/com/racehorse/stable/controller/StableController.java` | Modified | 01C.3 / 01C.5 / 01C.6 | Restricted `POST /routines/backfill-execute` to `ROLE_STABLE_STAFF` + `PERM_STABLE_CARE_EDIT` |
| `BACKEND/src/main/java/com/racehorse/stable/service/StableService.java` | Modified | 01C.3 / 01C.4 / 01C.6 | Added non-blank validation for `OTHER` activity in `validateRoutine` |
| `BACKEND/src/test/java/com/racehorse/health/service/BarnHealthServiceTest.java` | Modified | Pre-existing | Preserved untouched |
| `BACKEND/src/test/java/com/racehorse/media/service/HorseProfileFileServiceTest.java` | Modified | Pre-existing | Preserved untouched |
| `BACKEND/src/test/java/com/racehorse/stable/service/StableServiceTest.java` | Modified | 01C.3 / 01C.4 / 01C.6 | Added unit tests for `OTHER` custom content validation and persistence (31 tests) |
| `BACKEND/src/test/java/com/racehorse/media/controller/` | Untracked | Pre-existing | Preserved untouched |
| `BACKEND/src/test/java/com/racehorse/stable/controller/StableControllerSecurityTest.java` | Untracked | 01C.3 / 01C.5 / 01C.6 | Added tests for backfill execute authorization (26 tests) |
| `FRONTEND/src/AdminPages.jsx` | Modified | Pre-existing | Preserved untouched |
| `FRONTEND/src/App.jsx` | Modified | Pre-existing | Preserved untouched |
| `FRONTEND/src/StableCarePage.jsx` | Modified | 01C.3 / 01C.4 / 01C.6 | Backfill button to Stable staff, custom content input, `HH:mm` time formatting, Vietnamese status localization |
| `FRONTEND/src/VetPage.jsx` | Modified | Pre-existing | Preserved untouched |
| `FRONTEND/src/api.js` | Modified | 01C.3 | Preserved untouched (DELETE endpoint remains removed) |
| `FRONTEND/src/styles.css` | Modified | Pre-existing | Preserved untouched |
| `FRONTEND/src/dailyRoutine.test.js` | Untracked | 01C.3 / 01C.4 / 01C.6 | Added tests for `OTHER` custom content, time formatting, health status localization, and backfill authority (21 tests) |
| `FRONTEND/src/imageFallback.js` | Untracked | Pre-existing | Preserved untouched |
| `FRONTEND/src/imageFallback.test.js` | Untracked | Pre-existing | Preserved untouched (6 tests) |

---

## 3. Backfill Authorization Scope & Exact Changes

### 3.1 Authorization Matrix

| Endpoint | Prior Authorization (01C.5.1) | Approved Authorization (01C.6) | HTTP Status for Stable Staff | HTTP Status for Trainer / Manager |
|---|---|---|---|---|
| `POST /api/stables/routines/backfill-execute` | `HEAD_TRAINER`, `CLUB_MANAGER` + edit perm | `STABLE_STAFF` + edit perm | **200 OK** | **403 Forbidden** |
| `GET /api/stables/routines/backfill-preview` | `PERM_STABLE_CARE_VIEW` | `PERM_STABLE_CARE_VIEW` (Unchanged) | **200 OK** | **200 OK** |

### 3.2 Exact Controller Change (`StableController.java`)

```java
    @PostMapping("/routines/backfill-execute")
    @PreAuthorize("hasRole('STABLE_STAFF') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    StableService.RoutineBackfillPreview executeRoutineBackfill(@AuthenticationPrincipal Jwt jwt) {
        return service.executeRoutineBackfill(user(jwt));
    }
```

### 3.3 Frontend Visibility Alignment (`StableCarePage.jsx`)

In `FRONTEND/src/StableCarePage.jsx`, the button trigger for bulk backfill is conditioned on `canManageDailyRoutine` (which evaluates to `userRoles.includes('STABLE_STAFF')`):

```jsx
{canManageDailyRoutine && (
  <button
    type="button"
    className="button secondary"
    style={{ minHeight: '34px', fontSize: '11px', display: 'inline-flex', alignItems: 'center', gap: '5px' }}
    title="Kiểm tra và khởi tạo lịch sinh hoạt mặc định theo thể trạng cho các ngựa trong chuồng"
    onClick={() => setShowBackfillModal(true)}
  >
    <RotateCcw size={13} /> Khởi tạo lịch mặc định
  </button>
)}
```

---

## 4. Custom Activity Content ("Hoạt động khác") Implementation

### 4.1 Persistence Approach (Zero Schema Migration Required)

- **Storage Layer:** Routine records are persisted in `module_records` where `record_type = 'HORSE_DAILY_ROUTINE'`. The payload is stored in `data_json NVARCHAR(MAX)`.
- **Field Utilization:** For `activityType === 'OTHER'`, the custom activity description is stored in the existing `notes` field.
- **DTO Preservation:** `DailyRoutineRequest(String activityType, LocalTime startTime, LocalTime endTime, String notes)` and `DailyRoutineView(UUID id, UUID horseId, String activityType, LocalTime startTime, LocalTime endTime, String notes, boolean active)` are reused directly without modifying the API contract or altering table structures.

### 4.2 Backend Validation (`StableService.java`)

In `StableService.validateRoutine`:
```java
private void validateRoutine(DailyRoutineRequest r) {
    if (r.activityType() == null || r.activityType().isBlank()) throw new BusinessException("Hoạt động sinh hoạt không được để trống");
    if (r.startTime() == null || r.endTime() == null) throw new BusinessException("Thời gian bắt đầu và kết thúc không được để trống");
    if (!r.startTime().isBefore(r.endTime())) throw new BusinessException("Giờ bắt đầu phải trước giờ kết thúc");
    if ("OTHER".equalsIgnoreCase(r.activityType().trim()) && (r.notes() == null || r.notes().isBlank())) {
        throw new BusinessException("Vui lòng nhập nội dung cho hoạt động khác");
    }
}
```

### 4.3 Frontend Edit Modal & Segregation (`StableCarePage.jsx`)

1. **Dedicated Input:** When `activityType === 'OTHER'` is selected in `RoutineModal`, the generic "Ghi chú hướng dẫn" input is replaced with:
   ```jsx
   <label className="field full">
     <span>Nội dung hoạt động khác *</span>
     <input
       required
       value={form.customActivity}
       onChange={(e) => setForm({ ...form, customActivity: e.target.value })}
       placeholder="Nhập tên / mô tả hoạt động sinh hoạt khác (ví dụ: Tập bơi hồ thủy liệu, Khám răng...)"
       disabled={isRestricted}
     />
   </label>
   ```
2. **State Segregation:** `customActivity` (used for `OTHER`) and `notes` (used for standard activities) are maintained independently. If a user switches from `OTHER` to another activity type (e.g., `TURNOUT`), the custom text is not submitted as standard instructions.
3. **Display Presentation:** In the Daily Routine view:
   ```jsx
   <b style={{ fontSize: '12px', color: '#173129' }}>
     {r.activityType === 'OTHER' && r.notes ? r.notes : (ROUTINE_ACTIVITY_LABELS[r.activityType] || r.activityType)}
   </b>
   {r.activityType === 'OTHER' ? (
     <div style={{ fontSize: '10px', color: 'var(--muted)', marginTop: '2px' }}>Hoạt động khác</div>
   ) : (
     r.notes && <div style={{ fontSize: '10px', color: 'var(--muted)', marginTop: '2px' }}>{r.notes}</div>
   )}
   ```
4. **Modal Selector:** Dropdown entries display the saved custom activity name:
   `{formatTimeHHmm(r.startTime)} - {formatTimeHHmm(r.endTime)} · {r.activityType === 'OTHER' && r.notes ? r.notes : (ROUTINE_ACTIVITY_LABELS[r.activityType] || r.activityType)}`

---

## 5. Training Schedule Read-Only Visibility

### 5.1 Architecture & Boundaries
- **Ownership:** Training schedules are created, assigned, and managed by Trainers (`ROLE_HEAD_TRAINER`).
- **Stable Access:** Stable Staff view the active session details strictly as read-only context within `StablesView`.
- **Data Source:** Existing `GET /api/stables` projection via `StableService.AssignmentView` (`isTrainingActive` and `trainingSchedule`).
- **No Boundary Creep:** Zero training endpoints were exposed or modified for `ROLE_STABLE_STAFF`.

### 5.2 Precedence & State Rendering
- **State C (Medical Restriction / Injury):** Has absolute precedence over training. Renders warning banner and hides both training schedules and default routines.
- **State B (Active Training Today):** Displays blue training card with session status, intensity, task name, time range (`HH:mm`), and racetrack. Default daily routines are temporarily hidden; mutation actions are disabled.
- **State A (Normal State):** Default daily routines are fully active and mutable. When a training session ends, the horse seamlessly reverts to State A with all original routines preserved intact.

---

## 6. Time Formatting & Vietnamese Localization

### 6.1 `HH:mm` Time Formatting Helper
```javascript
const formatTimeHHmm = (val) => {
  if (!val) return ''
  const str = String(val).trim()
  const m = str.match(/^(\d{1,2}):(\d{2})(?::\d{2})?/)
  if (m) {
    return `${m[1].padStart(2, '0')}:${m[2]}`
  }
  return str
}
```
All user-facing displays (`06:00:00 - 07:00:00`) now render consistently as `06:00 - 07:00`.

### 6.2 Vietnamese Status Language Standardization

The `HEALTH_STATUS_MAP` and helper `formatHorseHealthStatus` provide consistent Vietnamese translation for all horse health codes:

| Backend Code | Prior Display | Standardized Vietnamese Display | Category |
|---|---|---|---|
| `ACTIVE` | Khỏe mạnh | Khỏe mạnh | Normal |
| `FIT` | FIT | Khỏe mạnh | Normal |
| `HEALTHY` | HEALTHY | Khỏe mạnh | Normal |
| `INJURED` | Chấn thương | Chấn thương | Medical Hold |
| `QUARANTINED` | QUARANTINED | Đang cách ly | Clinical Isolation |
| `RESTING` | Nghỉ ngơi | Nghỉ ngơi | Resting |
| `RECOVERING` | Hồi phục | Hồi phục | Recovery |
| `RETIRED` | Đã giải nghệ | Đã giải nghệ | Retired |
| `SICK` | SICK | Đang điều trị | Medical Care |
| `RESTRICTED` | RESTRICTED | Hạn chế y tế | Restriction |

Quarantine warning notices across allocation, reassignment, and transfer dialogs have been updated to remove English tags (e.g., `(QUARANTINED)`), using natural Vietnamese phrasing throughout.

---

## 7. Automated Verification Results

### 7.1 Backend Tests (`StableControllerSecurityTest` + `StableServiceTest`)

Execution command:
```bash
mvn -s maven-settings.xml test "-Dtest=StableControllerSecurityTest,StableServiceTest"
```

Output:
```text
[INFO] Running com.racehorse.stable.controller.StableControllerSecurityTest
[INFO] Tests run: 26, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.510 s -- in com.racehorse.stable.controller.StableControllerSecurityTest
[INFO] Running com.racehorse.stable.service.StableServiceTest
[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.081 s -- in com.racehorse.stable.service.StableServiceTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 57, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### 7.2 Frontend Tests (`dailyRoutine.test.js` + `imageFallback.test.js`)

Execution command:
```bash
node --test src/dailyRoutine.test.js src/imageFallback.test.js
```

Output:
```text
✔ stableCareApi in api.js does not expose deleteRoutine endpoint (removed per RH-STABLE-UI-01C.3) (1.8448ms)
✔ computeCanManageDailyRoutine permits STABLE_STAFF for normal horse (0.1442ms)
✔ computeCanManageDailyRoutine denies HEAD_TRAINER (0.2039ms)
✔ computeCanManageDailyRoutine denies CLUB_MANAGER (0.1198ms)
✔ computeCanManageDailyRoutine denies VETERINARIAN and HORSE_OWNER (0.2225ms)
✔ computeCanManageDailyRoutine denies Stable users while horse is training (0.257ms)
✔ computeCanManageDailyRoutine denies Stable users while horse is injured or medically restricted (0.1415ms)
✔ validateRoutineForm validates chronological order of start and end times (0.2796ms)
✔ validateRoutineForm blocks any modification when horse is restricted (0.0998ms)
✔ validateRoutineForm for OTHER requires non-blank custom content (0.183ms)
✔ formatTimeHHmm consistently formats seconds and minutes (0.1183ms)
✔ formatHorseHealthStatus maps all English codes to Vietnamese labels (0.1063ms)
✔ getRoutineDisplayTitle displays custom description for OTHER and label for standard (0.868ms)
✔ isHorseMovementRestricted identifies injured, training locked, or emergency horses (0.2938ms)
✔ State A: normal healthy horse displays default daily routine and enables mutation actions (0.1453ms)
✔ State B: horse currently training displays training schedule read-only and temporarily hides default routine (0.3777ms)
✔ State C: medical hold overrides training state and takes strict precedence (0.1075ms)
✔ Transition: when training session completes, horse seamlessly restores default routine without data loss (0.1184ms)
✔ computeCanExecuteBackfill permits STABLE_STAFF (01C.6 approved rule) (0.0797ms)
✔ computeCanExecuteBackfill denies HEAD_TRAINER, CLUB_MANAGER, VETERINARIAN, and HORSE_OWNER (0.0444ms)
✔ Five-Role & Caretaker boundary: caretaker_user_id does not create a role or authorization boundary for daily routine (0.0488ms)
✔ handleImageFallback hides target and reveals next element sibling with specified display mode (1.1182ms)
✔ handleImageFallback supports grid display mode for modal/hero avatars (0.124ms)
✔ handleImageFallback handles missing or detached next element sibling safely (0.1522ms)
✔ handleImageFallback handles sibling lacking style object safely (0.6667ms)
✔ handleImageFallback handles null or invalid event objects safely (0.338ms)
✔ handleImageFallback catches unexpected DOM property mutation exceptions (0.126ms)
ℹ tests 27
ℹ suites 0
ℹ pass 27
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 93.4724
```

### 7.3 Frontend Production Build

Execution command:
```bash
npm run build
```

Output:
```text
> racehorse-club-frontend@0.1.0 build
> vite build

vite v8.2.2 building client environment for production...
transforming...
✓ 3201 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                                    0.73 kB │ gzip:   0.42 kB
dist/assets/index-ChtB4gFY.css                    91.77 kB │ gzip:  19.04 kB
dist/assets/eye-CQfRl6C5.js                        0.28 kB │ gzip:   0.22 kB
dist/assets/map-pin-BHzzisw_.js                    0.28 kB │ gzip:   0.24 kB
dist/assets/shield-alert-CkPlovCy.js               0.38 kB │ gzip:   0.27 kB
dist/assets/rolldown-runtime-hePW80VL.js           0.71 kB │ gzip:   0.42 kB
dist/assets/with-selector-DM0vd3nm.js              1.67 kB │ gzip:   0.74 kB
dist/assets/TrainingReceptionPage-BCxlKz4t.js      2.84 kB │ gzip:   1.15 kB
dist/assets/TrainingEmergencyPage-BTzqo8Pl.js      9.46 kB │ gzip:   3.32 kB
dist/assets/WalletPage-BTfV5wdS.js                11.38 kB │ gzip:   4.04 kB
dist/assets/TrainingMetricsPage-4c2r3Efq.js       11.97 kB │ gzip:   4.53 kB
dist/assets/TrainingRacePage-DaEUEIAi.js          14.43 kB │ gzip:   4.50 kB
dist/assets/jsx-runtime-Dg-1YV3G.js               18.72 kB │ gzip:   6.07 kB
dist/assets/purify.es-7fJ1DZ6H.js                 26.92 kB │ gzip:  10.69 kB
dist/assets/FitnessPage-BitTyxUE.js               36.88 kB │ gzip:  12.10 kB
dist/assets/FinanceReportsPage-CgDiPn4C.js        46.33 kB │ gzip:  10.73 kB
dist/assets/TrainingSchedulePage-DVI9Kd4X.js      57.75 kB │ gzip:  13.35 kB
dist/assets/index.es-DDPOP7Fe.js                 151.41 kB │ gzip:  48.90 kB
dist/assets/StableCarePage-B-okuCcQ.js           160.01 kB │ gzip:  34.12 kB
dist/assets/html2canvas-Dn6bw578.js              199.49 kB │ gzip:  46.77 kB
dist/assets/LineChart-jObyKTju.js                352.59 kB │ gzip: 102.50 kB
dist/assets/jspdf.es.min-DDiiQwPh.js             399.06 kB │ gzip: 129.63 kB
dist/assets/index-D7B1ajpq.js                    411.59 kB │ gzip: 122.51 kB
dist/assets/VetPage-DLsxkTDl.js                1,175.13 kB │ gzip: 310.50 kB

✓ built in 1.11s
```

---

## 8. Safety & Integrity Confirmations

1. `[FACT]` **DELETE Daily Routine Inactive:** DELETE endpoint remains completely absent; no delete button exists in the frontend UI.
2. `[FACT]` **Zero Schema Changes:** Custom activity content is safely persisted in the existing `notes` property within `module_records.data_json`. No SQL migrations were introduced.
3. `[FACT]` **No Process Interruptions:** Spring Boot backend process (PID 34388, port 8080) and Vite frontend server (port 5173) remained active without restart.
4. `[FACT]` **Product Git Safety:** Product repository `C:\DEV\RACE HORSE\HORSE-PROJECT` had zero commits, zero resets, and zero pushes.
5. `[FACT]` **Workflow Exclusivity:** Only this report is committed and pushed to `C:\DEV\clone-horse` (`main`).

---

## 9. Conclusion

Task **RH-STABLE-UI-01C.6** is complete. Bulk Daily Routine backfill execution has been restricted strictly to `ROLE_STABLE_STAFF`. Trainer-created training schedules are visible read-only in the Stable interface without expanding role permissions. Custom activity descriptions are fully supported and persisted for "Hoạt động khác" without schema changes. User-facing times are standardized to `HH:mm`, and all horse-status labels are localized into clear, natural Vietnamese.
