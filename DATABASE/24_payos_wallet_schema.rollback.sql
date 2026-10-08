/* Roll back migration 24 only before any real wallet or PayOS activity exists. */
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @hasWalletState INT = 0;
    DECLARE @hasPayosOrders INT = 0;
    DECLARE @hasWalletLedger INT = 0;
    IF COL_LENGTH(N'dbo.users', N'wallet_balance_vnd') IS NOT NULL
        EXEC sp_executesql N'SELECT @result = COUNT(*) FROM dbo.users WHERE wallet_balance_vnd <> 0
            OR active_service_plan IS NOT NULL OR service_next_due_date IS NOT NULL',
            N'@result INT OUTPUT', @result=@hasWalletState OUTPUT;
    IF @hasWalletState > 0
        THROW 51085, N'Wallet state exists; use a compensating migration instead of dropping it.', 1;
    IF COL_LENGTH(N'dbo.module_records', N'payos_order_code') IS NOT NULL
        EXEC sp_executesql N'SELECT @result = COUNT(*) FROM dbo.module_records WHERE payos_order_code IS NOT NULL',
            N'@result INT OUTPUT', @result=@hasPayosOrders OUTPUT;
    IF @hasPayosOrders > 0
        THROW 51086, N'PayOS orders exist; use a compensating migration instead of dropping them.', 1;
    IF COL_LENGTH(N'dbo.financial_transactions', N'wallet_owner_user_id') IS NOT NULL
        EXEC sp_executesql N'SELECT @result = COUNT(*) FROM dbo.financial_transactions WHERE wallet_owner_user_id IS NOT NULL',
            N'@result INT OUTPUT', @result=@hasWalletLedger OUTPUT;
    IF @hasWalletLedger > 0
        THROW 51087, N'Wallet ledger exists; use a compensating migration instead of dropping it.', 1;

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.financial_transactions')
               AND name=N'ux_financial_transactions_wallet_source')
        DROP INDEX ux_financial_transactions_wallet_source ON dbo.financial_transactions;
    IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.financial_transactions')
               AND name=N'fk_financial_transactions_wallet_owner')
        ALTER TABLE dbo.financial_transactions DROP CONSTRAINT fk_financial_transactions_wallet_owner;
    IF COL_LENGTH(N'dbo.financial_transactions', N'wallet_owner_user_id') IS NOT NULL
        ALTER TABLE dbo.financial_transactions DROP COLUMN wallet_owner_user_id;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.financial_transactions')
               AND name=N'ck_financial_transactions_category' AND definition LIKE N'%WALLET_TOPUP%')
    BEGIN
        ALTER TABLE dbo.financial_transactions DROP CONSTRAINT ck_financial_transactions_category;
        ALTER TABLE dbo.financial_transactions ADD CONSTRAINT ck_financial_transactions_category CHECK
            (category IN ('FEED_COST','MEDICAL_COST','OPERATING_COST','PRIZE_REVENUE'));
    END;

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.module_records')
               AND name=N'ux_module_records_payos_order_code')
        DROP INDEX ux_module_records_payos_order_code ON dbo.module_records;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.module_records')
               AND name=N'ck_module_records_payos_order')
        ALTER TABLE dbo.module_records DROP CONSTRAINT ck_module_records_payos_order;
    IF COL_LENGTH(N'dbo.module_records', N'payos_order_code') IS NOT NULL
        ALTER TABLE dbo.module_records DROP COLUMN payos_order_code;
    IF COL_LENGTH(N'dbo.module_records', N'payos_expected_amount_vnd') IS NOT NULL
        ALTER TABLE dbo.module_records DROP COLUMN payos_expected_amount_vnd;
    IF COL_LENGTH(N'dbo.module_records', N'payos_expires_at') IS NOT NULL
        ALTER TABLE dbo.module_records DROP COLUMN payos_expires_at;

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.horse_ownerships')
               AND name=N'ux_horse_ownerships_one_owner')
        DROP INDEX ux_horse_ownerships_one_owner ON dbo.horse_ownerships;

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.users')
               AND name=N'ck_users_wallet_balance_nonnegative')
        ALTER TABLE dbo.users DROP CONSTRAINT ck_users_wallet_balance_nonnegative;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.users')
               AND name=N'ck_users_active_service_plan')
        ALTER TABLE dbo.users DROP CONSTRAINT ck_users_active_service_plan;
    IF EXISTS (SELECT 1 FROM sys.default_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.users')
               AND name=N'df_users_wallet_balance_vnd')
        ALTER TABLE dbo.users DROP CONSTRAINT df_users_wallet_balance_vnd;
    IF COL_LENGTH(N'dbo.users', N'wallet_balance_vnd') IS NOT NULL
        ALTER TABLE dbo.users DROP COLUMN wallet_balance_vnd;
    IF COL_LENGTH(N'dbo.users', N'active_service_plan') IS NOT NULL
        ALTER TABLE dbo.users DROP COLUMN active_service_plan;
    IF COL_LENGTH(N'dbo.users', N'service_plan_started_on') IS NOT NULL
        ALTER TABLE dbo.users DROP COLUMN service_plan_started_on;
    IF COL_LENGTH(N'dbo.users', N'service_next_due_date') IS NOT NULL
        ALTER TABLE dbo.users DROP COLUMN service_next_due_date;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
