/* Align existing databases with the current evaluation form and entity mapping. Safe to rerun. */
USE [racehorse];
GO

SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.training_evaluations', N'U') IS NULL
        THROW 51076, N'Apply the training evaluations schema before migration 26.', 1;

    IF EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id = OBJECT_ID(N'dbo.training_evaluations')
                 AND name = N'ck_training_evaluations_exertion')
        ALTER TABLE dbo.training_evaluations DROP CONSTRAINT ck_training_evaluations_exertion;

    IF EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID(N'dbo.training_evaluations')
                 AND name = N'perceived_exertion' AND is_nullable = 0)
        ALTER TABLE dbo.training_evaluations ALTER COLUMN perceived_exertion TINYINT NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id = OBJECT_ID(N'dbo.training_evaluations')
                     AND name = N'ck_training_evaluations_exertion')
        ALTER TABLE dbo.training_evaluations ADD CONSTRAINT ck_training_evaluations_exertion
            CHECK (perceived_exertion IS NULL OR perceived_exertion BETWEEN 1 AND 10);

    IF COL_LENGTH(N'dbo.training_evaluations', N'training_load') IS NOT NULL
    BEGIN
        IF EXISTS (SELECT 1 FROM sys.indexes
                   WHERE object_id = OBJECT_ID(N'dbo.training_evaluations')
                     AND name = N'idx_training_evaluations_dashboard')
            DROP INDEX idx_training_evaluations_dashboard ON dbo.training_evaluations;

        IF EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id = OBJECT_ID(N'dbo.training_evaluations')
                     AND name = N'ck_training_evaluations_load')
            ALTER TABLE dbo.training_evaluations DROP CONSTRAINT ck_training_evaluations_load;

        ALTER TABLE dbo.training_evaluations DROP COLUMN training_load;
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE object_id = OBJECT_ID(N'dbo.training_evaluations')
                     AND name = N'idx_training_evaluations_dashboard')
        CREATE INDEX idx_training_evaluations_dashboard
            ON dbo.training_evaluations(session_date, fitness_status)
            INCLUDE (horse_id, average_speed_kmh, heart_rate_recovery_bpm);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
