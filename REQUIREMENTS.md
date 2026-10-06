# REQUIREMENTS: Training Time Rule Change -- Impact Analysis

> **Date:** 2026-10-02
> **Status:** ANALYSIS ONLY -- awaiting project owner approval before any implementation.

---

## 1. Summary of the New Rule

| Aspect | Old rule (current code) | New rule (project owner) |
| :--- | :--- | :--- |
| Operating window | 06:00 to 24:00 (midnight next day) | 05:00 to 11:00 (same day) |
| Slot model | 18 x 60-minute slots per lane per day | No slots. One session fills the entire 6-hour window |
| Trainer input | Picks a start time + end time (any `Instant`) | Picks a DATE only. System derives 05:00-11:00 |
| Track exclusivity | Per lane per slot (multiple horses on different lanes simultaneously) | TBD -- see open questions |

The 60-minute slot grid, 06:00 opens_at, 00:00 closes_at (closes_next_day = true), and the 18-slot-per-day model are all superseded.

---

## 2. Impact Table

### 2.1 Database

| # | File / Object | Current Behavior | Required Change | Risk |
| :--- | :--- | :--- | :--- | :--- |
| D1 | `racetracks.opens_at` column (default `'06:00:00'`) | Defaults to 06:00. Used by `RacetrackService.slots()` to start the slot loop. | Change default to `'05:00:00'`. | Low |
| D2 | `racetracks.closes_at` column (default `'00:00:00'`) | Defaults to midnight (next day). | Change default to `'11:00:00'`. | Low |
| D3 | `racetracks.closes_next_day` column (default `1`) | True because midnight is the next calendar day. | Change default to `0` (11:00 is same day). | Low |
| D4 | `racetracks.slot_minutes` column (default `60`) | 60-minute slots. | Column becomes meaningless. Either drop it, set to `360`, or keep as legacy. See open question Q1. | Medium |
| D5 | `ck_racetracks_slot_minutes` CHECK constraint | `CHECK (slot_minutes = 60)` | Must be dropped or changed. If slot_minutes is retained as `360`, change to `CHECK (slot_minutes = 360)`. | Medium |
| D6 | `ck_racetracks_operating_hours` CHECK constraint | `CHECK (opens_at = '06:00:00' AND closes_at = '00:00:00' AND closes_next_day = 1)` | Must be dropped and recreated: `CHECK (opens_at = '05:00:00' AND closes_at = '11:00:00' AND closes_next_day = 0)`. Or, if configurable per track, remove the CHECK entirely. See Q2. | High |
| D7 | Default constraints `df_racetracks_opens_at`, `df_racetracks_closes_at`, `df_racetracks_closes_next_day`, `df_racetracks_slot_minutes` | Set defaults matching old values. | Update all four defaults to new values. | Low |
| D8 | `training_schedules.scheduled_start` / `scheduled_end` columns (`DATETIMEOFFSET NOT NULL`) | Trainer picks arbitrary start and end times (any time within the operating window). | These columns are still needed to store the computed 05:00 and 11:00 instants for the chosen date. The service layer must derive them from the date alone. | Medium |
| D9 | `ck_training_schedules_dates` CHECK | `CHECK (scheduled_start < scheduled_end)` | No change needed. 05:00 < 11:00 always holds. | None |
| D10 | Trigger `tr_training_schedules_no_staff_overlap` | Prevents overlapping schedules for the same staff member using `scheduled_start < s.scheduled_end AND scheduled_end > s.scheduled_start`. | Still correct, but now all sessions on the same date share the exact same 05:00-11:00 window, so a staff member can only have ONE schedule per day. Trigger will fire if a second schedule is created for the same date and staff. | Medium |
| D11 | Index `idx_training_schedules_staff_time` | On `(assigned_staff_id, scheduled_start, scheduled_end)` | Still useful. No change. | None |
| D12 | Index `idx_training_schedules_horse_time` | On `(horse_id, scheduled_start)` | Still useful. No change. | None |
| D13 | `trial_run_bookings.booked_start` / `booked_end` columns | Store the specific 60-minute slot instant. | Under the new model there is only one 6-hour window. These columns can store the fixed 05:00-11:00 instants. See Q3 about whether trial_run_bookings are still needed or become a simpler per-lane-per-date reservation. | High |
| D14 | `ck_trial_run_bookings_dates` CHECK | `CHECK (booked_start < booked_end)` | No change needed (05:00 < 11:00). | None |
| D15 | `uq_trial_run_bookings_schedule` UNIQUE | `UNIQUE (training_schedule_id)` -- one booking per schedule. | No change needed. | None |
| D16 | Trigger `tr_trial_run_bookings_no_lane_overlap` | Prevents overlapping bookings on the same lane. | Still works, but now every booking on the same date occupies the same 6-hour window, so the trigger effectively makes each lane exclusive for the entire day. One horse per lane per day. | Medium |
| D17 | `daily_tasks` table (migrated to `module_records`) | Contains `scheduled_start` / `scheduled_end` (`DATETIMEOFFSET`). Has a `training_schedule_id` FK. | The old `daily_tasks` table was dropped and merged into `module_records` in migration 12. Current care tasks use `scheduledDate` + `scheduledTime` in JSON (`data_json`). No direct dependency on the training slot grid. | Low |
| D18 | `horse_daily_routines` table (migrated to `module_records`) | Uses `start_time TIME` / `end_time TIME` for daily activity windows. | Was also migrated into `module_records`. These routines (turnout, grooming) are independent of the training window. No change required. | None |
| D19 | `telemetry_sessions` | FK `training_schedule_id`, columns `started_at` / `ended_at`. | No schema change needed. A telemetry session can still link to the training schedule and record its own timestamps within the 05:00-11:00 window. | None |
| D20 | `training_evaluations` | `session_date DATE`, `duration_minutes INT`, FK `training_schedule_id`. | `session_date` is unaffected (still a date). `duration_minutes` was manually entered; under the new rule the window is fixed at 360 minutes but the actual training duration may differ. See Q4. | Low |
| D21 | Trigger `tr_training_schedules_plan_and_lock` | Validates horse matches plan and blocks HIGH intensity when horse is locked. | No time-related change. Still works. | None |
| D22 | Trigger `tr_training_plan_phases_no_overlap` | Uses `start_date`/`end_date` (`DATE` columns). | No change. Phases are date ranges, not time-of-day. | None |
| D23 | Existing rows in `racetracks` | Any existing track rows have `opens_at='06:00:00'`, `closes_at='00:00:00'`, `closes_next_day=1`, `slot_minutes=60`. | Must be updated by migration script. See Section 3. | Medium |
| D24 | Existing rows in `training_schedules` | Existing schedules have arbitrary `scheduled_start` / `scheduled_end` values within the old 06:00-24:00 window. | Old rows are historical records. They should be preserved as-is. New validation applies only to new rows. See Q5. | Medium |
| D25 | Existing rows in `trial_run_bookings` | Existing bookings have 60-minute windows. | Historical records. Preserve as-is. | Medium |

### 2.2 Backend Services

| # | File / Method | Current Behavior | Required Change | Risk |
| :--- | :--- | :--- | :--- | :--- |
| B1 | `RacetrackService.slots()` (lines 56-82) | Generates 18 x 60-min slots per lane by looping from `opensAt` to `closingDateTime` stepping by `slotMinutes`. Returns `List<TrackSlotResponse>`. | This entire method must be reworked or replaced. Under the new model there is one "slot" per lane per day (the full 05:00-11:00 window). The loop becomes trivial: one entry per active lane. Alternatively, retire the slot grid entirely and use a simpler availability query. | High |
| B2 | `RacetrackService.validateSlotStart()` (lines 126-135) | Enforces that slot start is on the hour, within 06:00-24:00. Hardcoded error message: `"Slot phai nam trong gio mo cua 06:00-24:00"`. | Remove this method entirely if slots are eliminated. If a booking still needs a date, validation reduces to checking that the date is valid and the track is active. | High |
| B3 | `RacetrackService.book()` (lines 85-108) | Takes `CreateTrialRunBookingRequest(scheduleId, laneId, date, startTime)`. Computes `end = start + slotMinutes`. Validates booking fits within schedule window. Checks horse and lane overlap. | Request no longer needs `startTime`; only `date` and `laneId`. The booking start/end become the fixed 05:00-11:00 for that date. The containment check (`start >= schedule.scheduledStart && end <= schedule.scheduledEnd`) becomes trivially true since both are 05:00-11:00 on the same date. Horse/lane overlap checks still apply. | High |
| B4 | `RacetrackService.closingDateTime()` (lines 137-139) | Uses `closesNextDay` to decide whether to add a day. | With `closesNextDay = false` and `closesAt = 11:00`, this naturally returns `date.atTime(11:00)`. Method still works correctly but may benefit from simplification. | Low |
| B5 | `TrainingScheduleService.create()` (lines 59-106) | Accepts `CreateTrainingScheduleRequest` with `scheduledStart` (Instant) and `scheduledEnd` (Instant). Validates `start < end`, phase date range, staff overlap, horse overlap, training lock. | Request should accept `LocalDate trainingDate` instead of `scheduledStart`/`scheduledEnd`. Service derives `scheduledStart = date.atTime(05:00).atZone(clubZone).toInstant()` and `scheduledEnd = date.atTime(11:00).atZone(clubZone).toInstant()`. All overlap checks remain the same. | High |
| B6 | `TrainingScheduleService.calendar()` and `mine()` | Query by `from`/`to` date range. Convert dates to Instants at UTC midnight boundaries. | The date-to-Instant conversion should use 05:00 club time as the day boundary instead of UTC midnight, to correctly scope sessions that are 05:00-11:00 in club time. | Medium |
| B7 | `TrainingEvaluationService.create()` (lines 57-100) | Accepts `durationMinutes` as free input. Links evaluation to schedule via `trainingScheduleId`. Sets `schedule.completedAt`. | No direct time model dependency. `durationMinutes` remains a trainer-entered observation. However, `sessionDate` should align with the training date. See Q4. | Low |
| B8 | `TelemetryService.enter()` (lines 71-91) | Accepts `startedAt`/`endedAt` (`OffsetDateTime`). Optionally links to `trainingScheduleId`. | No structural change needed. Telemetry readings can occur at any time during the 05:00-11:00 window. The schedule link is by FK, not by time containment. | Low |
| B9 | `DailyAutomationJob.runAtSixEveryDay()` (line 17) | Cron: `0 0 6 * * *` -- runs at 06:00 club time. | 06:00 is now inside the training window (05:00-11:00). The job creates preventive care reminders and owner reports. These are not training-related, so no functional conflict, but if the intent was "run before training starts" the cron should change to `0 0 5 * * *` or earlier. See Q6. | Low |
| B10 | `TrainingLockService` | Blocks HIGH intensity schedules when horse is locked. No time-of-day dependency. | No change needed. | None |
| B11 | `CareTaskService` (lines 34-63) | Stores `scheduledDate` + `scheduledTime` in JSON. Overdue check uses `LocalDate.now()` and `LocalTime.now()`. | No change needed. Care tasks are independent of the training slot grid. The frontend auto-creates a RECOVERY task at `scheduledEnd` time; that time will now always be 11:00. | Low |
| B12 | `FitnessDashboardService.get()` | Queries evaluations by date range. No dependency on time-of-day. | No change needed. | None |
| B13 | `TrainingPlanService` | Manages phases with `startDate`/`endDate` (DATE, not time). | No change needed. | None |
| B14 | `TrainingQueueService` | Manages horse onboarding queue. No time-of-day dependency. | No change needed. | None |

### 2.3 Backend DTOs

| # | DTO | Current Fields | Required Change | Risk |
| :--- | :--- | :--- | :--- | :--- |
| T1 | `CreateTrainingScheduleRequest` | `@NotNull Instant scheduledStart`, `@NotNull Instant scheduledEnd` | Replace with `@NotNull LocalDate trainingDate`. Remove `scheduledStart`/`scheduledEnd`. | High |
| T2 | `CreateTrialRunBookingRequest` | `@NotNull LocalDate date`, `@NotNull LocalTime startTime` | Remove `startTime`. Keep `date`, `laneId`, `trainingScheduleId`. | Medium |
| T3 | `TrackSlotResponse` | `laneId, laneNumber, date, startTime, endTime, endsNextDay, available, bookingId, trainingScheduleId` | Simplify to a per-lane-per-date availability response: `laneId, laneNumber, date, available, bookingId, trainingScheduleId`. Remove `startTime`, `endTime`, `endsNextDay`. | High |
| T4 | `RacetrackResponse` | Includes `opensAt`, `closesAt`, `closesNextDay`, `slotMinutes`. | Still useful for UI display. Values will reflect new 05:00/11:00/false configuration. `slotMinutes` can be set to `360` or removed from the response. See Q1. | Low |
| T5 | `TrainingScheduleResponse` | `scheduledStart` (Instant), `scheduledEnd` (Instant) | Still returned, but values will always be 05:00 and 11:00 of the training date in UTC. Consider adding `trainingDate` (LocalDate) for convenience. | Low |
| T6 | `TrialRunBookingResponse` | `bookedStart` (Instant), `bookedEnd` (Instant) | Values will always be the full 05:00-11:00 window. Consider adding `trainingDate` for convenience. | Low |
| T7 | `CreateTrainingEvaluationRequest` | `@NotNull @PastOrPresent LocalDate sessionDate`, `@Min(1) int durationMinutes` | No structural change. `durationMinutes` remains trainer-entered. | None |
| T8 | `CreateRacetrackRequest` | `name`, `location`, `laneCount` -- does not include time/slot config. | No change. Defaults come from the entity/DB. | None |

### 2.4 Backend Entities

| # | Entity | Current Defaults | Required Change | Risk |
| :--- | :--- | :--- | :--- | :--- |
| E1 | `Racetrack.java` (lines 24-31) | `opensAt = LocalTime.of(6, 0)`, `closesAt = LocalTime.MIDNIGHT`, `closesNextDay = true`, `slotMinutes = 60` | Change to `opensAt = LocalTime.of(5, 0)`, `closesAt = LocalTime.of(11, 0)`, `closesNextDay = false`. Set `slotMinutes = 360` or remove the field. | Medium |
| E2 | `TrainingSchedule.java` | `scheduledStart` / `scheduledEnd` are `Instant` fields. | No schema change. Service layer populates from date. | None |
| E3 | `TrialRunBooking.java` | `bookedStart` / `bookedEnd` are `Instant` fields. | No schema change. Values derived from date + track config. | None |

### 2.5 Backend Tests

| # | Test Class | What It Tests | Required Change | Risk |
| :--- | :--- | :--- | :--- | :--- |
| X1 | `RacetrackServiceTest.generatesExactlyEighteenOneHourSlotsPerActiveLane()` | Asserts 18 slots from 06:00 to midnight. Checks first slot = 06:00-07:00, last = 23:00-00:00. | Must be rewritten to assert 1 slot per lane covering 05:00-11:00 (or whatever the new slot response shape becomes). | High |
| X2 | `RacetrackServiceTest.bookingAtElevenPmEndsAtMidnightAndUsesConfiguredClubTimeZone()` | Books at 23:00 ICT, asserts 1-hour duration, checks UTC conversion. | Must be rewritten for the new booking model (full-day booking, no startTime). | High |
| X3 | `TrainingScheduleServiceTest.rejectsHighIntensityScheduleWhileHorseIsLocked()` | Uses `scheduledStart = "2026-09-16T01:00:00Z"`, `scheduledEnd = "2026-09-16T02:00:00Z"`. | Request shape changes from Instants to LocalDate. Mock data must use `LocalDate.of(2026, 9, 16)`. | Medium |
| X4 | `TrainingEvaluationServiceTest` (all tests) | Uses `sessionDate = LocalDate`, `durationMinutes = 42/45`. References schedule with 1-hour window. | Schedule mock should use 05:00-11:00 window. `durationMinutes` stays as-is. | Low |
| X5 | `DailyAutomationJobTest` | Tests preventive care reminder. No slot dependency. | No change needed unless the cron time moves to 05:00. | None |
| X6 | `CareTaskServiceTest` | Tests care task creation with `scheduledDate` + `scheduledTime`. | No change needed. | None |
| X7 | `TrainingLockServiceTest` | Tests training lock timing. No slot dependency. | No change needed. | None |
| X8 | `FitnessDashboardServiceTest` | Tests date-range queries. No slot dependency. | No change needed. | None |
| X9 | `TrainingPlanServiceTest` | Tests phase overlap using dates. | No change needed. | None |

### 2.6 Frontend

| # | Component / File | Current Behavior | Required Change | Risk |
| :--- | :--- | :--- | :--- | :--- |
| F1 | `TrainingSchedulePage.jsx` -- `CreateScheduleModal` (lines 319-521) | Two `<input type="datetime-local">` fields for `scheduledStart` and `scheduledEnd`. | Replace with a single `<input type="date">` for the training date. Remove start/end time pickers. | High |
| F2 | `TrainingSchedulePage.jsx` -- Slot dropdown (lines 354-366, 486-504) | Fetches `/api/racetracks/{id}/slots?date=` and renders a `<select>` of `Lane X - HH:MM` options. Disabled slots show `(Da dat)`. | Replace slot picker with a simpler lane-availability picker. Each lane is either available or booked for the date. No time dimension. | High |
| F3 | `TrainingSchedulePage.jsx` -- Weekly calendar grid (lines 111-190) | Displays schedule cards in 7 day columns. Cards show `formatTime(scheduledStart) - formatTime(scheduledEnd)` with a clock icon. | Time display on cards should show "05:00 - 11:00" (fixed) or simply the date without a time range since it is always the same. Consider removing the clock icon + time display or showing just "Buoi sang" (Morning session). | Medium |
| F4 | `TrainingSchedulePage.jsx` -- Weekly summary table (lines 202-247) | Columns include `formatTime(scheduledStart) - formatTime(scheduledEnd)`. | Same as F3. Remove or simplify time column. | Low |
| F5 | `TrainingSchedulePage.jsx` -- `ScheduleCard` (lines 275-305) | Shows time range with clock icon. | Simplify or remove time display. | Low |
| F6 | `TrainingSchedulePage.jsx` -- `EvaluationModal` (lines 523-593) | Pre-fills `sessionDate` from `schedule.scheduledStart.split('T')[0]`. `durationMinutes` defaults to 60. | `sessionDate` derivation still works. `durationMinutes` default should change to 360 or remain free-form. See Q4. | Low |
| F7 | `TrainingSchedulePage.jsx` -- Auto-create care task (lines 400-413) | On schedule creation, creates a RECOVERY care task at `scheduledEnd` time. | `scheduledEnd` will always be 11:00 club time. The auto-generated care task will always be at 11:00. This is probably correct. | Low |
| F8 | `TrainingPlansSection.jsx` -- `ScheduleModal` (lines 152-218) | Also has two `<input type="datetime-local">` for quick schedule creation from a phase. | Same as F1: replace with a single date picker. | High |
| F9 | `FitnessPage.jsx` -- `EvaluationModal` (lines 303-315) | Standalone evaluation form. Uses `sessionDate` (date picker) and `durationMinutes`. | No change needed. This is an independent evaluation entry. | None |
| F10 | `StableCarePage.jsx` -- `CareTasksView` (lines 854-1228) | Care tasks use `scheduledDate` + `scheduledTime`. No dependency on training slot grid. | No change needed. | None |
| F11 | `StableCarePage.jsx` -- Daily routines / Nutrition views | Use `startTime`/`endTime` (`TIME`) and `feedingTime` (`TIME`). Independent of training. | No change needed. | None |
| F12 | `api.js` | API calls to all endpoints. | Update any call that sends `scheduledStart`/`scheduledEnd` (Instant) to send `trainingDate` (LocalDate) instead. Update slot API consumption. | Medium |

### 2.7 Documentation

| # | File | Required Change | Risk |
| :--- | :--- | :--- | :--- |
| O1 | `BACKEND/README.md` (line 554) | States "18 slot, 60 phut, 06:00-24:00". Must be updated. | Low |
| O2 | `DATABASE/README.md` (line 25) | States "06:00-24:00, slot 60 phut". Must be updated. | Low |
| O3 | `PROJECT_RECONNAISSANCE.md` (lines 176, 482, 970) | References "18 slot" and "06:00-24:00" multiple times. Must be updated. | Low |
| O4 | `BUSINESS_DECISIONS_PENDING.md` | No current mention of training time rules. Add entry if any aspect remains undecided. | Low |

---

## 3. Database Migration Plan

### 3.1 New Idempotent Migration Script (e.g. `15_training_window_change.sql`)

```
1. Drop CHECK constraint ck_racetracks_operating_hours
2. Drop CHECK constraint ck_racetracks_slot_minutes
3. Drop DEFAULT constraints df_racetracks_opens_at, df_racetracks_closes_at,
   df_racetracks_closes_next_day, df_racetracks_slot_minutes
4. Add new DEFAULT constraints:
   - opens_at DEFAULT '05:00:00'
   - closes_at DEFAULT '11:00:00'
   - closes_next_day DEFAULT 0
   - slot_minutes DEFAULT 360  (or drop the column entirely -- see Q1)
5. Add new CHECK constraints:
   - ck_racetracks_operating_hours CHECK (opens_at = '05:00:00'
     AND closes_at = '11:00:00' AND closes_next_day = 0)
   - ck_racetracks_slot_minutes CHECK (slot_minutes = 360)
     (or drop the constraint if the column is dropped)
6. UPDATE existing rows:
   UPDATE dbo.racetracks
   SET opens_at = '05:00:00',
       closes_at = '11:00:00',
       closes_next_day = 0,
       slot_minutes = 360;
```

All steps must be idempotent (use `IF EXISTS` / `IF NOT EXISTS` guards).

### 3.2 Update `racehorse_database_full.sql`

The full schema file must reflect the same changes:
- Lines 1730-1776: Change defaults and CHECK constraints to match the new values.

### 3.3 Historical Data Preservation

Existing rows in `training_schedules` and `trial_run_bookings` with old 06:00-24:00 window times should be preserved. They represent completed or cancelled historical sessions. The new constraints and defaults only affect new rows and the `racetracks` configuration table.

---

## 4. API Changes

### 4.1 Request Shape Changes

| Endpoint | Current Request | New Request |
| :--- | :--- | :--- |
| `POST /api/training-schedules` | `{ trainingPlanPhaseId, assignedStaffId, taskName, scheduledStart: Instant, scheduledEnd: Instant }` | `{ trainingPlanPhaseId, assignedStaffId, taskName, trainingDate: LocalDate }` |
| `POST /api/trial-run-bookings` | `{ trainingScheduleId, racetrackLaneId, date: LocalDate, startTime: LocalTime }` | `{ trainingScheduleId, racetrackLaneId, date: LocalDate }` |
| `GET /api/racetracks/{id}/slots?date=` | Returns `List<TrackSlotResponse>` with 18 entries per lane | Returns a simpler availability list: one entry per lane (available or booked) |

### 4.2 Response Shape Changes

| Response DTO | Field Changes |
| :--- | :--- |
| `TrackSlotResponse` | Remove `startTime`, `endTime`, `endsNextDay`. Add optional `trainingDate`. `available` remains. |
| `RacetrackResponse` | `slotMinutes` becomes 360 or is removed. `opensAt` becomes 05:00, `closesAt` becomes 11:00, `closesNextDay` becomes false. |
| `TrainingScheduleResponse` | No structural change. `scheduledStart`/`scheduledEnd` will always be the same-day 05:00/11:00. Consider adding a derived `trainingDate` field. |
| `TrialRunBookingResponse` | No structural change. `bookedStart`/`bookedEnd` will always be 05:00/11:00. Consider adding `trainingDate`. |

### 4.3 Tests to Change or Add

| Test | Action |
| :--- | :--- |
| `RacetrackServiceTest.generatesExactlyEighteenOneHourSlotsPerActiveLane` | Rewrite: assert 1 entry per lane, full 05:00-11:00 window. |
| `RacetrackServiceTest.bookingAtElevenPmEndsAtMidnight...` | Rewrite: booking for a date produces 05:00-11:00 UTC+7 instants. |
| `TrainingScheduleServiceTest.rejectsHighIntensityScheduleWhileHorseIsLocked` | Update request to use `trainingDate` instead of `scheduledStart`/`scheduledEnd`. |
| NEW test: `rejectsSecondScheduleForSameHorseOnSameDate` | The horse overlap check should reject two schedules on the same date since all sessions span 05:00-11:00. |
| NEW test: `rejectsSecondScheduleForSameStaffOnSameDate` | The staff overlap check should reject two schedules on the same date. |
| NEW test: `slotsReturnOneEntryPerLane` | Assert the new simplified slot response. |
| NEW test: `bookingWithoutStartTimeUsesFullWindow` | Assert that `bookedStart` is 05:00 and `bookedEnd` is 11:00 in club timezone. |

---

## 5. Frontend Changes by Screen

### 5.1 Training Schedule Page (`TrainingSchedulePage.jsx`)

1. **Schedule creation form** -- Replace two `datetime-local` inputs with one `date` input. Remove slot/time picker.
2. **Slot dropdown** -- Replace the 18-slot-per-lane dropdown with a simple lane availability selector (checkboxes or a dropdown of available lanes for the chosen date).
3. **Calendar cards** -- Remove or simplify the `HH:MM - HH:MM` time display. All sessions are 05:00-11:00.
4. **Weekly summary table** -- Simplify or remove the time column.
5. **Evaluation modal** -- `durationMinutes` default changes from 60 to a value appropriate for a 6-hour window.

### 5.2 Training Plans Section (`TrainingPlansSection.jsx`)

1. **Quick schedule modal** -- Replace two `datetime-local` inputs with one `date` input.

### 5.3 Fitness Page (`FitnessPage.jsx`)

No changes needed. The standalone evaluation form is date-based.

### 5.4 Stable Care Page (`StableCarePage.jsx`)

No changes needed. Care tasks are independent of the slot grid.

### 5.5 Admin Pages (`AdminPages.jsx`)

No changes needed. No direct dependency on training time model.

---

## 6. Conflicts with Previously Agreed Rules

| # | Previous Rule | Source | Conflict |
| :--- | :--- | :--- | :--- |
| C1 | "Every racetrack operates 06:00 through midnight in 60-minute slots" | `10_confirmed_business_rules.sql` line 13, `BACKEND/README.md` line 554 | Directly superseded. The comment, defaults, and CHECK constraints all encode 06:00-24:00 / 60 min. |
| C2 | `ck_racetracks_slot_minutes CHECK (slot_minutes = 60)` | `10_confirmed_business_rules.sql` line 47 | Superseded. |
| C3 | `ck_racetracks_operating_hours CHECK (opens_at = '06:00:00' AND ...)` | `10_confirmed_business_rules.sql` line 56-57 | Superseded. |
| C4 | Racetrack entity Java defaults: `opensAt = LocalTime.of(6, 0)`, `slotMinutes = 60` | `Racetrack.java` lines 24-31 | Superseded. |
| C5 | `validateSlotStart()` error message: "Slot phai nam trong gio mo cua 06:00-24:00" | `RacetrackService.java` line 134 | Superseded. Method and message must be rewritten or removed. |
| C6 | Test assertion: "exactly eighteen one-hour slots per active lane" | `RacetrackServiceTest.java` line 55 | Superseded. |
| C7 | Test assertion: slot starts at 06:00 and last slot ends at midnight | `RacetrackServiceTest.java` lines 56-60 | Superseded. |
| C8 | Cron job at 06:00 (`0 0 6 * * *`) | `application.properties` line 25 | Not directly conflicting (the job does non-training work), but 06:00 is now mid-session. See Q6. |

---

## 7. Open Questions for the Project Owner

1. **Q1 -- `slot_minutes` column:** Should `slot_minutes` be set to 360 (to represent the 6-hour window as a single "slot"), dropped entirely, or kept at 60 as a legacy artifact? Dropping it is cleanest but requires entity and response changes.

2. **Q2 -- Configurable per track?** Should 05:00 and 11:00 be hard-coded constraints (like the current 06:00/24:00 CHECK), or should they be stored as configurable values per track? If different tracks could have different windows in the future, the CHECK constraint should not pin exact values.

3. **Q3 -- Trial run bookings under the new model:** If the training session fills the entire window and each lane is exclusive per day, does the concept of a "trial run booking" still make sense? Currently a booking reserves one 60-minute slot on one lane. Under the new rule, it would reserve a lane for the entire 6-hour window. Should the booking table be kept (with full-window times) or should lane assignment be a simpler per-date boolean on `training_schedules`?

4. **Q4 -- `durationMinutes` in evaluations:** Currently the trainer enters an observed duration. With a fixed 6-hour window, should `durationMinutes` still be freely entered (the horse may not train the full 6 hours), or should it default to 360 and not be editable?

5. **Q5 -- Migration of old data:** Old `training_schedules` rows have `scheduled_start`/`scheduled_end` that do not match the 05:00-11:00 window. Options:
   - (a) Leave them as-is. They are historical and should display with their original times.
   - (b) Backfill them to 05:00-11:00 on their respective dates. This loses the original time information.
   - Recommendation: Option (a). Which does the project owner prefer?

6. **Q6 -- DailyAutomationJob cron timing:** The cron currently runs at 06:00, which is now one hour into the training window. Should it be moved to 04:30 or 05:00 to complete before training starts, or is 06:00 acceptable since the job (preventive care reminders and owner reports) is not training-related?

7. **Q7 -- One horse per track per day or one horse per lane per day?** The new rule says the session fills the whole window. If a track has multiple lanes, can multiple horses train simultaneously on different lanes (one horse per lane per day), or is the track exclusive to one horse for the entire day?

8. **Q8 -- One schedule per horse per day?** Since all sessions are 05:00-11:00, the existing horse overlap check will automatically reject a second session on the same date. Is this the intended behavior (maximum one training session per horse per day)?

9. **Q9 -- Staff overlap:** Similarly, since all sessions are 05:00-11:00, a staff member can only supervise one session per day. Is this intended, or should one staff member be able to supervise multiple horses during the same window?

10. **Q10 -- Recovery care task timing:** The frontend auto-creates a RECOVERY care task at `scheduledEnd` time. Under the new rule, this will always be 11:00. Is that correct?

11. **Q11 -- Training plan phase date validation:** The current code checks `scheduleDate is within [phase.startDate, phase.endDate]`. This check uses `LocalDate.ofInstant(scheduledStart, UTC)`. Under the new model, `scheduledStart` is 05:00 ICT (22:00 UTC previous day). If `phase.startDate = 2026-09-16` and the session is for Sep 16, the UTC date of `scheduledStart` would be Sep 15 -- failing the check. Should the date derivation use club timezone instead of UTC?

12. **Q12 -- What happens to the `TrackSlotResponse` endpoint name?** `GET /api/racetracks/{id}/slots?date=` implies multiple slots. Should the endpoint be renamed to something like `/api/racetracks/{id}/availability?date=`?

---

## 8. Suggested Implementation Order

| Step | Task | Dependencies |
| :--- | :--- | :--- |
| 0 | Resolve open questions Q1-Q12 with the project owner. | None |
| 1 | Write idempotent DB migration `15_training_window_change.sql`. | Q1, Q2 answers |
| 2 | Update `racehorse_database_full.sql` to match. | Step 1 |
| 3 | Update `Racetrack.java` entity defaults. | Step 1 |
| 4 | Refactor `CreateTrainingScheduleRequest` DTO (Instants to LocalDate). | None |
| 5 | Refactor `TrainingScheduleService.create()` to derive times from date. | Step 4 |
| 6 | Fix phase-date validation timezone bug (Q11). | Step 5 |
| 7 | Refactor `CreateTrialRunBookingRequest` (remove `startTime`). | None |
| 8 | Refactor `RacetrackService.slots()` and `book()`. Remove `validateSlotStart()`. | Steps 3, 7 |
| 9 | Simplify `TrackSlotResponse` or create new availability DTO. | Step 8 |
| 10 | Rewrite `RacetrackServiceTest` and `TrainingScheduleServiceTest`. Add new tests. | Steps 5, 8 |
| 11 | Update Frontend: `CreateScheduleModal` date picker. | Steps 4, 5 |
| 12 | Update Frontend: Lane availability picker (replaces slot dropdown). | Step 9 |
| 13 | Update Frontend: Calendar card display. | Step 11 |
| 14 | Update Frontend: `TrainingPlansSection.ScheduleModal`. | Step 4 |
| 15 | Update Frontend: `api.js` calls. | Steps 4, 7, 9 |
| 16 | Update all documentation (`README.md` files, `PROJECT_RECONNAISSANCE.md`). | All above |
| 17 | Run full test suite and manual smoke test. | All above |
