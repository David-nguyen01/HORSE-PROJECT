/* Concurrency lock & database constraint against duplicate active stall assignments. Idempotent SQL Server migration. */
USE [racehorse_local];
GO
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

CREATE OR ALTER TRIGGER dbo.tr_module_records_stall_assignment_unique_active
ON dbo.module_records
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (
        SELECT 1 FROM inserted
        WHERE module_code = 'STABLE_CARE'
          AND record_type = 'STALL_ASSIGNMENT'
          AND status = 'ACTIVE'
    )
        RETURN;

    -- 1. Chặn 2 phân bổ ACTIVE cho cùng một ô chuồng (stallId)
    IF EXISTS (
        SELECT 1
        FROM dbo.module_records m
        WHERE m.module_code = 'STABLE_CARE'
          AND m.record_type = 'STALL_ASSIGNMENT'
          AND m.status = 'ACTIVE'
        GROUP BY JSON_VALUE(m.data_json, '$.stallId')
        HAVING COUNT(*) > 1
    )
    BEGIN
        THROW 52001, N'Ô chuồng đã có phân bổ hoạt động, không thể phân bổ trùng.', 1;
    END;

    -- 2. Chặn 1 ngựa có 2 phân bổ ACTIVE đồng thời
    IF EXISTS (
        SELECT 1
        FROM dbo.module_records m
        WHERE m.module_code = 'STABLE_CARE'
          AND m.record_type = 'STALL_ASSIGNMENT'
          AND m.status = 'ACTIVE'
          AND m.horse_id IS NOT NULL
        GROUP BY m.horse_id
        HAVING COUNT(*) > 1
    )
    BEGIN
        THROW 52002, N'Ngựa đã có phân bổ ô chuồng đang hoạt động.', 1;
    END;

    -- 3. Chặn khu chuồng vượt quá sức chứa tối đa 4 con ngựa
    IF EXISTS (
        SELECT 1
        FROM dbo.module_records m
        WHERE m.module_code = 'STABLE_CARE'
          AND m.record_type = 'STALL_ASSIGNMENT'
          AND m.status = 'ACTIVE'
        GROUP BY JSON_VALUE(m.data_json, '$.stableId')
        HAVING COUNT(DISTINCT m.horse_id) > 4
    )
    BEGIN
        THROW 52003, N'Khu chuồng này đã đạt sức chứa tối đa 4 con ngựa.', 1;
    END;
END;
GO
