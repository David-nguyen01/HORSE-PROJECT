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
