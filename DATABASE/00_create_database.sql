-- =========================================================================
-- 00_create_database.sql
-- Racehorse Training Management System: Database Initialization
-- Target: SQL Server Local Development Baseline (racehorse_local)
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

-- Ensure application login exists for local execution
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'racehorse_local_app')
BEGIN
    CREATE LOGIN [racehorse_local_app] WITH PASSWORD = N'LocalApp@123', CHECK_POLICY = OFF;
END;
GO

USE [racehorse_local];
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'racehorse_local_app')
BEGIN
    CREATE USER [racehorse_local_app] FOR LOGIN [racehorse_local_app];
    ALTER ROLE db_owner ADD MEMBER [racehorse_local_app];
END;
GO

SELECT
    DB_NAME() AS database_name,
    DATABASEPROPERTYEX(DB_NAME(), 'Collation') AS collation_name,
    SUSER_SNAME() AS executed_by;
GO
