-- =========================================================================
-- 02_horses_and_profiles.sql
-- Horses, Pedigree, Ownerships, Files & Measurements
-- Target Database: racehorse_local
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
GO

USE [racehorse_local];
GO

-- 1. Horses (Core horse registry with service plans, manual pedigree and weight tracking)
IF OBJECT_ID(N'dbo.horses', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horses (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horses PRIMARY KEY,
        code VARCHAR(50) NOT NULL CONSTRAINT uq_horses_code UNIQUE,
        name NVARCHAR(150) NOT NULL,
        microchip_passport_number VARCHAR(100) NULL CONSTRAINT uq_horses_microchip UNIQUE,
        birth_date DATE NOT NULL,
        gender VARCHAR(20) NOT NULL,
        color_markings NVARCHAR(500) NOT NULL,
        breed NVARCHAR(100) NOT NULL,
        sire_id UNIQUEIDENTIFIER NULL,
        dam_id UNIQUEIDENTIFIER NULL,
        sire_manual_name NVARCHAR(150) NULL,
        sire_manual_id VARCHAR(50) NULL,
        dam_manual_name NVARCHAR(150) NULL,
        dam_manual_id VARCHAR(50) NULL,
        current_trainer_id UNIQUEIDENTIFIER NULL,
        caretaker_user_id UNIQUEIDENTIFIER NULL,
        diagnosing_vet_user_id UNIQUEIDENTIFIER NULL,
        stable_location NVARCHAR(150) NOT NULL,
        status VARCHAR(20) NOT NULL CONSTRAINT df_horses_status DEFAULT 'ACTIVE',
        health_status VARCHAR(20) NULL,
        health_changed_by_user_id UNIQUEIDENTIFIER NULL,
        health_source_type VARCHAR(30) NULL,
        health_source_id UNIQUEIDENTIFIER NULL,
        health_changed_at DATETIMEOFFSET NULL,
        archived_at DATETIMEOFFSET NULL,
        payment_status VARCHAR(20) NOT NULL CONSTRAINT df_horses_payment_status DEFAULT 'UNPAID',
        service_override BIT NOT NULL CONSTRAINT df_horses_service_override DEFAULT 0,
        override_reason NVARCHAR(300) NULL,
        paid_until DATE NULL,
        active_service_plan VARCHAR(10) NULL,
        next_service_plan VARCHAR(10) NULL,
        service_plan_started_on DATE NULL,
        service_next_due_date DATE NULL,
        weight_kg DECIMAL(7,2) NULL,
        weight_record_id UNIQUEIDENTIFIER NULL,
        weight_measured_at DATETIMEOFFSET NULL,
        weight_measured_by_user_id UNIQUEIDENTIFIER NULL,
        weight_notes NVARCHAR(1000) NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horses_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horses_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_horses_version DEFAULT 0,
        CONSTRAINT fk_horses_sire FOREIGN KEY (sire_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_horses_dam FOREIGN KEY (dam_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_horses_trainer FOREIGN KEY (current_trainer_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_horses_caretaker FOREIGN KEY (caretaker_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_horses_diagnosing_vet FOREIGN KEY (diagnosing_vet_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_horses_health_user FOREIGN KEY (health_changed_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_horses_weight_measured_by FOREIGN KEY (weight_measured_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_horses_distinct_parents CHECK (sire_id IS NULL OR dam_id IS NULL OR sire_id <> dam_id),
        CONSTRAINT ck_horses_gender CHECK (gender IN ('STALLION', 'MARE', 'GELDING')),
        CONSTRAINT ck_horses_status CHECK (status IN ('PENDING_PAYMENT', 'ACTIVE', 'RESTING', 'INJURED', 'RETIRED', 'DECEASED')),
        CONSTRAINT ck_horses_health_status CHECK (health_status IS NULL OR health_status IN ('FIT', 'MONITOR', 'INJURED', 'QUARANTINED')),
        CONSTRAINT ck_horses_active_service_plan CHECK (active_service_plan IS NULL OR active_service_plan IN ('P30','P90','P365')),
        CONSTRAINT ck_horses_next_service_plan CHECK (next_service_plan IS NULL OR next_service_plan IN ('P30','P90','P365')),
        CONSTRAINT ck_horses_weight_kg CHECK (weight_kg IS NULL OR weight_kg > 0)
    );
    CREATE NONCLUSTERED INDEX idx_horses_name ON dbo.horses(name);
    CREATE NONCLUSTERED INDEX idx_horses_trainer ON dbo.horses(current_trainer_id);
    CREATE NONCLUSTERED INDEX idx_horses_caretaker ON dbo.horses(caretaker_user_id);
    CREATE NONCLUSTERED INDEX idx_horses_status ON dbo.horses(status, health_status);
END;
GO

-- 2. Horse Ownerships
IF OBJECT_ID(N'dbo.horse_ownerships', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_ownerships (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_ownerships PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        owner_id UNIQUEIDENTIFIER NOT NULL,
        ownership_percentage DECIMAL(5,2) NOT NULL,
        is_primary_contact BIT NOT NULL CONSTRAINT df_horse_ownerships_is_primary DEFAULT 0,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_ownerships_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_ownerships_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_horse_ownerships_version DEFAULT 0,
        CONSTRAINT fk_horse_ownerships_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id) ON DELETE CASCADE,
        CONSTRAINT fk_horse_ownerships_owner FOREIGN KEY (owner_id) REFERENCES dbo.users(id),
        CONSTRAINT uq_horse_ownerships_horse_owner UNIQUE (horse_id, owner_id),
        CONSTRAINT ck_horse_ownerships_pct CHECK (ownership_percentage > 0 AND ownership_percentage <= 100)
    );
    CREATE NONCLUSTERED INDEX idx_horse_ownerships_owner ON dbo.horse_ownerships(owner_id);
    CREATE UNIQUE NONCLUSTERED INDEX ux_horse_ownerships_one_owner ON dbo.horse_ownerships(horse_id);
END;
GO

-- 3. Stored Files (Cloudinary storage for general attachments)
IF OBJECT_ID(N'dbo.stored_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.stored_files (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_stored_files PRIMARY KEY,
        uploaded_by_user_id UNIQUEIDENTIFIER NOT NULL,
        cloudinary_asset_id VARCHAR(100) NOT NULL CONSTRAINT uq_stored_files_cloudinary_asset UNIQUE,
        cloudinary_public_id VARCHAR(300) NOT NULL,
        resource_type VARCHAR(20) NOT NULL,
        file_format VARCHAR(30) NULL,
        original_filename NVARCHAR(255) NOT NULL,
        content_type VARCHAR(150) NOT NULL,
        file_size BIGINT NOT NULL,
        secure_url VARCHAR(1000) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_stored_files_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_stored_files_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_stored_files_size CHECK (file_size > 0),
        CONSTRAINT ck_stored_files_resource CHECK (resource_type IN ('image', 'raw', 'video'))
    );
END;
GO

-- 4. Horse Profile Files (Cloudinary documents, passports, photos with approval workflow)
IF OBJECT_ID(N'dbo.horse_profile_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_profile_files (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_profile_files PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        uploaded_by_id UNIQUEIDENTIFIER NOT NULL,
        approved_by_id UNIQUEIDENTIFIER NULL,
        cloudinary_asset_id VARCHAR(100) NOT NULL CONSTRAINT uq_horse_profile_files_cloudinary_asset UNIQUE,
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
        CONSTRAINT fk_horse_profile_files_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id) ON DELETE CASCADE,
        CONSTRAINT fk_horse_profile_files_uploader FOREIGN KEY (uploaded_by_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_horse_profile_files_approver FOREIGN KEY (approved_by_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_horse_profile_files_size CHECK (file_size > 0),
        CONSTRAINT ck_horse_profile_files_resource_type CHECK (resource_type IN ('image', 'raw')),
        CONSTRAINT ck_horse_profile_files_status CHECK (status IN ('ACTIVE', 'PENDING', 'REPLACED', 'DELETED'))
    );
    CREATE NONCLUSTERED INDEX idx_horse_profile_files_horse ON dbo.horse_profile_files(horse_id);
    CREATE UNIQUE NONCLUSTERED INDEX uq_horse_profile_files_one_active
        ON dbo.horse_profile_files(horse_id)
        WHERE status = 'ACTIVE';
    CREATE NONCLUSTERED INDEX idx_horse_profile_files_pending
        ON dbo.horse_profile_files(horse_id, created_at)
        WHERE status = 'PENDING';
END;
GO
