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

/*
 * Compact schema: keep high-traffic transactional tables relational and merge
 * the not-yet-implemented medical/stable/inventory submodules into one JSON-backed table.
 */
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
            ('PROFILE','TRAINING','MEDICAL','STABLE_CARE','INVENTORY','RACING','REPORTING','SYSTEM')),
        CONSTRAINT ck_module_records_json CHECK (ISJSON(data_json)=1),
        CONSTRAINT fk_module_records_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_module_records_user FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );
    CREATE INDEX idx_module_records_lookup ON dbo.module_records(module_code, record_type, event_date);
    CREATE INDEX idx_module_records_horse ON dbo.module_records(horse_id, module_code, record_type);
END;
GO

/* Archive existing rows before compacting tables that do not yet have backend APIs. */
IF EXISTS (SELECT 1 FROM dbo.horse_additional_files)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'PROFILE','HORSE_ADDITIONAL_FILES','horse_additional_files',(SELECT * FROM dbo.horse_additional_files FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.training_evaluation_files)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'TRAINING','TRAINING_EVALUATION_FILES','training_evaluation_files',(SELECT * FROM dbo.training_evaluation_files FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.horse_health_status_history)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'MEDICAL','HEALTH_STATUS_HISTORY','horse_health_status_history',(SELECT * FROM dbo.horse_health_status_history FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.medical_examinations)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'MEDICAL','MEDICAL_EXAMINATIONS','medical_examinations',(SELECT * FROM dbo.medical_examinations FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.medical_examination_addenda)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'MEDICAL','MEDICAL_EXAMINATION_ADDENDA','medical_examination_addenda',(SELECT * FROM dbo.medical_examination_addenda FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.injury_markers)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'MEDICAL','INJURY_MARKERS','injury_markers',(SELECT * FROM dbo.injury_markers FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.injury_recovery_updates)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'MEDICAL','INJURY_RECOVERY_UPDATES','injury_recovery_updates',(SELECT * FROM dbo.injury_recovery_updates FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.stalls)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'STABLE_CARE','STALLS','stalls',(SELECT * FROM dbo.stalls FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.horse_stall_assignments)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'STABLE_CARE','HORSE_STALL_ASSIGNMENTS','horse_stall_assignments',(SELECT * FROM dbo.horse_stall_assignments FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.horse_daily_routines)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'STABLE_CARE','HORSE_DAILY_ROUTINES','horse_daily_routines',(SELECT * FROM dbo.horse_daily_routines FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.nutrition_plans)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'STABLE_CARE','NUTRITION_PLANS','nutrition_plans',(SELECT * FROM dbo.nutrition_plans FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.daily_tasks)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'STABLE_CARE','DAILY_TASKS','daily_tasks',(SELECT * FROM dbo.daily_tasks FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.incident_reports)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'STABLE_CARE','INCIDENT_REPORTS','incident_reports',(SELECT * FROM dbo.incident_reports FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.inventory_categories)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'INVENTORY','INVENTORY_CATEGORIES','inventory_categories',(SELECT * FROM dbo.inventory_categories FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.inventory_items)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'INVENTORY','INVENTORY_ITEMS','inventory_items',(SELECT * FROM dbo.inventory_items FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.storage_areas)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'INVENTORY','STORAGE_AREAS','storage_areas',(SELECT * FROM dbo.storage_areas FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.inventory_balances)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'INVENTORY','INVENTORY_BALANCES','inventory_balances',(SELECT * FROM dbo.inventory_balances FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.inventory_transactions)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'INVENTORY','INVENTORY_TRANSACTIONS','inventory_transactions',(SELECT * FROM dbo.inventory_transactions FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.replenishment_requests)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'INVENTORY','REPLENISHMENT_REQUESTS','replenishment_requests',(SELECT * FROM dbo.replenishment_requests FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.race_registrations)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'RACING','RACE_REGISTRATIONS','race_registrations',(SELECT * FROM dbo.race_registrations FOR JSON PATH);
IF EXISTS (SELECT 1 FROM dbo.audit_settings)
    INSERT dbo.module_records(id,module_code,record_type,source_table,data_json)
    SELECT NEWID(),'SYSTEM','AUDIT_SETTINGS','audit_settings',(SELECT * FROM dbo.audit_settings FOR JSON PATH);
GO

/* Current health is one-to-one with a horse, so it belongs on the horse aggregate. */
IF COL_LENGTH(N'dbo.horses',N'health_status') IS NULL
BEGIN
    ALTER TABLE dbo.horses ADD
        health_status VARCHAR(20) NULL,
        health_changed_by_user_id UNIQUEIDENTIFIER NULL,
        health_source_type VARCHAR(30) NULL,
        health_source_id UNIQUEIDENTIFIER NULL,
        health_changed_at DATETIMEOFFSET NULL;
END;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_horses_health_status')
    ALTER TABLE dbo.horses ADD CONSTRAINT ck_horses_health_status
        CHECK (health_status IS NULL OR health_status IN ('FIT','MONITOR','INJURED','QUARANTINED'));
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_horses_health_changed_by')
    ALTER TABLE dbo.horses ADD CONSTRAINT fk_horses_health_changed_by
        FOREIGN KEY(health_changed_by_user_id) REFERENCES dbo.users(id);
GO
IF OBJECT_ID(N'dbo.horse_health_status',N'U') IS NOT NULL
BEGIN
    UPDATE h SET health_status=s.health_status,health_changed_by_user_id=s.changed_by_user_id,
        health_source_type=s.source_type,health_source_id=s.source_id,health_changed_at=s.changed_at
    FROM dbo.horses h JOIN dbo.horse_health_status s ON s.horse_id=h.id
    WHERE h.health_status IS NULL OR s.changed_at>h.health_changed_at;
END;
GO

/* Three RBAC metadata tables become one direct role/module/action matrix. */
IF OBJECT_ID(N'dbo.role_permissions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.role_permissions (
        role_code VARCHAR(40) NOT NULL,
        module_code VARCHAR(30) NOT NULL,
        action_code VARCHAR(20) NOT NULL,
        granted BIT NOT NULL,
        changed_by_user_id UNIQUEIDENTIFIER NOT NULL,
        changed_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT pk_role_permissions PRIMARY KEY(role_code,module_code,action_code),
        CONSTRAINT ck_role_permissions_role CHECK (role_code IN
            ('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','STABLE_STAFF','HORSE_OWNER')),
        CONSTRAINT ck_role_permissions_module CHECK (module_code IN
            ('PROFILE','TRAINING','MEDICAL','STABLE_CARE','RACING','REPORTING')),
        CONSTRAINT ck_role_permissions_action CHECK (action_code IN ('VIEW','EDIT','APPROVE')),
        CONSTRAINT fk_compact_role_permissions_user FOREIGN KEY(changed_by_user_id) REFERENCES dbo.users(id)
    );
END;
GO

IF OBJECT_ID(N'dbo.role_permission_assignments', N'U') IS NOT NULL
BEGIN
    MERGE dbo.role_permissions target
    USING (
        SELECT rd.code role_code,mp.module_code,mp.action_code,rpa.granted,rpa.changed_by_user_id,rpa.changed_at
        FROM dbo.role_permission_assignments rpa
        JOIN dbo.role_definitions rd ON rd.id=rpa.role_definition_id
        JOIN dbo.module_permissions mp ON mp.id=rpa.module_permission_id
    ) source
    ON target.role_code=source.role_code AND target.module_code=source.module_code AND target.action_code=source.action_code
    WHEN NOT MATCHED THEN INSERT(role_code,module_code,action_code,granted,changed_by_user_id,changed_at)
        VALUES(source.role_code,source.module_code,source.action_code,source.granted,source.changed_by_user_id,source.changed_at);
END;
GO

/* Completion is a state of a preventive schedule, not a separate aggregate. */
IF COL_LENGTH(N'dbo.preventive_care_schedules',N'completed_by_user_id') IS NULL
BEGIN
    ALTER TABLE dbo.preventive_care_schedules ADD
        completed_by_user_id UNIQUEIDENTIFIER NULL,
        completed_at DATETIMEOFFSET NULL,
        completion_notes NVARCHAR(2000) NULL,
        next_schedule_id UNIQUEIDENTIFIER NULL;
END;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_preventive_care_completed_by')
    ALTER TABLE dbo.preventive_care_schedules ADD CONSTRAINT fk_preventive_care_completed_by
        FOREIGN KEY(completed_by_user_id) REFERENCES dbo.users(id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_preventive_care_next')
    ALTER TABLE dbo.preventive_care_schedules ADD CONSTRAINT fk_preventive_care_next
        FOREIGN KEY(next_schedule_id) REFERENCES dbo.preventive_care_schedules(id);
GO
IF OBJECT_ID(N'dbo.preventive_care_completions',N'U') IS NOT NULL
BEGIN
    UPDATE s SET completed_by_user_id=c.performed_by_user_id,completed_at=c.performed_at,
        completion_notes=c.notes,next_schedule_id=c.next_schedule_id
    FROM dbo.preventive_care_schedules s JOIN dbo.preventive_care_completions c ON c.schedule_id=s.id
    WHERE s.completed_at IS NULL;
END;
GO

/* Report membership is immutable report content, stored with the report itself. */
IF COL_LENGTH(N'dbo.owner_reports',N'horse_ids_json') IS NULL
    ALTER TABLE dbo.owner_reports ADD horse_ids_json NVARCHAR(MAX) NOT NULL
        CONSTRAINT df_owner_reports_horses DEFAULT N'[]';
GO
IF COL_LENGTH(N'dbo.owner_reports',N'transaction_ids_json') IS NULL
    ALTER TABLE dbo.owner_reports ADD transaction_ids_json NVARCHAR(MAX) NOT NULL
        CONSTRAINT df_owner_reports_transactions DEFAULT N'[]';
GO
IF OBJECT_ID(N'dbo.owner_report_horses',N'U') IS NOT NULL
    UPDATE r SET horse_ids_json=(SELECT h.horse_id AS id FROM dbo.owner_report_horses h WHERE h.owner_report_id=r.id FOR JSON PATH)
    FROM dbo.owner_reports r
    WHERE EXISTS(SELECT 1 FROM dbo.owner_report_horses h WHERE h.owner_report_id=r.id);
GO
IF OBJECT_ID(N'dbo.owner_report_transactions',N'U') IS NOT NULL
    UPDATE r SET transaction_ids_json=(SELECT t.financial_transaction_id AS id FROM dbo.owner_report_transactions t WHERE t.owner_report_id=r.id FOR JSON PATH)
    FROM dbo.owner_reports r
    WHERE EXISTS(SELECT 1 FROM dbo.owner_report_transactions t WHERE t.owner_report_id=r.id);
GO

/* Drop merged detail tables in dependency order. */
DROP TRIGGER IF EXISTS dbo.tr_horse_health_status_history;
DROP TABLE IF EXISTS dbo.daily_task_files;
DROP TABLE IF EXISTS dbo.incident_report_files;
DROP TABLE IF EXISTS dbo.injury_update_files;
DROP TABLE IF EXISTS dbo.training_evaluation_files;
DROP TABLE IF EXISTS dbo.nutrition_meal_items;
DROP TABLE IF EXISTS dbo.nutrition_plan_meals;
DROP TABLE IF EXISTS dbo.injury_recovery_updates;
DROP TABLE IF EXISTS dbo.medical_examination_addenda;
DROP TABLE IF EXISTS dbo.owner_report_transactions;
DROP TABLE IF EXISTS dbo.owner_report_horses;
DROP TABLE IF EXISTS dbo.preventive_care_completions;
DROP TABLE IF EXISTS dbo.daily_tasks;
DROP TABLE IF EXISTS dbo.incident_reports;
DROP TABLE IF EXISTS dbo.nutrition_plans;
DROP TABLE IF EXISTS dbo.injury_markers;
DROP TABLE IF EXISTS dbo.medical_examinations;
DROP TABLE IF EXISTS dbo.horse_health_status_history;
DROP TABLE IF EXISTS dbo.horse_health_status;
DROP TABLE IF EXISTS dbo.horse_additional_files;
DROP TABLE IF EXISTS dbo.race_registrations;
DROP TABLE IF EXISTS dbo.storage_area_staff;
DROP TABLE IF EXISTS dbo.inventory_transactions;
DROP TABLE IF EXISTS dbo.replenishment_requests;
DROP TABLE IF EXISTS dbo.inventory_balances;
DROP TABLE IF EXISTS dbo.horse_daily_routines;
DROP TABLE IF EXISTS dbo.horse_stall_assignments;
DROP TABLE IF EXISTS dbo.stalls;
DROP TABLE IF EXISTS dbo.storage_areas;
DROP TABLE IF EXISTS dbo.inventory_items;
DROP TABLE IF EXISTS dbo.inventory_categories;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_financial_transactions_stable')
    ALTER TABLE dbo.financial_transactions ADD CONSTRAINT fk_financial_transactions_stable
        FOREIGN KEY(stable_id) REFERENCES dbo.stables(id);
GO
DROP TABLE IF EXISTS dbo.audit_settings;
DROP TABLE IF EXISTS dbo.user_role_assignments;
DROP TABLE IF EXISTS dbo.role_permission_assignments;
DROP TABLE IF EXISTS dbo.module_permissions;
DROP TABLE IF EXISTS dbo.role_definitions;
GO

/* sysdiagrams is an SSMS support table, not a Racehorse business table. */
DECLARE @table_count INT=(SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0 AND name<>N'sysdiagrams');
IF @table_count > 30
    THROW 51030, N'Compact schema vượt quá giới hạn 30 bảng.', 1;
GO
