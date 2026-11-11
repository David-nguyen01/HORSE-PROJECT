# REPORT RH-WORKFLOW-01: Product Source to Personal Review Mirror Synchronization & 01E.5 Review

**Date:** 2026-11-11  
**Status:** COMPLETE  
**Source Repository:** `C:\DEV\RACE HORSE\HORSE-PROJECT` (Branch: `develop`, HEAD: `65165624859051913e940f53dee8253e02040eb7`)  
**Personal Review Mirror:** `C:\DEV\clone-horse\source-mirror`  
**Host Repository:** `thangnx-mc/clone-horse` (Branch: `main`)  
**Snapshot ID:** `SNAPSHOT-20261111-021256-DEVELOP-6516562`  

---

## 1. Executive Summary

This task synchronized the authoritative Racehorse product source code and verified working-tree modifications from `C:\DEV\RACE HORSE\HORSE-PROJECT` (branch `develop`) into the personal review mirror at `C:\DEV\clone-horse\source-mirror`.

The personal review mirror had last been synchronized on November 9, 2026 (`SNAPSHOT-20261109-214456-DEVELOP-6516562`) and lacked the recent implementation changes, hardening, and test suites from tasks `RH-HORSE-PHOTO-*` and `RH-STABLE-UI-*` (01A through 01E.5).

### Key Outcomes:
1. **Mirror Discovery:** Successfully identified and verified the exact mirror path at `C:\DEV\clone-horse\source-mirror`. Confirmed that no other directories named `minor` or `mirror` exist across `C:\DEV`.
2. **Safe Synchronization:** Executed the hardened synchronization tool (`sync-source-mirror.ps1 -Apply`).
   - Synchronized **448 source files** (totaling 4,122,864 bytes / ~3.93 MB).
   - Achieved **100% SHA-256 cryptographic match** across all 448 mirrored files.
   - Successfully mirrored all 25 modified tracked files and all 15 untracked files/tests/documentation without modifying the source repository.
3. **Repository Boundaries & Preservations:**
   - Authoritative team repository (`HORSE-PROJECT`) remained strictly untouched (ZERO Git commits, ZERO resets, ZERO reverse sync).
   - Mirror host repository (`clone-horse`) preserved all infrastructure files (`MIRROR_GUIDE.md`, `sync-source-mirror.ps1`, `test-mirror-tooling.ps1`, `snapshot-manifest.*`).
   - Zero conflicts encountered.
4. **01E.5 Source Review in the Mirror:**
   - **Stable Stalls TDZ Fix:** Confirmed `loadRoutines` is initialized at line 352 prior to `loadStables` referencing it at line 365 in `StableCarePage.jsx`. Full dependency array audit confirmed zero remaining TDZ risks.
   - **Training Preparation Unbounded Query:** Confirmed `findForCaretaker(@Param("userId") UUID userId)` in `TrainingScheduleRepository.java` strictly retains `s.publishedAt is not null`. Verified that draft/unfinalized training schedules (`publishedAt == null`, status `"PLANNED"`) are safely prevented from appearing in Stable Staff preparation.
   - **Runtime Activation:** Confirmed frontend fix was applied via Vite HMR; backend process remains running with updated compiled classes on disk awaiting next scheduled maintenance restart.

---

## 2. Part A — Discovery & Baseline Inspection

### 2.1 Mirror Path Discovery
A recursive directory inspection was conducted across `C:\DEV`:
- Verified that no folder named `minor` exists in `C:\DEV`.
- Located the canonical review mirror at:
  `C:\DEV\clone-horse\source-mirror`
- The folder contains the permanent mirror infrastructure established on November 5, 2026 (`SOURCE_MIRROR_INITIALIZATION_REPORT.md` and `MIRROR_GUIDE.md`).

### 2.2 Baseline Inspection Before Synchronization

| Metric / Attribute | Authoritative Team Repository | Personal Review Mirror Host |
| :--- | :--- | :--- |
| **Path** | `C:\DEV\RACE HORSE\HORSE-PROJECT` | `C:\DEV\clone-horse` (mirror root: `source-mirror/`) |
| **Branch** | `develop` | `main` |
| **Git HEAD** | `65165624859051913e940f53dee8253e02040eb7` | `78589d2c28d174e37294fdda02bd5cfc9802df82` |
| **Remote URL** | `https://github.com/David-nguyen01/HORSE-PROJECT.git` | `https://github.com/thangnx-mc/clone-horse.git` |
| **Working Tree State** | 33 modified/untracked files (active working tree) | Clean (`nothing to commit, working tree clean`) |
| **Last Mirror Snapshot** | N/A (Source of Truth) | `SNAPSHOT-20261109-214456-DEVELOP-6516562` (Nov 9, 2026) |

### 2.3 Pre-Sync Drift Analysis
Prior to this task, the mirror lacked the changes introduced across:
- `RH-HORSE-PHOTO-01` through `01F` (profile file controllers, image fallback helper and tests).
- `RH-STABLE-UI-01A` through `01C.6` (Stable routine permissions, backfill restrictions, custom activity inputs).
- `RH-STABLE-UI-01D` through `01D.1` (Training preparation date filtering, vet query isolation).
- `RH-STABLE-UI-01E.1` through `01E.5` (Stall map refresh, calendar-day schedule projection, TDZ reference error fix, unbounded caretaker query).

---

## 3. Part B — Safe Synchronization Methodology

### 3.1 Execution and Process Boundaries
The synchronization was conducted via the hardened, parameterized script `source-mirror/sync-source-mirror.ps1`:
```powershell
powershell -ExecutionPolicy Bypass -File "C:\DEV\clone-horse\source-mirror\sync-source-mirror.ps1" -Apply
```

### 3.2 Security and Safety Guarantees Enforced
1. **Strict Canonical Path Validation:** The script strictly enforced that the destination resolves to `C:\DEV\clone-horse\source-mirror`, rejecting any path traversal (`..`) or symbolic links/reparse points.
2. **One-Way Protection:** Source inspection is strictly read-only (`git ls-files`, `Test-Path`, `Get-FileHash`, `Copy-Item`). The team repository working tree was never modified, reset, stashed, checked out, or committed.
3. **Exclusion Filters:**
   - `.git/` VCS repository metadata.
   - `node_modules/` dependencies.
   - `BACKEND/target/` and `FRONTEND/dist/` build outputs.
   - `.env` files and environment secrets (permitted only `.env.example` and `.env.template`).
   - Certificates, keystores, and private credentials (`*.pfx`, `*.pem`, `*.key`, `*.jks`, `*.keystore`).
   - Application runtime logs and dumps (`*.log`, `*.tmp`, `*.bak`).
4. **Infrastructure Protection:** Infrastructure files (`MIRROR_GUIDE.md`, `sync-source-mirror.ps1`, `test-mirror-tooling.ps1`, `snapshot-manifest.json`, `snapshot-manifest.md`) were protected from deletion or overwrite.
5. **Conflict Reconciliation:** No conflicting local edits existed in `clone-horse/source-mirror/`. All target files were updated cleanly.

---

## 4. Part C — Synchronization Verification & Evidence

### 4.1 Snapshot Manifest Summary
- **Snapshot ID:** `SNAPSHOT-20261111-021256-DEVELOP-6516562`
- **Timestamp:** `2026-11-11T02:12:56+07:00`
- **Total Files Mirrored:** 448 source files
- **Total Size:** 4,122,864 bytes (~3.93 MB)
- **Tracked Files Mirrored:** 433
- **Untracked Source/Test Files Mirrored:** 15
- **Stale Files Pruned:** 0
- **SHA-256 Hash Mismatches:** 0 (100% verified match across all 448 files)

### 4.2 Untracked & Modified Source Files Mirrored
The following active working-tree files were captured with full cryptographic fidelity:

#### Backend:
- `BACKEND/src/main/java/com/racehorse/health/dto/BarnHealthMapResponse.java`
- `BACKEND/src/main/java/com/racehorse/health/service/BarnHealthService.java`
- `BACKEND/src/main/java/com/racehorse/horse/dto/HorseResponse.java`
- `BACKEND/src/main/java/com/racehorse/horse/mapper/HorseMapper.java`
- `BACKEND/src/main/java/com/racehorse/media/controller/HorsePhotoController.java`
- `BACKEND/src/main/java/com/racehorse/media/controller/HorseProfileFileController.java`
- `BACKEND/src/main/java/com/racehorse/media/service/HorseProfileFileService.java`
- `BACKEND/src/main/java/com/racehorse/stable/controller/StableController.java`
- `BACKEND/src/main/java/com/racehorse/stable/service/StableService.java`
- `BACKEND/src/main/java/com/racehorse/training/controller/TrainingScheduleController.java`
- `BACKEND/src/main/java/com/racehorse/training/repository/TrainingScheduleRepository.java`
- `BACKEND/src/main/java/com/racehorse/training/service/TrainingScheduleService.java`
- `BACKEND/src/test/java/com/racehorse/health/service/BarnHealthServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/horse/service/HorseServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/media/service/HorseProfileFileServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/stable/service/StableServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/training/service/TrainingScheduleServiceTest.java`
- `BACKEND/src/test/java/com/racehorse/media/controller/HorsePhotoControllerTest.java`
- `BACKEND/src/test/java/com/racehorse/stable/controller/StableControllerTest.java`

#### Frontend:
- `FRONTEND/src/AdminPages.jsx`
- `FRONTEND/src/App.jsx`
- `FRONTEND/src/StableCarePage.jsx`
- `FRONTEND/src/TrainingSchedulePage.jsx`
- `FRONTEND/src/VetPage.jsx`
- `FRONTEND/src/api.js`
- `FRONTEND/src/styles.css`
- `FRONTEND/src/trainingRules.js`
- `FRONTEND/src/dailyRoutine.test.js`
- `FRONTEND/src/imageFallback.js`
- `FRONTEND/src/imageFallback.test.js`
- `FRONTEND/src/stallRefreshAndTrainerEligibility.test.js`
- `FRONTEND/src/trainingPreparation.test.js`

#### Documentation & Reports:
- `docs/RH_STABLE_UI_01C_6_DAILY_ROUTINE_AND_STABLE_UI_REFINEMENT_REPORT.md`
- `docs/RH_STABLE_UI_01D_1_TRAINING_QUERY_SCOPE_AUDIT_REPORT.md`
- `docs/RH_STABLE_UI_01D_TRAINING_PREPARATION_FILTER_REPORT.md`
- `docs/RH_STABLE_UI_01E_1_STABLE_REFRESH_TRAINER_ELIGIBILITY_REPORT.md`
- `docs/RH_STABLE_UI_01E_2_TRAINING_PREPARATION_QUERY_CORRECTION_REPORT.md`
- `docs/RH_STABLE_UI_01E_3_DAILY_TRAINING_PROJECTION_AND_QUERY_VERIFICATION_REPORT.md`
- `docs/RH_STABLE_UI_01E_5_STALL_RUNTIME_AND_PREPARATION_DEFAULT_REGRESSION_REPORT.md`
- `docs/RH_STABLE_UI_01E_TRAINING_ROUTINE_STATUS_INTEGRATION_AUDIT_REPORT.md`

### 4.3 Review Readiness
The mirror now 100% represents the active authoritative `develop` working tree and is fully validated for direct source code review and audit.

---

## 5. Part D — Review of 01E.5 Changes in the Mirror

### 5.1 Stable Stalls Temporal Dead Zone (TDZ) Fix
Inspected `source-mirror/FRONTEND/src/StableCarePage.jsx`:
1. **Declaration Order:**
   - Line 352: `const loadRoutines = useCallback(...)` is declared.
   - Line 365: `const loadStables = useCallback(...)` is declared, referencing `[selectedStableId, loadRoutines]`.
   - `loadRoutines` is fully initialized before `loadStables` evaluates its dependency array during initial mount, eliminating the `ReferenceError: Cannot access 'loadRoutines' before initialization`.
2. **Comprehensive Callback Dependency Audit:**
   - `loadPendingCounts` (line 334): dependencies `[]` -> Initialized first.
   - `loadRoutines` (line 352): dependencies `[]` -> Initialized second.
   - `loadStables` (line 365): dependencies `[selectedStableId, loadRoutines]` -> Both defined above.
   - `handleRefresh` (line 424): dependencies `[loading, loadStables, loadPendingCounts]` -> All defined above.
   - `handleSelectStall` (line 438): dependencies `[loadRoutines]` -> Defined above.
   - `useEffect` (line 467): dependencies `[targetHorseId, stables, handleSelectStall]` -> All defined above.
3. **Conclusion:** Zero remaining TDZ hazards exist in `StablesView`.

### 5.2 Training Preparation Unbounded Query & Published Status Integrity
Inspected:
- `source-mirror/BACKEND/src/main/java/com/racehorse/training/repository/TrainingScheduleRepository.java`
- `source-mirror/BACKEND/src/main/java/com/racehorse/training/service/TrainingScheduleService.java`
- `source-mirror/BACKEND/src/main/java/com/racehorse/training/controller/TrainingScheduleController.java`
- `source-mirror/FRONTEND/src/trainingRules.js`

#### Retention of `s.publishedAt is not null`
Inspection of `TrainingScheduleRepository.java` confirms:
```java
    @EntityGraph(attributePaths = {"phase", "horse", "racetrack", "createdBy"})
    @Query("""
            select s from TrainingSchedule s
            where s.horse.caretaker.id = :userId
              and s.publishedAt is not null
              and s.cancelledAt is null
              and s.completedAt is null
              and (s.sessionStatus is null or s.sessionStatus in ('PREPARING', 'READY'))
            order by s.scheduledStart
            """)
    List<TrainingSchedule> findForCaretaker(@Param("userId") UUID userId);
```
Both the 1-argument query (line 74) and the 3-argument query (line 86) **explicitly retain** `and s.publishedAt is not null`.

#### Domain Analysis: Why `publishedAt is not null` is Mandatory
1. In the Racehorse training plan lifecycle, Head Trainers create training plans and draft sessions. Sessions in draft state have `publishedAt == null` (domain status `"PLANNED"`).
2. Caretaker notifications (`notifyCaretaker`) are triggered only upon explicit publication (`schedule.publish(instant)`).
3. If `publishedAt is not null` were removed from `findForCaretaker`, draft and unpublished sessions would leak into Stable Staff's "Chuẩn bị huấn luyện" queue, prompting Stable Staff to prepare horses for unapproved or tentative workouts.
4. **Conclusion:** Retaining `publishedAt is not null` is an essential safety boundary. Draft sessions cannot appear in Stable Training Preparation.

### 5.3 Runtime Activation Status
- **Frontend:** The fix in `StableCarePage.jsx` was loaded by the running Vite development server via Hot Module Replacement (HMR). Reloading `/stable-care/stables` immediately renders without errors.
- **Backend:** The updated classes are present in `BACKEND/target/classes`. The active Spring Boot process (PID 34388, task-25860) remains undisturbed, preserving test and background execution continuity. Bytecode changes will activate cleanly upon the next process restart.

---

## 6. Repository Integrity & Verification Checklist

- [x] Authoritative repository (`HORSE-PROJECT`) working tree untouched and preserved.
- [x] Zero Git commits or mutations in `HORSE-PROJECT`.
- [x] Mirror host repository (`clone-horse`) synchronized to latest working tree.
- [x] 100% SHA-256 match verified across all 448 mirrored files.
- [x] Zero conflicts; all protected infrastructure files preserved.
- [x] 01E.5 TDZ fix and unbounded query domain integrity verified in mirror.
- [x] Personal review mirror is fully validated and safe for subsequent reviews.
