USE [racehorse_local];
GO

-- 1. Create service_packages table if not exists
IF OBJECT_ID(N'dbo.service_packages', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.service_packages (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT df_service_packages_id DEFAULT NEWID(),
        code VARCHAR(30) NOT NULL,
        name NVARCHAR(100) NOT NULL,
        description NVARCHAR(500) NULL,
        price_vnd DECIMAL(19,0) NOT NULL,
        duration_days INT NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_service_packages_is_active DEFAULT 1,
        display_order INT NOT NULL CONSTRAINT df_service_packages_display_order DEFAULT 0,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_service_packages_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_service_packages_updated_at DEFAULT SYSDATETIMEOFFSET(),
        created_by_user_id UNIQUEIDENTIFIER NULL,
        updated_by_user_id UNIQUEIDENTIFIER NULL,
        version BIGINT NOT NULL CONSTRAINT df_service_packages_version DEFAULT 0,

        CONSTRAINT pk_service_packages PRIMARY KEY (id),
        CONSTRAINT uq_service_packages_code UNIQUE (code),
        CONSTRAINT fk_service_packages_created_by FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_service_packages_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_service_packages_price_nonnegative CHECK (price_vnd >= 0),
        CONSTRAINT ck_service_packages_duration_positive CHECK (duration_days > 0),
        CONSTRAINT ck_service_packages_display_order_nonnegative CHECK (display_order >= 0)
    );

    CREATE NONCLUSTERED INDEX idx_service_packages_active_order 
    ON dbo.service_packages (is_active, display_order, created_at);
END;
GO

-- 2. Seed initial canonical packages (P30, P90, P365) if not present
IF NOT EXISTS (SELECT 1 FROM dbo.service_packages WHERE code = 'P30')
    INSERT INTO dbo.service_packages (code, name, description, price_vnd, duration_days, is_active, display_order)
    VALUES ('P30', N'Gói 1 tháng Tiêu chuẩn', N'Chăm sóc dinh dưỡng tiêu chuẩn, theo dõi sức khỏe và huấn luyện cơ bản trong 30 ngày.', 5000000, 30, 1, 1);

IF NOT EXISTS (SELECT 1 FROM dbo.service_packages WHERE code = 'P90')
    INSERT INTO dbo.service_packages (code, name, description, price_vnd, duration_days, is_active, display_order)
    VALUES ('P90', N'Gói 3 tháng Nâng cao', N'Phác đồ huấn luyện chuyên sâu, chế độ dinh dưỡng tối ưu và kiểm tra thú y định kỳ trong 90 ngày.', 15000000, 90, 1, 2);

IF NOT EXISTS (SELECT 1 FROM dbo.service_packages WHERE code = 'P365')
    INSERT INTO dbo.service_packages (code, name, description, price_vnd, duration_days, is_active, display_order)
    VALUES ('P365', N'Gói 12 tháng Toàn diện', N'Chăm sóc chuồng trại trọn gói, chuẩn bị thi đấu giải đua chuyên nghiệp và chăm sóc y tế toàn diện cả năm.', 60000000, 365, 1, 3);
GO

-- 3. Relax legacy hardcoded plan-code check constraints
IF OBJECT_ID(N'dbo.ck_horses_active_service_plan', N'C') IS NOT NULL
    ALTER TABLE dbo.horses DROP CONSTRAINT ck_horses_active_service_plan;
GO

IF OBJECT_ID(N'dbo.ck_horses_next_service_plan', N'C') IS NOT NULL
    ALTER TABLE dbo.horses DROP CONSTRAINT ck_horses_next_service_plan;
GO

IF OBJECT_ID(N'dbo.ck_users_active_service_plan', N'C') IS NOT NULL
    ALTER TABLE dbo.users DROP CONSTRAINT ck_users_active_service_plan;
GO

-- 4. Expand column size to VARCHAR(30) to accommodate flexible package codes
ALTER TABLE dbo.horses ALTER COLUMN active_service_plan VARCHAR(30) NULL;
ALTER TABLE dbo.horses ALTER COLUMN next_service_plan VARCHAR(30) NULL;
ALTER TABLE dbo.users ALTER COLUMN active_service_plan VARCHAR(30) NULL;
GO

-- 5. Add foreign key constraints referencing service_packages(code)
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_horses_active_service_plan')
    ALTER TABLE dbo.horses WITH CHECK ADD
        CONSTRAINT fk_horses_active_service_plan FOREIGN KEY (active_service_plan) REFERENCES dbo.service_packages(code);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_horses_next_service_plan')
    ALTER TABLE dbo.horses WITH CHECK ADD
        CONSTRAINT fk_horses_next_service_plan FOREIGN KEY (next_service_plan) REFERENCES dbo.service_packages(code);
GO
