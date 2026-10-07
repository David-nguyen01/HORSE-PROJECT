-- =========================================================================
-- 01_core_auth.sql
-- Core Authentication, User Accounts, Sessions & RBAC Matrix
-- Target Database: racehorse_local
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
GO

USE [racehorse_local];
GO

-- 1. Users
IF OBJECT_ID(N'dbo.users', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.users (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_users PRIMARY KEY,
        username NVARCHAR(80) NOT NULL CONSTRAINT uq_users_username UNIQUE,
        email NVARCHAR(254) NOT NULL CONSTRAINT uq_users_email UNIQUE,
        password_hash VARCHAR(100) NOT NULL,
        enabled BIT NOT NULL CONSTRAINT df_users_enabled DEFAULT 1,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_users_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_users_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_users_version DEFAULT 0
    );
END;
GO

-- 2. User Roles
IF OBJECT_ID(N'dbo.user_roles', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.user_roles (
        user_id UNIQUEIDENTIFIER NOT NULL,
        role VARCHAR(40) NOT NULL,
        CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
        CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE,
        CONSTRAINT ck_user_roles_role CHECK (role IN (
            'CLUB_MANAGER', 'HEAD_TRAINER', 'STABLE_STAFF', 'HORSE_OWNER', 'VETERINARIAN'))
    );
    CREATE NONCLUSTERED INDEX idx_user_roles_role ON dbo.user_roles(role);
END;
GO

-- 3. Login Sessions
IF OBJECT_ID(N'dbo.login_sessions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.login_sessions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_login_sessions PRIMARY KEY,
        user_id UNIQUEIDENTIFIER NOT NULL,
        refresh_token_hash VARCHAR(64) NOT NULL,
        ip_address VARCHAR(45) NULL,
        user_agent NVARCHAR(500) NULL,
        last_activity_at DATETIMEOFFSET NOT NULL CONSTRAINT df_login_sessions_last_activity DEFAULT SYSDATETIMEOFFSET(),
        expires_at DATETIMEOFFSET NOT NULL,
        revoked_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_login_sessions_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_login_sessions_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_login_sessions_version DEFAULT 0,
        CONSTRAINT fk_login_sessions_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE
    );
    CREATE NONCLUSTERED INDEX idx_login_sessions_lookup ON dbo.login_sessions(user_id, expires_at, revoked_at);
END;
GO

-- 4. Dynamic Role Permissions (RBAC)
IF OBJECT_ID(N'dbo.role_permissions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.role_permissions (
        role_code VARCHAR(40) NOT NULL,
        module_code VARCHAR(30) NOT NULL,
        action_code VARCHAR(20) NOT NULL,
        granted BIT NOT NULL,
        changed_by_user_id UNIQUEIDENTIFIER NOT NULL,
        changed_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT pk_role_permissions PRIMARY KEY (role_code, module_code, action_code),
        CONSTRAINT ck_role_permissions_role CHECK (role_code IN
            ('CLUB_MANAGER', 'HEAD_TRAINER', 'VETERINARIAN', 'STABLE_STAFF', 'HORSE_OWNER')),
        CONSTRAINT ck_role_permissions_module CHECK (module_code IN
            ('PROFILE', 'TRAINING', 'MEDICAL', 'STABLE_CARE', 'RACING', 'REPORTING')),
        CONSTRAINT ck_role_permissions_action CHECK (action_code IN ('VIEW', 'EDIT', 'APPROVE', 'CLAIM')),
        CONSTRAINT fk_role_permissions_user FOREIGN KEY (changed_by_user_id) REFERENCES dbo.users(id)
    );
END;
GO
