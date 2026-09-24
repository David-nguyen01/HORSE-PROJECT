# STEP 2B — IMPLEMENT FLOW 2 FIXES REPORT

**Repository:** SWP Racehorse Training Management System  
**Date:** 2026-09-24  
**Author:** Antigravity  
**Scope:** Backend Only (`BACKEND/`) — Flow 2 (FR03, FR04, FR05, FR06, FR07)  
**Status:** IMPLEMENTATION COMPLETE / AUTOMATED VERIFICATION PASS / EXTERNAL VERIFICATION PENDING  

---

## 1. Executive Summary

In accordance with the audit findings in `STEP_2A_FLOW2_SRS_COMPLIANCE_AUDIT.md` and the official **SRS v1.0**, Step 2B implemented fixes for the 3 identified compliance gaps in Flow 2 (Training Plan & Execution).

All implementations were carried out under strict architectural constraints:
* **Backend-Only Boundary:** Zero changes to `FRONTEND/`, React components, or UI code.
* **Database Schema Preservation:** Zero schema changes, zero migrations, and zero table additions. The strict 30-table ceiling (`THROW 51030`) was strictly respected by leveraging existing relational columns and filtered unique indexes already defined in `06_training_plans_scheduling_monitoring.sql`.
* **Safe & Backward-Compatible Design:** Overloaded constructors and methods were retained to guarantee zero regressions for existing integrations.
* **Automated Verification:** 53/53 tests pass cleanly (100% success rate), including newly added unit tests for RBAC annotations, schedule completion lifecycle, and phase filtering.

---

## 2. Gap 1 Implementation: FR04 RBAC Decoupling & Read Access

### 2.1 Problem & Root Cause
`TrainingPlanController` had a class-level security annotation:
```java
@PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
```
This inadvertently blocked all read operations (`GET /api/training-plans/{planId}` and `GET /api/horses/{horseId}/training-plans`) for users with only `PERM_TRAINING_VIEW` (such as `HORSE_OWNER`, `VETERINARIAN`, `STABLE_STAFF`). According to SRS FR04 and Section 3 RBAC, view access is permitted to all roles with `PERM_TRAINING_VIEW`, with horse-ownership access scoping applied to `HORSE_OWNER`.

### 2.2 Changes Implemented
1. **[`TrainingPlanController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/controller/TrainingPlanController.java):**
   - Removed class-level `@PreAuthorize`.
   - Added method-level `@PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")` to write endpoints:
     - `POST /api/training-plans` (`create`)
     - `PUT /api/training-plans/{planId}` (`update`)
     - `POST /api/training-plans/{planId}/copy` (`copy`)
   - Added method-level `@PreAuthorize("hasAuthority('PERM_TRAINING_VIEW')")` to read endpoints:
     - `GET /api/training-plans/{planId}` (`get`)
     - `GET /api/horses/{horseId}/training-plans` (`listForHorse`)
   - Read methods extract caller `userId` from `Jwt` and determine role privilege (`ROLE_CLUB_MANAGER`, `ROLE_HEAD_TRAINER`, `ROLE_VETERINARIAN`, `ROLE_STABLE_STAFF`), delegating to `TrainingPlanService`.
2. **[`TrainingPlanService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/service/TrainingPlanService.java):**
   - Added `get(UUID planId, UUID userId, boolean privileged)` and `listForHorse(UUID horseId, UUID userId, boolean privileged)`.
   - Invokes `horseAccessService.requireReadable(horseId, userId, privileged)` ensuring `HORSE_OWNER` can only access training plans for horses they own, while staff can view any active horse's plans.
   - Retained backward-compatible 1-argument overloads `get(UUID planId)` and `listForHorse(UUID horseId)`.
3. **Automated Tests:**
   - Created [`TrainingPlanControllerTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/training/controller/TrainingPlanControllerTest.java): Verifies by reflection that class-level `@PreAuthorize` is absent, method annotations strictly match SRS rules, and caller privilege flags are routed accurately.
   - Enhanced [`TrainingPlanServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/training/service/TrainingPlanServiceTest.java): Verifies `requireReadable` enforcement on `get` and `listForHorse`.

---

## 3. Gap 2 Implementation: FR05 ↔ FR07 Linkage & Completion Lifecycle

### 3.1 Problem & Root Cause
In SRS FR05 and FR07, recorded training evaluations must link to the scheduled session they evaluate (`training_schedule_id`), and submitting an evaluation completes the training schedule. In the initial codebase, `TrainingEvaluation` entity did not have the `@ManyToOne` association to `TrainingSchedule`, `CreateTrainingEvaluationRequest` did not accept `trainingScheduleId`, and `completedAt` on `TrainingSchedule` was never updated upon evaluation creation.

### 3.2 Changes Implemented
1. **[`TrainingSchedule.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/entity/TrainingSchedule.java):**
   - Added `complete(Instant time)` method to record completion time when `completedAt == null`.
   - Added `cancel(Instant time)` method consistent with entity lifecycle standards.
2. **[`TrainingEvaluation.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/entity/TrainingEvaluation.java):**
   - Added `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "training_schedule_id") private TrainingSchedule trainingSchedule;`.
   - Added constructors and getter `getTrainingSchedule()`.
3. **[`CreateTrainingEvaluationRequest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/dto/CreateTrainingEvaluationRequest.java):**
   - Added optional `UUID trainingScheduleId`.
   - Added overloaded 11-argument constructor for backward compatibility with existing tests.
4. **[`TrainingEvaluationResponse.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/dto/TrainingEvaluationResponse.java):**
   - Added `UUID trainingScheduleId` to response.
   - Added overloaded 14-argument constructor for backward compatibility.
5. **[`TrainingEvaluationRepository.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/repository/TrainingEvaluationRepository.java):**
   - Added `boolean existsByTrainingScheduleId(UUID trainingScheduleId);`.
   - Added `Optional<TrainingEvaluation> findByTrainingScheduleId(UUID trainingScheduleId);`.
   - Added `trainingSchedule` to `@EntityGraph(attributePaths = {"horse", "recordedBy", "trainingSchedule"})` on `findDetailById` and `findByHorseIdOrderBySessionDateDescCompletedAtDesc`.
6. **[`TrainingEvaluationService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/service/TrainingEvaluationService.java):**
   - Injected `TrainingScheduleRepository scheduleRepository`.
   - In `create(...)`, when `request.trainingScheduleId() != null`:
     - Looks up schedule by ID (`findDetailById`), throwing `ResourceNotFoundException` if missing.
     - Validates that the schedule belongs to the horse being evaluated (`schedule.getHorse().getId().equals(horse.getId())`), throwing `BusinessException` on mismatch.
     - Validates that schedule is not cancelled (`schedule.getCancelledAt() == null`), throwing `BusinessException` if cancelled.
     - Validates that schedule has not already been evaluated (`!repository.existsByTrainingScheduleId(schedule.getId())`), throwing `BusinessException` if duplicate.
     - Marks schedule completed via `schedule.complete(clock.instant())`.
     - Links schedule entity to `TrainingEvaluation`.
   - Updated `toResponse` to map `trainingScheduleId`.
7. **Automated Tests:**
   - In [`TrainingEvaluationServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/training/service/TrainingEvaluationServiceTest.java), added 4 new comprehensive test cases:
     - `createWithTrainingScheduleMarksScheduleCompletedAndLinksEntity`: Proves schedule is linked and `completedAt` timestamp is populated.
     - `createWithTrainingScheduleRejectsHorseMismatch`: Verifies rejection if schedule belongs to another horse.
     - `createWithTrainingScheduleRejectsCancelledSchedule`: Verifies rejection if schedule was cancelled.
     - `createWithTrainingScheduleRejectsAlreadyEvaluatedSchedule`: Verifies rejection if schedule was already evaluated.

---

## 4. Gap 3 Implementation: FR03 Fitness Dashboard Phase Filtering

### 4.1 Problem & Root Cause
In SRS FR03, the Fitness Dashboard must allow filtering aggregated fitness and metrics by training phase (`phaseId`). In the existing code, `FitnessDashboardController` and `FitnessDashboardService` only accepted `horseId`, `from`, `to`, `fitnessStatus`, and `query`, omitting the phase dimension.

### 4.2 Changes Implemented
1. **[`FitnessDashboardResponse.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/dto/FitnessDashboardResponse.java):**
   - Updated `FilterSummary` record to include `UUID phaseId`.
   - Retained 4-argument constructor for backward compatibility.
2. **[`FitnessDashboardController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/controller/FitnessDashboardController.java):**
   - Added `@RequestParam(required = false) UUID phaseId` to `dashboard(...)`.
3. **[`TrainingEvaluationRepository.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/repository/TrainingEvaluationRepository.java):**
   - Updated JPQL query in `findDashboardData` to support `phaseId`:
     ```sql
     and (:phaseId is null or (e.trainingSchedule is not null and e.trainingSchedule.phase.id = :phaseId))
     ```
   - Included `trainingSchedule` in `@EntityGraph` to prevent N+1 queries.
   - Added default 4-arg `findDashboardData` method for backward compatibility.
4. **[`FitnessDashboardService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/service/FitnessDashboardService.java):**
   - Injected `TrainingPlanPhaseRepository phaseRepository`.
   - Added `get(UUID horseId, UUID phaseId, LocalDate from, LocalDate to, FitnessStatus fitnessStatus, String query)`:
     - When `phaseId != null`:
       - Loads phase details (`phaseRepository.findDetailById(phaseId)`).
       - Validates that if `horseId` was also provided, it matches the phase's horse (`phase.getTrainingPlan().getHorse().getId()`).
       - Automatically scopes horse to `phaseHorseId` if `horseId` was null.
       - Intelligently defaults `effectiveFrom` to `phase.getStartDate()` and `effectiveTo` to `phase.getEndDate()` (bounded by `today`) when date parameters are omitted.
       - Validates that phase has already started if dates are omitted.
     - Passes `phaseId` to `evaluationRepository.findDashboardData(...)`.
   - Retained overloaded 5-argument `get(...)` method for backward compatibility.
5. **Automated Tests:**
   - In [`FitnessDashboardServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/training/service/FitnessDashboardServiceTest.java):
     - Added `dashboardFiltersByPhaseAndScopesHorse`: Verifies phase lookup, date defaulting to phase boundaries, horse scoping, and query filtering.
     - Added `dashboardRejectsPhaseBelongingToDifferentHorse`: Verifies rejection when `horseId` does not match the phase's horse.

---

## 5. Database Impact & Constraint Compliance

* **30-Table Limit:** Strict enforcement verified. Zero tables added (`dbo.module_records` was used for Flow 1 media; Flow 2 uses existing first-class relational tables).
* **Existing Schema Columns Reused:**
  - `dbo.training_evaluations.training_schedule_id` (already existed as `UNIQUEIDENTIFIER NULL`).
  - `dbo.training_evaluations.uq_training_evaluations_schedule` (already existed as filtered unique index).
  - `dbo.training_schedules.completed_at` (already existed as `DATETIME2(0) NULL`).
* **Zero Migrations Required:** All changes map directly to existing, active database schemas.

---

## 6. Automated Test Results

Test execution was performed via `mvn test` in `BACKEND/`:
```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.racehorse.auth.dto.RegisterRequestValidationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.auth.service.LoginSessionServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.auth.service.UserManagementServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.common.config.SecurityConfigurationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.health.service.TrainingLockServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.horse.service.HorseServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.horse.service.PedigreeServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.media.service.HorsePhotoServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.media.service.HorseProfileFileServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.media.service.ProfileFileValidatorTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.racing.service.RaceResultServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.training.controller.TrainingPlanControllerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.training.service.FitnessDashboardServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.training.service.RacetrackServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.training.service.TrainingEvaluationServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.training.service.TrainingPlanServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.training.service.TrainingScheduleServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 53, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 7. Modified & Created Files Summary

### Modified Files (Backend Main)
1. `BACKEND/src/main/java/com/racehorse/training/controller/TrainingPlanController.java`
2. `BACKEND/src/main/java/com/racehorse/training/service/TrainingPlanService.java`
3. `BACKEND/src/main/java/com/racehorse/training/entity/TrainingSchedule.java`
4. `BACKEND/src/main/java/com/racehorse/training/entity/TrainingEvaluation.java`
5. `BACKEND/src/main/java/com/racehorse/training/dto/CreateTrainingEvaluationRequest.java`
6. `BACKEND/src/main/java/com/racehorse/training/dto/TrainingEvaluationResponse.java`
7. `BACKEND/src/main/java/com/racehorse/training/repository/TrainingEvaluationRepository.java`
8. `BACKEND/src/main/java/com/racehorse/training/service/TrainingEvaluationService.java`
9. `BACKEND/src/main/java/com/racehorse/training/controller/FitnessDashboardController.java`
10. `BACKEND/src/main/java/com/racehorse/training/dto/FitnessDashboardResponse.java`
11. `BACKEND/src/main/java/com/racehorse/training/service/FitnessDashboardService.java`

### Modified & Created Files (Backend Tests)
1. `BACKEND/src/test/java/com/racehorse/training/controller/TrainingPlanControllerTest.java` *(New)*
2. `BACKEND/src/test/java/com/racehorse/training/service/TrainingPlanServiceTest.java`
3. `BACKEND/src/test/java/com/racehorse/training/service/TrainingEvaluationServiceTest.java`
4. `BACKEND/src/test/java/com/racehorse/training/service/FitnessDashboardServiceTest.java`

---

## 8. Definition of Done & Status Matrix

| Dimension | Status | Notes |
| :--- | :--- | :--- |
| **Implementation Status** | **Complete** | All 3 gaps from STEP 2A implemented cleanly in backend code. |
| **Automated Verification** | **Passed** | 53 unit tests executed with 0 failures, 0 errors. |
| **Database Limit** | **Passed** | 30-table ceiling preserved; existing schema columns reused. |
| **Frontend Isolation** | **Passed** | 0 files modified in `FRONTEND/`. |
| **External / Runtime Verification** | **Pending User Verification** | Live API integration testing against SQL Server runtime pending. |
| **Final Definition of Done** | **Satisfied for Step 2B** | Ready for user testing and transition to Flow 3. |

---

## 9. User Handoff for External Runtime Verification

To verify Flow 2 behavior in a running instance against live database and security contexts:

### Test Case 1: Training Plan RBAC Decoupling (FR04)
1. **Action:** Log in as `HORSE_OWNER` or `VETERINARIAN` (having `PERM_TRAINING_VIEW`). Call `GET /api/training-plans/{planId}` and `GET /api/horses/{horseId}/training-plans`.
2. **Expected Behavior:** Returns HTTP 200 with training plan and phases (scoped to owned horse for `HORSE_OWNER`).
3. **Failure Condition:** HTTP 403 Forbidden.

### Test Case 2: Schedule Evaluation & Auto-Completion (FR05 ↔ FR07)
1. **Action:** As `HEAD_TRAINER`, publish a schedule for a horse. Then call `POST /api/training-evaluations` providing the `trainingScheduleId`.
2. **Expected Behavior:** Returns HTTP 201 with `trainingScheduleId` in response; checking `GET /api/training-schedules/calendar` shows `completedAt` timestamp populated.
3. **Failure Condition:** Schedule remains uncompleted (`completedAt == null`) or duplicate evaluation on the same schedule is allowed.

### Test Case 3: Fitness Dashboard Phase Filter (FR03)
1. **Action:** Call `GET /api/fitness/dashboard?phaseId={phaseId}`.
2. **Expected Behavior:** Returns HTTP 200 with `filters.phaseId` matching, data points scoped only to evaluations under schedules of that phase, and horse summary scoped to that phase's horse.
3. **Failure Condition:** HTTP 500 error or evaluations from other phases returned.
