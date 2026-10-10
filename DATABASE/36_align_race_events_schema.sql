/* Align dbo.race_events schema with RaceEventService (race_name, racecourse, race_class). */
USE [racehorse_local];
GO

IF COL_LENGTH(N'dbo.race_events', N'race_name') IS NULL
BEGIN
    ALTER TABLE dbo.race_events ADD race_name NVARCHAR(200) NULL;
END;
GO

IF COL_LENGTH(N'dbo.race_events', N'racecourse') IS NULL
BEGIN
    ALTER TABLE dbo.race_events ADD racecourse NVARCHAR(300) NULL;
END;
GO

IF COL_LENGTH(N'dbo.race_events', N'race_class') IS NULL
BEGIN
    ALTER TABLE dbo.race_events ADD race_class NVARCHAR(100) NULL;
END;
GO

IF EXISTS (
    SELECT 1 FROM sys.columns 
    WHERE object_id = OBJECT_ID(N'dbo.race_events') AND name = N'name' AND is_nullable = 0
)
BEGIN
    ALTER TABLE dbo.race_events ALTER COLUMN name NVARCHAR(200) NULL;
END;
GO

IF EXISTS (
    SELECT 1 FROM sys.columns 
    WHERE object_id = OBJECT_ID(N'dbo.race_events') AND name = N'location' AND is_nullable = 0
)
BEGIN
    ALTER TABLE dbo.race_events ALTER COLUMN location NVARCHAR(300) NULL;
END;
GO

IF EXISTS (
    SELECT 1 FROM sys.columns 
    WHERE object_id = OBJECT_ID(N'dbo.race_events') AND name = N'race_time' AND is_nullable = 0
)
BEGIN
    ALTER TABLE dbo.race_events ALTER COLUMN race_time TIME NULL;
END;
GO
