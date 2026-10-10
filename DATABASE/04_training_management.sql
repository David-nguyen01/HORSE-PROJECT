-- =========================================================================
-- 04_training_management.sql
-- Training Plans, Phases, Locks, Schedules, Lane Bookings & Evaluations
-- Target Database: racehorse_local
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
GO

USE [racehorse_local];
GO

-- 1. Training Plans
IF OBJECT_ID(N'dbo.training_plans', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_plans (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_training_plans PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        name NVARCHAR(200) NOT NULL,
        source_plan_id UNIQUEIDENTIFIER NULL,
        target_goal NVARCHAR(500) NULL,
        notes NVARCHAR(2000) NULL,
        is_active BIT NOT NULL CONSTRAINT df_training_plans_is_active DEFAULT 1,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_plans_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_plans_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_training_plans_version DEFAULT 0,
        CONSTRAINT fk_training_plans_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_training_plans_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_training_plans_source FOREIGN KEY (source_plan_id) REFERENCES dbo.training_plans(id)
    );
END;
GO

-- 2. Training Plan Phases
IF OBJECT_ID(N'dbo.training_plan_phases', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_plan_phases (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_training_plan_phases PRIMARY KEY,
        training_plan_id UNIQUEIDENTIFIER NOT NULL,
        phase_order INT NOT NULL,
        name NVARCHAR(150) NOT NULL,
        start_date DATE NOT NULL,
        end_date DATE NOT NULL,
        target_distance_meters DECIMAL(12,2) NOT NULL,
        workload_intensity VARCHAR(10) NOT NULL,
        surface_type VARCHAR(20) NOT NULL,
        rest_days_json NVARCHAR(MAX) NOT NULL CONSTRAINT df_training_plan_phases_rest_days DEFAULT N'[]',
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_plan_phases_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_plan_phases_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_training_plan_phases_version DEFAULT 0,
        CONSTRAINT fk_training_plan_phases_plan FOREIGN KEY (training_plan_id) REFERENCES dbo.training_plans(id) ON DELETE CASCADE,
        CONSTRAINT uq_training_plan_phases_order UNIQUE (training_plan_id, phase_order),
        CONSTRAINT ck_training_plan_phases_dates CHECK (start_date <= end_date),
        CONSTRAINT ck_training_plan_phases_intensity CHECK (workload_intensity IN ('LOW', 'MEDIUM', 'HIGH')),
        CONSTRAINT ck_training_plan_phases_surface CHECK (surface_type IN ('GRASS', 'DIRT', 'SYNTHETIC')),
        CONSTRAINT ck_training_plan_phases_rest_days_json CHECK (ISJSON(rest_days_json) = 1)
    );
END;
GO

-- 3. Training Locks (Veterinary or administrative safety hold)
IF OBJECT_ID(N'dbo.training_locks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_locks (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_training_locks PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        reason NVARCHAR(2000) NOT NULL,
        review_date DATE NULL,
        locked_by_user_id UNIQUEIDENTIFIER NOT NULL,
        locked_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_locks_locked_at DEFAULT SYSDATETIMEOFFSET(),
        released_by_user_id UNIQUEIDENTIFIER NULL,
        release_reason NVARCHAR(2000) NULL,
        released_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_locks_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_locks_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_training_locks_version DEFAULT 0,
        CONSTRAINT fk_training_locks_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_training_locks_locked_by FOREIGN KEY (locked_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_training_locks_released_by FOREIGN KEY (released_by_user_id) REFERENCES dbo.users(id)
    );
    CREATE NONCLUSTERED INDEX idx_training_locks_horse ON dbo.training_locks(horse_id);
    CREATE UNIQUE NONCLUSTERED INDEX ux_training_locks_active_horse
        ON dbo.training_locks(horse_id)
        WHERE released_at IS NULL;
END;
GO

-- 4. Training Schedules (one-hour slots starting 06:00 through 23:00)
IF OBJECT_ID(N'dbo.training_schedules', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_schedules (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_training_schedules PRIMARY KEY,
        training_plan_phase_id UNIQUEIDENTIFIER NOT NULL,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        racetrack_id UNIQUEIDENTIFIER NOT NULL,
        task_name NVARCHAR(300) NOT NULL,
        notes NVARCHAR(2000) NULL,
        workload_intensity VARCHAR(10) NOT NULL,
        scheduled_start DATETIMEOFFSET NOT NULL,
        scheduled_end DATETIMEOFFSET NOT NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        published_at DATETIMEOFFSET NULL,
        cancelled_at DATETIMEOFFSET NULL,
        completed_at DATETIMEOFFSET NULL,
        session_status VARCHAR(20) NULL CONSTRAINT df_training_schedules_session_status DEFAULT 'PREPARING',
        ready_at DATETIMEOFFSET NULL,
        ready_by_user_id UNIQUEIDENTIFIER NULL,
        started_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_schedules_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_schedules_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_training_schedules_version DEFAULT 0,
        CONSTRAINT fk_training_schedules_phase FOREIGN KEY (training_plan_phase_id) REFERENCES dbo.training_plan_phases(id),
        CONSTRAINT fk_training_schedules_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_training_schedules_racetrack FOREIGN KEY (racetrack_id) REFERENCES dbo.racetracks(id),
        CONSTRAINT fk_training_schedules_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_training_schedules_ready_by FOREIGN KEY (ready_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_training_schedules_times CHECK (scheduled_start < scheduled_end),
        CONSTRAINT ck_training_schedules_intensity CHECK (workload_intensity IN ('LOW', 'MEDIUM', 'HIGH')),
        CONSTRAINT ck_training_v3_session_status CHECK (
            (session_status IS NOT NULL AND session_status IN ('PREPARING', 'READY', 'IN_PROGRESS', 'COMPLETED', 'INJURED', 'EMERGENCY_STOPPED'))
            OR (session_status IS NULL AND cancelled_at IS NOT NULL))
    );
    CREATE NONCLUSTERED INDEX idx_training_schedules_horse_date ON dbo.training_schedules(horse_id, scheduled_start);
    CREATE NONCLUSTERED INDEX idx_training_schedules_track ON dbo.training_schedules(racetrack_id, scheduled_start);
END;
GO

-- 5. Trial Run Bookings (Lane reservation)
IF OBJECT_ID(N'dbo.trial_run_bookings', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.trial_run_bookings (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_trial_run_bookings PRIMARY KEY,
        training_schedule_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT uq_trial_run_bookings_schedule UNIQUE,
        racetrack_lane_id UNIQUEIDENTIFIER NOT NULL,
        booked_start DATETIMEOFFSET NOT NULL,
        booked_end DATETIMEOFFSET NOT NULL,
        cancelled_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_trial_run_bookings_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_trial_run_bookings_schedule FOREIGN KEY (training_schedule_id) REFERENCES dbo.training_schedules(id),
        CONSTRAINT fk_trial_run_bookings_lane FOREIGN KEY (racetrack_lane_id) REFERENCES dbo.racetrack_lanes(id),
        CONSTRAINT ck_trial_run_bookings_times CHECK (booked_start < booked_end)
    );
    CREATE NONCLUSTERED INDEX idx_trial_run_bookings_lane_time ON dbo.trial_run_bookings(racetrack_lane_id, booked_start, booked_end);
END;
GO

-- 6. Training Evaluations (Metrics, Timings & Health After Run)
IF OBJECT_ID(N'dbo.training_evaluations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_evaluations (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_training_evaluations PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        training_schedule_id UNIQUEIDENTIFIER NULL,
        recorded_by_user_id UNIQUEIDENTIFIER NOT NULL,
        session_date DATE NOT NULL,
        duration_minutes INT NOT NULL,
        distance_meters DECIMAL(12,2) NOT NULL,
        average_speed_kmh DECIMAL(8,2) NOT NULL,
        heart_rate_recovery_bpm INT NOT NULL,
        perceived_exertion TINYINT NULL,
        fitness_status VARCHAR(10) NOT NULL,
        trainer_notes NVARCHAR(4000) NOT NULL,
        video_url NVARCHAR(2000) NULL,
        completed_at DATETIMEOFFSET NOT NULL,
        actual_run_minutes DECIMAL(8,2) NULL,
        average_heart_rate_bpm INT NULL,
        max_heart_rate_bpm INT NULL,
        peak_heart_rate_bpm INT NULL,
        heart_rate_after_rest_bpm INT NULL,
        rest_minutes DECIMAL(6,2) NULL,
        intensity_percent DECIMAL(5,2) NULL,
        max_speed_kmh DECIMAL(8,2) NULL,
        heart_rate_at_stop_bpm INT NULL,
        heart_rate_after_1min_bpm INT NULL,
        heart_rate_recovery_rate DECIMAL(9,2) NULL,
        hrr1_bpm INT NULL,
        track_completion_seconds DECIMAL(12,3) NULL,
        injured BIT NULL,
        injury_location NVARCHAR(200) NULL,
        injury_notes NVARCHAR(4000) NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_evaluations_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_training_evaluations_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_training_evaluations_version DEFAULT 0,
        CONSTRAINT fk_training_evaluations_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_training_evaluations_schedule FOREIGN KEY (training_schedule_id) REFERENCES dbo.training_schedules(id),
        CONSTRAINT fk_training_evaluations_recorded_by FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_training_evaluations_duration CHECK (duration_minutes > 0),
        CONSTRAINT ck_training_evaluations_distance CHECK (distance_meters > 0),
        CONSTRAINT ck_training_evaluations_speed CHECK (average_speed_kmh > 0),
        CONSTRAINT ck_training_evaluations_exertion CHECK (perceived_exertion IS NULL OR perceived_exertion BETWEEN 1 AND 10),
        CONSTRAINT ck_training_evaluations_fitness CHECK (fitness_status IN ('GREEN', 'YELLOW', 'RED')),
        CONSTRAINT ck_training_evaluations_max_speed CHECK (max_speed_kmh IS NULL OR (max_speed_kmh > 0 AND max_speed_kmh >= average_speed_kmh)),
        CONSTRAINT ck_training_evaluations_heart_rate_at_stop CHECK (heart_rate_at_stop_bpm IS NULL OR heart_rate_at_stop_bpm BETWEEN 0 AND 300),
        CONSTRAINT ck_training_evaluations_heart_rate_after_1min CHECK (heart_rate_after_1min_bpm IS NULL OR heart_rate_after_1min_bpm BETWEEN 0 AND 300)
    );
    CREATE NONCLUSTERED INDEX idx_training_evaluations_horse_date ON dbo.training_evaluations(horse_id, session_date DESC, completed_at DESC);
    CREATE NONCLUSTERED INDEX idx_training_evaluations_dashboard
        ON dbo.training_evaluations(session_date, fitness_status)
        INCLUDE (horse_id, average_speed_kmh, heart_rate_recovery_bpm);
END;
GO

-- =========================================================================
-- TRAINING TRIGGERS
-- =========================================================================

-- Trigger: Enforce 06:00 - midnight, one-hour training slots
CREATE OR ALTER TRIGGER dbo.tr_enforce_training_window
ON dbo.training_schedules
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted i
        LEFT JOIN deleted d ON d.id = i.id
        WHERE i.cancelled_at IS NULL
          AND (d.id IS NULL OR i.scheduled_start <> d.scheduled_start
               OR i.scheduled_end <> d.scheduled_end OR d.cancelled_at IS NOT NULL)
          AND (
            CAST(i.scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS TIME) < '06:00:00'
            OR CAST(i.scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS TIME) > '23:00:00'
            OR DATEPART(MINUTE, i.scheduled_start AT TIME ZONE 'SE Asia Standard Time') <> 0
            OR DATEPART(SECOND, i.scheduled_start AT TIME ZONE 'SE Asia Standard Time') <> 0
            OR i.scheduled_end <> DATEADD(HOUR, 1, i.scheduled_start)
          )
    )
    BEGIN
        THROW 51001, N'Lịch huấn luyện phải là slot 1 giờ bắt đầu từ 06:00 đến 23:00 (kết thúc muộn nhất 00:00 hôm sau).', 1;
    END;
END;
GO

-- Trigger: Lane Reservation Integrity (No Concurrent Overlap on Same Lane)
CREATE OR ALTER TRIGGER dbo.tr_trial_run_bookings_no_lane_overlap
ON dbo.trial_run_bookings
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.trial_run_bookings b WITH (UPDLOCK, HOLDLOCK)
          ON b.racetrack_lane_id = i.racetrack_lane_id
         AND b.id <> i.id
        WHERE b.cancelled_at IS NULL
          AND i.cancelled_at IS NULL
          AND i.booked_start < b.booked_end AND i.booked_end > b.booked_start
    )
    BEGIN
        THROW 51003, N'Làn chạy đã được đặt trong khung giờ này.', 1;
    END;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_schedules s ON s.id = i.training_schedule_id
        JOIN dbo.racetrack_lanes l ON l.id = i.racetrack_lane_id
        WHERE i.cancelled_at IS NULL
          AND (s.racetrack_id <> l.racetrack_id OR s.racetrack_id IS NULL
            OR s.cancelled_at IS NOT NULL OR l.is_active = 0 OR l.lane_number NOT BETWEEN 1 AND 4
            OR i.booked_start <> s.scheduled_start OR i.booked_end <> s.scheduled_end)
    )
    BEGIN
        THROW 51074, N'Thông tin làn chạy và thời gian đặt làn phải khớp với buổi tập đang hoạt động.', 1;
    END;
END;
GO

-- Trigger: Training Schedule Plan, Phase & Health Integrity
CREATE OR ALTER TRIGGER dbo.tr_training_schedules_plan_and_lock
ON dbo.training_schedules
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    -- Horse must match plan's horse
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.training_plan_phases p ON p.id = i.training_plan_phase_id
        JOIN dbo.training_plans tp ON tp.id = p.training_plan_id
        WHERE tp.horse_id <> i.horse_id
    )
    BEGIN
        THROW 51012, N'Ngựa trên lịch phải trùng với ngựa của kế hoạch huấn luyện.', 1;
    END;

    -- Session date must be within active non-expired plan phase
    IF EXISTS (
        SELECT 1
        FROM inserted i
        LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.training_plan_phases p ON p.id = i.training_plan_phase_id
        JOIN dbo.training_plans tp ON tp.id = p.training_plan_id
        CROSS APPLY (SELECT MAX(end_date) AS last_end FROM dbo.training_plan_phases WHERE training_plan_id = tp.id) expiry
        WHERE i.cancelled_at IS NULL
          AND (d.id IS NULL OR i.training_plan_phase_id <> d.training_plan_phase_id
            OR i.horse_id <> d.horse_id OR i.scheduled_start <> d.scheduled_start OR i.scheduled_end <> d.scheduled_end)
          AND (tp.is_active = 0
            OR expiry.last_end < CONVERT(date, SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time')
            OR CONVERT(date, i.scheduled_start AT TIME ZONE 'SE Asia Standard Time') NOT BETWEEN p.start_date AND p.end_date)
    )
    BEGIN
        THROW 51014, N'Ngày tập phải nằm trong giai đoạn huấn luyện còn hiệu lực.', 1;
    END;

    -- Injured horse cannot be scheduled or started
    IF EXISTS (
        SELECT 1
        FROM inserted i
        LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.horses h ON h.id = i.horse_id
        WHERE i.cancelled_at IS NULL
          AND (d.id IS NULL OR i.scheduled_start <> d.scheduled_start OR i.scheduled_end <> d.scheduled_end
            OR i.horse_id <> d.horse_id OR (i.session_status = 'IN_PROGRESS' AND ISNULL(d.session_status, '') <> 'IN_PROGRESS'))
          AND (h.status = 'INJURED' OR h.health_status = 'INJURED')
    )
    BEGIN
        THROW 51075, N'Ngựa đang bị chấn thương, không thể xếp hoặc bắt đầu buổi tập.', 1;
    END;

    -- High workload intensity blocked while active training lock exists
    IF EXISTS (
        SELECT 1
        FROM inserted i
        LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.training_locks l ON l.horse_id = i.horse_id AND l.released_at IS NULL
        WHERE i.cancelled_at IS NULL
          AND i.workload_intensity = 'HIGH'
          AND (d.id IS NULL OR i.workload_intensity <> d.workload_intensity
            OR i.scheduled_start <> d.scheduled_start OR i.scheduled_end <> d.scheduled_end
            OR (i.session_status = 'IN_PROGRESS' AND ISNULL(d.session_status, '') <> 'IN_PROGRESS'))
    )
    BEGIN
        THROW 51013, N'Không thể xếp lịch cường độ cao khi ngựa đang bị khóa huấn luyện.', 1;
    END;

    -- 1 Horse / 1 Session / Day (no duplicate session on same date)
    IF EXISTS (
        SELECT 1
        FROM inserted i
        LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.training_schedules s WITH (UPDLOCK, HOLDLOCK)
          ON s.horse_id = i.horse_id AND s.id <> i.id
        WHERE i.cancelled_at IS NULL
          AND s.cancelled_at IS NULL
          AND (d.id IS NULL OR i.horse_id <> d.horse_id OR i.scheduled_start <> d.scheduled_start
            OR i.scheduled_end <> d.scheduled_end OR d.cancelled_at IS NOT NULL)
          AND (CONVERT(date, s.scheduled_start AT TIME ZONE 'SE Asia Standard Time') =
               CONVERT(date, i.scheduled_start AT TIME ZONE 'SE Asia Standard Time')
            OR (i.scheduled_start < s.scheduled_end AND i.scheduled_end > s.scheduled_start))
    )
    BEGIN
        THROW 51076, N'Ngựa đã có buổi tập trong ngày này.', 1;
    END;
END;
GO

-- Trigger: Plan Expiry Check on Evaluations
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
    BEGIN
        THROW 51014, N'Kế hoạch đã hết hạn, không thể tạo đánh giá mới.', 1;
    END;
END;
GO
