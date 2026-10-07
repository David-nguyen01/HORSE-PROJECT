-- =========================================================================
-- 08_compact_records.sql
-- Unified Compact Storage Table (module_records)
-- Target Database: racehorse_local
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
GO

USE [racehorse_local];
GO

-- Module Records (Unified semi-structured record repository for stalls, inventory & nutrition)
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
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_module_records_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_module_records_updated_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT ck_module_records_module CHECK (module_code IN
            ('PROFILE', 'TRAINING', 'MEDICAL', 'STABLE_CARE', 'INVENTORY', 'RACING', 'REPORTING', 'SYSTEM')),
        CONSTRAINT ck_module_records_json CHECK (ISJSON(data_json) = 1),
        CONSTRAINT fk_module_records_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_module_records_user FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );
    CREATE NONCLUSTERED INDEX idx_module_records_lookup ON dbo.module_records(module_code, record_type, event_date);
    CREATE NONCLUSTERED INDEX idx_module_records_horse ON dbo.module_records(horse_id, module_code, record_type);
END;
GO
