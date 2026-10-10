/* Race registration status lifecycle integrity. Idempotent SQL Server migration. */
USE [racehorse_local];
GO
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

CREATE OR ALTER TRIGGER dbo.tr_module_records_race_registration_status_guard
ON dbo.module_records
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (
        SELECT 1 FROM inserted
        WHERE module_code = 'RACING'
          AND record_type = 'RACE_REGISTRATION'
    )
        RETURN;

    -- 1. Chặn xác nhận (CONFIRMED) nếu trạng thái trước đó không phải APPROVE
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN deleted d ON d.id = i.id
        WHERE i.module_code = 'RACING'
          AND i.record_type = 'RACE_REGISTRATION'
          AND i.status = 'CONFIRMED'
          AND d.status <> 'APPROVE'
    )
    BEGIN
        THROW 53001, N'Chỉ đăng ký đã được chủ ngựa duyệt (APPROVE) mới có thể xác nhận.', 1;
    END;

    -- 2. Chặn rút đơn (WITHDRAWN) nếu trạng thái trước đó là CONFIRMED
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN deleted d ON d.id = i.id
        WHERE i.module_code = 'RACING'
          AND i.record_type = 'RACE_REGISTRATION'
          AND i.status = 'WITHDRAWN'
          AND d.status = 'CONFIRMED'
    )
    BEGIN
        THROW 53002, N'Không thể rút đăng ký đã được xác nhận.', 1;
    END;

    -- 3. Chặn rút đơn (WITHDRAWN) hoặc xác nhận (CONFIRMED) sau khi giải đua đã hoàn thành
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN deleted d ON d.id = i.id
        JOIN dbo.race_events e ON cast(e.id as varchar(36)) = JSON_VALUE(i.data_json, '$.raceEventId')
        WHERE i.module_code = 'RACING'
          AND i.record_type = 'RACE_REGISTRATION'
          AND (i.status = 'WITHDRAWN' OR i.status = 'CONFIRMED')
          AND d.status <> i.status
          AND e.completed_at IS NOT NULL
    )
    BEGIN
        THROW 53003, N'Không thể thay đổi đăng ký sau khi giải đua đã hoàn thành.', 1;
    END;
END;
GO
