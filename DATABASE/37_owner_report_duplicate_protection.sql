/* =========================================================================
   37_owner_report_duplicate_protection.sql
   Target Database: racehorse_local
   Purpose:
     Enforces database-level duplicate content prevention for Owner Financial
     Reports (SRS FR19 / BUG-03).
     A report is a duplicate only when:
       (owner_user_id, period_start, period_end, content_hash)
     matches an existing report.
     Reports for the same owner and period with different business content
     are allowed and preserved.
     Concurrent requests with equivalent business content cannot both commit.
   ========================================================================= */

USE [racehorse_local];
GO
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

-- 1. Thêm cột content_hash vào dbo.owner_reports nếu chưa tồn tại
IF COL_LENGTH(N'dbo.owner_reports', N'content_hash') IS NULL
BEGIN
    ALTER TABLE dbo.owner_reports ADD content_hash VARCHAR(64) NULL;
END;
GO

-- 2. Thêm DEFAULT constraint cho content_hash (fallback SHA-256 của chuỗi rỗng)
IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.owner_reports')
      AND name = N'df_owner_reports_content_hash'
)
BEGIN
    ALTER TABLE dbo.owner_reports
    ADD CONSTRAINT df_owner_reports_content_hash
    DEFAULT 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855' FOR content_hash;
END;
GO

-- 3. Cập nhật content_hash cho các bản ghi cũ nếu có (bản ghi có content_hash IS NULL)
IF EXISTS (SELECT 1 FROM dbo.owner_reports WHERE content_hash IS NULL)
BEGIN
    UPDATE dbo.owner_reports
    SET content_hash = LOWER(CONVERT(VARCHAR(64), HASHBYTES('SHA2_256',
        CONCAT('horses:', ISNULL(horse_ids_json, '[]'), ';transactions:', ISNULL(transaction_ids_json, '[]'))
    ), 2))
    WHERE content_hash IS NULL;
END;
GO

-- 4. Đảm bảo cột content_hash không chứa NULL
IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.owner_reports')
      AND name = N'content_hash'
      AND is_nullable = 1
)
BEGIN
    ALTER TABLE dbo.owner_reports ALTER COLUMN content_hash VARCHAR(64) NOT NULL;
END;
GO

-- 5. Kiểm tra dữ liệu trùng lặp lịch sử trước khi tạo Unique Index
IF EXISTS (
    SELECT owner_user_id, period_start, period_end, content_hash
    FROM dbo.owner_reports
    GROUP BY owner_user_id, period_start, period_end, content_hash
    HAVING COUNT(*) > 1
)
BEGIN
    PRINT N'[WARNING] Phát hiện nhóm bản ghi trùng lặp nội dung lịch sử trong dbo.owner_reports!';
    PRINT N'[WARNING] Unique index ux_owner_reports_period_content chưa thể tạo cho đến khi dữ liệu lịch sử được giải quyết.';
    SELECT owner_user_id, period_start, period_end, content_hash, COUNT(*) AS duplicate_count
    FROM dbo.owner_reports
    GROUP BY owner_user_id, period_start, period_end, content_hash
    HAVING COUNT(*) > 1;
END
ELSE
BEGIN
    -- 6. Tạo Unique Nonclustered Index trên (owner_user_id, period_start, period_end, content_hash)
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.owner_reports')
          AND name = N'ux_owner_reports_period_content'
    )
    BEGIN
        CREATE UNIQUE NONCLUSTERED INDEX ux_owner_reports_period_content
            ON dbo.owner_reports(owner_user_id, period_start, period_end, content_hash);
        PRINT N'[SUCCESS] Đã tạo Unique Index ux_owner_reports_period_content thành công.';
    END;
END;
GO
