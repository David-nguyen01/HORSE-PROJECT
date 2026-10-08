/* Separate each horse's service term from the owner's shared wallet. No new tables. */
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.horses', N'U') IS NULL OR OBJECT_ID(N'dbo.users', N'U') IS NULL
       OR OBJECT_ID(N'dbo.horse_ownerships', N'U') IS NULL
        THROW 51090, N'Apply the base and wallet schemas before migration 25.', 1;
    IF COL_LENGTH(N'dbo.users', N'wallet_balance_vnd') IS NULL
        THROW 51091, N'Apply wallet migration 24 before migration 25.', 1;
    IF EXISTS (SELECT 1 FROM dbo.horse_ownerships GROUP BY horse_id HAVING COUNT(*) <> 1)
        THROW 51092, N'Each horse must have exactly one owner before migration 25.', 1;

    IF COL_LENGTH(N'dbo.horses', N'active_service_plan') IS NULL
        ALTER TABLE dbo.horses ADD active_service_plan VARCHAR(10) NULL;
    IF COL_LENGTH(N'dbo.horses', N'next_service_plan') IS NULL
        ALTER TABLE dbo.horses ADD next_service_plan VARCHAR(10) NULL;
    IF COL_LENGTH(N'dbo.horses', N'service_plan_started_on') IS NULL
        ALTER TABLE dbo.horses ADD service_plan_started_on DATE NULL;
    IF COL_LENGTH(N'dbo.horses', N'service_next_due_date') IS NULL
        ALTER TABLE dbo.horses ADD service_next_due_date DATE NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.horses')
                   AND name=N'ck_horses_service_plans')
        EXEC(N'ALTER TABLE dbo.horses ADD CONSTRAINT ck_horses_service_plans CHECK (
            (active_service_plan IS NULL OR active_service_plan IN (''P30'',''P90'',''P365''))
            AND (next_service_plan IS NULL OR next_service_plan IN (''P30'',''P90'',''P365'')))');

    /* Preserve already-paid horses through their existing paid-until date. */
    EXEC(N'UPDATE h SET active_service_plan=u.active_service_plan,
        service_plan_started_on=u.service_plan_started_on,
        service_next_due_date=COALESCE(DATEADD(day,1,h.paid_until),u.service_next_due_date)
    FROM dbo.horses h
    JOIN dbo.horse_ownerships ho ON ho.horse_id=h.id
    JOIN dbo.users u ON u.id=ho.owner_id
    WHERE h.active_service_plan IS NULL AND h.payment_status=''PAID''
      AND u.active_service_plan IS NOT NULL AND h.paid_until IS NOT NULL');

    /* An unpaid active horse is waiting for its own purchase, not for the owner-wide package. */
    UPDATE dbo.horses SET status='PENDING_PAYMENT'
    WHERE payment_status<>'PAID' AND status='ACTIVE';

    IF (SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0 AND name<>N'sysdiagrams') > 30
        THROW 51093, N'Migration 25 would exceed the 30-table limit.', 1;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
