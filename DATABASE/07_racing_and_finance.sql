-- =========================================================================
-- 07_racing_and_finance.sql
-- Race Events, Race Results, Financial Transactions, Owner Reports & Audit Logs
-- Target Database: racehorse_local
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
GO

USE [racehorse_local];
GO

-- 1. Race Events (Competitions, Classes & Requirements)
IF OBJECT_ID(N'dbo.race_events', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.race_events (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_race_events PRIMARY KEY,
        race_name NVARCHAR(200) NOT NULL,
        racecourse NVARCHAR(200) NOT NULL,
        race_date DATE NOT NULL,
        registration_deadline DATETIMEOFFSET NOT NULL,
        distance_meters DECIMAL(12,2) NOT NULL,
        race_class NVARCHAR(100) NOT NULL,
        minimum_age_years INT NULL,
        maximum_age_years INT NULL,
        minimum_weight_kg DECIMAL(7,2) NULL,
        maximum_weight_kg DECIMAL(7,2) NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_race_events_active DEFAULT 1,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_race_events_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_race_events_updated_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_race_events_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_race_events_distance CHECK (distance_meters > 0),
        CONSTRAINT ck_race_events_age_min CHECK (minimum_age_years IS NULL OR minimum_age_years >= 0),
        CONSTRAINT ck_race_events_age_max CHECK (maximum_age_years IS NULL OR maximum_age_years >= 0),
        CONSTRAINT ck_race_events_age_range CHECK (minimum_age_years IS NULL OR maximum_age_years IS NULL OR minimum_age_years <= maximum_age_years),
        CONSTRAINT ck_race_events_weight_min CHECK (minimum_weight_kg IS NULL OR minimum_weight_kg > 0),
        CONSTRAINT ck_race_events_weight_max CHECK (maximum_weight_kg IS NULL OR maximum_weight_kg > 0),
        CONSTRAINT ck_race_events_weight_range CHECK (minimum_weight_kg IS NULL OR maximum_weight_kg IS NULL OR minimum_weight_kg <= maximum_weight_kg)
    );
    CREATE NONCLUSTERED INDEX idx_race_events_upcoming ON dbo.race_events(race_date, registration_deadline, is_active);
END;
GO

-- 2. Race Results (Horse Race History & Finishes)
IF OBJECT_ID(N'dbo.race_results', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.race_results (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_race_results PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        race_date DATE NOT NULL,
        race_name NVARCHAR(200) NOT NULL,
        racecourse NVARCHAR(200) NOT NULL,
        distance VARCHAR(50) NOT NULL,
        finish_position INT NOT NULL,
        finish_time VARCHAR(50) NOT NULL,
        jockey NVARCHAR(150) NOT NULL,
        track_condition NVARCHAR(100) NULL,
        prize_money VARCHAR(100) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_race_results_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_race_results_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_race_results_version DEFAULT 0,
        CONSTRAINT fk_race_results_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id) ON DELETE CASCADE,
        CONSTRAINT ck_race_results_finish_position CHECK (finish_position >= 1)
    );
    CREATE NONCLUSTERED INDEX idx_race_results_horse_date ON dbo.race_results(horse_id, race_date DESC);
END;
GO

-- 3. Financial Transactions (Revenues & Expenses)
IF OBJECT_ID(N'dbo.financial_transactions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.financial_transactions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_financial_transactions PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NULL,
        stable_id UNIQUEIDENTIFIER NULL,
        transaction_date DATE NOT NULL,
        transaction_type VARCHAR(10) NOT NULL,
        category VARCHAR(30) NOT NULL,
        amount DECIMAL(19,2) NOT NULL,
        currency CHAR(3) NOT NULL CONSTRAINT df_financial_transactions_currency DEFAULT 'VND',
        description NVARCHAR(2000) NOT NULL,
        source_entity_type VARCHAR(80) NULL,
        source_entity_id UNIQUEIDENTIFIER NULL,
        recorded_by_user_id UNIQUEIDENTIFIER NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_financial_transactions_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_financial_transactions_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_financial_transactions_stable FOREIGN KEY (stable_id) REFERENCES dbo.stables(id),
        CONSTRAINT fk_financial_transactions_user FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_financial_transactions_type CHECK (transaction_type IN ('EXPENSE', 'REVENUE')),
        CONSTRAINT ck_financial_transactions_category CHECK (category IN ('FEED_COST', 'MEDICAL_COST', 'OPERATING_COST', 'PRIZE_REVENUE')),
        CONSTRAINT ck_financial_transactions_amount CHECK (amount >= 0)
    );
    CREATE NONCLUSTERED INDEX idx_financial_transactions_period ON dbo.financial_transactions(transaction_date, transaction_type, category);
    CREATE NONCLUSTERED INDEX idx_financial_transactions_horse ON dbo.financial_transactions(horse_id, transaction_date);
END;
GO

-- 4. Owner Reports (Generated Owner Periodic Summaries)
IF OBJECT_ID(N'dbo.owner_reports', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.owner_reports (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_owner_reports PRIMARY KEY,
        owner_user_id UNIQUEIDENTIFIER NOT NULL,
        period_start DATE NOT NULL,
        period_end DATE NOT NULL,
        generated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_owner_reports_generated_at DEFAULT SYSDATETIMEOFFSET(),
        generated_by_user_id UNIQUEIDENTIFIER NOT NULL,
        report_data_json NVARCHAR(MAX) NULL,
        horse_ids_json NVARCHAR(MAX) NOT NULL CONSTRAINT df_owner_reports_horses DEFAULT N'[]',
        transaction_ids_json NVARCHAR(MAX) NOT NULL CONSTRAINT df_owner_reports_transactions DEFAULT N'[]',
        pdf_file_id UNIQUEIDENTIFIER NULL,
        CONSTRAINT fk_owner_reports_owner FOREIGN KEY (owner_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_owner_reports_generator FOREIGN KEY (generated_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_owner_reports_pdf FOREIGN KEY (pdf_file_id) REFERENCES dbo.stored_files(id),
        CONSTRAINT ck_owner_reports_period CHECK (period_start <= period_end)
    );
    CREATE NONCLUSTERED INDEX idx_owner_reports_owner_period ON dbo.owner_reports(owner_user_id, period_end DESC);
END;
GO

-- 5. Notifications
IF OBJECT_ID(N'dbo.notifications', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.notifications (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_notifications PRIMARY KEY,
        recipient_user_id UNIQUEIDENTIFIER NOT NULL,
        notification_type VARCHAR(50) NOT NULL,
        priority VARCHAR(10) NOT NULL CONSTRAINT df_notifications_priority DEFAULT 'NORMAL',
        title NVARCHAR(200) NOT NULL,
        message NVARCHAR(2000) NOT NULL,
        related_entity_type VARCHAR(80) NULL,
        related_entity_id UNIQUEIDENTIFIER NULL,
        deduplication_key VARCHAR(200) NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_notifications_created_at DEFAULT SYSDATETIMEOFFSET(),
        read_at DATETIMEOFFSET NULL,
        CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_user_id) REFERENCES dbo.users(id) ON DELETE CASCADE,
        CONSTRAINT ck_notifications_priority CHECK (priority IN ('NORMAL', 'HIGH'))
    );
    CREATE NONCLUSTERED INDEX idx_notifications_recipient ON dbo.notifications(recipient_user_id, read_at, created_at DESC);
END;
GO

-- 6. Audit Logs (Append-Only Governance Trail)
IF OBJECT_ID(N'dbo.audit_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.audit_logs (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_audit_logs PRIMARY KEY,
        actor_user_id UNIQUEIDENTIFIER NULL,
        occurred_at DATETIMEOFFSET NOT NULL CONSTRAINT df_audit_logs_occurred_at DEFAULT SYSDATETIMEOFFSET(),
        module_code VARCHAR(50) NOT NULL,
        action_code VARCHAR(30) NOT NULL,
        entity_type VARCHAR(80) NULL,
        entity_id UNIQUEIDENTIFIER NULL,
        description NVARCHAR(2000) NOT NULL,
        old_values_json NVARCHAR(MAX) NULL,
        new_values_json NVARCHAR(MAX) NULL,
        ip_address VARCHAR(45) NULL,
        user_agent VARCHAR(500) NULL,
        CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_audit_logs_old_json CHECK (old_values_json IS NULL OR ISJSON(old_values_json)=1),
        CONSTRAINT ck_audit_logs_new_json CHECK (new_values_json IS NULL OR ISJSON(new_values_json)=1)
    );
    CREATE INDEX idx_audit_logs_time ON dbo.audit_logs(occurred_at DESC);
    CREATE INDEX idx_audit_logs_actor ON dbo.audit_logs(actor_user_id, occurred_at DESC);
    CREATE INDEX idx_audit_logs_module_action ON dbo.audit_logs(module_code, action_code, occurred_at DESC);
END;
GO

-- Trigger: Audit Logs are strictly append-only
CREATE OR ALTER TRIGGER dbo.tr_audit_logs_append_only
ON dbo.audit_logs
INSTEAD OF UPDATE, DELETE
AS
BEGIN
    THROW 51020, N'Nhật ký kiểm toán là dữ liệu chỉ ghi thêm, không được sửa hoặc xóa.', 1;
END;
GO
