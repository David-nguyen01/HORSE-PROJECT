/* REVIEW ONLY: restores the three definitions captured from Azure on 2026-10-05.
   Run only after separately approving rollback. Existing staff/vet triggers are untouched.
   Evaluation expiry trigger was absent in the captured Azure schema. */
SET XACT_ABORT ON;
BEGIN TRY
BEGIN TRANSACTION;
IF EXISTS (SELECT 1 FROM dbo.training_schedules a JOIN dbo.training_schedules b
  ON a.racetrack_id=b.racetrack_id AND a.id<>b.id
  WHERE a.cancelled_at IS NULL AND b.cancelled_at IS NULL
    AND a.scheduled_start<b.scheduled_end AND a.scheduled_end>b.scheduled_start)
  THROW 51077, N'Cannot restore ground exclusivity after lane sharing. Review affected sessions; no records were changed.', 1;
EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_training_schedules_ground_exclusivity ON dbo.training_schedules AFTER INSERT, UPDATE AS BEGIN     SET NOCOUNT ON;     IF EXISTS (         SELECT 1 FROM dbo.training_schedules s         JOIN inserted i ON s.racetrack_id = i.racetrack_id                        AND s.id <> i.id                        AND s.cancelled_at IS NULL                        AND i.cancelled_at IS NULL                        AND i.racetrack_id IS NOT NULL         WHERE s.scheduled_start < i.scheduled_end           AND s.scheduled_end   > i.scheduled_start     )         THROW 50063, N''Sân tập đã được đăng ký cho ngựa khác trong ngày này (1 sân / 1 ngựa / ngày).'', 1; END;');
EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_training_schedules_plan_and_lock ON dbo.training_schedules AFTER INSERT, UPDATE AS BEGIN     SET NOCOUNT ON;      IF EXISTS (         SELECT 1         FROM inserted i         JOIN dbo.training_plan_phases p ON p.id = i.training_plan_phase_id         JOIN dbo.training_plans plan_record ON plan_record.id = p.training_plan_id         WHERE plan_record.horse_id <> i.horse_id     )         THROW 51012, N''Ngựa trên lịch phải trùng với ngựa của kế hoạch huấn luyện.'', 1;      IF EXISTS (         SELECT 1         FROM inserted i         JOIN dbo.training_plan_phases p ON p.id = i.training_plan_phase_id         JOIN dbo.training_plans plan_record ON plan_record.id = p.training_plan_id         WHERE plan_record.is_active = 0            OR p.end_date < CONVERT(date, SYSUTCDATETIME() AT TIME ZONE ''UTC'' AT TIME ZONE ''SE Asia Standard Time'')     )         THROW 51014, N''Không thể tạo buổi tập cho kế hoạch đã hết hạn.'', 1;      IF EXISTS (         SELECT 1         FROM inserted i         JOIN dbo.training_locks training_lock           ON training_lock.horse_id = i.horse_id          AND training_lock.released_at IS NULL         WHERE i.workload_intensity = ''HIGH''           AND i.cancelled_at IS NULL     )         THROW 51013, N''Không thể xếp lịch cư�?ng độ cao khi ngựa đang bị khóa huấn luyện.'', 1; END; ');
EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_trial_run_bookings_no_lane_overlap  ON dbo.trial_run_bookings  AFTER INSERT, UPDATE  AS  BEGIN      SET NOCOUNT ON;      IF EXISTS (          SELECT 1          FROM inserted i          JOIN dbo.trial_run_bookings b            ON b.racetrack_lane_id = i.racetrack_lane_id           AND b.id <> i.id           AND b.cancelled_at IS NULL           AND i.cancelled_at IS NULL           AND i.booked_start < b.booked_end           AND i.booked_end > b.booked_start      )      BEGIN          THROW 51003, N''L�n ch?y ?� ???c ??t trong khung gi? n�y.'', 1;      END;  END;');
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
THROW;
END CATCH;
