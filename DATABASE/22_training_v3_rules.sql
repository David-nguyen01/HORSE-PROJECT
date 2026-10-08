/* REVIEW ONLY: apply after 21 with explicit Azure approval.
   Preserves staff/vet overlap triggers. Lane/horse booking checks use locking reads.
   Trigger replacement and removal are transactional; original definitions are in rollback. */
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF COL_LENGTH(N'dbo.training_schedules',N'session_status') IS NULL
        THROW 51073, N'Apply migration 21 before migration 22.', 1;
    -- Migration 19 creates this expiry trigger in fresh installs. Leave it intact;
    -- migration 22 does not replace or remove it.
    DROP TRIGGER IF EXISTS dbo.tr_training_schedules_ground_exclusivity;

    EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_trial_run_bookings_no_lane_overlap
    ON dbo.trial_run_bookings AFTER INSERT, UPDATE AS
    BEGIN
      SET NOCOUNT ON;
      IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.trial_run_bookings b WITH (UPDLOCK,HOLDLOCK)
         ON b.racetrack_lane_id=i.racetrack_lane_id AND b.id<>i.id
         WHERE b.cancelled_at IS NULL AND i.cancelled_at IS NULL
           AND (CONVERT(date,b.booked_start AT TIME ZONE ''SE Asia Standard Time'')=
                CONVERT(date,i.booked_start AT TIME ZONE ''SE Asia Standard Time'')
                OR (i.booked_start<b.booked_end AND i.booked_end>b.booked_start)))
        THROW 51003, N''Lane is already reserved on this date.'', 1;
      IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.training_schedules s ON s.id=i.training_schedule_id
          JOIN dbo.racetrack_lanes l ON l.id=i.racetrack_lane_id
          WHERE i.cancelled_at IS NULL AND (s.racetrack_id<>l.racetrack_id OR s.racetrack_id IS NULL
             OR s.cancelled_at IS NOT NULL OR l.is_active=0 OR l.lane_number NOT BETWEEN 1 AND 4
             OR i.booked_start<>s.scheduled_start OR i.booked_end<>s.scheduled_end))
        THROW 51074, N''Lane, ground and booking times must match the active training session.'', 1;
    END;');

    EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_training_schedules_plan_and_lock
    ON dbo.training_schedules AFTER INSERT, UPDATE AS
    BEGIN
      SET NOCOUNT ON;
      IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.training_plan_phases p ON p.id=i.training_plan_phase_id
          JOIN dbo.training_plans tp ON tp.id=p.training_plan_id WHERE tp.horse_id<>i.horse_id)
        THROW 51012, N''Session horse must belong to its plan.'', 1;
      -- Validate scheduling edits/creation, not harmless historical lifecycle updates.
      IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id
          JOIN dbo.training_plan_phases p ON p.id=i.training_plan_phase_id
          JOIN dbo.training_plans tp ON tp.id=p.training_plan_id
          CROSS APPLY (SELECT MAX(end_date) last_end FROM dbo.training_plan_phases WHERE training_plan_id=tp.id) expiry
          WHERE i.cancelled_at IS NULL AND (d.id IS NULL OR i.training_plan_phase_id<>d.training_plan_phase_id
             OR i.horse_id<>d.horse_id OR i.scheduled_start<>d.scheduled_start OR i.scheduled_end<>d.scheduled_end)
            AND (tp.is_active=0 OR expiry.last_end<CONVERT(date,SYSUTCDATETIME() AT TIME ZONE ''UTC'' AT TIME ZONE ''SE Asia Standard Time'')
             OR CONVERT(date,i.scheduled_start AT TIME ZONE ''SE Asia Standard Time'') NOT BETWEEN p.start_date AND p.end_date))
        THROW 51014, N''Session date must be inside a non-expired active plan phase.'', 1;
      IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id
          JOIN dbo.horses h ON h.id=i.horse_id WHERE i.cancelled_at IS NULL
          AND (d.id IS NULL OR i.scheduled_start<>d.scheduled_start OR i.scheduled_end<>d.scheduled_end
             OR i.horse_id<>d.horse_id OR (i.session_status=''IN_PROGRESS'' AND ISNULL(d.session_status,'''')<>''IN_PROGRESS''))
          AND (h.status=''INJURED'' OR h.health_status=''INJURED''))
        THROW 51075, N''An injured horse cannot be scheduled or start training.'', 1;
      IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id
          JOIN dbo.training_locks l ON l.horse_id=i.horse_id AND l.released_at IS NULL
          WHERE i.cancelled_at IS NULL AND i.workload_intensity=''HIGH''
          AND (d.id IS NULL OR i.workload_intensity<>d.workload_intensity
             OR i.scheduled_start<>d.scheduled_start OR i.scheduled_end<>d.scheduled_end
             OR (i.session_status=''IN_PROGRESS'' AND ISNULL(d.session_status,'''')<>''IN_PROGRESS'')))
        THROW 51013, N''HIGH intensity is blocked while a training lock is active.'', 1;
      IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id
          JOIN dbo.training_schedules s WITH (UPDLOCK,HOLDLOCK)
          ON s.horse_id=i.horse_id AND s.id<>i.id
          WHERE i.cancelled_at IS NULL AND s.cancelled_at IS NULL
            AND (d.id IS NULL OR i.horse_id<>d.horse_id OR i.scheduled_start<>d.scheduled_start
                 OR i.scheduled_end<>d.scheduled_end OR d.cancelled_at IS NOT NULL)
            AND (CONVERT(date,s.scheduled_start AT TIME ZONE ''SE Asia Standard Time'')=
                 CONVERT(date,i.scheduled_start AT TIME ZONE ''SE Asia Standard Time'')
              OR (i.scheduled_start<s.scheduled_end AND i.scheduled_end>s.scheduled_start)))
        THROW 51076, N''Horse already has training on this date or an overlapping session.'', 1;
    END;');
    -- Evaluation expiry trigger was absent in observed Azure. Do not remove an
    -- unreviewed trigger introduced after inventory; the preflight above stops.
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
