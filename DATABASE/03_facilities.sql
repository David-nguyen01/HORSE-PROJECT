-- =========================================================================
-- 03_facilities.sql
-- Facilities, Stables, Racetracks & Racetrack Lanes
-- Target Database: racehorse_local
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
GO

USE [racehorse_local];
GO

-- 1. Stables (Physical barn facilities)
IF OBJECT_ID(N'dbo.stables', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.stables (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_stables PRIMARY KEY,
        name NVARCHAR(150) NOT NULL CONSTRAINT uq_stables_name UNIQUE,
        location NVARCHAR(300) NULL,
        is_active BIT NOT NULL CONSTRAINT df_stables_is_active DEFAULT 1,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_stables_created_at DEFAULT SYSDATETIMEOFFSET()
    );
END;
GO

-- 2. Racetracks (Training grounds)
IF OBJECT_ID(N'dbo.racetracks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.racetracks (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_racetracks PRIMARY KEY,
        name NVARCHAR(200) NOT NULL CONSTRAINT uq_racetracks_name UNIQUE,
        location NVARCHAR(300) NULL,
        is_active BIT NOT NULL CONSTRAINT df_racetracks_is_active DEFAULT 1,
        opens_at TIME NOT NULL CONSTRAINT df_racetracks_opens_at DEFAULT '05:00:00',
        closes_at TIME NOT NULL CONSTRAINT df_racetracks_closes_at DEFAULT '11:00:00',
        closes_next_day BIT NOT NULL CONSTRAINT df_racetracks_closes_next_day DEFAULT 0,
        slot_minutes INT NOT NULL CONSTRAINT df_racetracks_slot_minutes DEFAULT 360,
        CONSTRAINT ck_racetracks_operating_hours CHECK (opens_at = '05:00:00' AND closes_at = '11:00:00' AND closes_next_day = 0)
    );
END;
GO

-- 3. Racetrack Lanes (4 designated running lanes per active ground)
IF OBJECT_ID(N'dbo.racetrack_lanes', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.racetrack_lanes (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_racetrack_lanes PRIMARY KEY,
        racetrack_id UNIQUEIDENTIFIER NOT NULL,
        lane_number INT NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_racetrack_lanes_is_active DEFAULT 1,
        CONSTRAINT fk_racetrack_lanes_track FOREIGN KEY (racetrack_id) REFERENCES dbo.racetracks(id) ON DELETE CASCADE,
        CONSTRAINT uq_racetrack_lanes_number UNIQUE (racetrack_id, lane_number),
        CONSTRAINT ck_racetrack_lanes_number CHECK (lane_number BETWEEN 1 AND 4)
    );
END;
GO
