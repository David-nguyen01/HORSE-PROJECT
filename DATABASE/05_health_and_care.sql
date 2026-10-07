-- =========================================================================
-- 05_health_and_care.sql
-- Care Routine Definitions, Care Tasks & Preventive Care Schedules
-- Target Database: racehorse_local
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
GO

USE [racehorse_local];
GO

-- 1. Care Routine Definitions (Flow 4 Daily Facility & Horse Care Routines)
IF OBJECT_ID(N'dbo.care_routine_definitions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.care_routine_definitions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_care_routine_definitions PRIMARY KEY,
        code VARCHAR(50) NOT NULL CONSTRAINT uq_care_routine_definitions_code UNIQUE,
        title NVARCHAR(255) NOT NULL,
        description NVARCHAR(MAX) NULL,
        task_type VARCHAR(50) NOT NULL,
        scheduled_time TIME NOT NULL,
        scope VARCHAR(20) NOT NULL CONSTRAINT df_care_routine_scope DEFAULT 'FACILITY',
        is_active BIT NOT NULL CONSTRAINT df_care_routine_is_active DEFAULT 1,
        sort_order INT NOT NULL CONSTRAINT df_care_routine_sort_order DEFAULT 0,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_care_routine_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_care_routine_updated_at DEFAULT SYSDATETIMEOFFSET()
    );
END;
GO

-- 2. Care Tasks (Generated Daily Routine Instances & Ad-hoc Care Tasks)
IF OBJECT_ID(N'dbo.care_tasks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.care_tasks (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_care_tasks PRIMARY KEY,
        routine_definition_id UNIQUEIDENTIFIER NULL,
        task_category VARCHAR(20) NOT NULL CONSTRAINT df_care_tasks_category DEFAULT 'ROUTINE',
        scope VARCHAR(20) NOT NULL CONSTRAINT df_care_tasks_scope DEFAULT 'FACILITY',
        horse_id UNIQUEIDENTIFIER NULL,
        title NVARCHAR(255) NOT NULL,
        description NVARCHAR(MAX) NULL,
        task_type VARCHAR(50) NOT NULL,
        scheduled_date DATE NOT NULL,
        scheduled_time TIME NOT NULL,
        status VARCHAR(30) NOT NULL CONSTRAINT df_care_tasks_status DEFAULT 'PENDING',
        assigned_staff_id UNIQUEIDENTIFIER NULL,
        completed_at DATETIMEOFFSET NULL,
        completed_by_user_id UNIQUEIDENTIFIER NULL,
        completed_by_name NVARCHAR(100) NULL,
        completion_notes NVARCHAR(MAX) NULL,
        photo_url NVARCHAR(1000) NULL,
        photo_bytes VARBINARY(MAX) NULL,
        photo_content_type VARCHAR(100) NULL,
        created_by_user_id UNIQUEIDENTIFIER NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_care_tasks_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_care_tasks_updated_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_care_tasks_routine FOREIGN KEY (routine_definition_id) REFERENCES dbo.care_routine_definitions(id),
        CONSTRAINT fk_care_tasks_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_care_tasks_staff FOREIGN KEY (assigned_staff_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_care_tasks_completed_by FOREIGN KEY (completed_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_care_tasks_created_by FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id)
    );
    CREATE UNIQUE NONCLUSTERED INDEX uq_care_tasks_facility_routine_instance
        ON dbo.care_tasks (routine_definition_id, scheduled_date)
        WHERE routine_definition_id IS NOT NULL AND horse_id IS NULL;
    CREATE UNIQUE NONCLUSTERED INDEX uq_care_tasks_horse_routine_instance
        ON dbo.care_tasks (routine_definition_id, scheduled_date, horse_id)
        WHERE routine_definition_id IS NOT NULL AND horse_id IS NOT NULL;
    CREATE NONCLUSTERED INDEX idx_care_tasks_date_status
        ON dbo.care_tasks (scheduled_date, status);
END;
GO

-- 3. Preventive Care Schedules (Vaccination, Deworming & Farrier Recurrence)
IF OBJECT_ID(N'dbo.preventive_care_schedules', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.preventive_care_schedules (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_preventive_care_schedules PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        care_type VARCHAR(20) NOT NULL,
        details NVARCHAR(2000) NULL,
        due_date DATE NOT NULL,
        recurrence_days INT NULL,
        reminder_lead_days INT NOT NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        cancelled_at DATETIMEOFFSET NULL,
        completed_by_user_id UNIQUEIDENTIFIER NULL,
        completed_at DATETIMEOFFSET NULL,
        completion_notes NVARCHAR(2000) NULL,
        next_schedule_id UNIQUEIDENTIFIER NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_preventive_care_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_preventive_care_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_preventive_care_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_preventive_care_completed_by FOREIGN KEY (completed_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_preventive_care_next FOREIGN KEY (next_schedule_id) REFERENCES dbo.preventive_care_schedules(id),
        CONSTRAINT ck_preventive_care_type CHECK (care_type IN ('VACCINATION', 'DEWORMING', 'FARRIER')),
        CONSTRAINT ck_preventive_care_recurrence CHECK (recurrence_days IS NULL OR recurrence_days > 0),
        CONSTRAINT ck_preventive_care_reminder CHECK (reminder_lead_days >= 0)
    );
    CREATE NONCLUSTERED INDEX idx_preventive_care_due ON dbo.preventive_care_schedules(due_date, cancelled_at);
END;
GO
