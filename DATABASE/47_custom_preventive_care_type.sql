USE racehorse_local;
GO

IF COL_LENGTH('dbo.preventive_care_schedules', 'care_name') IS NULL
    ALTER TABLE dbo.preventive_care_schedules ADD care_name NVARCHAR(120) NULL;
GO

IF OBJECT_ID(N'dbo.ck_preventive_care_type', N'C') IS NOT NULL
    ALTER TABLE dbo.preventive_care_schedules DROP CONSTRAINT ck_preventive_care_type;
GO

ALTER TABLE dbo.preventive_care_schedules WITH CHECK ADD
    CONSTRAINT ck_preventive_care_type
    CHECK (care_type IN ('VACCINATION', 'DEWORMING', 'FARRIER', 'OTHER'));
GO

IF OBJECT_ID(N'dbo.ck_preventive_care_other_name', N'C') IS NULL
    ALTER TABLE dbo.preventive_care_schedules WITH CHECK ADD
        CONSTRAINT ck_preventive_care_other_name
        CHECK (care_type <> 'OTHER' OR (care_name IS NOT NULL AND LEN(LTRIM(RTRIM(care_name))) > 0));
GO
