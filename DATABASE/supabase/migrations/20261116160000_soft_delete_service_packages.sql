ALTER TABLE dbo.service_packages
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;
