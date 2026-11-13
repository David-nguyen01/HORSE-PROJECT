-- Training time rules are owned by the Spring backend.
-- The database keeps persistence constraints only and no longer rejects a
-- schedule because of its start time or duration.
DROP TRIGGER IF EXISTS tr_enforce_training_window ON dbo.training_schedules;
DROP FUNCTION IF EXISTS dbo.enforce_training_window();
