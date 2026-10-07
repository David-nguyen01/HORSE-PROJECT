-- =========================================================================
-- 06_telemetry_and_alerts.sql
-- Horse Metric Thresholds, Telemetry Sessions, Readings & Alert Events
-- Target Database: racehorse_local
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
GO

USE [racehorse_local];
GO

-- 1. Horse Metric Thresholds (Heart rate & speed boundaries)
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
    CREATE NONCLUSTERED INDEX idx_horse_metric_thresholds_active
        ON dbo.horse_metric_thresholds(horse_id, metric_type, effective_from, effective_to);
END;
GO

-- 2. Telemetry Sessions (Resting, training & recovery telemetry tracking)
IF OBJECT_ID(N'dbo.telemetry_sessions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.telemetry_sessions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_telemetry_sessions PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        training_schedule_id UNIQUEIDENTIFIER NULL,
        started_at DATETIMEOFFSET NOT NULL,
        ended_at DATETIMEOFFSET NULL,
        recorded_by_user_id UNIQUEIDENTIFIER NOT NULL,
        entry_method VARCHAR(20) NOT NULL,
        measurement_context VARCHAR(30) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_telemetry_sessions_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_telemetry_sessions_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_telemetry_sessions_schedule FOREIGN KEY (training_schedule_id) REFERENCES dbo.training_schedules(id),
        CONSTRAINT fk_telemetry_sessions_recorder FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_telemetry_sessions_dates CHECK (ended_at IS NULL OR started_at < ended_at)
    );
    CREATE NONCLUSTERED INDEX idx_telemetry_sessions_horse_time ON dbo.telemetry_sessions(horse_id, started_at DESC);
END;
GO

-- 3. Telemetry Readings (High frequency biometric data points)
IF OBJECT_ID(N'dbo.telemetry_readings', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.telemetry_readings (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_telemetry_readings PRIMARY KEY,
        telemetry_session_id UNIQUEIDENTIFIER NOT NULL,
        recorded_at DATETIMEOFFSET NOT NULL,
        heart_rate_bpm INT NULL,
        speed_kmh DECIMAL(8,3) NULL,
        CONSTRAINT fk_telemetry_readings_session FOREIGN KEY (telemetry_session_id) REFERENCES dbo.telemetry_sessions(id) ON DELETE CASCADE,
        CONSTRAINT ck_telemetry_readings_value CHECK (heart_rate_bpm IS NOT NULL OR speed_kmh IS NOT NULL),
        CONSTRAINT ck_telemetry_readings_heart_rate CHECK (heart_rate_bpm IS NULL OR heart_rate_bpm BETWEEN 0 AND 300),
        CONSTRAINT ck_telemetry_readings_speed CHECK (speed_kmh IS NULL OR speed_kmh >= 0)
    );
    CREATE NONCLUSTERED INDEX idx_telemetry_readings_session_time
        ON dbo.telemetry_readings(telemetry_session_id, recorded_at);
END;
GO

-- 4. Alert Events (Threshold breach notifications & acknowledgments)
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
    CREATE NONCLUSTERED INDEX idx_alert_events_horse_time ON dbo.alert_events(horse_id, occurred_at DESC);
END;
GO
