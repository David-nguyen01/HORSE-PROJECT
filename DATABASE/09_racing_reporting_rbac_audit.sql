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

/* FR18 */
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
    CREATE INDEX idx_race_events_upcoming ON dbo.race_events(race_date, registration_deadline, is_active);
END;
GO

IF OBJECT_ID(N'dbo.race_registrations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.race_registrations (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_race_registrations PRIMARY KEY,
        race_event_id UNIQUEIDENTIFIER NOT NULL,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        registered_by_user_id UNIQUEIDENTIFIER NOT NULL,
        registered_weight_kg DECIMAL(7,2) NULL,
        status VARCHAR(15) NOT NULL,
        submitted_at DATETIMEOFFSET NOT NULL,
        confirmed_at DATETIMEOFFSET NULL,
        withdrawn_at DATETIMEOFFSET NULL,
        withdrawn_by_user_id UNIQUEIDENTIFIER NULL,
        CONSTRAINT fk_race_registrations_event FOREIGN KEY (race_event_id) REFERENCES dbo.race_events(id),
        CONSTRAINT fk_race_registrations_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_race_registrations_user FOREIGN KEY (registered_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_race_registrations_withdrawer FOREIGN KEY (withdrawn_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT uq_race_registrations_horse UNIQUE (race_event_id, horse_id),
        CONSTRAINT ck_race_registrations_weight CHECK (registered_weight_kg IS NULL OR registered_weight_kg > 0),
        CONSTRAINT ck_race_registrations_status CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'WITHDRAWN')),
        CONSTRAINT ck_race_registrations_withdraw CHECK (
            (status <> 'WITHDRAWN' AND withdrawn_at IS NULL AND withdrawn_by_user_id IS NULL)
            OR (status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL AND withdrawn_by_user_id IS NOT NULL))
    );
    CREATE INDEX idx_race_registrations_event_status ON dbo.race_registrations(race_event_id, status);
END;
GO

/* FR19 and FR22 use the same traceable ledger. */
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
        currency CHAR(3) NOT NULL,
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
    CREATE INDEX idx_financial_transactions_period ON dbo.financial_transactions(transaction_date, transaction_type, category);
    CREATE INDEX idx_financial_transactions_horse ON dbo.financial_transactions(horse_id, transaction_date);
END;
GO

IF OBJECT_ID(N'dbo.owner_reports', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.owner_reports (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_owner_reports PRIMARY KEY,
        owner_user_id UNIQUEIDENTIFIER NOT NULL,
        period_start DATE NOT NULL,
        period_end DATE NOT NULL,
        generated_at DATETIMEOFFSET NOT NULL,
        generated_by_user_id UNIQUEIDENTIFIER NOT NULL,
        pdf_file_id UNIQUEIDENTIFIER NULL,
        CONSTRAINT fk_owner_reports_owner FOREIGN KEY (owner_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_owner_reports_generator FOREIGN KEY (generated_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_owner_reports_pdf FOREIGN KEY (pdf_file_id) REFERENCES dbo.stored_files(id),
        CONSTRAINT ck_owner_reports_period CHECK (period_start <= period_end)
    );
    CREATE INDEX idx_owner_reports_owner_period ON dbo.owner_reports(owner_user_id, period_end DESC);
END;
GO

IF OBJECT_ID(N'dbo.owner_report_horses', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.owner_report_horses (
        owner_report_id UNIQUEIDENTIFIER NOT NULL,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT pk_owner_report_horses PRIMARY KEY (owner_report_id, horse_id),
        CONSTRAINT fk_owner_report_horses_report FOREIGN KEY (owner_report_id) REFERENCES dbo.owner_reports(id),
        CONSTRAINT fk_owner_report_horses_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id)
    );
END;
GO

IF OBJECT_ID(N'dbo.owner_report_transactions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.owner_report_transactions (
        owner_report_id UNIQUEIDENTIFIER NOT NULL,
        financial_transaction_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT pk_owner_report_transactions PRIMARY KEY (owner_report_id, financial_transaction_id),
        CONSTRAINT fk_owner_report_transactions_report FOREIGN KEY (owner_report_id) REFERENCES dbo.owner_reports(id),
        CONSTRAINT fk_owner_report_transactions_tx FOREIGN KEY (financial_transaction_id) REFERENCES dbo.financial_transactions(id)
    );
END;
GO

/* FR21 database foundation for editable RBAC. Existing user_roles remains intact for current backend compatibility. */
IF OBJECT_ID(N'dbo.role_definitions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.role_definitions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_role_definitions PRIMARY KEY,
        code VARCHAR(40) NOT NULL,
        display_name NVARCHAR(150) NOT NULL,
        is_system BIT NOT NULL CONSTRAINT df_role_definitions_system DEFAULT 0,
        is_active BIT NOT NULL CONSTRAINT df_role_definitions_active DEFAULT 1,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_role_definitions_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_role_definitions_updated_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT uq_role_definitions_code UNIQUE (code)
    );
END;
GO

IF OBJECT_ID(N'dbo.module_permissions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.module_permissions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_module_permissions PRIMARY KEY,
        module_code VARCHAR(30) NOT NULL,
        action_code VARCHAR(20) NOT NULL,
        CONSTRAINT uq_module_permissions UNIQUE (module_code, action_code),
        CONSTRAINT ck_module_permissions_module CHECK (module_code IN ('PROFILE', 'TRAINING', 'MEDICAL', 'STABLE_CARE', 'RACING', 'REPORTING')),
        CONSTRAINT ck_module_permissions_action CHECK (action_code IN ('VIEW', 'EDIT', 'APPROVE'))
    );
END;
GO

IF OBJECT_ID(N'dbo.role_permission_assignments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.role_permission_assignments (
        role_definition_id UNIQUEIDENTIFIER NOT NULL,
        module_permission_id UNIQUEIDENTIFIER NOT NULL,
        granted BIT NOT NULL,
        changed_by_user_id UNIQUEIDENTIFIER NOT NULL,
        changed_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT pk_role_permission_assignments PRIMARY KEY (role_definition_id, module_permission_id),
        CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_definition_id) REFERENCES dbo.role_definitions(id),
        CONSTRAINT fk_role_permissions_permission FOREIGN KEY (module_permission_id) REFERENCES dbo.module_permissions(id),
        CONSTRAINT fk_role_permissions_user FOREIGN KEY (changed_by_user_id) REFERENCES dbo.users(id)
    );
END;
GO

IF OBJECT_ID(N'dbo.user_role_assignments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.user_role_assignments (
        user_id UNIQUEIDENTIFIER NOT NULL,
        role_definition_id UNIQUEIDENTIFIER NOT NULL,
        assigned_by_user_id UNIQUEIDENTIFIER NOT NULL,
        assigned_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT pk_user_role_assignments PRIMARY KEY (user_id, role_definition_id),
        CONSTRAINT fk_user_role_assignments_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_user_role_assignments_role FOREIGN KEY (role_definition_id) REFERENCES dbo.role_definitions(id),
        CONSTRAINT fk_user_role_assignments_assigner FOREIGN KEY (assigned_by_user_id) REFERENCES dbo.users(id)
    );
END;
GO

DECLARE @roles TABLE (code VARCHAR(40), display_name NVARCHAR(150));
INSERT INTO @roles VALUES
('CLUB_MANAGER', N'Quản lý Câu lạc bộ'),
('HEAD_TRAINER', N'Huấn luyện viên trưởng'),
('STABLE_STAFF', N'Người chăm sóc / Nhân viên chuồng'),
('HORSE_OWNER', N'Chủ sở hữu Ngựa'),
('VETERINARIAN', N'Bác sĩ thú y');

INSERT INTO dbo.role_definitions(id, code, display_name, is_system, is_active)
SELECT NEWID(), r.code, r.display_name, 1, 1
FROM @roles r
WHERE NOT EXISTS (SELECT 1 FROM dbo.role_definitions d WHERE d.code=r.code);
GO

DECLARE @modules TABLE (code VARCHAR(30));
INSERT INTO @modules VALUES ('PROFILE'),('TRAINING'),('MEDICAL'),('STABLE_CARE'),('RACING'),('REPORTING');
DECLARE @actions TABLE (code VARCHAR(20));
INSERT INTO @actions VALUES ('VIEW'),('EDIT'),('APPROVE');
INSERT INTO dbo.module_permissions(id, module_code, action_code)
SELECT NEWID(), m.code, a.code FROM @modules m CROSS JOIN @actions a
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.module_permissions p WHERE p.module_code=m.code AND p.action_code=a.code);
GO

/* FR23 append-only audit. */
IF OBJECT_ID(N'dbo.audit_settings', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.audit_settings (
        id TINYINT NOT NULL CONSTRAINT pk_audit_settings PRIMARY KEY,
        retention_days INT NOT NULL,
        updated_by_user_id UNIQUEIDENTIFIER NOT NULL,
        updated_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT ck_audit_settings_singleton CHECK (id = 1),
        CONSTRAINT ck_audit_settings_retention CHECK (retention_days > 0),
        CONSTRAINT fk_audit_settings_user FOREIGN KEY (updated_by_user_id) REFERENCES dbo.users(id)
    );
END;
GO

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

CREATE OR ALTER TRIGGER dbo.tr_audit_logs_append_only
ON dbo.audit_logs
INSTEAD OF UPDATE, DELETE
AS
BEGIN
    THROW 51020, N'Nhật ký kiểm toán là dữ liệu chỉ ghi thêm, không được sửa hoặc xóa.', 1;
END;
GO
