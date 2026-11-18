-- One daily routine instance per staff member. Existing shared rows remain as manager history.
DROP INDEX IF EXISTS dbo.uq_care_tasks_facility_routine_instance;
CREATE UNIQUE INDEX uq_care_tasks_facility_routine_instance
    ON dbo.care_tasks (routine_definition_id, scheduled_date)
    WHERE routine_definition_id IS NOT NULL AND horse_id IS NULL AND assigned_staff_id IS NULL;
CREATE UNIQUE INDEX uq_care_tasks_staff_routine_instance
    ON dbo.care_tasks (routine_definition_id, scheduled_date, assigned_staff_id)
    WHERE routine_definition_id IS NOT NULL AND horse_id IS NULL AND assigned_staff_id IS NOT NULL;
