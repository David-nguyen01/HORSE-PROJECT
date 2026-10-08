/* Keep the most recent veterinarian-entered weight on the horse. */
USE [racehorse];
GO
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.horses', N'U') IS NULL
        THROW 51081, N'Apply the horse schema before migration 30.', 1;

    IF COL_LENGTH(N'dbo.horses', N'weight_kg') IS NULL
        ALTER TABLE dbo.horses ADD weight_kg DECIMAL(7,2) NULL;
    IF COL_LENGTH(N'dbo.horses', N'weight_record_id') IS NULL
        ALTER TABLE dbo.horses ADD weight_record_id UNIQUEIDENTIFIER NULL;
    IF COL_LENGTH(N'dbo.horses', N'weight_measured_at') IS NULL
        ALTER TABLE dbo.horses ADD weight_measured_at DATETIMEOFFSET NULL;
    IF COL_LENGTH(N'dbo.horses', N'weight_measured_by_user_id') IS NULL
        ALTER TABLE dbo.horses ADD weight_measured_by_user_id UNIQUEIDENTIFIER NULL;
    IF COL_LENGTH(N'dbo.horses', N'weight_notes') IS NULL
        ALTER TABLE dbo.horses ADD weight_notes NVARCHAR(1000) NULL;

    IF OBJECT_ID(N'dbo.horse_weight_records', N'U') IS NOT NULL
    BEGIN
        -- Compile after ALTER TABLE has created the new columns.
        EXEC(N';WITH latest AS (
            SELECT id, horse_id, weight_kg, measured_at, measured_by_user_id, notes,
                   ROW_NUMBER() OVER (
                       PARTITION BY horse_id ORDER BY measured_at DESC, created_at DESC, id DESC
                   ) AS rn
            FROM dbo.horse_weight_records
        )
        UPDATE h SET weight_kg=w.weight_kg, weight_record_id=w.id,
                     weight_measured_at=w.measured_at,
                     weight_measured_by_user_id=w.measured_by_user_id,
                     weight_notes=w.notes
        FROM dbo.horses h
        JOIN latest w ON w.horse_id=h.id AND w.rn=1
        WHERE h.weight_measured_at IS NULL OR h.weight_measured_at<w.measured_at;');

        DROP TABLE dbo.horse_weight_records;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id=OBJECT_ID(N'dbo.horses') AND name=N'ck_horses_weight_kg'
    )
        EXEC(N'ALTER TABLE dbo.horses WITH CHECK ADD CONSTRAINT ck_horses_weight_kg
            CHECK (weight_kg IS NULL OR weight_kg>0);');

    IF NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys
        WHERE parent_object_id=OBJECT_ID(N'dbo.horses') AND name=N'fk_horses_weight_measured_by'
    )
        EXEC(N'ALTER TABLE dbo.horses WITH CHECK ADD CONSTRAINT fk_horses_weight_measured_by
            FOREIGN KEY(weight_measured_by_user_id) REFERENCES dbo.users(id);');

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
