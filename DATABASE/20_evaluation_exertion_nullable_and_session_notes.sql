/* Make perceived exertion optional and add optional session notes. Safe to rerun. */
USE [racehorse];
GO

IF COL_LENGTH(N'dbo.training_schedules', N'notes') IS NULL
    ALTER TABLE dbo.training_schedules ADD notes NVARCHAR(2000) NULL;
GO

IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = N'ck_training_evaluations_exertion'
      AND parent_object_id = OBJECT_ID(N'dbo.training_evaluations')
)
    ALTER TABLE dbo.training_evaluations DROP CONSTRAINT ck_training_evaluations_exertion;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.training_evaluations')
      AND name = N'perceived_exertion'
      AND is_nullable = 0
)
    ALTER TABLE dbo.training_evaluations ALTER COLUMN perceived_exertion TINYINT NULL;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = N'ck_training_evaluations_exertion'
      AND parent_object_id = OBJECT_ID(N'dbo.training_evaluations')
)
    ALTER TABLE dbo.training_evaluations ADD CONSTRAINT ck_training_evaluations_exertion
        CHECK (perceived_exertion IS NULL OR perceived_exertion BETWEEN 1 AND 10);
GO
