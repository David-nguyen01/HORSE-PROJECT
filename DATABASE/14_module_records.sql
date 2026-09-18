USE [racehorse];
GO

IF OBJECT_ID(N'dbo.module_records', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.module_records (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_module_records PRIMARY KEY,
        module_code VARCHAR(30) NOT NULL,
        record_type VARCHAR(50) NOT NULL,
        status VARCHAR(20) NOT NULL,
        data_json NVARCHAR(MAX) NOT NULL,
        horse_id UNIQUEIDENTIFIER NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_module_records_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_module_records_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_module_records_version DEFAULT 0
    );

    CREATE INDEX idx_module_records_code_type ON dbo.module_records(module_code, record_type);
    CREATE INDEX idx_module_records_horse ON dbo.module_records(horse_id);
END;
GO
