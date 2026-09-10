USE [racehorse];
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.horse_profile_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_profile_files (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_profile_files PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        uploaded_by_id UNIQUEIDENTIFIER NOT NULL,
        approved_by_id UNIQUEIDENTIFIER NULL,
        cloudinary_asset_id VARCHAR(100) NOT NULL,
        cloudinary_public_id VARCHAR(300) NOT NULL,
        resource_type VARCHAR(20) NOT NULL,
        file_format VARCHAR(30) NULL,
        original_filename NVARCHAR(255) NOT NULL,
        content_type VARCHAR(150) NOT NULL,
        file_size BIGINT NOT NULL,
        secure_url VARCHAR(1000) NOT NULL,
        status VARCHAR(20) NOT NULL,
        approved_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_profile_files_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_profile_files_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_horse_profile_files_version DEFAULT 0,
        CONSTRAINT fk_horse_profile_files_horse FOREIGN KEY (horse_id)
            REFERENCES dbo.horses(id) ON DELETE CASCADE,
        CONSTRAINT fk_horse_profile_files_uploader FOREIGN KEY (uploaded_by_id)
            REFERENCES dbo.users(id),
        CONSTRAINT fk_horse_profile_files_approver FOREIGN KEY (approved_by_id)
            REFERENCES dbo.users(id),
        CONSTRAINT uq_horse_profile_files_cloudinary_asset UNIQUE (cloudinary_asset_id),
        CONSTRAINT ck_horse_profile_files_size CHECK (file_size > 0),
        CONSTRAINT ck_horse_profile_files_resource_type CHECK (resource_type IN ('image', 'raw')),
        CONSTRAINT ck_horse_profile_files_status CHECK (status IN (
            'ACTIVE', 'PENDING', 'REPLACED', 'DELETED'))
    );

END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'uq_horse_profile_files_one_active'
      AND object_id = OBJECT_ID(N'dbo.horse_profile_files')
)
BEGIN
    CREATE UNIQUE INDEX uq_horse_profile_files_one_active
        ON dbo.horse_profile_files(horse_id)
        WHERE status = 'ACTIVE';
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'idx_horse_profile_files_pending'
      AND object_id = OBJECT_ID(N'dbo.horse_profile_files')
)
BEGIN
    CREATE INDEX idx_horse_profile_files_pending
        ON dbo.horse_profile_files(horse_id, created_at)
        WHERE status = 'PENDING';
END;
GO
