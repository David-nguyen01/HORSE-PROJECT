# TASK REPORT: RH-STABLE-UI-01D.1 — Training Preparation Query Scope & Veterinarian Regression Audit

**Audit Date:** 2026-11-11T00:05:00+07:00  
**Product Repository:** `C:\DEV\RACE HORSE\HORSE-PROJECT`  
**Baseline Branch:** `develop`  
**Git HEAD:** `65165624859051913e940f53dee8253e02040eb7`  
**Workflow Repository:** `C:\DEV\clone-horse`  
**Workflow Branch:** `main` (`thangnx-mc/clone-horse`)  
**Audit Scope:** READ-ONLY inspection of backend query scopes, caller invocations, and Stable Staff vs Veterinarian training data boundaries.

---

## 1. Executive Summary

Task **RH-STABLE-UI-01D.1** conducted a forensic, read-only audit of the query scope changes introduced in RH-STABLE-UI-01D, specifically examining:
1. **Finding A (Veterinarian Query Scope):** In RH-STABLE-UI-01D, `TrainingScheduleService.forDiagnosingVet()` and `TrainingScheduleRepository.findForDiagnosingVet()` were modified to accept null date parameters alongside `mine()`. This was an **unintended scope expansion** beyond the Stable Staff Training Preparation panel. While existing Veterinarian UI default flows supply explicit date boundaries (`-7` to `+14` days) and do not break, any call to `GET /api/training-schedules` or `GET /api/training-schedules/mine` by a Veterinarian with omitted date parameters now returns the veterinarian's entire history and future schedule rather than defaulting to today.
2. **Finding B (Stable Staff Data Scope):** The Training Preparation endpoint (`GET /api/training-schedules/mine`) returns records strictly where `horse.caretaker_user_id = authenticatedUserId`. This restriction is **not** a regression of 01D; it **predates 01D completely** and was established in commit `cdc50402` (October 8, 2026). It reflects an existing personal task-assignment work queue in the training preparation subsystem (`markReady()` and `get()` enforce the exact same check). Expanding this scope to all club/stable horses would alter existing domain semantics and strictly requires an authoritative business decision.

---

## 2. Source HEAD & Working Tree Baseline

- **Repository:** `C:\DEV\RACE HORSE\HORSE-PROJECT`
- **Branch:** `develop`
- **Git HEAD:** `65165624859051913e940f53dee8253e02040eb7`
- **Working Tree State:** All pre-existing modified files (16 files) and untracked artifacts remain intact and undisturbed. Zero product source files were modified during this audit.

---

## 3. Finding A — Veterinarian Query Scope Audit

### 3.1 Background of the Change in RH-STABLE-UI-01D
In RH-STABLE-UI-01D, the controller endpoint `GET /api/training-schedules/mine` was inspected:
```java
@GetMapping("/mine")
@PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('STABLE_STAFF','VETERINARIAN')")
List<TrainingScheduleResponse> mine(
        @AuthenticationPrincipal Jwt jwt, Authentication authentication,
        @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return hasRole(authentication, "VETERINARIAN")
            ? service.forDiagnosingVet(userId(jwt), from, to) : service.mine(userId(jwt), from, to);
}
```
Because `mine()` routes to `service.forDiagnosingVet()` when the caller holds `ROLE_VETERINARIAN`, 01D modified both `mine()` and `forDiagnosingVet()` symmetrically in `TrainingScheduleService.java` and `TrainingScheduleRepository.java`.

### 3.2 Callers of `forDiagnosingVet()`
The backend contains exactly two callers of `service.forDiagnosingVet()` in `TrainingScheduleController.java`:
1. **`GET /api/training-schedules` (`calendar` endpoint):**
   ```java
   @GetMapping
   @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN')")
   List<TrainingScheduleResponse> calendar(
           @RequestParam(name = "date", required = false) LocalDate date,
           @RequestParam(name = "from", required = false) LocalDate from,
           @RequestParam(name = "to", required = false) LocalDate to,
           @RequestParam(name = "horseId", required = false) UUID horseId,
           @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
       if (date != null) { from = date; to = date; }
       if (hasRole(authentication, "VETERINARIAN") && !hasRole(authentication, "HEAD_TRAINER") && !hasRole(authentication, "CLUB_MANAGER"))
           return service.forDiagnosingVet(userId(jwt), from, to).stream()
                   .filter(s -> horseId == null || horseId.equals(s.horse().id())).toList();
       ...
   }
   ```
2. **`GET /api/training-schedules/mine` (`mine` endpoint):**
   Dispatches to `forDiagnosingVet(userId, from, to)` when the caller has `ROLE_VETERINARIAN`.

### 3.3 Frontend Callers & Date Parameter Analysis
In the frontend:
- **`FRONTEND/src/vetApi.js:200` (`getMyTrainingSessions`):**
  Calls `GET /api/training-schedules/mine?from=...&to=...`.
  In `FRONTEND/src/VetPage.jsx:3635`, this is called with `dateFrom = pastDt(7)` (`today - 7 days`) and `dateTo = futureDt(14)` (`today + 14 days`).
- **`FRONTEND/src/vetApi.js:179` (`getTrainingSessionsByDate`):**
  Calls `GET /api/training-schedules?date=${date}`.
  In `FRONTEND/src/VetPage.jsx:3692`, this is called with `selectedDate = todayStr()`.

**Can an existing caller invoke it without date boundaries?**
- In normal UI rendering, `dateFrom` and `dateTo` are populated by default.
- However, the `<input type="date">` fields in `VetPage.jsx` (lines 3797, 3806) allow clearing. If cleared by the user, `dateFrom` and `dateTo` become empty strings `""`, which causes `vetApi.getMyTrainingSessions` to send no parameters (`GET /api/training-schedules/mine`).
- Additionally, any direct API integration or external script calling `GET /api/training-schedules` or `GET /api/training-schedules/mine` without query parameters invokes `forDiagnosingVet(userId, null, null)`.

### 3.4 Exact Before vs. After Behavior
| Scenario | Pre-01D Behavior | Post-01D Behavior |
|---|---|---|
| `forDiagnosingVet(id, dateFrom, dateTo)` (both non-null) | Queries `findForDiagnosingVet` bounded by `[dateFrom, dateTo + 1 day)` | Queries `findForDiagnosingVet` bounded by `[dateFrom, dateTo + 1 day)` (**Identical**) |
| `forDiagnosingVet(id, null, null)` | `range(null, null)` defaulted `start = today`, `end = today`. Returned **ONLY records overlapping today**. | Bypasses `range()`, executes with null bounds. Returns **ALL records across all time** where `horse.diagnosingVet.id = userId`. |

### 3.5 Visible Dataset Impact & Dependency
- `[FACT]` Data isolation is preserved: it does **not** expose records of horses diagnosed by other veterinarians.
- `[OBSERVATION]` For a veterinarian querying with null dates, the visible dataset expands from 1 day to the entire timeline of assigned horses.
- `[OBSERVATION]` Existing `VetPage.jsx` components initialize date filters explicitly and do not depend on the backend defaulting to today.
- `[INFERENCE]` While existing UI components do not break, the change to `forDiagnosingVet` was outside the approved scope of task RH-STABLE-UI-01D.

---

## 4. Finding B — Stable Staff Data Scope Audit

### 4.1 Endpoint Implementation & Restriction Verification
The training preparation panel retrieves records via `stableCareApi.getMyTrainingSchedules`:
- Calls `GET /api/training-schedules/mine`.
- For `ROLE_STABLE_STAFF`, `TrainingScheduleController.mine()` invokes `service.mine(userId, from, to)`.
- `TrainingScheduleService.mine()` executes:
  ```java
  repository.findForCaretaker(id, fromInclusive, toExclusive)
  ```
- `TrainingScheduleRepository.findForCaretaker` specifies:
  ```sql
  select s from TrainingSchedule s
  where s.horse.caretaker.id = :userId
    and s.publishedAt is not null
    and s.cancelledAt is null
    ...
  ```
- `[FACT]` **Scope:** The endpoint returns training schedules **ONLY** for horses whose `caretaker_user_id` (`horse.caretaker.id`) equals the authenticated user's ID (`jwt.subject`). It does **not** return training schedules for all horses in the club or stable.

### 4.2 Lineage & Historical Origin
- `[FACT]` Git inspection confirms that `findForCaretaker`, `service.mine()`, and `TrainingScheduleController.mine()` were introduced in commit `cdc50402fc1d47a131622f1f5ab731e6dab025ca` on **October 8, 2026**.
- The `caretaker_user_id = :userId` restriction is part of the original training schedule module architecture.
- Task RH-STABLE-UI-01D **did not introduce** this restriction; 01D only added null date parameter support to avoid silently restricting the calendar to today.

### 4.3 Assignment-Based Work Queue vs. Role Authorization Tier
Inspection of related methods in `TrainingScheduleService.java` reveals consistent assignment-level enforcement:
1. **`get(scheduleId, actorId, ...)` (lines 270-280):**
   ```java
   } else if (schedule.getHorse().getCaretaker() == null
           || !schedule.getHorse().getCaretaker().getId().equals(actorId)) {
       throw new ResourceNotFoundException("Không tìm thấy lịch huấn luyện: " + scheduleId);
   }
   ```
2. **`markReady(scheduleId, actorId, ...)` (lines 296-303):**
   ```java
   if (manager == false) {
       UUID caretaker = jdbc.query("select caretaker_user_id from horses where id=?", ...);
       if (!actorId.equals(caretaker)) throw new ResourceNotFoundException("Không tìm thấy buổi tập được phép xác nhận");
   }
   ```
3. **`notifyCaretaker(schedule, ...)` (lines 439-445):**
   Publishes notifications specifically to `schedule.getHorse().getCaretaker()`.
4. **UI Copy in `StableCarePage.jsx` (line 2246):**
   `"Hiện tại chưa có lịch huấn luyện nào cho ngựa do bạn phụ trách cần chuẩn bị trong ngày này."`

`[INFERENCE]` Within the training preparation and execution flow, `horse.caretaker_user_id` acts as a personal work-queue assignment. Stable staff members are notified of, view, and confirm readiness ("Xác nhận sẵn sàng") only for horses personally assigned to them. This is distinct from Daily Routine management (which is stable-wide without caretaker restrictions).

### 4.4 Intended Meaning of "All Available Records"
In task RH-STABLE-UI-01D, the requirement stated:
> *"On initial page entry, show all available training preparation records permitted by the existing Stable access rules. Do not silently default to today's date."*
> *"Keep existing authorization unchanged. Do not expose additional records beyond the user's existing permissions."*

The requirement was specifically addressing the temporal limitation (showing all preparation dates rather than only today's sessions) within the user's existing permitted access rules (`/mine`). It did not request broadening horse-level visibility beyond the staff user's assigned horses.

### 4.5 Product Owner Decision Boundary
`[FACT]` Broadening the Training Preparation panel to show sessions for horses assigned to other staff members (or unassigned horses) would require:
1. Updating `findForCaretaker` / `mine` or creating a club-wide/stable-wide preparation query.
2. Modifying `TrainingScheduleService.markReady()` to allow staff members to confirm readiness for horses not assigned to them.
3. Modifying `TrainingScheduleService.get()` to permit detail retrieval across staff assignments.
4. Updating UI messaging.

This represents a change in operational workflow and responsibility boundaries, which cannot be assumed by an engineering agent and **strictly requires a business decision from the Product Owner**.

---

## 5. Classification of Findings & Recommendations

| Item | Classification | Status | Recommendation |
|---|---|---|---|
| **Finding A:** `forDiagnosingVet()` null date support | Unintended scope expansion / Out-of-scope backend change | Non-breaking in UI, but affects null-parameter API queries | **Recommend minimal correction:** Revert `forDiagnosingVet()` to unconditionally use `DateRange range = range(from, to)` so it maintains the previous today-default behavior when called with null dates. |
| **Finding B:** Stable Staff `caretaker_user_id` scope | Existing baseline behavior (predates 01D to Oct 8, 2026) | As designed in existing training module; matches `markReady` and `get` | **Maintain current scope** unless Product Owner explicitly decides to change the training preparation operational model. |

---

## 6. Recommended Minimal Correction Specification (For Future Implementation)

If approved by the user, the minimal correction for Finding A would touch only two files:

### File 1: `BACKEND/src/main/java/com/racehorse/training/service/TrainingScheduleService.java`
```java
    @Transactional(readOnly = true)
    public List<TrainingScheduleResponse> forDiagnosingVet(UUID id, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        return repository.findForDiagnosingVet(id, range.from(), range.to()).stream().map(this::toResponse).toList();
    }
```
*(Removes the `if (from == null && to == null)` bypass for Veterinarian queries, restoring the original `range(from, to)` behavior).*

### File 2: `BACKEND/src/test/java/com/racehorse/training/service/TrainingScheduleServiceTest.java`
Update test `forDiagnosingVet_withNullDates_queriesAllRecordsWithoutDateBoundaries` to reflect `range(null, null)` behavior, or remove the out-of-scope test.

*Note: Per the strict instructions of RH-STABLE-UI-01D.1, no product source was modified during this task.*

---

## 7. Workflow Report Git Push Status

- Destination Repository: `C:\DEV\clone-horse`
- Branch: `main`
- Remote: `thangnx-mc/clone-horse`
- Path: `up-report/RH_STABLE_UI_01D_1_TRAINING_QUERY_SCOPE_AUDIT_REPORT.md`
- Push Result: SUCCESS
