/* Align the deployed schema with the training entities. Safe to rerun. */
USE [racehorse];
GO

IF COL_LENGTH(N'dbo.training_evaluations', N'actual_run_minutes') IS NULL
BEGIN
    ALTER TABLE dbo.training_evaluations ADD
        actual_run_minutes DECIMAL(8,2) NULL,
        average_heart_rate_bpm INT NULL,
        max_heart_rate_bpm INT NULL,
        peak_heart_rate_bpm INT NULL,
        heart_rate_after_rest_bpm INT NULL,
        rest_minutes DECIMAL(6,2) NULL,
        intensity_percent DECIMAL(7,2) NULL,
        heart_rate_recovery_rate DECIMAL(9,2) NULL,
        hrr1_bpm INT NULL;
END;
GO

ALTER TABLE dbo.training_schedules ALTER COLUMN assigned_staff_id UNIQUEIDENTIFIER NULL;

IF COL_LENGTH(N'dbo.training_schedules', N'racetrack_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD racetrack_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_racetrack
        FOREIGN KEY (racetrack_id) REFERENCES dbo.racetracks(id);
END;

IF COL_LENGTH(N'dbo.training_schedules', N'veterinarian_user_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD veterinarian_user_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_vet
        FOREIGN KEY (veterinarian_user_id) REFERENCES dbo.users(id);
END;

IF COL_LENGTH(N'dbo.training_schedules', N'staff_assigned_by') IS NULL
    ALTER TABLE dbo.training_schedules ADD
        staff_assigned_by VARCHAR(20) NULL,
        staff_assigned_at DATETIMEOFFSET NULL,
        vet_assigned_by VARCHAR(20) NULL,
        vet_assigned_at DATETIMEOFFSET NULL;
GO
