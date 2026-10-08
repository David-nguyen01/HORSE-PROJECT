/* Allow an emergency report to mark its in-progress session as injured. */
SET XACT_ABORT ON;
USE [racehorse];
GO

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH(N'dbo.training_schedules', N'session_status') IS NULL
        THROW 51080, N'Apply the training session status migration before migration 29.', 1;

    IF EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id=OBJECT_ID(N'dbo.training_schedules')
          AND name=N'ck_training_v3_session_status'
          AND definition NOT LIKE N'%INJURED%'
    )
        ALTER TABLE dbo.training_schedules DROP CONSTRAINT ck_training_v3_session_status;

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id=OBJECT_ID(N'dbo.training_schedules')
          AND name=N'ck_training_v3_session_status'
          AND definition LIKE N'%INJURED%'
    )
        ALTER TABLE dbo.training_schedules WITH CHECK ADD CONSTRAINT ck_training_v3_session_status CHECK (
            (session_status IS NOT NULL AND session_status IN ('PREPARING','READY','IN_PROGRESS','COMPLETED','INJURED'))
            OR (session_status IS NULL AND cancelled_at IS NOT NULL)
        );

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
