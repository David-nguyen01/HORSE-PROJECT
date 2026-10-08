/* Owner wallet and PayOS order storage. No new tables. Run only after reviewing live schema. */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
GO
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.users', N'U') IS NULL
       OR OBJECT_ID(N'dbo.horse_ownerships', N'U') IS NULL
       OR OBJECT_ID(N'dbo.module_records', N'U') IS NULL
       OR OBJECT_ID(N'dbo.financial_transactions', N'U') IS NULL
        THROW 51080, N'Apply the compact base schema before wallet migration 24.', 1;

    IF EXISTS (SELECT 1 FROM dbo.horse_ownerships GROUP BY horse_id HAVING COUNT(*) > 1)
        THROW 51081, N'A horse has multiple owners; resolve ownership before wallet migration 24.', 1;
    IF EXISTS (SELECT 1 FROM dbo.horses h
               WHERE NOT EXISTS (SELECT 1 FROM dbo.horse_ownerships o WHERE o.horse_id = h.id))
        THROW 51082, N'A horse has no owner; resolve ownership before wallet migration 24.', 1;

    IF COL_LENGTH(N'dbo.users', N'wallet_balance_vnd') IS NULL
        ALTER TABLE dbo.users ADD wallet_balance_vnd DECIMAL(19,0) NOT NULL
            CONSTRAINT df_users_wallet_balance_vnd DEFAULT (0);
    IF COL_LENGTH(N'dbo.users', N'active_service_plan') IS NULL
        ALTER TABLE dbo.users ADD active_service_plan VARCHAR(10) NULL;
    IF COL_LENGTH(N'dbo.users', N'service_plan_started_on') IS NULL
        ALTER TABLE dbo.users ADD service_plan_started_on DATE NULL;
    IF COL_LENGTH(N'dbo.users', N'service_next_due_date') IS NULL
        ALTER TABLE dbo.users ADD service_next_due_date DATE NULL;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.users')
                   AND name=N'ck_users_wallet_balance_nonnegative')
        EXEC(N'ALTER TABLE dbo.users ADD CONSTRAINT ck_users_wallet_balance_nonnegative
            CHECK (wallet_balance_vnd >= 0)');
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.users')
                   AND name=N'ck_users_active_service_plan')
        EXEC(N'ALTER TABLE dbo.users ADD CONSTRAINT ck_users_active_service_plan
            CHECK (active_service_plan IS NULL OR active_service_plan IN (''P30'',''P90'',''P365''))');

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.horse_ownerships')
                   AND name=N'ux_horse_ownerships_one_owner')
        CREATE UNIQUE INDEX ux_horse_ownerships_one_owner ON dbo.horse_ownerships(horse_id);

    IF COL_LENGTH(N'dbo.module_records', N'payos_order_code') IS NULL
        ALTER TABLE dbo.module_records ADD payos_order_code BIGINT NULL;
    IF COL_LENGTH(N'dbo.module_records', N'payos_expected_amount_vnd') IS NULL
        ALTER TABLE dbo.module_records ADD payos_expected_amount_vnd DECIMAL(19,0) NULL;
    IF COL_LENGTH(N'dbo.module_records', N'payos_expires_at') IS NULL
        ALTER TABLE dbo.module_records ADD payos_expires_at DATETIMEOFFSET NULL;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.module_records')
                   AND name=N'ck_module_records_payos_order')
        EXEC(N'ALTER TABLE dbo.module_records ADD CONSTRAINT ck_module_records_payos_order CHECK (
            (payos_order_code IS NULL AND payos_expected_amount_vnd IS NULL AND payos_expires_at IS NULL)
            OR (payos_order_code > 0 AND payos_expected_amount_vnd >= 100000
                AND payos_expires_at IS NOT NULL AND user_id IS NOT NULL
                AND module_code=''SYSTEM'' AND record_type=''PAYOS_TOPUP''
                AND status IN (''PENDING'',''PAID'',''EXPIRED'',''CANCELLED'')))');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.module_records')
                   AND name=N'ux_module_records_payos_order_code')
        EXEC(N'CREATE UNIQUE INDEX ux_module_records_payos_order_code
            ON dbo.module_records(payos_order_code) WHERE payos_order_code IS NOT NULL');

    IF COL_LENGTH(N'dbo.financial_transactions', N'wallet_owner_user_id') IS NULL
        ALTER TABLE dbo.financial_transactions ADD wallet_owner_user_id UNIQUEIDENTIFIER NULL;
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.financial_transactions')
                   AND name=N'fk_financial_transactions_wallet_owner')
        ALTER TABLE dbo.financial_transactions ADD CONSTRAINT fk_financial_transactions_wallet_owner
            FOREIGN KEY (wallet_owner_user_id) REFERENCES dbo.users(id);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.financial_transactions')
                   AND name=N'ux_financial_transactions_wallet_source')
        EXEC(N'CREATE UNIQUE INDEX ux_financial_transactions_wallet_source
            ON dbo.financial_transactions(wallet_owner_user_id,category,source_entity_id)
            WHERE wallet_owner_user_id IS NOT NULL AND source_entity_id IS NOT NULL');

    DECLARE @categoryDefinition NVARCHAR(MAX) = (
        SELECT definition FROM sys.check_constraints
        WHERE parent_object_id=OBJECT_ID(N'dbo.financial_transactions')
          AND name=N'ck_financial_transactions_category');
    IF @categoryDefinition IS NULL
        THROW 51083, N'Expected financial category constraint was not found; review the live schema.', 1;
    IF @categoryDefinition NOT LIKE N'%FEED_COST%'
       OR @categoryDefinition NOT LIKE N'%MEDICAL_COST%'
       OR @categoryDefinition NOT LIKE N'%OPERATING_COST%'
       OR @categoryDefinition NOT LIKE N'%PRIZE_REVENUE%'
        THROW 51088, N'Financial categories differ from the reviewed schema.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id=OBJECT_ID(N'dbo.financial_transactions')
                     AND name=N'ck_financial_transactions_category'
                     AND definition LIKE N'%WALLET_TOPUP%')
    BEGIN
        ALTER TABLE dbo.financial_transactions DROP CONSTRAINT ck_financial_transactions_category;
        ALTER TABLE dbo.financial_transactions ADD CONSTRAINT ck_financial_transactions_category CHECK
            (category IN ('FEED_COST','MEDICAL_COST','OPERATING_COST','PRIZE_REVENUE',
                          'WALLET_TOPUP','SERVICE_FEE','LATE_FEE','PLAN_ADJUSTMENT'));
    END;

    IF (SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0 AND name<>N'sysdiagrams') > 30
        THROW 51084, N'Wallet migration would exceed the 30-table limit.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
