-- =========================================================================
-- 00_create_database.sql
-- Racehorse Training Management System: Database Initialization
-- Target: SQL Server Local Development Baseline
-- =========================================================================

USE [master];
GO

IF DB_ID(N'racehorse_local') IS NULL
BEGIN
    CREATE DATABASE [racehorse_local]
    COLLATE Vietnamese_100_CI_AS_SC;
END;
GO

ALTER DATABASE [racehorse_local] SET RECOVERY SIMPLE;
GO

USE [racehorse_local];
GO

SELECT
    DB_NAME() AS database_name,
    DATABASEPROPERTYEX(DB_NAME(), 'Collation') AS collation_name,
    SUSER_SNAME() AS executed_by;
GO
