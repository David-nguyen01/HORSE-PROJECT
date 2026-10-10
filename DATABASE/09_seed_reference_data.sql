-- =========================================================================
-- 09_seed_reference_data.sql
-- Seed Core Reference Data: 5 Role Accounts, RBAC, Grounds, Stables, Routines & Baseline Catalog
-- Target Database: racehorse_local
-- Note: All 5 test accounts use password "Admin123"
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
GO

USE [racehorse_local];
GO

-- =========================================================================
-- 1. DETERMINISTIC TEST ACCOUNTS FOR ALL 5 ROLES
-- =========================================================================
DECLARE @adminId   UNIQUEIDENTIFIER = '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11';
DECLARE @trainerId UNIQUEIDENTIFIER = '22222222-2222-2222-2222-222222222222';
DECLARE @staffId   UNIQUEIDENTIFIER = '48772A9D-9DA0-44A8-90F2-0459B3ADB768';
DECLARE @ownerId   UNIQUEIDENTIFIER = '44444444-4444-4444-4444-444444444444';
DECLARE @vetId     UNIQUEIDENTIFIER = '55555555-5555-5555-5555-555555555555';
DECLARE @hash VARCHAR(100)          = '$2a$10$Sf0Drsuesp0Aw0q9W1FFY.8JkfFUgq7U7a991JY80e2zXzEBliH/u'; -- Admin123

-- CLUB_MANAGER: admin@gmail.com
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE email = 'admin@gmail.com')
BEGIN
    INSERT INTO dbo.users (id, username, email, password_hash, enabled)
    VALUES (@adminId, 'admin', 'admin@gmail.com', @hash, 1);
    INSERT INTO dbo.user_roles (user_id, role) VALUES (@adminId, 'CLUB_MANAGER');
END;

-- HEAD_TRAINER: trainer@gmail.com
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE email = 'trainer@gmail.com')
BEGIN
    INSERT INTO dbo.users (id, username, email, password_hash, enabled)
    VALUES (@trainerId, 'head_trainer', 'trainer@gmail.com', @hash, 1);
    INSERT INTO dbo.user_roles (user_id, role) VALUES (@trainerId, 'HEAD_TRAINER');
END;

-- STABLE_STAFF: staff@gmail.com
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE email = 'staff@gmail.com')
BEGIN
    INSERT INTO dbo.users (id, username, email, password_hash, enabled)
    VALUES (@staffId, 'stable_staff', 'staff@gmail.com', @hash, 1);
    INSERT INTO dbo.user_roles (user_id, role) VALUES (@staffId, 'STABLE_STAFF');
END;

-- HORSE_OWNER: owner@gmail.com (with preloaded wallet balance)
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE email = 'owner@gmail.com')
BEGIN
    INSERT INTO dbo.users (id, username, email, password_hash, enabled, wallet_balance_vnd, active_service_plan, service_plan_started_on, service_next_due_date)
    VALUES (@ownerId, 'horse_owner', 'owner@gmail.com', @hash, 1, 50000000, 'P30', '2026-10-01', '2026-11-01');
    INSERT INTO dbo.user_roles (user_id, role) VALUES (@ownerId, 'HORSE_OWNER');
END;

-- VETERINARIAN: vet@gmail.com
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE email = 'vet@gmail.com')
BEGIN
    INSERT INTO dbo.users (id, username, email, password_hash, enabled)
    VALUES (@vetId, 'vet_dr', 'vet@gmail.com', @hash, 1);
    INSERT INTO dbo.user_roles (user_id, role) VALUES (@vetId, 'VETERINARIAN');
END;
GO

-- =========================================================================
-- 2. ROLE PERMISSION MATRIX
-- =========================================================================
DECLARE @adminId UNIQUEIDENTIFIER = '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11';
DECLARE @now DATETIMEOFFSET = SYSDATETIMEOFFSET();

-- CLUB_MANAGER: Full access across all modules
INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at)
SELECT 'CLUB_MANAGER', m, a, 1, @adminId, @now
FROM (VALUES ('PROFILE'), ('TRAINING'), ('MEDICAL'), ('STABLE_CARE'), ('RACING'), ('REPORTING'), ('SYSTEM'), ('INVENTORY')) modules(m)
CROSS JOIN (VALUES ('VIEW'), ('EDIT'), ('APPROVE'), ('CLAIM')) actions(a)
WHERE NOT EXISTS (SELECT 1 FROM dbo.role_permissions WHERE role_code='CLUB_MANAGER' AND module_code=m AND action_code=a);

-- HEAD_TRAINER: Training management, horse profile, racing, reporting
INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at)
SELECT 'HEAD_TRAINER', m, a, 1, @adminId, @now
FROM (VALUES 
    ('PROFILE', 'VIEW'), ('PROFILE', 'EDIT'), ('PROFILE', 'APPROVE'),
    ('TRAINING', 'VIEW'), ('TRAINING', 'EDIT'), ('TRAINING', 'APPROVE'),
    ('MEDICAL', 'VIEW'), ('MEDICAL', 'EDIT'),
    ('STABLE_CARE', 'VIEW'), ('STABLE_CARE', 'EDIT'),
    ('RACING', 'VIEW'), ('RACING', 'EDIT'),
    ('REPORTING', 'VIEW'),
    ('INVENTORY', 'VIEW')
) permissions(m, a)
WHERE NOT EXISTS (SELECT 1 FROM dbo.role_permissions WHERE role_code='HEAD_TRAINER' AND module_code=m AND action_code=a);

-- HORSE_OWNER: Owner view & approvals
INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at)
SELECT 'HORSE_OWNER', m, a, 1, @adminId, @now
FROM (VALUES 
    ('PROFILE', 'VIEW'),
    ('TRAINING', 'VIEW'),
    ('MEDICAL', 'VIEW'),
    ('STABLE_CARE', 'VIEW'),
    ('RACING', 'VIEW'), ('RACING', 'APPROVE'),
    ('REPORTING', 'VIEW')
) permissions(m, a)
WHERE NOT EXISTS (SELECT 1 FROM dbo.role_permissions WHERE role_code='HORSE_OWNER' AND module_code=m AND action_code=a);

-- STABLE_STAFF: Daily care tasks, inventory, training monitoring
INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at)
SELECT 'STABLE_STAFF', m, a, 1, @adminId, @now
FROM (VALUES 
    ('PROFILE', 'VIEW'),
    ('TRAINING', 'VIEW'), ('TRAINING', 'EDIT'),
    ('STABLE_CARE', 'VIEW'), ('STABLE_CARE', 'EDIT'),
    ('MEDICAL', 'VIEW'),
    ('INVENTORY', 'VIEW'), ('INVENTORY', 'EDIT')
) permissions(m, a)
WHERE NOT EXISTS (SELECT 1 FROM dbo.role_permissions WHERE role_code='STABLE_STAFF' AND module_code=m AND action_code=a);

-- VETERINARIAN: Medical records, health examinations, training medical monitoring
INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at)
SELECT 'VETERINARIAN', m, a, 1, @adminId, @now
FROM (VALUES 
    ('PROFILE', 'VIEW'),
    ('TRAINING', 'VIEW'),
    ('MEDICAL', 'VIEW'), ('MEDICAL', 'EDIT'), ('MEDICAL', 'APPROVE'),
    ('STABLE_CARE', 'VIEW')
) permissions(m, a)
WHERE NOT EXISTS (SELECT 1 FROM dbo.role_permissions WHERE role_code='VETERINARIAN' AND module_code=m AND action_code=a);
GO

-- =========================================================================
-- 3. FACILITIES: STABLES & RACETRACKS
-- =========================================================================

-- Stables: Chuồng B & Chuồng A
DECLARE @stableBId UNIQUEIDENTIFIER = 'E8D772BF-F73A-4C0C-9175-DD33FF4D4568';
DECLARE @stableAId UNIQUEIDENTIFIER = 'A1111111-1111-1111-1111-111111111111';

IF NOT EXISTS (SELECT 1 FROM dbo.stables WHERE id = @stableBId)
    INSERT INTO dbo.stables (id, name, location, is_active)
    VALUES (@stableBId, N'Chuồng B', N'Khu chuồng trại B', 1);

IF NOT EXISTS (SELECT 1 FROM dbo.stables WHERE id = @stableAId)
    INSERT INTO dbo.stables (id, name, location, is_active)
    VALUES (@stableAId, N'Chuồng A', N'Khu chuồng trại A (Chăm sóc & Phục hồi)', 1);

-- 10 Neutral Racetracks with 4 lanes each (06:00 - midnight, hourly slots)
DECLARE @TrackNames TABLE (name NVARCHAR(200) NOT NULL PRIMARY KEY);
INSERT INTO @TrackNames (name)
VALUES
    (N'Sân tập 01'), (N'Sân tập 02'), (N'Sân tập 03'), (N'Sân tập 04'), (N'Sân tập 05'),
    (N'Sân tập 06'), (N'Sân tập 07'), (N'Sân tập 08'), (N'Sân tập 09'), (N'Sân tập 10');

INSERT INTO dbo.racetracks (id, name, is_active, opens_at, closes_at, closes_next_day, slot_minutes)
SELECT NEWID(), names.name, 1, '06:00:00', '00:00:00', 1, 60
FROM @TrackNames names
WHERE NOT EXISTS (SELECT 1 FROM dbo.racetracks WHERE name = names.name);

DECLARE @LaneNumber INT = 1;
WHILE @LaneNumber <= 4
BEGIN
    INSERT INTO dbo.racetrack_lanes (id, racetrack_id, lane_number, is_active)
    SELECT NEWID(), track.id, @LaneNumber, 1
    FROM dbo.racetracks track
    JOIN @TrackNames names ON names.name = track.name
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.racetrack_lanes lane
        WHERE lane.racetrack_id = track.id AND lane.lane_number = @LaneNumber
    );
    SET @LaneNumber += 1;
END;
GO

-- =========================================================================
-- 4. CARE ROUTINE DEFINITIONS (FLOW 4)
-- =========================================================================
IF NOT EXISTS (SELECT 1 FROM dbo.care_routine_definitions WHERE code = 'ROUTINE_MORNING_STABLE_CHECK')
    INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order)
    VALUES (NEWID(), 'ROUTINE_MORNING_STABLE_CHECK', N'Kiểm tra an toàn khu vực chuồng & thông gió',
            N'Kiểm tra tổng quan khu vực chuồng nuôi, chốt khóa an toàn, độ thông thoáng và đèn chiếu sáng đầu ca sáng.',
            'OTHER', '07:00:00', 'FACILITY', 1, 10);

IF NOT EXISTS (SELECT 1 FROM dbo.care_routine_definitions WHERE code = 'ROUTINE_WATER_TROUGH_CHECK')
    INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order)
    VALUES (NEWID(), 'ROUTINE_WATER_TROUGH_CHECK', N'Kiểm tra & vệ sinh hệ thống máng nước tự động',
            N'Kiểm tra van phao tự động, áp lực nước và cọ rửa máng uống đảm bảo nguồn nước sạch cho toàn bộ chuồng trại.',
            'FEEDING', '08:00:00', 'FACILITY', 1, 20);

IF NOT EXISTS (SELECT 1 FROM dbo.care_routine_definitions WHERE code = 'ROUTINE_MORNING_MUCKING')
    INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order)
    VALUES (NEWID(), 'ROUTINE_MORNING_MUCKING', N'Dọn phân chuồng & thay chất độn chuồng ca sáng',
            N'Thu gom phân, thay lớp rơm/mùn cưa ẩm ướt và rải vôi bột khử khuẩn lối đi hành lang chuồng.',
            'MUCKING', '08:30:00', 'FACILITY', 1, 30);

IF NOT EXISTS (SELECT 1 FROM dbo.care_routine_definitions WHERE code = 'ROUTINE_MIDDAY_FORAGE')
    INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order)
    VALUES (NEWID(), 'ROUTINE_MIDDAY_FORAGE', N'Bổ sung cỏ khô & kiểm tra máng ăn trưa',
            N'Cấp thêm cỏ khô Alfalfa/Timothy cho từng ô chuồng và theo dõi lượng ăn của các ngựa trong chuồng.',
            'FEEDING', '11:30:00', 'FACILITY', 1, 40);

IF NOT EXISTS (SELECT 1 FROM dbo.care_routine_definitions WHERE code = 'ROUTINE_EVENING_BEDDING_CHECK')
    INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order)
    VALUES (NEWID(), 'ROUTINE_EVENING_BEDDING_CHECK', N'Kiểm tra đệm lót & khóa chốt ca tối',
            N'San phẳng lớp đệm lót cuối ngày, bổ sung rơm sạch và chốt khóa an toàn toàn bộ cửa chuồng trước khi hết ca.',
            'OTHER', '16:30:00', 'FACILITY', 1, 50);
GO

-- =========================================================================
-- 5. STANDARD FEEDING PLANS & BASELINE INVENTORY (MODULE_RECORDS)
-- =========================================================================
DECLARE @adminUserId UNIQUEIDENTIFIER = '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11';

-- Standard Feeding Plans
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'C1010000-0000-0000-0000-000000000001')
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json)
    VALUES ('C1010000-0000-0000-0000-000000000001', 'STABLE_CARE', 'STANDARD_FEEDING_PLAN', 'api', NULL, @adminUserId, 'ACTIVE', '2026-10-01',
            N'{"planCode":"STD_TRAINING","planName":"Khẩu phần Huấn luyện Tiêu chuẩn","description":"Dành cho ngựa đua trong chu kỳ luyện tập tiêu chuẩn, đảm bảo năng lượng và thể lực bền bỉ.","targetGroup":"Ngựa tập luyện tiêu chuẩn","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải & khoáng vi lượng: 40g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Vitamin E & B-Complex: 25g"}]}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'C1010000-0000-0000-0000-000000000002')
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json)
    VALUES ('C1010000-0000-0000-0000-000000000002', 'STABLE_CARE', 'STANDARD_FEEDING_PLAN', 'api', NULL, @adminUserId, 'ACTIVE', '2026-10-01',
            N'{"planCode":"STD_RECOVERY","planName":"Khẩu phần Nghỉ ngơi & Phục hồi","description":"Dành cho ngựa sau thi đấu hoặc đang trong giai đoạn nghỉ dưỡng phục hồi thể lực, giảm tinh bột và tăng xơ tiêu hóa.","targetGroup":"Ngựa nghỉ dưỡng / phục hồi","meals":[{"mealName":"Bữa sáng (07:00)","feedingTime":"07:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Men vi sinh tiêu hóa: 30g"},{"mealName":"Bữa trưa (12:00)","feedingTime":"12:00:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":4.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải bù nước: 25g"}]}');

-- Baseline Sample Inventory Items
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000001')
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, status, data_json)
    VALUES ('A1010000-0000-0000-0000-000000000001', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, 'ACTIVE',
            N'{"itemName":"Cỏ khô Alfalfa (Mỹ vụ mới)","category":"FEED","unit":"kg","quantityOnHand":850.0,"minThreshold":300.0,"location":"Kho Thức Ăn A - Dãy 1","description":"Cỏ linh lăng sấy khô giàu đạm & canxi cho ngựa đua","referencePrice":45000.0}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000002')
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, status, data_json)
    VALUES ('A1010000-0000-0000-0000-000000000002', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, 'ACTIVE',
            N'{"itemName":"Yến mạch nguyên hạt cán dập (Oats)","category":"FEED","unit":"kg","quantityOnHand":120.0,"minThreshold":200.0,"location":"Kho Thức Ăn A - Dãy 2","description":"Yến mạch giàu tinh bột tiêu hóa nhanh cho ngựa luyện tập cường độ cao","referencePrice":38000.0}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000003')
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, status, data_json)
    VALUES ('A1010000-0000-0000-0000-000000000003', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, 'ACTIVE',
            N'{"itemName":"Cám viên dinh dưỡng cao năng lượng","category":"FEED","unit":"bao (25kg)","quantityOnHand":45.0,"minThreshold":20.0,"location":"Kho Thức Ăn B","description":"Hỗn hợp cám viên bổ sung khoáng và vitamin thiết yếu cho ngựa đua","referencePrice":480000.0}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000004')
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, status, data_json)
    VALUES ('A1010000-0000-0000-0000-000000000004', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, 'ACTIVE',
            N'{"itemName":"Chế phẩm điện giải & khoáng vi lượng","category":"FEED","unit":"hộp (2kg)","quantityOnHand":18.0,"minThreshold":10.0,"location":"Tủ Dinh Dưỡng Kho Y Tế","description":"Bột điện giải bù nước và điện giải sau buổi tập nặng","referencePrice":320000.0}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000005')
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, status, data_json)
    VALUES ('A1010000-0000-0000-0000-000000000005', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, 'ACTIVE',
            N'{"itemName":"Rơm lúa mì lót chuồng khử ẩm","category":"BEDDING","unit":"cuộn (20kg)","quantityOnHand":180.0,"minThreshold":50.0,"location":"Kho Đệm Lót C","description":"Rơm lúa mì sấy khô tự nhiên, hút ẩm tốt làm đệm chuồng","referencePrice":85000.0}');
GO
