/* New sessions reserve their lane for the fixed 05:00-11:00 training window.
   Historical hourly sessions and bookings remain unchanged. Safe to rerun. */
USE [racehorse_local];
GO
SET XACT_ABORT ON;
GO
BEGIN TRANSACTION;

IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE parent_object_id=OBJECT_ID(N'dbo.racetracks') AND name=N'ck_racetracks_operating_hours')
    ALTER TABLE dbo.racetracks DROP CONSTRAINT ck_racetracks_operating_hours;
IF EXISTS (SELECT 1 FROM sys.default_constraints
           WHERE parent_object_id=OBJECT_ID(N'dbo.racetracks') AND name=N'df_racetracks_opens_at')
    ALTER TABLE dbo.racetracks DROP CONSTRAINT df_racetracks_opens_at;
IF EXISTS (SELECT 1 FROM sys.default_constraints
           WHERE parent_object_id=OBJECT_ID(N'dbo.racetracks') AND name=N'df_racetracks_closes_at')
    ALTER TABLE dbo.racetracks DROP CONSTRAINT df_racetracks_closes_at;
IF EXISTS (SELECT 1 FROM sys.default_constraints
           WHERE parent_object_id=OBJECT_ID(N'dbo.racetracks') AND name=N'df_racetracks_closes_next_day')
    ALTER TABLE dbo.racetracks DROP CONSTRAINT df_racetracks_closes_next_day;
IF EXISTS (SELECT 1 FROM sys.default_constraints
           WHERE parent_object_id=OBJECT_ID(N'dbo.racetracks') AND name=N'df_racetracks_slot_minutes')
    ALTER TABLE dbo.racetracks DROP CONSTRAINT df_racetracks_slot_minutes;

UPDATE dbo.racetracks SET opens_at='05:00:00', closes_at='11:00:00',
    closes_next_day=0, slot_minutes=360
WHERE opens_at<>'05:00:00' OR closes_at<>'11:00:00'
   OR closes_next_day<>0 OR slot_minutes<>360;

ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_opens_at DEFAULT '05:00:00' FOR opens_at;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_at DEFAULT '11:00:00' FOR closes_at;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_next_day DEFAULT 0 FOR closes_next_day;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_slot_minutes DEFAULT 360 FOR slot_minutes;
ALTER TABLE dbo.racetracks WITH CHECK ADD CONSTRAINT ck_racetracks_operating_hours
    CHECK (opens_at='05:00:00' AND closes_at='11:00:00'
           AND closes_next_day=0 AND slot_minutes=360);
COMMIT TRANSACTION;
GO

CREATE OR ALTER TRIGGER dbo.tr_enforce_training_window
ON dbo.training_schedules
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i
        LEFT JOIN deleted d ON d.id=i.id
        WHERE i.cancelled_at IS NULL
          AND (d.id IS NULL OR i.scheduled_start<>d.scheduled_start
               OR i.scheduled_end<>d.scheduled_end OR d.cancelled_at IS NOT NULL)
          AND (CAST(i.scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS TIME)<>'05:00:00'
               OR i.scheduled_end<>DATEADD(HOUR,6,i.scheduled_start))
    )
        THROW 51001, N'Buổi tập phải dùng khung giờ cố định 05:00-11:00.', 1;
END;
GO
