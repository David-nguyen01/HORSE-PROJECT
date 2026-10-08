/* Store an unlinked parent's display name and supplied ID without creating horse records. Safe to rerun. */
USE [racehorse];
GO

IF OBJECT_ID(N'dbo.horses', N'U') IS NULL
    THROW 51077, N'Apply the horse schema before migration 27.', 1;
GO

IF COL_LENGTH(N'dbo.horses', N'sire_manual_name') IS NULL
    ALTER TABLE dbo.horses ADD sire_manual_name NVARCHAR(150) NULL;
GO
IF COL_LENGTH(N'dbo.horses', N'sire_manual_id') IS NULL
    ALTER TABLE dbo.horses ADD sire_manual_id VARCHAR(50) NULL;
GO
IF COL_LENGTH(N'dbo.horses', N'dam_manual_name') IS NULL
    ALTER TABLE dbo.horses ADD dam_manual_name NVARCHAR(150) NULL;
GO
IF COL_LENGTH(N'dbo.horses', N'dam_manual_id') IS NULL
    ALTER TABLE dbo.horses ADD dam_manual_id VARCHAR(50) NULL;
GO

IF OBJECT_ID(N'dbo.ck_horses_manual_pedigree', N'C') IS NULL
    ALTER TABLE dbo.horses WITH CHECK ADD CONSTRAINT ck_horses_manual_pedigree CHECK (
        ((sire_manual_name IS NULL AND sire_manual_id IS NULL)
            OR (sire_manual_name IS NOT NULL AND sire_manual_id IS NOT NULL
                AND LEN(LTRIM(RTRIM(sire_manual_name))) > 0 AND LEN(LTRIM(RTRIM(sire_manual_id))) > 0 AND sire_id IS NULL))
        AND ((dam_manual_name IS NULL AND dam_manual_id IS NULL)
            OR (dam_manual_name IS NOT NULL AND dam_manual_id IS NOT NULL
                AND LEN(LTRIM(RTRIM(dam_manual_name))) > 0 AND LEN(LTRIM(RTRIM(dam_manual_id))) > 0 AND dam_id IS NULL))
    );
GO
