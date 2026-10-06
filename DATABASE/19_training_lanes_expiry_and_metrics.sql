/* Enforce the approved fixed-window, lane-based training rules. Safe to rerun. */
USE [racehorse];
GO

IF OBJECT_ID(N'dbo.tr_training_schedules_ground_exclusivity', N'TR') IS NOT NULL
    DROP TRIGGER dbo.tr_training_schedules_ground_exclusivity;
GO

UPDATE dbo.racetracks
SET opens_at = '05:00:00',
    closes_at = '11:00:00',
    closes_next_day = 0,
    slot_minutes = 360
WHERE opens_at <> '05:00:00'
   OR closes_at <> '11:00:00'
   OR closes_next_day <> 0
   OR slot_minutes <> 360;
GO

ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_slot_minutes;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_slot_minutes DEFAULT 360 FOR slot_minutes;
GO

DECLARE @LaneNumber INT = 1;
WHILE @LaneNumber <= 4
BEGIN
    INSERT INTO dbo.racetrack_lanes (id, racetrack_id, lane_number, is_active)
    SELECT NEWID(), track.id, @LaneNumber, track.is_active
    FROM dbo.racetracks track
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.racetrack_lanes lane
        WHERE lane.racetrack_id = track.id AND lane.lane_number = @LaneNumber
    );
    SET @LaneNumber += 1;
END;
GO

UPDATE dbo.racetrack_lanes
SET is_active = 0
WHERE lane_number NOT BETWEEN 1 AND 4
  AND is_active = 1;
GO

CREATE OR ALTER TRIGGER dbo.tr_trial_run_bookings_no_lane_overlap
ON dbo.trial_run_bookings
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.trial_run_bookings b
          ON b.racetrack_lane_id = i.racetrack_lane_id
         AND b.id <> i.id
         AND b.cancelled_at IS NULL
         AND i.cancelled_at IS NULL
         AND CONVERT(date, b.booked_start AT TIME ZONE 'SE Asia Standard Time')
             = CONVERT(date, i.booked_start AT TIME ZONE 'SE Asia Standard Time')
    )
        THROW 51003, N'Làn chạy đã được đặt trong ngày này.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_schedules current_session ON current_session.id = i.training_schedule_id
        JOIN dbo.trial_run_bookings b
          ON b.id <> i.id
         AND b.cancelled_at IS NULL
         AND i.cancelled_at IS NULL
        JOIN dbo.training_schedules other_session ON other_session.id = b.training_schedule_id
        WHERE current_session.horse_id = other_session.horse_id
          AND CONVERT(date, current_session.scheduled_start AT TIME ZONE 'SE Asia Standard Time')
              = CONVERT(date, other_session.scheduled_start AT TIME ZONE 'SE Asia Standard Time')
    )
        THROW 51003, N'Ngựa đã có buổi tập trong ngày này.', 1;
END;
GO

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.training_evaluations')
      AND name = N'intensity_percent'
      AND (precision <> 5 OR scale <> 2)
)
    ALTER TABLE dbo.training_evaluations ALTER COLUMN intensity_percent DECIMAL(5,2) NULL;
GO

CREATE OR ALTER TRIGGER dbo.tr_training_schedules_plan_and_lock
ON dbo.training_schedules
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_plan_phases p ON p.id = i.training_plan_phase_id
        JOIN dbo.training_plans plan_record ON plan_record.id = p.training_plan_id
        WHERE plan_record.horse_id <> i.horse_id
    )
        THROW 51012, N'Ngựa trên lịch phải trùng với ngựa của kế hoạch huấn luyện.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_plan_phases p ON p.id = i.training_plan_phase_id
        JOIN dbo.training_plans plan_record ON plan_record.id = p.training_plan_id
        CROSS APPLY (SELECT MAX(end_date) AS last_phase_end FROM dbo.training_plan_phases WHERE training_plan_id = plan_record.id) expiry
        WHERE i.cancelled_at IS NULL
          AND (plan_record.is_active = 0 OR expiry.last_phase_end <
              CONVERT(date, SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time'))
    )
        THROW 51014, N'Kế hoạch đã hết hạn, không thể tạo buổi tập mới.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_locks training_lock
          ON training_lock.horse_id = i.horse_id
         AND training_lock.released_at IS NULL
        WHERE i.workload_intensity = 'HIGH'
          AND i.cancelled_at IS NULL
    )
        THROW 51013, N'Không thể xếp lịch cường độ cao khi ngựa đang bị khóa huấn luyện.', 1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_training_evaluations_plan_expiry
ON dbo.training_evaluations
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_schedules s ON s.id = i.training_schedule_id
        JOIN dbo.training_plan_phases p ON p.id = s.training_plan_phase_id
        JOIN dbo.training_plans plan_record ON plan_record.id = p.training_plan_id
        CROSS APPLY (SELECT MAX(end_date) AS last_phase_end FROM dbo.training_plan_phases WHERE training_plan_id = plan_record.id) expiry
        WHERE plan_record.is_active = 0
           OR expiry.last_phase_end <
              CONVERT(date, SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time')
    )
        THROW 51014, N'Kế hoạch đã hết hạn, không thể tạo đánh giá mới.', 1;
END;
GO
