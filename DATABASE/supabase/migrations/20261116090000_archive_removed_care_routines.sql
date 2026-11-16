-- Existing deployed instances were left on staff checklists when their
-- definitions were removed or moved out of the active catalog. Preserve
-- completion evidence while excluding these instances from current work.
UPDATE dbo.care_tasks AS task
SET task_category = 'ARCHIVED_ROUTINE',
    routine_definition_id = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE task.task_category = 'ROUTINE'
  AND (
      task.routine_definition_id IS NULL
      OR EXISTS (
          SELECT 1 FROM dbo.care_routine_definitions AS definition
          WHERE definition.id = task.routine_definition_id
            AND definition.is_active = false
      )
  );
