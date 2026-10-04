-- =========================================================================
-- 15_training_operational_window.sql
-- Branch: bacsithuy | Idempotent Migration
-- Chạy trên Azure SQL (Portal Query Editor hoặc Azure Data Studio)
-- =========================================================================

-- -------------------------------------------------------------------------
-- 1. RACETRACKS: cập nhật giờ vận hành thành 05:00–11:00
-- -------------------------------------------------------------------------
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'ck_racetracks_operating_hours')
    ALTER TABLE dbo.racetracks DROP CONSTRAINT ck_racetracks_operating_hours;
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'ck_racetracks_slot_minutes')
    ALTER TABLE dbo.racetracks DROP CONSTRAINT ck_racetracks_slot_minutes;

ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_opens_at;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_closes_at;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_closes_next_day;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_slot_minutes;

ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_opens_at      DEFAULT '05:00:00' FOR opens_at;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_at     DEFAULT '11:00:00' FOR closes_at;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_next_day DEFAULT 0        FOR closes_next_day;

UPDATE dbo.racetracks SET opens_at = '05:00:00', closes_at = '11:00:00', closes_next_day = 0;

ALTER TABLE dbo.racetracks ADD CONSTRAINT ck_racetracks_operating_hours
    CHECK (opens_at = '05:00:00' AND closes_at = '11:00:00' AND closes_next_day = 0);

-- -------------------------------------------------------------------------
-- 2. TRAINING_SCHEDULES: thêm các cột mới (idempotent)
-- -------------------------------------------------------------------------

-- assigned_staff_id thành NULLABLE (staff chưa được gán khi mới tạo lịch)
ALTER TABLE dbo.training_schedules ALTER COLUMN assigned_staff_id UNIQUEIDENTIFIER NULL;

-- Cột racetrack_id
IF COL_LENGTH(N'dbo.training_schedules', N'racetrack_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD racetrack_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_racetrack
        FOREIGN KEY (racetrack_id) REFERENCES dbo.racetracks(id);
END;

-- Cột veterinarian_user_id
IF COL_LENGTH(N'dbo.training_schedules', N'veterinarian_user_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD veterinarian_user_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_vet
        FOREIGN KEY (veterinarian_user_id) REFERENCES dbo.users(id);
END;

-- Cột audit claim
IF COL_LENGTH(N'dbo.training_schedules', N'staff_assigned_by') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD staff_assigned_by  VARCHAR(20) NULL;  -- 'USER' | 'SYSTEM'
    ALTER TABLE dbo.training_schedules ADD staff_assigned_at  DATETIMEOFFSET NULL;
    ALTER TABLE dbo.training_schedules ADD vet_assigned_by    VARCHAR(20) NULL;  -- 'USER' | 'SYSTEM'
    ALTER TABLE dbo.training_schedules ADD vet_assigned_at    DATETIMEOFFSET NULL;
END;

-- -------------------------------------------------------------------------
-- 3. TRIGGERS chống trùng lịch
-- -------------------------------------------------------------------------
GO

-- Trigger 1: nhân viên chuồng không trùng ca
CREATE OR ALTER TRIGGER dbo.tr_training_schedules_no_staff_overlap
ON dbo.training_schedules AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM dbo.training_schedules s
        JOIN inserted i ON s.assigned_staff_id = i.assigned_staff_id
                       AND s.id <> i.id
                       AND s.cancelled_at IS NULL
                       AND i.cancelled_at IS NULL
                       AND i.assigned_staff_id IS NOT NULL
        WHERE s.scheduled_start < i.scheduled_end
          AND s.scheduled_end   > i.scheduled_start
    )
        THROW 50061, N'Nhân viên chuồng đã nhận lịch huấn luyện khác trong khung giờ này.', 1;
END;
GO

-- Trigger 2: bác sĩ thú y không trùng ca
CREATE OR ALTER TRIGGER dbo.tr_training_schedules_no_vet_overlap
ON dbo.training_schedules AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM dbo.training_schedules s
        JOIN inserted i ON s.veterinarian_user_id = i.veterinarian_user_id
                       AND s.id <> i.id
                       AND s.cancelled_at IS NULL
                       AND i.cancelled_at IS NULL
                       AND i.veterinarian_user_id IS NOT NULL
        WHERE s.scheduled_start < i.scheduled_end
          AND s.scheduled_end   > i.scheduled_start
    )
        THROW 50062, N'Bác sĩ thú y đã nhận lịch theo dõi buổi tập khác trong khung giờ này.', 1;
END;
GO

-- Trigger 3: 1 sân chỉ phục vụ 1 ngựa/ngày
CREATE OR ALTER TRIGGER dbo.tr_training_schedules_ground_exclusivity
ON dbo.training_schedules AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM dbo.training_schedules s
        JOIN inserted i ON s.racetrack_id = i.racetrack_id
                       AND s.id <> i.id
                       AND s.cancelled_at IS NULL
                       AND i.cancelled_at IS NULL
                       AND i.racetrack_id IS NOT NULL
        WHERE s.scheduled_start < i.scheduled_end
          AND s.scheduled_end   > i.scheduled_start
    )
        THROW 50063, N'Sân tập đã được đăng ký cho ngựa khác trong ngày này (1 sân / 1 ngựa / ngày).', 1;
END;
GO

-- -------------------------------------------------------------------------
-- 4. QUYỀN: cấp PERM_TRAINING_CLAIM cho STABLE_STAFF và VETERINARIAN
-- -------------------------------------------------------------------------
ALTER TABLE dbo.role_permissions DROP CONSTRAINT IF EXISTS ck_role_permissions_action;
ALTER TABLE dbo.role_permissions ADD CONSTRAINT ck_role_permissions_action
    CHECK (action_code IN ('VIEW', 'EDIT', 'APPROVE', 'CLAIM'));

IF NOT EXISTS (SELECT 1 FROM dbo.role_permissions
               WHERE role_code='STABLE_STAFF' AND module_code='TRAINING' AND action_code='CLAIM')
    INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at)
    SELECT 'STABLE_STAFF', 'TRAINING', 'CLAIM', 1, TOP_USER.id, SYSDATETIMEOFFSET()
    FROM (SELECT TOP 1 id FROM dbo.users ORDER BY created_at) TOP_USER;

IF NOT EXISTS (SELECT 1 FROM dbo.role_permissions
               WHERE role_code='VETERINARIAN' AND module_code='TRAINING' AND action_code='CLAIM')
    INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at)
    SELECT 'VETERINARIAN', 'TRAINING', 'CLAIM', 1, TOP_USER.id, SYSDATETIMEOFFSET()
    FROM (SELECT TOP 1 id FROM dbo.users ORDER BY created_at) TOP_USER;
GO
