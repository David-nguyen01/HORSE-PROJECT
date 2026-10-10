/* Idempotent upgrade for existing racehorse_local databases. Historical bookings are preserved. */
USE [racehorse_local];
GO

IF OBJECT_ID(N'dbo.racetracks', N'U') IS NOT NULL
BEGIN
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'ck_racetracks_operating_hours' AND parent_object_id = OBJECT_ID(N'dbo.racetracks'))
        ALTER TABLE dbo.racetracks DROP CONSTRAINT ck_racetracks_operating_hours;
    IF EXISTS (SELECT 1 FROM sys.default_constraints WHERE name = N'df_racetracks_opens_at' AND parent_object_id = OBJECT_ID(N'dbo.racetracks'))
        ALTER TABLE dbo.racetracks DROP CONSTRAINT df_racetracks_opens_at;
    IF EXISTS (SELECT 1 FROM sys.default_constraints WHERE name = N'df_racetracks_closes_at' AND parent_object_id = OBJECT_ID(N'dbo.racetracks'))
        ALTER TABLE dbo.racetracks DROP CONSTRAINT df_racetracks_closes_at;
    IF EXISTS (SELECT 1 FROM sys.default_constraints WHERE name = N'df_racetracks_closes_next_day' AND parent_object_id = OBJECT_ID(N'dbo.racetracks'))
        ALTER TABLE dbo.racetracks DROP CONSTRAINT df_racetracks_closes_next_day;
    IF EXISTS (SELECT 1 FROM sys.default_constraints WHERE name = N'df_racetracks_slot_minutes' AND parent_object_id = OBJECT_ID(N'dbo.racetracks'))
        ALTER TABLE dbo.racetracks DROP CONSTRAINT df_racetracks_slot_minutes;

    UPDATE dbo.racetracks
       SET opens_at = '06:00:00', closes_at = '00:00:00', closes_next_day = 1, slot_minutes = 60
     WHERE opens_at <> '06:00:00' OR closes_at <> '00:00:00' OR closes_next_day <> 1 OR slot_minutes <> 60;

    ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_opens_at DEFAULT '06:00:00' FOR opens_at;
    ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_at DEFAULT '00:00:00' FOR closes_at;
    ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_next_day DEFAULT 1 FOR closes_next_day;
    ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_slot_minutes DEFAULT 60 FOR slot_minutes;
    ALTER TABLE dbo.racetracks WITH CHECK ADD CONSTRAINT ck_racetracks_operating_hours
        CHECK (opens_at = '06:00:00' AND closes_at = '00:00:00' AND closes_next_day = 1 AND slot_minutes = 60);
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_enforce_training_window
ON dbo.training_schedules
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i
        LEFT JOIN deleted d ON d.id = i.id
        WHERE i.cancelled_at IS NULL
          AND (d.id IS NULL OR i.scheduled_start <> d.scheduled_start
               OR i.scheduled_end <> d.scheduled_end OR d.cancelled_at IS NOT NULL)
          AND (CAST(i.scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS TIME) < '06:00:00'
               OR CAST(i.scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS TIME) > '23:00:00'
               OR DATEPART(MINUTE, i.scheduled_start AT TIME ZONE 'SE Asia Standard Time') <> 0
               OR DATEPART(SECOND, i.scheduled_start AT TIME ZONE 'SE Asia Standard Time') <> 0
               OR i.scheduled_end <> DATEADD(HOUR, 1, i.scheduled_start))
    )
        THROW 51001, N'Lịch huấn luyện phải là slot 1 giờ bắt đầu từ 06:00 đến 23:00 (kết thúc muộn nhất 00:00 hôm sau).', 1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_trial_run_bookings_no_lane_overlap
ON dbo.trial_run_bookings
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i
        JOIN dbo.trial_run_bookings b WITH (UPDLOCK, HOLDLOCK)
          ON b.racetrack_lane_id = i.racetrack_lane_id AND b.id <> i.id
        WHERE b.cancelled_at IS NULL AND i.cancelled_at IS NULL
          AND i.booked_start < b.booked_end AND i.booked_end > b.booked_start
    )
        THROW 51003, N'Làn chạy đã được đặt trong khung giờ này.', 1;

    IF EXISTS (
        SELECT 1 FROM inserted i
        JOIN dbo.training_schedules s ON s.id = i.training_schedule_id
        JOIN dbo.racetrack_lanes l ON l.id = i.racetrack_lane_id
        WHERE i.cancelled_at IS NULL
          AND (s.racetrack_id <> l.racetrack_id OR s.racetrack_id IS NULL
            OR s.cancelled_at IS NOT NULL OR l.is_active = 0 OR l.lane_number NOT BETWEEN 1 AND 4
            OR i.booked_start <> s.scheduled_start OR i.booked_end <> s.scheduled_end)
    )
        THROW 51074, N'Thông tin làn chạy và thời gian đặt làn phải khớp với buổi tập đang hoạt động.', 1;
END;
GO
