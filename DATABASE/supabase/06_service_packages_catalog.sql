-- Supabase PostgreSQL Migration: Service Packages Catalog
SET search_path TO dbo, public;

-- 1. Create service_packages table
CREATE TABLE IF NOT EXISTS dbo.service_packages (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    price_vnd NUMERIC(19,0) NOT NULL,
    duration_days INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id UUID,
    updated_by_user_id UUID,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_service_packages PRIMARY KEY (id),
    CONSTRAINT uq_service_packages_code UNIQUE (code),
    CONSTRAINT fk_service_packages_created_by FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
    CONSTRAINT fk_service_packages_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES dbo.users(id),
    CONSTRAINT ck_service_packages_price_nonnegative CHECK (price_vnd >= 0),
    CONSTRAINT ck_service_packages_duration_positive CHECK (duration_days > 0),
    CONSTRAINT ck_service_packages_display_order_nonnegative CHECK (display_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_service_packages_active_order 
ON dbo.service_packages (is_active, display_order, created_at);

-- 2. Seed initial canonical packages (P30, P90, P365)
INSERT INTO dbo.service_packages (code, name, description, price_vnd, duration_days, is_active, display_order)
VALUES 
    ('P30', 'Gói 1 tháng Tiêu chuẩn', 'Chăm sóc dinh dưỡng tiêu chuẩn, theo dõi sức khỏe và huấn luyện cơ bản trong 30 ngày.', 5000000, 30, TRUE, 1),
    ('P90', 'Gói 3 tháng Nâng cao', 'Phác đồ huấn luyện chuyên sâu, chế độ dinh dưỡng tối ưu và kiểm tra thú y định kỳ trong 90 ngày.', 15000000, 90, TRUE, 2),
    ('P365', 'Gói 12 tháng Toàn diện', 'Chăm sóc chuồng trại trọn gói, chuẩn bị thi đấu giải đua chuyên nghiệp và chăm sóc y tế toàn diện cả năm.', 60000000, 365, TRUE, 3)
ON CONFLICT (code) DO NOTHING;

-- 3. Relax legacy check constraints on horses and users
ALTER TABLE dbo.horses DROP CONSTRAINT IF EXISTS ck_horses_active_service_plan;
ALTER TABLE dbo.horses DROP CONSTRAINT IF EXISTS ck_horses_next_service_plan;
ALTER TABLE dbo.users DROP CONSTRAINT IF EXISTS ck_users_active_service_plan;

-- 4. Expand column size to VARCHAR(30)
ALTER TABLE dbo.horses ALTER COLUMN active_service_plan TYPE VARCHAR(30);
ALTER TABLE dbo.horses ALTER COLUMN next_service_plan TYPE VARCHAR(30);
ALTER TABLE dbo.users ALTER COLUMN active_service_plan TYPE VARCHAR(30);

-- 5. Add foreign keys from horses to service_packages(code)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_horses_active_service_plan') THEN
        ALTER TABLE dbo.horses ADD CONSTRAINT fk_horses_active_service_plan 
        FOREIGN KEY (active_service_plan) REFERENCES dbo.service_packages(code);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_horses_next_service_plan') THEN
        ALTER TABLE dbo.horses ADD CONSTRAINT fk_horses_next_plan_catalog 
        FOREIGN KEY (next_service_plan) REFERENCES dbo.service_packages(code);
    END IF;
END $$;
