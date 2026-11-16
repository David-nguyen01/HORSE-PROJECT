USE [racehorse_local];
GO

IF COL_LENGTH('dbo.service_packages', 'deleted_at') IS NULL
    ALTER TABLE dbo.service_packages ADD deleted_at DATETIMEOFFSET NULL;
GO
