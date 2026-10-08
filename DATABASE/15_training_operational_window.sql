/* Training operational window, ground reservations, staffing claims and evaluation metrics. */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
GO
USE [racehorse];
GO

ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS ck_racetracks_operating_hours;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS ck_racetracks_slot_minutes;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_opens_at;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_closes_at;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_closes_next_day;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_slot_minutes;
GO
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_opens_at DEFAULT '05:00:00' FOR opens_at;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_at DEFAULT '11:00:00' FOR closes_at;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_next_day DEFAULT 0 FOR closes_next_day;
ALTER TABLE dbo.racetracks ADD CONSTRAINT ck_racetracks_operating_hours
    CHECK (opens_at = '05:00:00' AND closes_at = '11:00:00' AND closes_next_day = 0);
UPDATE dbo.racetracks SET opens_at='05:00:00', closes_at='11:00:00', closes_next_day=0;
GO

ALTER TABLE dbo.training_schedules ALTER COLUMN assigned_staff_id UNIQUEIDENTIFIER NULL;
IF COL_LENGTH(N'dbo.training_schedules', N'racetrack_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD racetrack_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_racetrack FOREIGN KEY(racetrack_id) REFERENCES dbo.racetracks(id);
END;
IF COL_LENGTH(N'dbo.training_schedules', N'veterinarian_user_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD veterinarian_user_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_vet FOREIGN KEY(veterinarian_user_id) REFERENCES dbo.users(id);
END;
IF COL_LENGTH(N'dbo.training_schedules', N'staff_assigned_by') IS NULL
    ALTER TABLE dbo.training_schedules ADD staff_assigned_by VARCHAR(20) NULL, staff_assigned_at DATETIMEOFFSET NULL,
        vet_assigned_by VARCHAR(20) NULL, vet_assigned_at DATETIMEOFFSET NULL;
GO

UPDATE s SET racetrack_id=l.racetrack_id FROM dbo.training_schedules s
JOIN dbo.trial_run_bookings b ON b.training_schedule_id=s.id
JOIN dbo.racetrack_lanes l ON l.id=b.racetrack_lane_id
WHERE s.racetrack_id IS NULL;
GO
CREATE OR ALTER TRIGGER dbo.tr_training_schedules_no_staff_overlap ON dbo.training_schedules AFTER INSERT, UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM dbo.training_schedules s JOIN inserted i ON s.assigned_staff_id=i.assigned_staff_id AND s.id<>i.id
   AND s.cancelled_at IS NULL AND i.cancelled_at IS NULL AND i.assigned_staff_id IS NOT NULL
   WHERE s.scheduled_start<i.scheduled_end AND s.scheduled_end>i.scheduled_start)
 THROW 50061,N'Nhân viên chuồng đã được phân công lịch huấn luyện khác trong ngày này.',1;
END;
GO
CREATE OR ALTER TRIGGER dbo.tr_training_schedules_no_vet_overlap ON dbo.training_schedules AFTER INSERT, UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM dbo.training_schedules s JOIN inserted i ON s.veterinarian_user_id=i.veterinarian_user_id AND s.id<>i.id
   AND s.cancelled_at IS NULL AND i.cancelled_at IS NULL AND i.veterinarian_user_id IS NOT NULL
   WHERE s.scheduled_start<i.scheduled_end AND s.scheduled_end>i.scheduled_start)
 THROW 50062,N'Bác sĩ thú y đã nhận lịch theo dõi buổi tập khác trong ngày này.',1;
END;
GO
IF COL_LENGTH(N'dbo.training_evaluations',N'actual_run_minutes') IS NULL
 ALTER TABLE dbo.training_evaluations ADD actual_run_minutes DECIMAL(8,2) NULL, average_heart_rate_bpm INT NULL,
 max_heart_rate_bpm INT NULL, peak_heart_rate_bpm INT NULL, heart_rate_after_rest_bpm INT NULL,
 rest_minutes DECIMAL(6,2) NULL, intensity_percent DECIMAL(7,2) NULL, heart_rate_recovery_rate DECIMAL(9,2) NULL,
 hrr1_bpm INT NULL;
GO
IF COL_LENGTH(N'dbo.training_plans',N'goal_target_race') IS NULL
 ALTER TABLE dbo.training_plans ADD goal_target_race NVARCHAR(300) NULL, planned_sessions_per_week TINYINT NULL,
 rest_days NVARCHAR(100) NULL, trainer_notes NVARCHAR(4000) NULL;
GO

ALTER TABLE dbo.horses ALTER COLUMN name NVARCHAR(150) NOT NULL;
IF COL_LENGTH(N'dbo.horses',N'normalized_name') IS NULL
 ALTER TABLE dbo.horses ADD normalized_name AS (CONVERT(NVARCHAR(150),LTRIM(RTRIM(name))) COLLATE Vietnamese_100_CI_AS_SC) PERSISTED;
GO
IF NOT EXISTS(SELECT 1 FROM dbo.horses GROUP BY normalized_name HAVING COUNT(*)>1)
 AND NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name=N'uq_horses_normalized_name' AND object_id=OBJECT_ID(N'dbo.horses'))
 CREATE UNIQUE INDEX uq_horses_normalized_name ON dbo.horses(normalized_name);
GO
