/* Training sessions no longer carry staff or veterinarian assignments. */
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

IF OBJECT_ID(N'dbo.tr_training_schedules_no_staff_overlap', N'TR') IS NOT NULL
    DROP TRIGGER dbo.tr_training_schedules_no_staff_overlap;
IF OBJECT_ID(N'dbo.tr_training_schedules_no_vet_overlap', N'TR') IS NOT NULL
    DROP TRIGGER dbo.tr_training_schedules_no_vet_overlap;
GO

IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.training_schedules') AND name=N'idx_training_schedules_staff_time')
    DROP INDEX idx_training_schedules_staff_time ON dbo.training_schedules;
GO

IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.training_schedules') AND name=N'fk_training_schedules_staff')
    ALTER TABLE dbo.training_schedules DROP CONSTRAINT fk_training_schedules_staff;
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.training_schedules') AND name=N'fk_training_schedules_vet')
    ALTER TABLE dbo.training_schedules DROP CONSTRAINT fk_training_schedules_vet;
GO

IF COL_LENGTH(N'dbo.training_schedules', N'assigned_staff_id') IS NOT NULL
    ALTER TABLE dbo.training_schedules DROP COLUMN assigned_staff_id;
IF COL_LENGTH(N'dbo.training_schedules', N'veterinarian_user_id') IS NOT NULL
    ALTER TABLE dbo.training_schedules DROP COLUMN veterinarian_user_id;
IF COL_LENGTH(N'dbo.training_schedules', N'staff_assigned_by') IS NOT NULL
    ALTER TABLE dbo.training_schedules DROP COLUMN staff_assigned_by;
IF COL_LENGTH(N'dbo.training_schedules', N'staff_assigned_at') IS NOT NULL
    ALTER TABLE dbo.training_schedules DROP COLUMN staff_assigned_at;
IF COL_LENGTH(N'dbo.training_schedules', N'vet_assigned_by') IS NOT NULL
    ALTER TABLE dbo.training_schedules DROP COLUMN vet_assigned_by;
IF COL_LENGTH(N'dbo.training_schedules', N'vet_assigned_at') IS NOT NULL
    ALTER TABLE dbo.training_schedules DROP COLUMN vet_assigned_at;
GO
