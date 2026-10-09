-- =========================================================================
-- 08_compact_records.sql
-- Unified Compact Storage Table (module_records) & PayOS Wallet Integration
-- Target Database: racehorse_local
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
GO

USE [racehorse_local];
GO

-- Module Records (Unified semi-structured record repository for stalls, inventory, nutrition & PayOS)
IF OBJECT_ID(N'dbo.module_records', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.module_records (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_module_records PRIMARY KEY,
        module_code VARCHAR(30) NOT NULL,
        record_type VARCHAR(80) NOT NULL,
        source_table VARCHAR(128) NULL,
        horse_id UNIQUEIDENTIFIER NULL,
        user_id UNIQUEIDENTIFIER NULL,
        status VARCHAR(30) NULL,
        event_date DATE NULL,
        data_json NVARCHAR(MAX) NOT NULL,
        payos_order_code BIGINT NULL,
        payos_expected_amount_vnd DECIMAL(19,0) NULL,
        payos_expires_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_module_records_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_module_records_updated_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT ck_module_records_module CHECK (module_code IN
            ('PROFILE', 'TRAINING', 'MEDICAL', 'STABLE_CARE', 'INVENTORY', 'RACING', 'REPORTING', 'SYSTEM')),
        CONSTRAINT ck_module_records_json CHECK (ISJSON(data_json) = 1),
        CONSTRAINT ck_module_records_payos_order CHECK (
            (payos_order_code IS NULL AND payos_expected_amount_vnd IS NULL AND payos_expires_at IS NULL)
            OR (payos_order_code > 0 AND payos_expected_amount_vnd >= 100000
                AND payos_expires_at IS NOT NULL AND user_id IS NOT NULL
                AND module_code = 'SYSTEM' AND record_type = 'PAYOS_TOPUP'
                AND status IN ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED'))),
        CONSTRAINT fk_module_records_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_module_records_user FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );
    CREATE NONCLUSTERED INDEX idx_module_records_lookup ON dbo.module_records(module_code, record_type, event_date);
    CREATE NONCLUSTERED INDEX idx_module_records_horse ON dbo.module_records(horse_id, module_code, record_type);
    CREATE UNIQUE NONCLUSTERED INDEX ux_module_records_payos_order_code
        ON dbo.module_records(payos_order_code)
        WHERE payos_order_code IS NOT NULL;
END;
GO
