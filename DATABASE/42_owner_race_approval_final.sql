/* Owner approval is the final race registration decision. Safe to rerun. */
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
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id
        WHERE i.module_code='RACING' AND i.record_type='RACE_REGISTRATION'
          AND i.status='CONFIRMED'
          AND d.status NOT IN ('SUBMITTED','APPROVE','CONFIRMED')
    )
        THROW 53001, N'Chỉ đăng ký do chủ ngựa duyệt mới được xác nhận.', 1;

    IF EXISTS (
        SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id
        WHERE i.module_code='RACING' AND i.record_type='RACE_REGISTRATION'
          AND i.status='WITHDRAWN' AND d.status='CONFIRMED'
    )
        THROW 53002, N'Không thể rút đăng ký đã được chủ ngựa duyệt.', 1;

    IF EXISTS (
        SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id
        JOIN dbo.race_events e ON cast(e.id as varchar(36))=JSON_VALUE(i.data_json,'$.raceEventId')
        WHERE i.module_code='RACING' AND i.record_type='RACE_REGISTRATION'
          AND i.status IN ('WITHDRAWN','CONFIRMED') AND d.status<>i.status
          AND e.completed_at IS NOT NULL AND d.status<>'APPROVE'
    )
        THROW 53003, N'Không thể thay đổi đăng ký sau khi giải đua đã hoàn thành.', 1;
END;
GO
UPDATE dbo.module_records
SET status='CONFIRMED',
    data_json=JSON_MODIFY(data_json,'$.confirmedAt',
                          COALESCE(JSON_VALUE(data_json,'$.confirmedAt'),JSON_VALUE(data_json,'$.decidedAt'))),
    updated_at=SYSDATETIMEOFFSET()
WHERE module_code='RACING' AND record_type='RACE_REGISTRATION' AND status='APPROVE';
GO
/* The owner decision endpoint requires PERM_RACING_APPROVE in its JWT. */
UPDATE dbo.role_permissions
SET granted=1, changed_at=SYSDATETIMEOFFSET()
WHERE role_code='HORSE_OWNER' AND module_code='RACING' AND action_code='APPROVE'
  AND granted=0;
GO
