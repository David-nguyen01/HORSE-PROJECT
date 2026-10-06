/*
 * Racehorse Training Management System
 * Complete Microsoft SQL Server database installation
 *
 * Covers 20 business modules derived from FR01-FR23:
 *  01 Horse profile, ownership, files and weight
 *  02 Pedigree and race history
 *  03 Fitness dashboard and training evaluation (FR03 + FR07)
 *  04 Phased training plans
 *  05 Daily schedules and trial-run bookings
 *  06 Realtime telemetry and alerts
 *  07 Health map and medical examinations (FR08 + FR09)
 *  08 3D injury markers and recovery
 *  09 Emergency training locks
 *  10 Preventive care
 *  11 Stable allocation and daily routines
 *  12 Nutrition plans
 *  13 Daily care tasks
 *  14 Stable incident reports
 *  15 Inventory and replenishment
 *  16 Race events and registrations
 *  17 Owner and club reporting (FR19 + FR22)
 *  18 Master data administration
 *  19 Fixed five-role RBAC
 *  20 Append-only audit log
 *
 * Shared infrastructure: users, login sessions, notifications and stored files.
 * This script is rerunnable. Hibernate remains ddl-auto=none.
 */

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
GO

/* ========================================================================== */
/* Source: 00_create_database.sql */
/* ========================================================================== */
USE [master];
GO

IF DB_ID(N'racehorse') IS NULL
BEGIN
    CREATE DATABASE [racehorse];
END;
GO

ALTER DATABASE [racehorse] SET RECOVERY SIMPLE;
GO

USE [racehorse];
GO

SELECT
    DB_NAME() AS database_name,
    SUSER_SNAME() AS executed_by;
GO

/* ========================================================================== */
/* Source: 01_create_schema.sql */
/* ========================================================================== */
USE [racehorse];
GO

IF OBJECT_ID(N'dbo.horses', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horses (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horses PRIMARY KEY,
        code VARCHAR(50) NOT NULL,
        name NVARCHAR(150) NOT NULL,
        microchip_passport_number VARCHAR(100) NULL,
        birth_date DATE NOT NULL,
        gender VARCHAR(20) NOT NULL,
        color_markings NVARCHAR(500) NOT NULL,
        breed NVARCHAR(100) NOT NULL,
        sire_id UNIQUEIDENTIFIER NULL,
        dam_id UNIQUEIDENTIFIER NULL,
        stable_location NVARCHAR(150) NOT NULL,
        status VARCHAR(20) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horses_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horses_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_horses_version DEFAULT 0,
        CONSTRAINT uq_horses_code UNIQUE (code),
        CONSTRAINT fk_horses_sire FOREIGN KEY (sire_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_horses_dam FOREIGN KEY (dam_id) REFERENCES dbo.horses(id),
        CONSTRAINT ck_horses_distinct_parents CHECK (sire_id IS NULL OR dam_id IS NULL OR sire_id <> dam_id)
    );

    CREATE UNIQUE INDEX uq_horses_microchip_passport
        ON dbo.horses (microchip_passport_number)
        WHERE microchip_passport_number IS NOT NULL;
    CREATE INDEX idx_horses_name ON dbo.horses (name);
    CREATE INDEX idx_horses_status ON dbo.horses (status);
    CREATE INDEX idx_horses_sire_id ON dbo.horses (sire_id);
    CREATE INDEX idx_horses_dam_id ON dbo.horses (dam_id);
END;
GO

IF OBJECT_ID(N'dbo.users', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.users (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_users PRIMARY KEY,
        username VARCHAR(80) NOT NULL,
        email VARCHAR(254) NOT NULL,
        password_hash VARCHAR(100) NOT NULL,
        enabled BIT NOT NULL CONSTRAINT df_users_enabled DEFAULT 1,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_users_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_users_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_users_version DEFAULT 0,
        CONSTRAINT uq_users_username UNIQUE (username),
        CONSTRAINT uq_users_email UNIQUE (email)
    );
END;
GO

IF OBJECT_ID(N'dbo.user_roles', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.user_roles (
        user_id UNIQUEIDENTIFIER NOT NULL,
        role VARCHAR(40) NOT NULL,
        CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
        CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE,
        CONSTRAINT ck_user_roles_role CHECK (role IN (
            'CLUB_MANAGER',
            'HEAD_TRAINER',
            'STABLE_STAFF',
            'HORSE_OWNER',
            'VETERINARIAN'
        ))
    );
END;
GO

IF OBJECT_ID(N'dbo.login_sessions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.login_sessions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_login_sessions PRIMARY KEY,
        user_id UNIQUEIDENTIFIER NOT NULL,
        refresh_token_hash VARCHAR(64) NOT NULL,
        ip_address VARCHAR(45) NULL,
        user_agent VARCHAR(500) NULL,
        last_activity_at DATETIMEOFFSET NOT NULL,
        expires_at DATETIMEOFFSET NOT NULL,
        revoked_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_login_sessions_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_login_sessions_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_login_sessions_version DEFAULT 0,
        CONSTRAINT fk_login_sessions_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE,
        CONSTRAINT uq_login_sessions_refresh_token_hash UNIQUE (refresh_token_hash)
    );

    CREATE INDEX idx_login_sessions_user_id ON dbo.login_sessions (user_id);
    CREATE INDEX idx_login_sessions_expires_at ON dbo.login_sessions (expires_at);
END;
GO

/* ========================================================================== */
/* Source: 02_seed_data.sql */
/* ========================================================================== */
USE [racehorse];
GO

DECLARE @admin_id UNIQUEIDENTIFIER;

SELECT @admin_id = id
FROM dbo.users
WHERE email = 'admin@gmail.com';

IF @admin_id IS NULL
BEGIN
    SET @admin_id = NEWID();

    INSERT INTO dbo.users (
        id,
        username,
        email,
        password_hash,
        enabled,
        created_at,
        updated_at,
        version
    )
    VALUES (
        @admin_id,
        'admin',
        'admin@gmail.com',
        '$2a$10$Sf0Drsuesp0Aw0q9W1FFY.8JkfFUgq7U7a991JY80e2zXzEBliH/u',
        1,
        SYSDATETIMEOFFSET(),
        SYSDATETIMEOFFSET(),
        0
    );
END;

IF NOT EXISTS (
    SELECT 1
    FROM dbo.user_roles
    WHERE user_id = @admin_id
      AND role = 'CLUB_MANAGER'
)
BEGIN
    INSERT INTO dbo.user_roles (user_id, role)
    VALUES (@admin_id, 'CLUB_MANAGER');
END;
GO

/* ========================================================================== */
/* Source: 03_horse_role_features.sql */
/* ========================================================================== */
USE [racehorse];
GO

IF COL_LENGTH(N'dbo.horses', N'current_trainer_id') IS NULL
BEGIN
    ALTER TABLE dbo.horses ADD current_trainer_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.horses ADD CONSTRAINT fk_horses_current_trainer
        FOREIGN KEY (current_trainer_id) REFERENCES dbo.users(id);
    CREATE INDEX idx_horses_current_trainer_id ON dbo.horses(current_trainer_id);
END;
GO

IF COL_LENGTH(N'dbo.horses', N'archived_at') IS NULL
BEGIN
    ALTER TABLE dbo.horses ADD archived_at DATETIMEOFFSET NULL;
    CREATE INDEX idx_horses_archived_at ON dbo.horses(archived_at);
END;
GO

IF OBJECT_ID(N'dbo.horse_ownerships', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_ownerships (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_ownerships PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        owner_id UNIQUEIDENTIFIER NOT NULL,
        ownership_percentage DECIMAL(5, 2) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_ownerships_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_ownerships_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_horse_ownerships_version DEFAULT 0,
        CONSTRAINT fk_horse_ownerships_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id) ON DELETE CASCADE,
        CONSTRAINT fk_horse_ownerships_owner FOREIGN KEY (owner_id) REFERENCES dbo.users(id),
        CONSTRAINT uq_horse_ownerships_horse_owner UNIQUE (horse_id, owner_id),
        CONSTRAINT ck_horse_ownerships_percentage CHECK (
            ownership_percentage > 0 AND ownership_percentage <= 100)
    );

    CREATE INDEX idx_horse_ownerships_owner_id ON dbo.horse_ownerships(owner_id);
END;
GO

IF OBJECT_ID(N'dbo.race_results', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.race_results (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_race_results PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        race_date DATE NOT NULL,
        race_name VARCHAR(200) NOT NULL,
        racecourse VARCHAR(200) NOT NULL,
        distance VARCHAR(50) NOT NULL,
        finish_position INT NOT NULL,
        finish_time VARCHAR(50) NOT NULL,
        jockey VARCHAR(150) NOT NULL,
        track_condition VARCHAR(100) NULL,
        prize_money VARCHAR(100) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_race_results_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_race_results_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_race_results_version DEFAULT 0,
        CONSTRAINT fk_race_results_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id) ON DELETE CASCADE,
        CONSTRAINT ck_race_results_finish_position CHECK (finish_position >= 1)
    );

    CREATE INDEX idx_race_results_horse_date
        ON dbo.race_results(horse_id, race_date DESC);
END;
GO

/* ========================================================================== */
/* Source: 04_cloudinary_profile_files.sql */
/* ========================================================================== */
USE [racehorse];
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.horse_profile_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_profile_files (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_profile_files PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        uploaded_by_id UNIQUEIDENTIFIER NOT NULL,
        approved_by_id UNIQUEIDENTIFIER NULL,
        cloudinary_asset_id VARCHAR(100) NOT NULL,
        cloudinary_public_id VARCHAR(300) NOT NULL,
        resource_type VARCHAR(20) NOT NULL,
        file_format VARCHAR(30) NULL,
        original_filename NVARCHAR(255) NOT NULL,
        content_type VARCHAR(150) NOT NULL,
        file_size BIGINT NOT NULL,
        secure_url VARCHAR(1000) NOT NULL,
        status VARCHAR(20) NOT NULL,
        approved_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_profile_files_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_profile_files_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_horse_profile_files_version DEFAULT 0,
        CONSTRAINT fk_horse_profile_files_horse FOREIGN KEY (horse_id)
            REFERENCES dbo.horses(id) ON DELETE CASCADE,
        CONSTRAINT fk_horse_profile_files_uploader FOREIGN KEY (uploaded_by_id)
            REFERENCES dbo.users(id),
        CONSTRAINT fk_horse_profile_files_approver FOREIGN KEY (approved_by_id)
            REFERENCES dbo.users(id),
        CONSTRAINT uq_horse_profile_files_cloudinary_asset UNIQUE (cloudinary_asset_id),
        CONSTRAINT ck_horse_profile_files_size CHECK (file_size > 0),
        CONSTRAINT ck_horse_profile_files_resource_type CHECK (resource_type IN ('image', 'raw')),
        CONSTRAINT ck_horse_profile_files_status CHECK (status IN (
            'ACTIVE', 'PENDING', 'REPLACED', 'DELETED'))
    );

END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'uq_horse_profile_files_one_active'
      AND object_id = OBJECT_ID(N'dbo.horse_profile_files')
)
BEGIN
    CREATE UNIQUE INDEX uq_horse_profile_files_one_active
        ON dbo.horse_profile_files(horse_id)
        WHERE status = 'ACTIVE';
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'idx_horse_profile_files_pending'
      AND object_id = OBJECT_ID(N'dbo.horse_profile_files')
)
BEGIN
    CREATE INDEX idx_horse_profile_files_pending
        ON dbo.horse_profile_files(horse_id, created_at)
        WHERE status = 'PENDING';
END;
GO

/* ========================================================================== */
/* Source: 05_training_evaluations.sql */
/* ========================================================================== */
USE [racehorse];
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.training_evaluations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_evaluations (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_training_evaluations PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        recorded_by_user_id UNIQUEIDENTIFIER NOT NULL,
        session_date DATE NOT NULL,
        duration_minutes INT NOT NULL,
        distance_meters DECIMAL(12, 2) NOT NULL,
        average_speed_kmh DECIMAL(8, 2) NOT NULL,
        training_load DECIMAL(10, 2) NOT NULL,
        heart_rate_recovery_bpm INT NOT NULL,
        perceived_exertion TINYINT NULL,
        fitness_status VARCHAR(10) NOT NULL,
        trainer_notes NVARCHAR(4000) NOT NULL,
        video_url VARCHAR(2000) NULL,
        completed_at DATETIMEOFFSET NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_evaluations_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_evaluations_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_training_evaluations_version DEFAULT 0,
        CONSTRAINT fk_training_evaluations_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_training_evaluations_recorded_by FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_training_evaluations_duration CHECK (duration_minutes > 0),
        CONSTRAINT ck_training_evaluations_distance CHECK (distance_meters > 0),
        CONSTRAINT ck_training_evaluations_speed CHECK (average_speed_kmh > 0),
        CONSTRAINT ck_training_evaluations_load CHECK (training_load >= 0),
        CONSTRAINT ck_training_evaluations_recovery CHECK (heart_rate_recovery_bpm BETWEEN 0 AND 300),
        CONSTRAINT ck_training_evaluations_exertion CHECK (perceived_exertion IS NULL OR perceived_exertion BETWEEN 1 AND 10),
        CONSTRAINT ck_training_evaluations_fitness_status CHECK (fitness_status IN ('GREEN', 'YELLOW', 'RED'))
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'idx_training_evaluations_horse_date'
      AND object_id = OBJECT_ID(N'dbo.training_evaluations')
)
BEGIN
    CREATE INDEX idx_training_evaluations_horse_date
        ON dbo.training_evaluations(horse_id, session_date DESC, completed_at DESC);
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'idx_training_evaluations_dashboard'
      AND object_id = OBJECT_ID(N'dbo.training_evaluations')
)
BEGIN
    CREATE INDEX idx_training_evaluations_dashboard
        ON dbo.training_evaluations(session_date, fitness_status)
        INCLUDE (horse_id, training_load, average_speed_kmh, heart_rate_recovery_bpm);
END;
GO

/* ========================================================================== */
/* Source: 06_training_plans_scheduling_monitoring.sql */
/* ========================================================================== */
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

/* Shared notifications used by FR05, FR06, FR12, FR16 and FR19. */
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
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_notifications_created_at DEFAULT SYSDATETIMEOFFSET(),
        read_at DATETIMEOFFSET NULL,
        CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_notifications_priority CHECK (priority IN ('NORMAL', 'HIGH'))
    );
    CREATE INDEX idx_notifications_recipient_unread
        ON dbo.notifications(recipient_user_id, read_at, created_at DESC);
END;
GO

/* FR04 */
IF OBJECT_ID(N'dbo.training_plans', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_plans (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_training_plans PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        name NVARCHAR(200) NOT NULL,
        source_plan_id UNIQUEIDENTIFIER NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_training_plans_is_active DEFAULT 0,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_plans_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_plans_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_training_plans_version DEFAULT 0,
        CONSTRAINT fk_training_plans_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_training_plans_source FOREIGN KEY (source_plan_id) REFERENCES dbo.training_plans(id),
        CONSTRAINT fk_training_plans_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id)
    );
    CREATE INDEX idx_training_plans_horse ON dbo.training_plans(horse_id, is_active);
END;
GO

IF OBJECT_ID(N'dbo.training_plan_phases', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_plan_phases (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_training_plan_phases PRIMARY KEY,
        training_plan_id UNIQUEIDENTIFIER NOT NULL,
        phase_order INT NOT NULL,
        name NVARCHAR(200) NOT NULL,
        start_date DATE NOT NULL,
        end_date DATE NOT NULL,
        target_distance_meters DECIMAL(12,2) NOT NULL,
        workload_intensity VARCHAR(10) NOT NULL,
        surface_type VARCHAR(20) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_plan_phases_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_plan_phases_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_training_plan_phases_version DEFAULT 0,
        CONSTRAINT fk_training_plan_phases_plan FOREIGN KEY (training_plan_id) REFERENCES dbo.training_plans(id) ON DELETE CASCADE,
        CONSTRAINT uq_training_plan_phases_order UNIQUE (training_plan_id, phase_order),
        CONSTRAINT ck_training_plan_phases_order CHECK (phase_order > 0),
        CONSTRAINT ck_training_plan_phases_dates CHECK (start_date <= end_date),
        CONSTRAINT ck_training_plan_phases_distance CHECK (target_distance_meters > 0),
        CONSTRAINT ck_training_plan_phases_intensity CHECK (workload_intensity IN ('LOW', 'MEDIUM', 'HIGH')),
        CONSTRAINT ck_training_plan_phases_surface CHECK (surface_type IN ('GRASS', 'DIRT', 'SYNTHETIC'))
    );
    CREATE INDEX idx_training_plan_phases_dates
        ON dbo.training_plan_phases(training_plan_id, start_date, end_date);
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_training_plan_phases_no_overlap
ON dbo.training_plan_phases
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_plan_phases p
          ON p.training_plan_id = i.training_plan_id
         AND p.id <> i.id
         AND i.start_date <= p.end_date
         AND i.end_date >= p.start_date
    )
    BEGIN
        THROW 51001, N'Các giai đoạn trong cùng kế hoạch không được trùng thời gian.', 1;
    END;
END;
GO

/* FR05 */
IF OBJECT_ID(N'dbo.training_schedules', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_schedules (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_training_schedules PRIMARY KEY,
        training_plan_phase_id UNIQUEIDENTIFIER NOT NULL,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        assigned_staff_id UNIQUEIDENTIFIER NOT NULL,
        task_name NVARCHAR(300) NOT NULL,
        workload_intensity VARCHAR(10) NOT NULL,
        scheduled_start DATETIMEOFFSET NOT NULL,
        scheduled_end DATETIMEOFFSET NOT NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        published_at DATETIMEOFFSET NULL,
        cancelled_at DATETIMEOFFSET NULL,
        completed_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_schedules_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_schedules_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_training_schedules_version DEFAULT 0,
        CONSTRAINT fk_training_schedules_phase FOREIGN KEY (training_plan_phase_id) REFERENCES dbo.training_plan_phases(id),
        CONSTRAINT fk_training_schedules_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_training_schedules_staff FOREIGN KEY (assigned_staff_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_training_schedules_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_training_schedules_dates CHECK (scheduled_start < scheduled_end),
        CONSTRAINT ck_training_schedules_intensity CHECK (workload_intensity IN ('LOW', 'MEDIUM', 'HIGH'))
    );
    CREATE INDEX idx_training_schedules_staff_time
        ON dbo.training_schedules(assigned_staff_id, scheduled_start, scheduled_end);
    CREATE INDEX idx_training_schedules_horse_time
        ON dbo.training_schedules(horse_id, scheduled_start);
END;
GO

/* Links FR07 back to FR05 and enables the FR03 phase filter without changing entered metrics. */
IF COL_LENGTH(N'dbo.training_evaluations', N'training_schedule_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_evaluations ADD training_schedule_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_evaluations ADD CONSTRAINT fk_training_evaluations_schedule
        FOREIGN KEY (training_schedule_id) REFERENCES dbo.training_schedules(id);
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'uq_training_evaluations_schedule' AND object_id=OBJECT_ID(N'dbo.training_evaluations'))
BEGIN
    CREATE UNIQUE INDEX uq_training_evaluations_schedule
        ON dbo.training_evaluations(training_schedule_id)
        WHERE training_schedule_id IS NOT NULL;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_training_schedules_no_staff_overlap
ON dbo.training_schedules
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_schedules s
          ON s.assigned_staff_id = i.assigned_staff_id
         AND s.id <> i.id
         AND s.cancelled_at IS NULL
         AND i.cancelled_at IS NULL
         AND i.scheduled_start < s.scheduled_end
         AND i.scheduled_end > s.scheduled_start
    )
    BEGIN
        THROW 51002, N'Nhân viên đã có lịch trong khung giờ này.', 1;
    END;
END;
GO

IF OBJECT_ID(N'dbo.racetracks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.racetracks (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_racetracks PRIMARY KEY,
        name NVARCHAR(200) NOT NULL,
        location NVARCHAR(300) NULL,
        is_active BIT NOT NULL CONSTRAINT df_racetracks_is_active DEFAULT 1,
        CONSTRAINT uq_racetracks_name UNIQUE (name)
    );
END;
GO

IF OBJECT_ID(N'dbo.racetrack_lanes', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.racetrack_lanes (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_racetrack_lanes PRIMARY KEY,
        racetrack_id UNIQUEIDENTIFIER NOT NULL,
        lane_number INT NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_racetrack_lanes_is_active DEFAULT 1,
        CONSTRAINT fk_racetrack_lanes_track FOREIGN KEY (racetrack_id) REFERENCES dbo.racetracks(id),
        CONSTRAINT uq_racetrack_lanes_number UNIQUE (racetrack_id, lane_number),
        CONSTRAINT ck_racetrack_lanes_number CHECK (lane_number > 0)
    );
END;
GO

IF OBJECT_ID(N'dbo.trial_run_bookings', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.trial_run_bookings (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_trial_run_bookings PRIMARY KEY,
        training_schedule_id UNIQUEIDENTIFIER NOT NULL,
        racetrack_lane_id UNIQUEIDENTIFIER NOT NULL,
        booked_start DATETIMEOFFSET NOT NULL,
        booked_end DATETIMEOFFSET NOT NULL,
        cancelled_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_trial_run_bookings_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_trial_run_bookings_schedule FOREIGN KEY (training_schedule_id) REFERENCES dbo.training_schedules(id),
        CONSTRAINT fk_trial_run_bookings_lane FOREIGN KEY (racetrack_lane_id) REFERENCES dbo.racetrack_lanes(id),
        CONSTRAINT uq_trial_run_bookings_schedule UNIQUE (training_schedule_id),
        CONSTRAINT ck_trial_run_bookings_dates CHECK (booked_start < booked_end)
    );
    CREATE INDEX idx_trial_run_bookings_lane_time
        ON dbo.trial_run_bookings(racetrack_lane_id, booked_start, booked_end);
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_trial_run_bookings_no_lane_overlap
ON dbo.trial_run_bookings
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.trial_run_bookings b
          ON b.racetrack_lane_id = i.racetrack_lane_id
         AND b.id <> i.id
         AND b.cancelled_at IS NULL
         AND i.cancelled_at IS NULL
         AND i.booked_start < b.booked_end
         AND i.booked_end > b.booked_start
    )
    BEGIN
        THROW 51003, N'Làn chạy đã được đặt trong khung giờ này.', 1;
    END;
END;
GO

/* FR06 */
IF OBJECT_ID(N'dbo.horse_metric_thresholds', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_metric_thresholds (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_metric_thresholds PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        metric_type VARCHAR(20) NOT NULL,
        minimum_value DECIMAL(12,3) NULL,
        maximum_value DECIMAL(12,3) NULL,
        unit VARCHAR(30) NOT NULL,
        configured_by_user_id UNIQUEIDENTIFIER NOT NULL,
        effective_from DATETIMEOFFSET NOT NULL,
        effective_to DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_metric_thresholds_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_horse_metric_thresholds_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_horse_metric_thresholds_user FOREIGN KEY (configured_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_horse_metric_thresholds_type CHECK (metric_type IN ('HEART_RATE', 'SPEED')),
        CONSTRAINT ck_horse_metric_thresholds_range CHECK (
            minimum_value IS NOT NULL OR maximum_value IS NOT NULL),
        CONSTRAINT ck_horse_metric_thresholds_order CHECK (
            minimum_value IS NULL OR maximum_value IS NULL OR minimum_value < maximum_value),
        CONSTRAINT ck_horse_metric_thresholds_effective CHECK (
            effective_to IS NULL OR effective_from < effective_to)
    );
    CREATE INDEX idx_horse_metric_thresholds_active
        ON dbo.horse_metric_thresholds(horse_id, metric_type, effective_from, effective_to);
END;
GO

IF OBJECT_ID(N'dbo.telemetry_sessions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.telemetry_sessions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_telemetry_sessions PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        training_schedule_id UNIQUEIDENTIFIER NULL,
        started_at DATETIMEOFFSET NOT NULL,
        ended_at DATETIMEOFFSET NULL,
        CONSTRAINT fk_telemetry_sessions_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_telemetry_sessions_schedule FOREIGN KEY (training_schedule_id) REFERENCES dbo.training_schedules(id),
        CONSTRAINT ck_telemetry_sessions_dates CHECK (ended_at IS NULL OR started_at < ended_at)
    );
    CREATE INDEX idx_telemetry_sessions_horse_time ON dbo.telemetry_sessions(horse_id, started_at DESC);
END;
GO

IF OBJECT_ID(N'dbo.telemetry_readings', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.telemetry_readings (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_telemetry_readings PRIMARY KEY,
        telemetry_session_id UNIQUEIDENTIFIER NOT NULL,
        recorded_at DATETIMEOFFSET NOT NULL,
        heart_rate_bpm INT NULL,
        speed_kmh DECIMAL(8,3) NULL,
        CONSTRAINT fk_telemetry_readings_session FOREIGN KEY (telemetry_session_id) REFERENCES dbo.telemetry_sessions(id),
        CONSTRAINT ck_telemetry_readings_value CHECK (heart_rate_bpm IS NOT NULL OR speed_kmh IS NOT NULL),
        CONSTRAINT ck_telemetry_readings_heart_rate CHECK (heart_rate_bpm IS NULL OR heart_rate_bpm BETWEEN 0 AND 300),
        CONSTRAINT ck_telemetry_readings_speed CHECK (speed_kmh IS NULL OR speed_kmh >= 0)
    );
    CREATE INDEX idx_telemetry_readings_session_time
        ON dbo.telemetry_readings(telemetry_session_id, recorded_at);
END;
GO

IF OBJECT_ID(N'dbo.alert_events', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.alert_events (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_alert_events PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        telemetry_session_id UNIQUEIDENTIFIER NOT NULL,
        telemetry_reading_id BIGINT NOT NULL,
        threshold_id UNIQUEIDENTIFIER NOT NULL,
        metric_type VARCHAR(20) NOT NULL,
        observed_value DECIMAL(12,3) NOT NULL,
        threshold_value DECIMAL(12,3) NOT NULL,
        threshold_direction VARCHAR(10) NOT NULL,
        occurred_at DATETIMEOFFSET NOT NULL,
        acknowledged_by_user_id UNIQUEIDENTIFIER NULL,
        acknowledged_at DATETIMEOFFSET NULL,
        CONSTRAINT fk_alert_events_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_alert_events_session FOREIGN KEY (telemetry_session_id) REFERENCES dbo.telemetry_sessions(id),
        CONSTRAINT fk_alert_events_reading FOREIGN KEY (telemetry_reading_id) REFERENCES dbo.telemetry_readings(id),
        CONSTRAINT fk_alert_events_threshold FOREIGN KEY (threshold_id) REFERENCES dbo.horse_metric_thresholds(id),
        CONSTRAINT fk_alert_events_ack_user FOREIGN KEY (acknowledged_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_alert_events_type CHECK (metric_type IN ('HEART_RATE', 'SPEED')),
        CONSTRAINT ck_alert_events_direction CHECK (threshold_direction IN ('MINIMUM', 'MAXIMUM')),
        CONSTRAINT ck_alert_events_ack CHECK (
            (acknowledged_at IS NULL AND acknowledged_by_user_id IS NULL)
            OR (acknowledged_at IS NOT NULL AND acknowledged_by_user_id IS NOT NULL))
    );
    CREATE INDEX idx_alert_events_horse_time ON dbo.alert_events(horse_id, occurred_at DESC);
END;
GO

/* ========================================================================== */
/* Source: 07_health_medical.sql */
/* ========================================================================== */
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

/* Shared Cloudinary metadata for medical, task and incident attachments. */
IF OBJECT_ID(N'dbo.stored_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.stored_files (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_stored_files PRIMARY KEY,
        uploaded_by_user_id UNIQUEIDENTIFIER NOT NULL,
        cloudinary_asset_id VARCHAR(100) NOT NULL,
        cloudinary_public_id VARCHAR(300) NOT NULL,
        resource_type VARCHAR(20) NOT NULL,
        file_format VARCHAR(30) NULL,
        original_filename NVARCHAR(255) NOT NULL,
        content_type VARCHAR(150) NOT NULL,
        file_size BIGINT NOT NULL,
        secure_url VARCHAR(1000) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_stored_files_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_stored_files_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT uq_stored_files_cloudinary_asset UNIQUE (cloudinary_asset_id),
        CONSTRAINT ck_stored_files_size CHECK (file_size > 0),
        CONSTRAINT ck_stored_files_resource CHECK (resource_type IN ('image', 'raw', 'video'))
    );
END;
GO

/* FR01 supplementary files and FR07 optional attached video. */
IF OBJECT_ID(N'dbo.horse_additional_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_additional_files (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_additional_files PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        stored_file_id UNIQUEIDENTIFIER NOT NULL,
        display_order TINYINT NOT NULL,
        removed_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_additional_files_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_horse_additional_files_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_horse_additional_files_file FOREIGN KEY (stored_file_id) REFERENCES dbo.stored_files(id),
        CONSTRAINT uq_horse_additional_files_file UNIQUE (horse_id, stored_file_id),
        CONSTRAINT ck_horse_additional_files_order CHECK (display_order BETWEEN 1 AND 10)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'uq_horse_additional_files_active_order' AND object_id=OBJECT_ID(N'dbo.horse_additional_files'))
BEGIN
    CREATE UNIQUE INDEX uq_horse_additional_files_active_order
        ON dbo.horse_additional_files(horse_id, display_order)
        WHERE removed_at IS NULL;
END;
GO

IF OBJECT_ID(N'dbo.training_evaluation_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_evaluation_files (
        training_evaluation_id UNIQUEIDENTIFIER NOT NULL,
        stored_file_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT pk_training_evaluation_files PRIMARY KEY (training_evaluation_id, stored_file_id),
        CONSTRAINT fk_training_evaluation_files_evaluation FOREIGN KEY (training_evaluation_id) REFERENCES dbo.training_evaluations(id),
        CONSTRAINT fk_training_evaluation_files_file FOREIGN KEY (stored_file_id) REFERENCES dbo.stored_files(id)
    );
END;
GO

/* FR08 current state plus immutable state history. */
IF OBJECT_ID(N'dbo.horse_health_status', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_health_status (
        horse_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_health_status PRIMARY KEY,
        health_status VARCHAR(20) NOT NULL,
        changed_by_user_id UNIQUEIDENTIFIER NOT NULL,
        source_type VARCHAR(30) NOT NULL,
        source_id UNIQUEIDENTIFIER NULL,
        changed_at DATETIMEOFFSET NOT NULL,
        version BIGINT NOT NULL CONSTRAINT df_horse_health_status_version DEFAULT 0,
        CONSTRAINT fk_horse_health_status_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_horse_health_status_user FOREIGN KEY (changed_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_horse_health_status_value CHECK (health_status IN ('FIT', 'MONITOR', 'INJURED', 'QUARANTINED'))
    );
    CREATE INDEX idx_horse_health_status_value ON dbo.horse_health_status(health_status);
END;
GO

IF OBJECT_ID(N'dbo.horse_health_status_history', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_health_status_history (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_horse_health_status_history PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        health_status VARCHAR(20) NOT NULL,
        changed_by_user_id UNIQUEIDENTIFIER NOT NULL,
        source_type VARCHAR(30) NOT NULL,
        source_id UNIQUEIDENTIFIER NULL,
        changed_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT fk_health_history_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_health_history_user FOREIGN KEY (changed_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_health_history_status CHECK (health_status IN ('FIT', 'MONITOR', 'INJURED', 'QUARANTINED'))
    );
    CREATE INDEX idx_health_history_horse_time ON dbo.horse_health_status_history(horse_id, changed_at DESC);
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_horse_health_status_history
ON dbo.horse_health_status
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    INSERT INTO dbo.horse_health_status_history
        (horse_id, health_status, changed_by_user_id, source_type, source_id, changed_at)
    SELECT horse_id, health_status, changed_by_user_id, source_type, source_id, changed_at
    FROM inserted;
END;
GO

/* FR09 */
IF OBJECT_ID(N'dbo.medical_examinations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.medical_examinations (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_medical_examinations PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        veterinarian_user_id UNIQUEIDENTIFIER NOT NULL,
        examination_date DATE NOT NULL,
        symptoms NVARCHAR(4000) NOT NULL,
        diagnosis NVARCHAR(4000) NOT NULL,
        treatment_plan NVARCHAR(4000) NOT NULL,
        medication_and_dosage NVARCHAR(4000) NULL,
        follow_up_date DATE NULL,
        health_status_after VARCHAR(20) NOT NULL,
        submitted_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT fk_medical_examinations_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_medical_examinations_vet FOREIGN KEY (veterinarian_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_medical_examinations_follow_up CHECK (follow_up_date IS NULL OR follow_up_date >= examination_date),
        CONSTRAINT ck_medical_examinations_health CHECK (health_status_after IN ('FIT', 'MONITOR', 'INJURED', 'QUARANTINED'))
    );
    CREATE INDEX idx_medical_examinations_horse_date ON dbo.medical_examinations(horse_id, examination_date DESC, submitted_at DESC);
END;
GO

IF OBJECT_ID(N'dbo.medical_examination_addenda', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.medical_examination_addenda (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_medical_examination_addenda PRIMARY KEY,
        examination_id UNIQUEIDENTIFIER NOT NULL,
        authored_by_user_id UNIQUEIDENTIFIER NOT NULL,
        content NVARCHAR(4000) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_medical_addenda_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_medical_addenda_exam FOREIGN KEY (examination_id) REFERENCES dbo.medical_examinations(id),
        CONSTRAINT fk_medical_addenda_author FOREIGN KEY (authored_by_user_id) REFERENCES dbo.users(id)
    );
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_medical_examinations_no_delete
ON dbo.medical_examinations
INSTEAD OF DELETE
AS
BEGIN
    THROW 51010, N'Hồ sơ khám bệnh không được xóa; hãy tạo phụ lục.', 1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_medical_addenda_no_change
ON dbo.medical_examination_addenda
INSTEAD OF UPDATE, DELETE
AS
BEGIN
    THROW 51011, N'Phụ lục y tế đã ghi không được sửa hoặc xóa.', 1;
END;
GO

/* FR10 */
IF OBJECT_ID(N'dbo.injury_markers', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.injury_markers (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_injury_markers PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        examination_id UNIQUEIDENTIFIER NULL,
        model_name VARCHAR(100) NOT NULL,
        body_part_label NVARCHAR(150) NOT NULL,
        coordinate_x DECIMAL(12,6) NOT NULL,
        coordinate_y DECIMAL(12,6) NOT NULL,
        coordinate_z DECIMAL(12,6) NOT NULL,
        initial_severity NVARCHAR(100) NOT NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_injury_markers_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_injury_markers_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_injury_markers_exam FOREIGN KEY (examination_id) REFERENCES dbo.medical_examinations(id),
        CONSTRAINT fk_injury_markers_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id)
    );
    CREATE INDEX idx_injury_markers_horse ON dbo.injury_markers(horse_id, created_at DESC);
END;
GO

IF OBJECT_ID(N'dbo.injury_recovery_updates', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.injury_recovery_updates (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_injury_recovery_updates PRIMARY KEY,
        injury_marker_id UNIQUEIDENTIFIER NOT NULL,
        examination_id UNIQUEIDENTIFIER NULL,
        status_label NVARCHAR(100) NOT NULL,
        severity_label NVARCHAR(100) NULL,
        notes NVARCHAR(4000) NOT NULL,
        recorded_by_user_id UNIQUEIDENTIFIER NOT NULL,
        recorded_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT fk_injury_updates_marker FOREIGN KEY (injury_marker_id) REFERENCES dbo.injury_markers(id),
        CONSTRAINT fk_injury_updates_exam FOREIGN KEY (examination_id) REFERENCES dbo.medical_examinations(id),
        CONSTRAINT fk_injury_updates_user FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.users(id)
    );
    CREATE INDEX idx_injury_updates_timeline ON dbo.injury_recovery_updates(injury_marker_id, recorded_at);
END;
GO

IF OBJECT_ID(N'dbo.injury_update_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.injury_update_files (
        injury_update_id UNIQUEIDENTIFIER NOT NULL,
        stored_file_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT pk_injury_update_files PRIMARY KEY (injury_update_id, stored_file_id),
        CONSTRAINT fk_injury_update_files_update FOREIGN KEY (injury_update_id) REFERENCES dbo.injury_recovery_updates(id),
        CONSTRAINT fk_injury_update_files_file FOREIGN KEY (stored_file_id) REFERENCES dbo.stored_files(id)
    );
END;
GO

/* FR11 */
IF OBJECT_ID(N'dbo.training_locks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_locks (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_training_locks PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        reason NVARCHAR(2000) NOT NULL,
        review_date DATE NULL,
        locked_by_user_id UNIQUEIDENTIFIER NOT NULL,
        locked_at DATETIMEOFFSET NOT NULL,
        released_by_user_id UNIQUEIDENTIFIER NULL,
        release_reason NVARCHAR(2000) NULL,
        released_at DATETIMEOFFSET NULL,
        CONSTRAINT fk_training_locks_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_training_locks_locked_by FOREIGN KEY (locked_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_training_locks_released_by FOREIGN KEY (released_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_training_locks_release CHECK (
            (released_at IS NULL AND released_by_user_id IS NULL AND release_reason IS NULL)
            OR (released_at IS NOT NULL AND released_by_user_id IS NOT NULL AND release_reason IS NOT NULL)),
        CONSTRAINT ck_training_locks_release_time CHECK (released_at IS NULL OR released_at >= locked_at)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'uq_training_locks_active_horse' AND object_id=OBJECT_ID(N'dbo.training_locks'))
BEGIN
    CREATE UNIQUE INDEX uq_training_locks_active_horse ON dbo.training_locks(horse_id) WHERE released_at IS NULL;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_training_schedules_plan_and_lock
ON dbo.training_schedules
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_plan_phases p ON p.id = i.training_plan_phase_id
        JOIN dbo.training_plans plan_record ON plan_record.id = p.training_plan_id
        WHERE plan_record.horse_id <> i.horse_id
    )
    BEGIN
        THROW 51012, N'Ngựa trên lịch phải trùng với ngựa của kế hoạch huấn luyện.', 1;
    END;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_locks training_lock
          ON training_lock.horse_id = i.horse_id
         AND training_lock.released_at IS NULL
        WHERE i.workload_intensity = 'HIGH'
          AND i.cancelled_at IS NULL
    )
    BEGIN
        THROW 51013, N'Không thể xếp lịch cường độ cao khi ngựa đang bị khóa huấn luyện.', 1;
    END;
END;
GO

/* FR12 */
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
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_preventive_care_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_preventive_care_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_preventive_care_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_preventive_care_type CHECK (care_type IN ('VACCINATION', 'DEWORMING', 'FARRIER')),
        CONSTRAINT ck_preventive_care_recurrence CHECK (recurrence_days IS NULL OR recurrence_days > 0),
        CONSTRAINT ck_preventive_care_reminder CHECK (reminder_lead_days >= 0)
    );
    CREATE INDEX idx_preventive_care_due ON dbo.preventive_care_schedules(due_date, cancelled_at);
END;
GO

IF OBJECT_ID(N'dbo.preventive_care_completions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.preventive_care_completions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_preventive_care_completions PRIMARY KEY,
        schedule_id UNIQUEIDENTIFIER NOT NULL,
        performed_by_user_id UNIQUEIDENTIFIER NOT NULL,
        performed_at DATETIMEOFFSET NOT NULL,
        notes NVARCHAR(2000) NULL,
        next_schedule_id UNIQUEIDENTIFIER NULL,
        CONSTRAINT fk_preventive_completions_schedule FOREIGN KEY (schedule_id) REFERENCES dbo.preventive_care_schedules(id),
        CONSTRAINT fk_preventive_completions_performer FOREIGN KEY (performed_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_preventive_completions_next FOREIGN KEY (next_schedule_id) REFERENCES dbo.preventive_care_schedules(id),
        CONSTRAINT uq_preventive_completions_schedule UNIQUE (schedule_id)
    );
END;
GO

/* ========================================================================== */
/* Source: 08_stable_care_inventory.sql */
/* ========================================================================== */
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

/* FR13 */
IF OBJECT_ID(N'dbo.stables', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.stables (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_stables PRIMARY KEY,
        name NVARCHAR(150) NOT NULL,
        location NVARCHAR(300) NULL,
        is_active BIT NOT NULL CONSTRAINT df_stables_is_active DEFAULT 1,
        CONSTRAINT uq_stables_name UNIQUE (name)
    );
END;
GO

IF OBJECT_ID(N'dbo.stalls', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.stalls (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_stalls PRIMARY KEY,
        stable_id UNIQUEIDENTIFIER NOT NULL,
        stall_code VARCHAR(50) NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_stalls_is_active DEFAULT 1,
        CONSTRAINT fk_stalls_stable FOREIGN KEY (stable_id) REFERENCES dbo.stables(id),
        CONSTRAINT uq_stalls_code UNIQUE (stable_id, stall_code)
    );
END;
GO

IF OBJECT_ID(N'dbo.horse_stall_assignments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_stall_assignments (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_stall_assignments PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        stall_id UNIQUEIDENTIFIER NOT NULL,
        assigned_from DATETIMEOFFSET NOT NULL,
        assigned_until DATETIMEOFFSET NULL,
        assigned_by_user_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT fk_stall_assignments_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_stall_assignments_stall FOREIGN KEY (stall_id) REFERENCES dbo.stalls(id),
        CONSTRAINT fk_stall_assignments_user FOREIGN KEY (assigned_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_stall_assignments_dates CHECK (assigned_until IS NULL OR assigned_from < assigned_until)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'uq_stall_assignments_current_horse' AND object_id=OBJECT_ID(N'dbo.horse_stall_assignments'))
    CREATE UNIQUE INDEX uq_stall_assignments_current_horse ON dbo.horse_stall_assignments(horse_id) WHERE assigned_until IS NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'uq_stall_assignments_current_stall' AND object_id=OBJECT_ID(N'dbo.horse_stall_assignments'))
    CREATE UNIQUE INDEX uq_stall_assignments_current_stall ON dbo.horse_stall_assignments(stall_id) WHERE assigned_until IS NULL;
GO

IF OBJECT_ID(N'dbo.horse_daily_routines', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_daily_routines (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_daily_routines PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        activity_name NVARCHAR(200) NOT NULL,
        start_time TIME NOT NULL,
        end_time TIME NOT NULL,
        effective_from DATE NOT NULL,
        effective_until DATE NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_daily_routines_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_daily_routines_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_daily_routines_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_daily_routines_time CHECK (start_time < end_time),
        CONSTRAINT ck_daily_routines_dates CHECK (effective_until IS NULL OR effective_from <= effective_until)
    );
    CREATE INDEX idx_daily_routines_horse_time ON dbo.horse_daily_routines(horse_id, effective_from, start_time);
END;
GO

/* FR17 master data is created before FR14 because meal items reference it. */
IF OBJECT_ID(N'dbo.inventory_categories', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.inventory_categories (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_inventory_categories PRIMARY KEY,
        code VARCHAR(50) NOT NULL,
        name NVARCHAR(150) NOT NULL,
        category_group VARCHAR(20) NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_inventory_categories_active DEFAULT 1,
        CONSTRAINT uq_inventory_categories_code UNIQUE (code),
        CONSTRAINT ck_inventory_categories_group CHECK (category_group IN ('FEED', 'MEDICINE', 'EQUIPMENT'))
    );
END;
GO

IF OBJECT_ID(N'dbo.inventory_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.inventory_items (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_inventory_items PRIMARY KEY,
        category_id UNIQUEIDENTIFIER NOT NULL,
        sku VARCHAR(80) NOT NULL,
        name NVARCHAR(200) NOT NULL,
        unit VARCHAR(50) NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_inventory_items_active DEFAULT 1,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_inventory_items_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_inventory_items_updated_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_inventory_items_category FOREIGN KEY (category_id) REFERENCES dbo.inventory_categories(id),
        CONSTRAINT uq_inventory_items_sku UNIQUE (sku)
    );
END;
GO

/* FR14 versioned nutrition plans. */
IF OBJECT_ID(N'dbo.nutrition_plans', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.nutrition_plans (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_nutrition_plans PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        version_number INT NOT NULL,
        valid_from DATE NOT NULL,
        valid_until DATE NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        approved_by_user_id UNIQUEIDENTIFIER NOT NULL,
        approved_at DATETIMEOFFSET NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_nutrition_plans_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_nutrition_plans_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_nutrition_plans_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_nutrition_plans_approver FOREIGN KEY (approved_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT uq_nutrition_plans_version UNIQUE (horse_id, version_number),
        CONSTRAINT ck_nutrition_plans_version CHECK (version_number > 0),
        CONSTRAINT ck_nutrition_plans_dates CHECK (valid_until IS NULL OR valid_from <= valid_until)
    );
    CREATE INDEX idx_nutrition_plans_current ON dbo.nutrition_plans(horse_id, valid_from DESC, valid_until);
END;
GO

IF OBJECT_ID(N'dbo.nutrition_plan_meals', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.nutrition_plan_meals (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_nutrition_plan_meals PRIMARY KEY,
        nutrition_plan_id UNIQUEIDENTIFIER NOT NULL,
        meal_time TIME NOT NULL,
        meal_name NVARCHAR(100) NULL,
        CONSTRAINT fk_nutrition_plan_meals_plan FOREIGN KEY (nutrition_plan_id) REFERENCES dbo.nutrition_plans(id),
        CONSTRAINT uq_nutrition_plan_meals_time UNIQUE (nutrition_plan_id, meal_time)
    );
END;
GO

IF OBJECT_ID(N'dbo.nutrition_meal_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.nutrition_meal_items (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_nutrition_meal_items PRIMARY KEY,
        meal_id UNIQUEIDENTIFIER NOT NULL,
        inventory_item_id UNIQUEIDENTIFIER NOT NULL,
        quantity DECIMAL(12,3) NOT NULL,
        supplement_notes NVARCHAR(1000) NULL,
        CONSTRAINT fk_nutrition_meal_items_meal FOREIGN KEY (meal_id) REFERENCES dbo.nutrition_plan_meals(id),
        CONSTRAINT fk_nutrition_meal_items_item FOREIGN KEY (inventory_item_id) REFERENCES dbo.inventory_items(id),
        CONSTRAINT uq_nutrition_meal_items_item UNIQUE (meal_id, inventory_item_id),
        CONSTRAINT ck_nutrition_meal_items_quantity CHECK (quantity > 0)
    );
END;
GO

/* FR15 */
IF OBJECT_ID(N'dbo.daily_tasks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.daily_tasks (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_daily_tasks PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        assigned_staff_id UNIQUEIDENTIFIER NOT NULL,
        training_schedule_id UNIQUEIDENTIFIER NULL,
        task_name NVARCHAR(300) NOT NULL,
        scheduled_start DATETIMEOFFSET NOT NULL,
        scheduled_end DATETIMEOFFSET NOT NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        completed_by_user_id UNIQUEIDENTIFIER NULL,
        completed_at DATETIMEOFFSET NULL,
        completion_notes NVARCHAR(2000) NULL,
        cancelled_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_daily_tasks_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_daily_tasks_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_daily_tasks_version DEFAULT 0,
        CONSTRAINT fk_daily_tasks_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_daily_tasks_staff FOREIGN KEY (assigned_staff_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_daily_tasks_schedule FOREIGN KEY (training_schedule_id) REFERENCES dbo.training_schedules(id),
        CONSTRAINT fk_daily_tasks_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_daily_tasks_completer FOREIGN KEY (completed_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_daily_tasks_dates CHECK (scheduled_start < scheduled_end),
        CONSTRAINT ck_daily_tasks_completion CHECK (
            (completed_at IS NULL AND completed_by_user_id IS NULL)
            OR (completed_at IS NOT NULL AND completed_by_user_id IS NOT NULL))
    );
    CREATE INDEX idx_daily_tasks_staff_date ON dbo.daily_tasks(assigned_staff_id, scheduled_start, completed_at);
    CREATE INDEX idx_daily_tasks_horse_date ON dbo.daily_tasks(horse_id, scheduled_start);
END;
GO

IF OBJECT_ID(N'dbo.daily_task_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.daily_task_files (
        daily_task_id UNIQUEIDENTIFIER NOT NULL,
        stored_file_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT pk_daily_task_files PRIMARY KEY (daily_task_id, stored_file_id),
        CONSTRAINT fk_daily_task_files_task FOREIGN KEY (daily_task_id) REFERENCES dbo.daily_tasks(id),
        CONSTRAINT fk_daily_task_files_file FOREIGN KEY (stored_file_id) REFERENCES dbo.stored_files(id)
    );
END;
GO

/* FR16 */
IF OBJECT_ID(N'dbo.incident_reports', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.incident_reports (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_incident_reports PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        reported_by_user_id UNIQUEIDENTIFIER NOT NULL,
        incident_type NVARCHAR(150) NOT NULL,
        description NVARCHAR(4000) NOT NULL,
        submitted_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT fk_incident_reports_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_incident_reports_reporter FOREIGN KEY (reported_by_user_id) REFERENCES dbo.users(id)
    );
    CREATE INDEX idx_incident_reports_horse_time ON dbo.incident_reports(horse_id, submitted_at DESC);
END;
GO

IF OBJECT_ID(N'dbo.incident_report_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.incident_report_files (
        incident_report_id UNIQUEIDENTIFIER NOT NULL,
        stored_file_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT pk_incident_report_files PRIMARY KEY (incident_report_id, stored_file_id),
        CONSTRAINT fk_incident_report_files_report FOREIGN KEY (incident_report_id) REFERENCES dbo.incident_reports(id),
        CONSTRAINT fk_incident_report_files_file FOREIGN KEY (stored_file_id) REFERENCES dbo.stored_files(id)
    );
END;
GO

/* FR17 */
IF OBJECT_ID(N'dbo.storage_areas', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.storage_areas (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_storage_areas PRIMARY KEY,
        stable_id UNIQUEIDENTIFIER NULL,
        name NVARCHAR(150) NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_storage_areas_active DEFAULT 1,
        CONSTRAINT fk_storage_areas_stable FOREIGN KEY (stable_id) REFERENCES dbo.stables(id),
        CONSTRAINT uq_storage_areas_name UNIQUE (name)
    );
END;
GO

IF OBJECT_ID(N'dbo.storage_area_staff', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.storage_area_staff (
        storage_area_id UNIQUEIDENTIFIER NOT NULL,
        staff_user_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT pk_storage_area_staff PRIMARY KEY (storage_area_id, staff_user_id),
        CONSTRAINT fk_storage_area_staff_area FOREIGN KEY (storage_area_id) REFERENCES dbo.storage_areas(id),
        CONSTRAINT fk_storage_area_staff_user FOREIGN KEY (staff_user_id) REFERENCES dbo.users(id)
    );
END;
GO

IF OBJECT_ID(N'dbo.inventory_balances', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.inventory_balances (
        storage_area_id UNIQUEIDENTIFIER NOT NULL,
        inventory_item_id UNIQUEIDENTIFIER NOT NULL,
        current_quantity DECIMAL(14,3) NOT NULL,
        last_counted_at DATETIMEOFFSET NOT NULL,
        last_counted_by_user_id UNIQUEIDENTIFIER NOT NULL,
        version BIGINT NOT NULL CONSTRAINT df_inventory_balances_version DEFAULT 0,
        CONSTRAINT pk_inventory_balances PRIMARY KEY (storage_area_id, inventory_item_id),
        CONSTRAINT fk_inventory_balances_area FOREIGN KEY (storage_area_id) REFERENCES dbo.storage_areas(id),
        CONSTRAINT fk_inventory_balances_item FOREIGN KEY (inventory_item_id) REFERENCES dbo.inventory_items(id),
        CONSTRAINT fk_inventory_balances_user FOREIGN KEY (last_counted_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_inventory_balances_quantity CHECK (current_quantity >= 0)
    );
END;
GO

IF OBJECT_ID(N'dbo.inventory_transactions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.inventory_transactions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_inventory_transactions PRIMARY KEY,
        storage_area_id UNIQUEIDENTIFIER NOT NULL,
        inventory_item_id UNIQUEIDENTIFIER NOT NULL,
        transaction_type VARCHAR(15) NOT NULL,
        quantity_change DECIMAL(14,3) NOT NULL,
        resulting_quantity DECIMAL(14,3) NOT NULL,
        recorded_by_user_id UNIQUEIDENTIFIER NOT NULL,
        description NVARCHAR(1000) NULL,
        recorded_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT fk_inventory_transactions_area FOREIGN KEY (storage_area_id) REFERENCES dbo.storage_areas(id),
        CONSTRAINT fk_inventory_transactions_item FOREIGN KEY (inventory_item_id) REFERENCES dbo.inventory_items(id),
        CONSTRAINT fk_inventory_transactions_user FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_inventory_transactions_type CHECK (transaction_type IN ('COUNT', 'RECEIPT', 'USAGE', 'ADJUSTMENT')),
        CONSTRAINT ck_inventory_transactions_result CHECK (resulting_quantity >= 0)
    );
    CREATE INDEX idx_inventory_transactions_item_time ON dbo.inventory_transactions(storage_area_id, inventory_item_id, recorded_at DESC);
END;
GO

IF OBJECT_ID(N'dbo.replenishment_requests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.replenishment_requests (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_replenishment_requests PRIMARY KEY,
        storage_area_id UNIQUEIDENTIFIER NOT NULL,
        inventory_item_id UNIQUEIDENTIFIER NOT NULL,
        requested_quantity DECIMAL(14,3) NOT NULL,
        urgency VARCHAR(10) NOT NULL,
        requested_by_user_id UNIQUEIDENTIFIER NOT NULL,
        submitted_at DATETIMEOFFSET NOT NULL,
        reviewed_by_user_id UNIQUEIDENTIFIER NULL,
        reviewed_at DATETIMEOFFSET NULL,
        review_notes NVARCHAR(2000) NULL,
        CONSTRAINT fk_replenishment_requests_area FOREIGN KEY (storage_area_id) REFERENCES dbo.storage_areas(id),
        CONSTRAINT fk_replenishment_requests_item FOREIGN KEY (inventory_item_id) REFERENCES dbo.inventory_items(id),
        CONSTRAINT fk_replenishment_requests_requester FOREIGN KEY (requested_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_replenishment_requests_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_replenishment_requests_quantity CHECK (requested_quantity > 0),
        CONSTRAINT ck_replenishment_requests_urgency CHECK (urgency IN ('LOW', 'NORMAL', 'HIGH')),
        CONSTRAINT ck_replenishment_requests_review CHECK (
            (reviewed_at IS NULL AND reviewed_by_user_id IS NULL)
            OR (reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL))
    );
    CREATE INDEX idx_replenishment_requests_queue ON dbo.replenishment_requests(reviewed_at, urgency, submitted_at);
END;
GO

/* ========================================================================== */
/* Source: 09_racing_reporting_rbac_audit.sql */
/* ========================================================================== */
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
        CONSTRAINT uq_role_definitions_code UNIQUE (code),
        CONSTRAINT ck_role_definitions_fixed_code CHECK (code IN (
            'CLUB_MANAGER', 'HEAD_TRAINER', 'STABLE_STAFF', 'HORSE_OWNER', 'VETERINARIAN'))
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'ck_role_definitions_fixed_code'
      AND parent_object_id = OBJECT_ID(N'dbo.role_definitions'))
BEGIN
    ALTER TABLE dbo.role_definitions ADD CONSTRAINT ck_role_definitions_fixed_code CHECK (code IN (
        'CLUB_MANAGER', 'HEAD_TRAINER', 'STABLE_STAFF', 'HORSE_OWNER', 'VETERINARIAN'));
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

/* user_role_assignments intentionally omitted; user_roles is the canonical account-role table. */
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

/* ========================================================================== */
/* Source: 10_confirmed_business_rules.sql */
/* ========================================================================== */
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

/* Confirmed rule: every racetrack operates 06:00 through midnight in 60-minute slots. */
IF COL_LENGTH(N'dbo.racetracks', N'opens_at') IS NULL
BEGIN
    ALTER TABLE dbo.racetracks ADD opens_at TIME NOT NULL
        CONSTRAINT df_racetracks_opens_at DEFAULT '06:00:00';
END;
GO

IF COL_LENGTH(N'dbo.racetracks', N'closes_at') IS NULL
BEGIN
    ALTER TABLE dbo.racetracks ADD closes_at TIME NOT NULL
        CONSTRAINT df_racetracks_closes_at DEFAULT '00:00:00';
END;
GO

IF COL_LENGTH(N'dbo.racetracks', N'closes_next_day') IS NULL
BEGIN
    ALTER TABLE dbo.racetracks ADD closes_next_day BIT NOT NULL
        CONSTRAINT df_racetracks_closes_next_day DEFAULT 1;
END;
GO

IF COL_LENGTH(N'dbo.racetracks', N'slot_minutes') IS NULL
BEGIN
    ALTER TABLE dbo.racetracks ADD slot_minutes INT NOT NULL
        CONSTRAINT df_racetracks_slot_minutes DEFAULT 60;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'ck_racetracks_slot_minutes'
      AND parent_object_id = OBJECT_ID(N'dbo.racetracks'))
BEGIN
    ALTER TABLE dbo.racetracks ADD CONSTRAINT ck_racetracks_slot_minutes CHECK (slot_minutes = 60);
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'ck_racetracks_operating_hours'
      AND parent_object_id = OBJECT_ID(N'dbo.racetracks'))
BEGIN
    ALTER TABLE dbo.racetracks ADD CONSTRAINT ck_racetracks_operating_hours
        CHECK (opens_at = '06:00:00' AND closes_at = '00:00:00' AND closes_next_day = 1);
END;
GO

/* Confirmed rule: horse weight is entered manually and retained as a timeline. */
IF OBJECT_ID(N'dbo.horse_weight_records', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_weight_records (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_weight_records PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        measured_by_user_id UNIQUEIDENTIFIER NOT NULL,
        weight_kg DECIMAL(7,2) NOT NULL,
        measured_at DATETIMEOFFSET NOT NULL,
        notes NVARCHAR(1000) NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_weight_records_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_horse_weight_records_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_horse_weight_records_user FOREIGN KEY (measured_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_horse_weight_records_weight CHECK (weight_kg > 0)
    );
    CREATE INDEX idx_horse_weight_records_timeline
        ON dbo.horse_weight_records(horse_id, measured_at DESC);
END;
GO

/* ========================================================================== */
/* Source: 11_confirmed_automation_and_governance.sql */
/* ========================================================================== */
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

/* ========================================================================== */
/* Source: 12_compact_schema.sql */
/* ========================================================================== */
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

/* ========================================================================== */
/* Source: 15_training_operational_window.sql */
/* ========================================================================== */
/* Training operational window, ground reservations, staffing claims and evaluation metrics. */
USE [racehorse];
GO

ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS ck_racetracks_operating_hours;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS ck_racetracks_slot_minutes;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_opens_at;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_closes_at;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_closes_next_day;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_slot_minutes;
GO
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_opens_at DEFAULT '05:00:00' FOR opens_at;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_at DEFAULT '11:00:00' FOR closes_at;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_next_day DEFAULT 0 FOR closes_next_day;
ALTER TABLE dbo.racetracks ADD CONSTRAINT ck_racetracks_operating_hours
    CHECK (opens_at = '05:00:00' AND closes_at = '11:00:00' AND closes_next_day = 0);
UPDATE dbo.racetracks SET opens_at='05:00:00', closes_at='11:00:00', closes_next_day=0;
GO

ALTER TABLE dbo.training_schedules ALTER COLUMN assigned_staff_id UNIQUEIDENTIFIER NULL;
IF COL_LENGTH(N'dbo.training_schedules', N'racetrack_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD racetrack_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_racetrack FOREIGN KEY(racetrack_id) REFERENCES dbo.racetracks(id);
END;
IF COL_LENGTH(N'dbo.training_schedules', N'veterinarian_user_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD veterinarian_user_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_vet FOREIGN KEY(veterinarian_user_id) REFERENCES dbo.users(id);
END;
IF COL_LENGTH(N'dbo.training_schedules', N'staff_assigned_by') IS NULL
    ALTER TABLE dbo.training_schedules ADD staff_assigned_by VARCHAR(20) NULL, staff_assigned_at DATETIMEOFFSET NULL,
        vet_assigned_by VARCHAR(20) NULL, vet_assigned_at DATETIMEOFFSET NULL;
GO

UPDATE s SET racetrack_id=l.racetrack_id FROM dbo.training_schedules s
JOIN dbo.trial_run_bookings b ON b.training_schedule_id=s.id
JOIN dbo.racetrack_lanes l ON l.id=b.racetrack_lane_id
WHERE s.racetrack_id IS NULL;
GO
CREATE OR ALTER TRIGGER dbo.tr_training_schedules_no_staff_overlap ON dbo.training_schedules AFTER INSERT, UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM dbo.training_schedules s JOIN inserted i ON s.assigned_staff_id=i.assigned_staff_id AND s.id<>i.id
   AND s.cancelled_at IS NULL AND i.cancelled_at IS NULL AND i.assigned_staff_id IS NOT NULL
   WHERE s.scheduled_start<i.scheduled_end AND s.scheduled_end>i.scheduled_start)
 THROW 50061,N'Nhân viên chuồng đã được phân công lịch huấn luyện khác trong ngày này.',1;
END;
GO
CREATE OR ALTER TRIGGER dbo.tr_training_schedules_no_vet_overlap ON dbo.training_schedules AFTER INSERT, UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM dbo.training_schedules s JOIN inserted i ON s.veterinarian_user_id=i.veterinarian_user_id AND s.id<>i.id
   AND s.cancelled_at IS NULL AND i.cancelled_at IS NULL AND i.veterinarian_user_id IS NOT NULL
   WHERE s.scheduled_start<i.scheduled_end AND s.scheduled_end>i.scheduled_start)
 THROW 50062,N'Bác sĩ thú y đã nhận lịch theo dõi buổi tập khác trong ngày này.',1;
END;
GO
IF COL_LENGTH(N'dbo.training_evaluations',N'actual_run_minutes') IS NULL
 ALTER TABLE dbo.training_evaluations ADD actual_run_minutes DECIMAL(8,2) NULL, average_heart_rate_bpm INT NULL,
 max_heart_rate_bpm INT NULL, peak_heart_rate_bpm INT NULL, heart_rate_after_rest_bpm INT NULL,
 rest_minutes DECIMAL(6,2) NULL, intensity_percent DECIMAL(7,2) NULL, heart_rate_recovery_rate DECIMAL(9,2) NULL,
 hrr1_bpm INT NULL;
GO
IF COL_LENGTH(N'dbo.training_plans',N'goal_target_race') IS NULL
 ALTER TABLE dbo.training_plans ADD goal_target_race NVARCHAR(300) NULL, planned_sessions_per_week TINYINT NULL,
 rest_days NVARCHAR(100) NULL, trainer_notes NVARCHAR(4000) NULL;
GO

ALTER TABLE dbo.horses ALTER COLUMN name NVARCHAR(150) NOT NULL;
IF COL_LENGTH(N'dbo.horses',N'normalized_name') IS NULL
 ALTER TABLE dbo.horses ADD normalized_name AS (CONVERT(NVARCHAR(150),LTRIM(RTRIM(name))) COLLATE Vietnamese_100_CI_AS_SC) PERSISTED;
IF NOT EXISTS(SELECT 1 FROM dbo.horses GROUP BY normalized_name HAVING COUNT(*)>1)
 AND NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name=N'uq_horses_normalized_name' AND object_id=OBJECT_ID(N'dbo.horses'))
 CREATE UNIQUE INDEX uq_horses_normalized_name ON dbo.horses(normalized_name);
GO

/* ========================================================================== */
/* Source: 16_seed_training_racetracks.sql */
/* ========================================================================== */
/* Seed active training grounds only when a name is not already present. */
USE [racehorse];
GO

DECLARE @LaneCount INT = 4;

IF @LaneCount < 1
    THROW 51020, N'@LaneCount ph\u1ea3i l\u1edbn h\u01a1n ho\u1eb7c b\u1eb1ng 1.', 1;

DECLARE @TrackNames TABLE (name NVARCHAR(200) NOT NULL PRIMARY KEY);
INSERT INTO @TrackNames (name)
VALUES
    (N'Sân tập 01'), (N'Sân tập 02'), (N'Sân tập 03'), (N'Sân tập 04'), (N'Sân tập 05'),
    (N'Sân tập 06'), (N'Sân tập 07'), (N'Sân tập 08'), (N'Sân tập 09'), (N'Sân tập 10');

INSERT INTO dbo.racetracks (id, name, is_active)
SELECT NEWID(), names.name, 1
FROM @TrackNames names
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.racetracks track
    WHERE track.name = names.name
);

DECLARE @LaneNumber INT = 1;
WHILE @LaneNumber <= @LaneCount
BEGIN
    INSERT INTO dbo.racetrack_lanes (id, racetrack_id, lane_number, is_active)
    SELECT NEWID(), track.id, @LaneNumber, 1
    FROM dbo.racetracks track
    JOIN @TrackNames names ON names.name = track.name
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.racetrack_lanes lane
        WHERE lane.racetrack_id = track.id
          AND lane.lane_number = @LaneNumber
    );

    SET @LaneNumber += 1;
END;
GO

/* ========================================================================== */
/* Source: 17_align_training_operational_schema.sql */
/* ========================================================================== */
/* Align the deployed schema with the training entities. Safe to rerun. */
USE [racehorse];
GO

IF COL_LENGTH(N'dbo.training_evaluations', N'actual_run_minutes') IS NULL
BEGIN
    ALTER TABLE dbo.training_evaluations ADD
        actual_run_minutes DECIMAL(8,2) NULL,
        average_heart_rate_bpm INT NULL,
        max_heart_rate_bpm INT NULL,
        peak_heart_rate_bpm INT NULL,
        heart_rate_after_rest_bpm INT NULL,
        rest_minutes DECIMAL(6,2) NULL,
        intensity_percent DECIMAL(7,2) NULL,
        heart_rate_recovery_rate DECIMAL(9,2) NULL,
        hrr1_bpm INT NULL;
END;
GO

ALTER TABLE dbo.training_schedules ALTER COLUMN assigned_staff_id UNIQUEIDENTIFIER NULL;

IF COL_LENGTH(N'dbo.training_schedules', N'racetrack_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD racetrack_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_racetrack
        FOREIGN KEY (racetrack_id) REFERENCES dbo.racetracks(id);
END;

IF COL_LENGTH(N'dbo.training_schedules', N'veterinarian_user_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD veterinarian_user_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_vet
        FOREIGN KEY (veterinarian_user_id) REFERENCES dbo.users(id);
END;

IF COL_LENGTH(N'dbo.training_schedules', N'staff_assigned_by') IS NULL
    ALTER TABLE dbo.training_schedules ADD
        staff_assigned_by VARCHAR(20) NULL,
        staff_assigned_at DATETIMEOFFSET NULL,
        vet_assigned_by VARCHAR(20) NULL,
        vet_assigned_at DATETIMEOFFSET NULL;
GO

/* ========================================================================== */
/* Source: 18_training_plan_rest_days_and_evaluation_metrics.sql */
/* ========================================================================== */
/* Training-plan rest days, date-derived schedule eligibility and evaluation metrics. Safe to rerun. */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
GO
USE [racehorse];
GO

IF COL_LENGTH(N'dbo.training_plan_phases', N'rest_days_json') IS NULL
BEGIN
    ALTER TABLE dbo.training_plan_phases ADD rest_days_json NVARCHAR(MAX) NOT NULL
        CONSTRAINT df_training_plan_phases_rest_days_json DEFAULT N'[]';
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'ck_training_plan_phases_rest_days_json'
      AND parent_object_id = OBJECT_ID(N'dbo.training_plan_phases')
)
BEGIN
    ALTER TABLE dbo.training_plan_phases ADD CONSTRAINT ck_training_plan_phases_rest_days_json
        CHECK (ISJSON(rest_days_json) = 1);
END;
GO

IF COL_LENGTH(N'dbo.training_evaluations', N'max_speed_kmh') IS NULL
    ALTER TABLE dbo.training_evaluations ADD max_speed_kmh DECIMAL(8,2) NULL;
IF COL_LENGTH(N'dbo.training_evaluations', N'heart_rate_at_stop_bpm') IS NULL
    ALTER TABLE dbo.training_evaluations ADD heart_rate_at_stop_bpm INT NULL;
IF COL_LENGTH(N'dbo.training_evaluations', N'heart_rate_after_1min_bpm') IS NULL
    ALTER TABLE dbo.training_evaluations ADD heart_rate_after_1min_bpm INT NULL;
GO

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.training_evaluations')
      AND name = N'intensity_percent' AND (precision <> 5 OR scale <> 1)
)
    ALTER TABLE dbo.training_evaluations ALTER COLUMN intensity_percent DECIMAL(5,1) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_training_evaluations_max_speed')
    ALTER TABLE dbo.training_evaluations ADD CONSTRAINT ck_training_evaluations_max_speed
        CHECK (max_speed_kmh IS NULL OR (max_speed_kmh > 0 AND max_speed_kmh >= average_speed_kmh));
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_training_evaluations_heart_rate_at_stop')
    ALTER TABLE dbo.training_evaluations ADD CONSTRAINT ck_training_evaluations_heart_rate_at_stop
        CHECK (heart_rate_at_stop_bpm IS NULL OR heart_rate_at_stop_bpm BETWEEN 0 AND 300);
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_training_evaluations_heart_rate_after_1min')
    ALTER TABLE dbo.training_evaluations ADD CONSTRAINT ck_training_evaluations_heart_rate_after_1min
        CHECK (heart_rate_after_1min_bpm IS NULL OR heart_rate_after_1min_bpm BETWEEN 0 AND 300);
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_training_evaluations_recovery_consistency')
    ALTER TABLE dbo.training_evaluations ADD CONSTRAINT ck_training_evaluations_recovery_consistency
        CHECK (heart_rate_at_stop_bpm IS NULL OR heart_rate_after_1min_bpm IS NULL
            OR heart_rate_recovery_bpm = heart_rate_at_stop_bpm - heart_rate_after_1min_bpm);
GO

CREATE OR ALTER TRIGGER dbo.tr_training_schedules_plan_and_lock
ON dbo.training_schedules
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_plan_phases p ON p.id = i.training_plan_phase_id
        JOIN dbo.training_plans plan_record ON plan_record.id = p.training_plan_id
        WHERE plan_record.horse_id <> i.horse_id
    )
        THROW 51012, N'Ngựa trên lịch phải trùng với ngựa của kế hoạch huấn luyện.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_plan_phases p ON p.id = i.training_plan_phase_id
        JOIN dbo.training_plans plan_record ON plan_record.id = p.training_plan_id
        WHERE plan_record.is_active = 0
           OR p.end_date < CONVERT(date, SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time')
    )
        THROW 51014, N'Không thể tạo buổi tập cho kế hoạch đã hết hạn.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_locks training_lock
          ON training_lock.horse_id = i.horse_id
         AND training_lock.released_at IS NULL
        WHERE i.workload_intensity = 'HIGH'
          AND i.cancelled_at IS NULL
    )
        THROW 51013, N'Không thể xếp lịch cường độ cao khi ngựa đang bị khóa huấn luyện.', 1;
END;
GO

/* ========================================================================== */
/* Source: 19_training_lanes_expiry_and_metrics.sql */
/* ========================================================================== */
/* Enforce the approved fixed-window, lane-based training rules. Safe to rerun. */
USE [racehorse];
GO

IF OBJECT_ID(N'dbo.tr_training_schedules_ground_exclusivity', N'TR') IS NOT NULL
    DROP TRIGGER dbo.tr_training_schedules_ground_exclusivity;
GO

UPDATE dbo.racetracks
SET opens_at = '05:00:00',
    closes_at = '11:00:00',
    closes_next_day = 0,
    slot_minutes = 360
WHERE opens_at <> '05:00:00'
   OR closes_at <> '11:00:00'
   OR closes_next_day <> 0
   OR slot_minutes <> 360;
GO

ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_slot_minutes;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_slot_minutes DEFAULT 360 FOR slot_minutes;
GO

DECLARE @LaneNumber INT = 1;
WHILE @LaneNumber <= 4
BEGIN
    INSERT INTO dbo.racetrack_lanes (id, racetrack_id, lane_number, is_active)
    SELECT NEWID(), track.id, @LaneNumber, track.is_active
    FROM dbo.racetracks track
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.racetrack_lanes lane
        WHERE lane.racetrack_id = track.id AND lane.lane_number = @LaneNumber
    );
    SET @LaneNumber += 1;
END;
GO

UPDATE dbo.racetrack_lanes
SET is_active = 0
WHERE lane_number NOT BETWEEN 1 AND 4
  AND is_active = 1;
GO

CREATE OR ALTER TRIGGER dbo.tr_trial_run_bookings_no_lane_overlap
ON dbo.trial_run_bookings
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.trial_run_bookings b
          ON b.racetrack_lane_id = i.racetrack_lane_id
         AND b.id <> i.id
         AND b.cancelled_at IS NULL
         AND i.cancelled_at IS NULL
         AND CONVERT(date, b.booked_start AT TIME ZONE 'SE Asia Standard Time')
             = CONVERT(date, i.booked_start AT TIME ZONE 'SE Asia Standard Time')
    )
        THROW 51003, N'Làn chạy đã được đặt trong ngày này.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_schedules current_session ON current_session.id = i.training_schedule_id
        JOIN dbo.trial_run_bookings b
          ON b.id <> i.id
         AND b.cancelled_at IS NULL
         AND i.cancelled_at IS NULL
        JOIN dbo.training_schedules other_session ON other_session.id = b.training_schedule_id
        WHERE current_session.horse_id = other_session.horse_id
          AND CONVERT(date, current_session.scheduled_start AT TIME ZONE 'SE Asia Standard Time')
              = CONVERT(date, other_session.scheduled_start AT TIME ZONE 'SE Asia Standard Time')
    )
        THROW 51003, N'Ngựa đã có buổi tập trong ngày này.', 1;
END;
GO

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.training_evaluations')
      AND name = N'intensity_percent'
      AND (precision <> 5 OR scale <> 2)
)
    ALTER TABLE dbo.training_evaluations ALTER COLUMN intensity_percent DECIMAL(5,2) NULL;
GO

CREATE OR ALTER TRIGGER dbo.tr_training_schedules_plan_and_lock
ON dbo.training_schedules
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_plan_phases p ON p.id = i.training_plan_phase_id
        JOIN dbo.training_plans plan_record ON plan_record.id = p.training_plan_id
        WHERE plan_record.horse_id <> i.horse_id
    )
        THROW 51012, N'Ngựa trên lịch phải trùng với ngựa của kế hoạch huấn luyện.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_plan_phases p ON p.id = i.training_plan_phase_id
        JOIN dbo.training_plans plan_record ON plan_record.id = p.training_plan_id
        CROSS APPLY (SELECT MAX(end_date) AS last_phase_end FROM dbo.training_plan_phases WHERE training_plan_id = plan_record.id) expiry
        WHERE i.cancelled_at IS NULL
          AND (plan_record.is_active = 0 OR expiry.last_phase_end <
              CONVERT(date, SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time'))
    )
        THROW 51014, N'Kế hoạch đã hết hạn, không thể tạo buổi tập mới.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_locks training_lock
          ON training_lock.horse_id = i.horse_id
         AND training_lock.released_at IS NULL
        WHERE i.workload_intensity = 'HIGH'
          AND i.cancelled_at IS NULL
    )
        THROW 51013, N'Không thể xếp lịch cường độ cao khi ngựa đang bị khóa huấn luyện.', 1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_training_evaluations_plan_expiry
ON dbo.training_evaluations
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_schedules s ON s.id = i.training_schedule_id
        JOIN dbo.training_plan_phases p ON p.id = s.training_plan_phase_id
        JOIN dbo.training_plans plan_record ON plan_record.id = p.training_plan_id
        CROSS APPLY (SELECT MAX(end_date) AS last_phase_end FROM dbo.training_plan_phases WHERE training_plan_id = plan_record.id) expiry
        WHERE plan_record.is_active = 0
           OR expiry.last_phase_end <
              CONVERT(date, SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time')
    )
        THROW 51014, N'Kế hoạch đã hết hạn, không thể tạo đánh giá mới.', 1;
END;
GO

/* ========================================================================== */
/* Source: 20_evaluation_exertion_nullable_and_session_notes.sql */
/* ========================================================================== */
/* Make perceived exertion optional and add optional session notes. Safe to rerun. */
USE [racehorse];
GO

IF COL_LENGTH(N'dbo.training_schedules', N'notes') IS NULL
    ALTER TABLE dbo.training_schedules ADD notes NVARCHAR(2000) NULL;
GO

IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = N'ck_training_evaluations_exertion'
      AND parent_object_id = OBJECT_ID(N'dbo.training_evaluations')
)
    ALTER TABLE dbo.training_evaluations DROP CONSTRAINT ck_training_evaluations_exertion;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.training_evaluations')
      AND name = N'perceived_exertion'
      AND is_nullable = 0
)
    ALTER TABLE dbo.training_evaluations ALTER COLUMN perceived_exertion TINYINT NULL;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = N'ck_training_evaluations_exertion'
      AND parent_object_id = OBJECT_ID(N'dbo.training_evaluations')
)
    ALTER TABLE dbo.training_evaluations ADD CONSTRAINT ck_training_evaluations_exertion
        CHECK (perceived_exertion IS NULL OR perceived_exertion BETWEEN 1 AND 10);
GO
