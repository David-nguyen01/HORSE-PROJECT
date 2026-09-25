USE [racehorse];
GO

IF OBJECT_ID(N'dbo.horses', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horses (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horses PRIMARY KEY,
        code VARCHAR(50) NOT NULL,
        name NVARCHAR(150) NOT NULL,
        microchip_passport_number VARCHAR(100) NULL,
        birth_date DATE NOT NULL,
        gender VARCHAR(20) NOT NULL,
        color_markings NVARCHAR(500) NOT NULL,
        breed NVARCHAR(100) NOT NULL,
        sire_id UNIQUEIDENTIFIER NULL,
        dam_id UNIQUEIDENTIFIER NULL,
        stable_location NVARCHAR(150) NOT NULL,
        status VARCHAR(20) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horses_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horses_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_horses_version DEFAULT 0,
        CONSTRAINT uq_horses_code UNIQUE (code),
        CONSTRAINT fk_horses_sire FOREIGN KEY (sire_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_horses_dam FOREIGN KEY (dam_id) REFERENCES dbo.horses(id),
        CONSTRAINT ck_horses_distinct_parents CHECK (sire_id IS NULL OR dam_id IS NULL OR sire_id <> dam_id)
    );

    CREATE UNIQUE INDEX uq_horses_microchip_passport
        ON dbo.horses (microchip_passport_number)
        WHERE microchip_passport_number IS NOT NULL;
    CREATE INDEX idx_horses_name ON dbo.horses (name);
    CREATE INDEX idx_horses_status ON dbo.horses (status);
    CREATE INDEX idx_horses_sire_id ON dbo.horses (sire_id);
    CREATE INDEX idx_horses_dam_id ON dbo.horses (dam_id);
END;
GO

IF OBJECT_ID(N'dbo.users', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.users (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_users PRIMARY KEY,
        username VARCHAR(80) NOT NULL,
        email VARCHAR(254) NOT NULL,
        password_hash VARCHAR(100) NOT NULL,
        enabled BIT NOT NULL CONSTRAINT df_users_enabled DEFAULT 1,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_users_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_users_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_users_version DEFAULT 0,
        CONSTRAINT uq_users_username UNIQUE (username),
        CONSTRAINT uq_users_email UNIQUE (email)
    );
END;
GO

IF OBJECT_ID(N'dbo.user_roles', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.user_roles (
        user_id UNIQUEIDENTIFIER NOT NULL,
        role VARCHAR(40) NOT NULL,
        CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
        CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE,
        CONSTRAINT ck_user_roles_role CHECK (role IN (
            'CLUB_MANAGER',
            'HEAD_TRAINER',
            'STABLE_STAFF',
            'HORSE_OWNER',
            'VETERINARIAN'
        ))
    );
END;
GO

IF OBJECT_ID(N'dbo.login_sessions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.login_sessions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_login_sessions PRIMARY KEY,
        user_id UNIQUEIDENTIFIER NOT NULL,
        refresh_token_hash VARCHAR(64) NOT NULL,
        ip_address VARCHAR(45) NULL,
        user_agent VARCHAR(500) NULL,
        last_activity_at DATETIMEOFFSET NOT NULL,
        expires_at DATETIMEOFFSET NOT NULL,
        revoked_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_login_sessions_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_login_sessions_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_login_sessions_version DEFAULT 0,
        CONSTRAINT fk_login_sessions_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE,
        CONSTRAINT uq_login_sessions_refresh_token_hash UNIQUE (refresh_token_hash)
    );

    CREATE INDEX idx_login_sessions_user_id ON dbo.login_sessions (user_id);
    CREATE INDEX idx_login_sessions_expires_at ON dbo.login_sessions (expires_at);
END;
GO
