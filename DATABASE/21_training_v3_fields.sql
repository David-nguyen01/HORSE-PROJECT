/* REVIEW ONLY: execute against shared Azure only after explicit approval.
   No new tables. Re-running preserves data and never infers readiness from staffing.
   Existing cancelled rows keep cancelled_at and a NULL session_status.
   Run with a client that stops on SQL errors. */
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.training_schedules', N'U') IS NULL
       OR OBJECT_ID(N'dbo.training_evaluations', N'U') IS NULL
       OR OBJECT_ID(N'dbo.horses', N'U') IS NULL
        THROW 51070, N'Required training tables are missing.', 1;

    IF COL_LENGTH(N'dbo.training_schedules', N'notes') IS NULL
        ALTER TABLE dbo.training_schedules ADD notes NVARCHAR(2000) NULL;
    IF COL_LENGTH(N'dbo.training_schedules', N'session_status') IS NULL
        ALTER TABLE dbo.training_schedules ADD session_status VARCHAR(20) NULL;
    IF COL_LENGTH(N'dbo.training_schedules', N'ready_at') IS NULL
        ALTER TABLE dbo.training_schedules ADD ready_at DATETIMEOFFSET NULL;
    IF COL_LENGTH(N'dbo.training_schedules', N'ready_by_user_id') IS NULL
        ALTER TABLE dbo.training_schedules ADD ready_by_user_id UNIQUEIDENTIFIER NULL;
    IF COL_LENGTH(N'dbo.training_schedules', N'started_at') IS NULL
        ALTER TABLE dbo.training_schedules ADD started_at DATETIMEOFFSET NULL;

    -- The observed legacy trigger rejects every update to an expired phase.
    -- Suspend only that trigger during the derived-status backfill, under this
    -- transaction's schema lock, then restore its original enabled state.
    DECLARE @EnablePlanTrigger BIT = CASE WHEN EXISTS (SELECT 1 FROM sys.triggers
        WHERE object_id=OBJECT_ID(N'dbo.tr_training_schedules_plan_and_lock') AND is_disabled=0)
        THEN 1 ELSE 0 END;
    IF @EnablePlanTrigger=1
        DISABLE TRIGGER dbo.tr_training_schedules_plan_and_lock ON dbo.training_schedules;
    EXEC(N'UPDATE dbo.training_schedules SET session_status =
            CASE WHEN completed_at IS NOT NULL THEN ''COMPLETED'' ELSE ''PREPARING'' END
           WHERE session_status IS NULL AND cancelled_at IS NULL;');
    IF @EnablePlanTrigger=1
        ENABLE TRIGGER dbo.tr_training_schedules_plan_and_lock ON dbo.training_schedules;
    IF NOT EXISTS (SELECT 1 FROM sys.default_constraints d JOIN sys.columns c
                   ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id
                   WHERE d.parent_object_id=OBJECT_ID(N'dbo.training_schedules') AND c.name=N'session_status')
        EXEC(N'ALTER TABLE dbo.training_schedules ADD CONSTRAINT df_training_v3_session_status
               DEFAULT ''PREPARING'' FOR session_status;');
    IF OBJECT_ID(N'dbo.ck_training_v3_session_status', N'C') IS NULL
        EXEC(N'ALTER TABLE dbo.training_schedules WITH CHECK ADD CONSTRAINT ck_training_v3_session_status CHECK
          ((session_status IS NOT NULL AND session_status IN (''PREPARING'',''READY'',''IN_PROGRESS'',''COMPLETED''))
            OR (session_status IS NULL AND cancelled_at IS NOT NULL));');
    IF OBJECT_ID(N'dbo.fk_training_v3_ready_by', N'F') IS NULL
        EXEC(N'ALTER TABLE dbo.training_schedules WITH CHECK ADD CONSTRAINT fk_training_v3_ready_by
               FOREIGN KEY(ready_by_user_id) REFERENCES dbo.users(id);');

    IF COL_LENGTH(N'dbo.horses', N'caretaker_user_id') IS NULL
        ALTER TABLE dbo.horses ADD caretaker_user_id UNIQUEIDENTIFIER NULL;
    IF COL_LENGTH(N'dbo.horses', N'diagnosing_vet_user_id') IS NULL
        ALTER TABLE dbo.horses ADD diagnosing_vet_user_id UNIQUEIDENTIFIER NULL;
    IF OBJECT_ID(N'dbo.fk_horses_v3_caretaker', N'F') IS NULL
        EXEC(N'ALTER TABLE dbo.horses WITH CHECK ADD CONSTRAINT fk_horses_v3_caretaker
               FOREIGN KEY(caretaker_user_id) REFERENCES dbo.users(id);');
    IF OBJECT_ID(N'dbo.fk_horses_v3_diagnosing_vet', N'F') IS NULL
        EXEC(N'ALTER TABLE dbo.horses WITH CHECK ADD CONSTRAINT fk_horses_v3_diagnosing_vet
               FOREIGN KEY(diagnosing_vet_user_id) REFERENCES dbo.users(id);');
    -- No backfill from care tasks or medical exam authors.

    IF COL_LENGTH(N'dbo.training_evaluations', N'track_completion_seconds') IS NULL
        ALTER TABLE dbo.training_evaluations ADD track_completion_seconds DECIMAL(12,3) NULL;
    IF COL_LENGTH(N'dbo.training_evaluations', N'injured') IS NULL
        ALTER TABLE dbo.training_evaluations ADD injured BIT NULL;
    IF COL_LENGTH(N'dbo.training_evaluations', N'injury_location') IS NULL
        ALTER TABLE dbo.training_evaluations ADD injury_location NVARCHAR(200) NULL;
    IF COL_LENGTH(N'dbo.training_evaluations', N'injury_notes') IS NULL
        ALTER TABLE dbo.training_evaluations ADD injury_notes NVARCHAR(4000) NULL;
    IF OBJECT_ID(N'dbo.ck_training_v3_completion_time', N'C') IS NULL
        EXEC(N'ALTER TABLE dbo.training_evaluations WITH CHECK ADD CONSTRAINT ck_training_v3_completion_time
               CHECK (track_completion_seconds IS NULL OR track_completion_seconds > 0);');
    -- Legacy injury/timing remains unknown, not fabricated as false or 360 minutes.
    -- Preserve NOT NULL perceived_exertion; the approved evaluation form will supply 1..10.
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.training_evaluations')
               AND name=N'intensity_percent' AND (precision<>5 OR scale<>2))
    BEGIN
        IF EXISTS (SELECT 1 FROM dbo.training_evaluations WHERE ABS(intensity_percent)>999.99)
            THROW 51071, N'Intensity values exceed decimal(5,2); manual review required.', 1;
        ALTER TABLE dbo.training_evaluations ALTER COLUMN intensity_percent DECIMAL(5,2) NULL;
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
