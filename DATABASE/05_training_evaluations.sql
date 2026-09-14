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
        perceived_exertion TINYINT NOT NULL,
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
        CONSTRAINT ck_training_evaluations_exertion CHECK (perceived_exertion BETWEEN 1 AND 10),
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
