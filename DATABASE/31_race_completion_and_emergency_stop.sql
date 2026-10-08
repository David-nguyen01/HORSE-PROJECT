/* Explicit race completion and training emergency stop. Idempotent SQL Server migration. */
USE [racehorse];
GO
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF COL_LENGTH(N'dbo.race_events', N'completed_at') IS NULL
        ALTER TABLE dbo.race_events ADD completed_at DATETIMEOFFSET NULL;

    IF EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id=OBJECT_ID(N'dbo.training_schedules')
                 AND name=N'ck_training_v3_session_status')
        ALTER TABLE dbo.training_schedules DROP CONSTRAINT ck_training_v3_session_status;
    ALTER TABLE dbo.training_schedules WITH CHECK ADD CONSTRAINT ck_training_v3_session_status CHECK (
        (session_status IS NOT NULL AND session_status IN
            ('PREPARING','READY','IN_PROGRESS','COMPLETED','INJURED','EMERGENCY_STOPPED'))
        OR (session_status IS NULL AND cancelled_at IS NOT NULL)
    );
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
