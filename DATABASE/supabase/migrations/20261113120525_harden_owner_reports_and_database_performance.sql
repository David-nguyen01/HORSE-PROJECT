-- Harden the hosted PostgreSQL schema without deleting application data.
-- This migration is intentionally idempotent because content_hash was
-- partially added to the current hosted database before it was versioned.

SET search_path TO dbo, public, extensions;

-- Complete owner report duplicate protection on both fresh and existing DBs.
ALTER TABLE dbo.owner_reports
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

ALTER TABLE dbo.owner_reports
    ALTER COLUMN content_hash SET DEFAULT
        'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';

UPDATE dbo.owner_reports
SET content_hash = encode(
    extensions.digest(
        convert_to(
            'horses:' || COALESCE(horse_ids_json, '[]') ||
            ';transactions:' || COALESCE(transaction_ids_json, '[]'),
            'UTF8'
        ),
        'sha256'
    ),
    'hex'
)
WHERE content_hash IS NULL OR btrim(content_hash) = '';

-- Preserve every historical report if an earlier race created duplicates.
-- Only the later duplicate hashes are disambiguated; no report is removed.
WITH duplicate_rows AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY owner_user_id, period_start, period_end, content_hash
               ORDER BY generated_at, id
           ) AS duplicate_number
    FROM dbo.owner_reports
)
UPDATE dbo.owner_reports report
SET content_hash = encode(
    extensions.digest(
        convert_to(report.content_hash || ':' || report.id::text, 'UTF8'),
        'sha256'
    ),
    'hex'
)
FROM duplicate_rows duplicate
WHERE duplicate.id = report.id
  AND duplicate.duplicate_number > 1;

ALTER TABLE dbo.owner_reports
    ALTER COLUMN content_hash SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_owner_reports_period_content
    ON dbo.owner_reports(owner_user_id, period_start, period_end, content_hash);

-- Prevent object-name shadowing inside trigger functions.
ALTER FUNCTION dbo.audit_logs_append_only()
    SET search_path = pg_catalog, dbo;
ALTER FUNCTION dbo.enforce_training_window()
    SET search_path = pg_catalog, dbo;
ALTER FUNCTION dbo.race_registration_status_guard()
    SET search_path = pg_catalog, dbo;
ALTER FUNCTION dbo.stall_assignment_capacity_guard()
    SET search_path = pg_catalog, dbo;
ALTER FUNCTION dbo.training_evaluation_plan_expiry()
    SET search_path = pg_catalog, dbo;
ALTER FUNCTION dbo.training_schedule_plan_and_lock()
    SET search_path = pg_catalog, dbo;
ALTER FUNCTION dbo.trial_run_booking_guard()
    SET search_path = pg_catalog, dbo;

-- Add only indexes used by regular role workflows and reporting queries.
CREATE INDEX IF NOT EXISTS idx_care_tasks_staff_date_status
    ON dbo.care_tasks(assigned_staff_id, scheduled_date, status);
CREATE INDEX IF NOT EXISTS idx_care_tasks_horse_date_status
    ON dbo.care_tasks(horse_id, scheduled_date, status);
CREATE INDEX IF NOT EXISTS idx_financial_transactions_wallet_period
    ON dbo.financial_transactions(wallet_owner_user_id, transaction_date)
    WHERE wallet_owner_user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_module_records_user_event
    ON dbo.module_records(user_id, event_date)
    WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_preventive_care_horse_due
    ON dbo.preventive_care_schedules(horse_id, due_date, cancelled_at);
CREATE INDEX IF NOT EXISTS idx_training_plans_horse_active
    ON dbo.training_plans(horse_id, is_active);
CREATE INDEX IF NOT EXISTS idx_training_schedules_phase_start
    ON dbo.training_schedules(training_plan_phase_id, scheduled_start);
