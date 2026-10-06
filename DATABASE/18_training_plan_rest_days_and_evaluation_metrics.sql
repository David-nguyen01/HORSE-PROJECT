/* Training-plan rest days, date-derived schedule eligibility and evaluation metrics. Safe to rerun. */
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

IF COL_LENGTH(N'dbo.training_plan_phases', N'rest_days_json') IS NULL
BEGIN
    ALTER TABLE dbo.training_plan_phases ADD rest_days_json NVARCHAR(MAX) NOT NULL
        CONSTRAINT df_training_plan_phases_rest_days_json DEFAULT N'[]';
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'ck_training_plan_phases_rest_days_json'
      AND parent_object_id = OBJECT_ID(N'dbo.training_plan_phases')
)
BEGIN
    ALTER TABLE dbo.training_plan_phases ADD CONSTRAINT ck_training_plan_phases_rest_days_json
        CHECK (ISJSON(rest_days_json) = 1);
END;
GO

IF COL_LENGTH(N'dbo.training_evaluations', N'max_speed_kmh') IS NULL
    ALTER TABLE dbo.training_evaluations ADD max_speed_kmh DECIMAL(8,2) NULL;
IF COL_LENGTH(N'dbo.training_evaluations', N'heart_rate_at_stop_bpm') IS NULL
    ALTER TABLE dbo.training_evaluations ADD heart_rate_at_stop_bpm INT NULL;
IF COL_LENGTH(N'dbo.training_evaluations', N'heart_rate_after_1min_bpm') IS NULL
    ALTER TABLE dbo.training_evaluations ADD heart_rate_after_1min_bpm INT NULL;
GO

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.training_evaluations')
      AND name = N'intensity_percent' AND (precision <> 5 OR scale <> 1)
)
    ALTER TABLE dbo.training_evaluations ALTER COLUMN intensity_percent DECIMAL(5,1) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_training_evaluations_max_speed')
    ALTER TABLE dbo.training_evaluations ADD CONSTRAINT ck_training_evaluations_max_speed
        CHECK (max_speed_kmh IS NULL OR (max_speed_kmh > 0 AND max_speed_kmh >= average_speed_kmh));
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_training_evaluations_heart_rate_at_stop')
    ALTER TABLE dbo.training_evaluations ADD CONSTRAINT ck_training_evaluations_heart_rate_at_stop
        CHECK (heart_rate_at_stop_bpm IS NULL OR heart_rate_at_stop_bpm BETWEEN 0 AND 300);
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_training_evaluations_heart_rate_after_1min')
    ALTER TABLE dbo.training_evaluations ADD CONSTRAINT ck_training_evaluations_heart_rate_after_1min
        CHECK (heart_rate_after_1min_bpm IS NULL OR heart_rate_after_1min_bpm BETWEEN 0 AND 300);
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_training_evaluations_recovery_consistency')
    ALTER TABLE dbo.training_evaluations ADD CONSTRAINT ck_training_evaluations_recovery_consistency
        CHECK (heart_rate_at_stop_bpm IS NULL OR heart_rate_after_1min_bpm IS NULL
            OR heart_rate_recovery_bpm = heart_rate_at_stop_bpm - heart_rate_after_1min_bpm);
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
        WHERE plan_record.is_active = 0
           OR p.end_date < CONVERT(date, SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time')
    )
        THROW 51014, N'Không thể tạo buổi tập cho kế hoạch đã hết hạn.', 1;

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
