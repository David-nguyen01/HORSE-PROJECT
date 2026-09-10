USE [master];
GO

IF DB_ID(N'racehorse') IS NULL
BEGIN
    CREATE DATABASE [racehorse];
END;
GO

ALTER DATABASE [racehorse] SET RECOVERY SIMPLE;
GO

USE [racehorse];
GO

SELECT
    DB_NAME() AS database_name,
    SUSER_SNAME() AS executed_by;
GO
