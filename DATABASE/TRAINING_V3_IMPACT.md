# Training v3 database impact and approval report

Initial impact report dated 2026-10-05; its execution state and final validation were superseded on 2026-10-06 in the execution note below. The impact descriptions remain the record of the approved scripts.

## Verified inventory

Evidence: BACKEND/target/azure-schema-inventory.txt (read-only metadata plus active-ground count).
37 user tables including sysdiagrams, 36 excluding it: already above the cap of 30. No tables are added or dropped. Ten active grounds: no seed. No migration-history table identified; filenames do not prove execution.

Schedule staffing already nullable: preserve columns, history and staff/vet overlap triggers. Lane references stay in trial_run_bookings. Evaluation metrics already exist; intensity is decimal(5,1), entity expects (5,2). perceived_exertion remains NOT NULL, range 1–10. racetracks has no code. role_permissions already accepts CLAIM; no permission changes proposed. Any future permission insert must supply granted, changed_by_user_id, changed_at. Race JSON/status fits existing module_records: no race DDL.

## Script 21_training_v3_fields.sql

1. Database: add nullable schedule notes, session_status, ready_at, ready_by_user_id, started_at; status default/check and ready-user FK. Add nullable horse caretaker_user_id/diagnosing_vet_user_id and user FKs. Add nullable evaluation track_completion_seconds, injured, injury_location, injury_notes with positive timing check. Align intensity to decimal(5,2), rejecting overflow.
2. Java impact, planned after approval: TrainingSchedule, TrainingScheduleResponse, OwnerTrainingScheduleResponse, TrainingScheduleService, TrainingScheduleRepository, TrainingScheduleController, OwnerTrainingScheduleController; Horse/HorseRepository and new assignment controller/service/DTO contracts; TrainingEvaluation, CreateTrainingEvaluationRequest, TrainingEvaluationResponse, TrainingEvaluationService, TrainingEvaluationController; TrainingPlanService for nested projection/diagnosis/injury gates; HorseMeasurementService for injury clearing; RaceEventService for readiness; existing notification service for recipient/dedup integration. New class names are not represented as existing files.
3. Frontend: TrainingSchedulePage.jsx lifecycle/evaluation/emergency form; TrainingMetricsPage.jsx timing/injury display; App.jsx notification/owner routes; styles.css. No staff/vet UI or horse-profile edits.
4. Other roles/Flow 4: additive nullable fields preserve historical staffing; no wallet/care-task changes. Existing evaluation submission can fail until the approved form supplies perceived_exertion. DDL alone does not complete v3. Injury synchronization must later update evaluation create and HorseMeasurementService.updateHealth; operational status becomes ACTIVE only when clearing INJURED health while status is INJURED.
5. Backfill/rollback: noncancelled rows with completed_at become COMPLETED; remaining noncancelled rows PREPARING. Cancelled rows retain cancellation and NULL lifecycle. No inferred readiness/start or caretaker/vet/timing/injury backfill. Only enabled plan/lock trigger is temporarily suspended in the transaction during status backfill, then restored. Other triggers may reject historical conflicts and roll back the migration. Rollback 21 retains additive columns/data intentionally and restores intensity precision only if lossless: compatibility rollback, not physical schema restoration. Verify old application behavior before reverting deployment.
6. Idempotence: catalog guards for columns/default/constraints; NULL-only status backfill; precision guard; transaction rollback on failure. No duplicated assignments or seed.

actual_run_minutes exists in the entity and generic metric setter; current creation does not establish a track-completion meaning. Do not reuse it: track timing has a separate seconds column.

## Script 22_training_v3_rules.sql

1. Database: remove whole-ground exclusivity; replace lane trigger with same-club-date/overlap checks and lane/ground/time consistency. Replace plan/lock trigger: plan-horse match, valid active/unexpired plan when scheduling, horse/day conflict, injured/HIGH-lock guards when scheduling/starting. Historical lifecycle updates no longer fail solely because a phase expired. Keep staff/vet triggers. Evaluation expiry trigger was absent: stop if one now exists for fresh review, never drop an unreviewed definition.
2. Java: TrainingScheduleService/Repository, TrialRunBookingRepository, RacetrackService and TrainingEvaluationService must maintain matching checks and handle transaction conflicts. TrainingPlanService supplies plan-wide rest/expiry rules. Existing schedule/racetrack controllers retain authorization. Late evaluation validates original session date. No extra entity changes beyond 21.
3. Frontend: TrainingSchedulePage.jsx existing free-lane API shows only usable lanes, never another trainer's horse identity. No fake occupied bookings. Lifecycle controls follow after approval.
4. Roles/Flow 4: multiple horses can share a ground on distinct lanes; four lanes retained. Staff/vet overlap checks remain for populated historical rows. Locking reads can serialize reservations or deadlock; conflict handling and database concurrency tests remain required after approval.
5. Backfill/rollback: no booking rows changed. Rollback restores three captured Azure trigger definitions, refusing if overlapping sessions now share a ground. No automatic deletion/rescheduling. Captured message encoding is preserved. Rollback 22 precedes compatibility rollback 21.
6. Idempotence: CREATE OR ALTER, conditional DROP, transaction; no booking rewrite. Preflight stops if migration 21 is missing or an unexpected evaluation expiry trigger appears. Scripts reviewed as text, not executed/SQL Server syntax-validated.

## Completed no-DDL changes per file

| Files | Changes |
|---|---|
| FRONTEND/src/App.jsx | Trainer/owner metrics routes, trainer scheduling navigation, training notification links scoped to these roles; manager dashboard retained |
| FRONTEND/src/TrainingMetricsPage.jsx | Real paginated evaluations, horse cards/detail charts, health/weight, truthful missing/error states |
| FRONTEND/src/TrainingSchedulePage.jsx | Tạo kế hoạch label; hide deferred mutations and obsolete staffing display |
| FRONTEND/src/styles.css | Metrics layouts and schedule columns |
| HorseController, HorseService, HorseRepository | Current-trainer horse search, unrestricted manager precedence |
| HorseMeasurementController, HorseMeasurementService | Trainer scope on measurements, readiness and health |
| RaceEventService | Trainer-scoped registration, owner decisions, withdrawal reasons, registration notifications and phase readiness |
| TrainingPlanService | Owner nested schedules omit staff/vet/assignment-user identities; preserve trainer/manager responses |
| TrainerHorseSearchTest, TrainerMeasurementAccessTest, TrainingPlanOwnerProjectionTest | Search/access/projection regression coverage |
| TrainingSessionAccessTest | Three recovered historical tests explicitly Disabled, compile-safe invocation |
| AzureConnectionSmokeTest | Disabled shared-database write test; not run against the shared database |

## Owner plan page inspection

The owner schedule page uses per-horse plan reads. `TrainingPlansSection` contains a plan request but is not rendered. No direct frontend plan-by-id caller was found; owner plan responses are sanitized as well.

## Execution note — 2026-10-06

The user approved execution of forward scripts 21 and 22. Both completed successfully against the configured Azure SQL database, in order. The post-run metadata-only inventory is saved at `BACKEND/target/azure-schema-inventory-post-v3.txt`; it reports 37 tables including `sysdiagrams` (36 application tables), 10 active racetracks, and the new lifecycle, caretaker/vet, evaluation, and trigger metadata. No business rows were queried beyond the authorized active-ground count. No credentials were printed or saved in this report.

Local implementation and verification continued after that approval. Current results: Java 21 compile and test-compile passed; backend suite 148 tests, 0 failures, 0 errors, 4 skipped; frontend production build passed with 2643 modules transformed. The database-writing smoke test remains skipped, so live application behavior against Azure is not asserted.

The context-file finding that owner evaluation DTO fields were ahead of schema is resolved by the applied migrations: metadata now shows the previously missing evaluation fields, including `max_speed_kmh`, `intensity_percent`, stop/one-minute BPM, recovery fields, `track_completion_seconds`, and the injury columns.

Known script/schema notes remain reported and were not changed: any future `role_permissions` insert must provide the required `granted`, `changed_by_user_id`, and `changed_at` columns; `racetracks` has no `code` column, so grounds must be identified by `name`. Neither forward script added permission rows or attempted a racetrack code.

No commits or pushes were made. No table was added. FitnessDashboardController and Flow 4 internals were left unchanged.
