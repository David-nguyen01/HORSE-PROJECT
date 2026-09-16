USE [racehorse];
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
GO

/* FR06: telemetry is entered manually by the Head Trainer. */
IF COL_LENGTH(N'dbo.telemetry_sessions', N'recorded_by_user_id') IS NULL
BEGIN
    ALTER TABLE dbo.telemetry_sessions ADD recorded_by_user_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.telemetry_sessions ADD CONSTRAINT fk_telemetry_sessions_recorder
        FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.users(id);
END;
GO

IF COL_LENGTH(N'dbo.telemetry_sessions', N'entry_method') IS NULL
BEGIN
    ALTER TABLE dbo.telemetry_sessions ADD entry_method VARCHAR(20) NOT NULL
        CONSTRAINT df_telemetry_sessions_entry_method DEFAULT 'MANUAL';
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_telemetry_sessions_entry_method')
BEGIN
    ALTER TABLE dbo.telemetry_sessions ADD CONSTRAINT ck_telemetry_sessions_entry_method CHECK (entry_method = 'MANUAL');
END;
GO

IF COL_LENGTH(N'dbo.telemetry_sessions', N'measurement_context') IS NULL
BEGIN
    ALTER TABLE dbo.telemetry_sessions ADD measurement_context VARCHAR(20) NOT NULL
        CONSTRAINT df_telemetry_sessions_context DEFAULT 'TRAINING';
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_telemetry_sessions_context')
BEGIN
    ALTER TABLE dbo.telemetry_sessions ADD CONSTRAINT ck_telemetry_sessions_context
        CHECK (measurement_context IN ('RESTING', 'TRAINING', 'RECOVERY'));
END;
GO

/* Notification jobs need an idempotency key so a retry cannot notify twice. */
IF COL_LENGTH(N'dbo.notifications', N'deduplication_key') IS NULL
BEGIN
    ALTER TABLE dbo.notifications ADD deduplication_key VARCHAR(200) NULL;
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'ux_notifications_deduplication_key'
               AND object_id=OBJECT_ID(N'dbo.notifications'))
BEGIN
    CREATE UNIQUE INDEX ux_notifications_deduplication_key
        ON dbo.notifications(deduplication_key) WHERE deduplication_key IS NOT NULL;
END;
GO

/* Inventory decisions must be explicit, not inferred from review timestamps. */
IF COL_LENGTH(N'dbo.replenishment_requests', N'decision_status') IS NULL
BEGIN
    ALTER TABLE dbo.replenishment_requests ADD decision_status VARCHAR(20) NOT NULL
        CONSTRAINT df_replenishment_requests_decision DEFAULT 'PENDING';
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_replenishment_requests_decision')
BEGIN
    ALTER TABLE dbo.replenishment_requests ADD CONSTRAINT ck_replenishment_requests_decision
        CHECK (decision_status IN ('PENDING', 'APPROVED', 'REJECTED', 'FULFILLED'));
END;
GO

/* Current accounting currency is VND. Allocation among co-owners is intentionally deferred. */
UPDATE dbo.financial_transactions SET currency='VND' WHERE currency <> 'VND';
GO

IF NOT EXISTS (SELECT 1 FROM sys.default_constraints WHERE name=N'df_financial_transactions_currency')
BEGIN
    ALTER TABLE dbo.financial_transactions ADD CONSTRAINT df_financial_transactions_currency
        DEFAULT 'VND' FOR currency;
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_financial_transactions_currency_vnd')
BEGIN
    ALTER TABLE dbo.financial_transactions ADD CONSTRAINT ck_financial_transactions_currency_vnd
        CHECK (currency = 'VND');
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'ux_owner_reports_daily'
               AND object_id=OBJECT_ID(N'dbo.owner_reports'))
BEGIN
    CREATE UNIQUE INDEX ux_owner_reports_daily
        ON dbo.owner_reports(owner_user_id, period_start, period_end);
END;
GO

/* Seed the editable RBAC matrix. Club Manager starts with every permission. */
DECLARE @manager UNIQUEIDENTIFIER = (SELECT TOP 1 id FROM dbo.users WHERE email=N'admin@gmail.com');

IF @manager IS NOT NULL
BEGIN
    MERGE dbo.role_permission_assignments AS target
    USING (
        SELECT rd.id AS role_id, mp.id AS permission_id,
            CAST(CASE
                WHEN rd.code='CLUB_MANAGER' THEN 1
                WHEN rd.code='HEAD_TRAINER' AND (
                    (mp.module_code IN ('PROFILE','TRAINING','RACING') AND mp.action_code IN ('VIEW','EDIT','APPROVE')) OR
                    (mp.module_code IN ('MEDICAL','STABLE_CARE','REPORTING') AND mp.action_code='VIEW')) THEN 1
                WHEN rd.code='VETERINARIAN' AND (
                    (mp.module_code='MEDICAL' AND mp.action_code IN ('VIEW','EDIT','APPROVE')) OR
                    (mp.module_code IN ('PROFILE','TRAINING','STABLE_CARE','RACING') AND mp.action_code='VIEW')) THEN 1
                WHEN rd.code='STABLE_STAFF' AND (
                    (mp.module_code='STABLE_CARE' AND mp.action_code IN ('VIEW','EDIT')) OR
                    (mp.module_code IN ('PROFILE','TRAINING') AND mp.action_code='VIEW')) THEN 1
                WHEN rd.code='HORSE_OWNER' AND mp.action_code='VIEW' THEN 1
                ELSE 0 END AS BIT) AS granted
        FROM dbo.role_definitions rd CROSS JOIN dbo.module_permissions mp
    ) AS source
    ON target.role_definition_id=source.role_id AND target.module_permission_id=source.permission_id
    WHEN NOT MATCHED THEN INSERT(role_definition_id,module_permission_id,granted,changed_by_user_id,changed_at)
        VALUES(source.role_id,source.permission_id,source.granted,@manager,SYSDATETIMEOFFSET());
END;
GO
