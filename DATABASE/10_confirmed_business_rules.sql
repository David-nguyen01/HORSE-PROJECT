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
