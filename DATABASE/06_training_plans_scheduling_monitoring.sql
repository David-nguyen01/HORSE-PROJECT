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
