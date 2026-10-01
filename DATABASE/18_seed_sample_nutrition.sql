-- 18_seed_sample_nutrition.sql
-- Seed Standard Feeding Plans and active nutrition plans for horses into dbo.module_records
-- Note: On Windows, execute sqlcmd with -f 65001 flag (e.g. sqlcmd -f 65001 -i DATABASE\18_seed_sample_nutrition.sql)

SET NOCOUNT ON;

DECLARE @adminUserId UNIQUEIDENTIFIER = '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11'; -- admin@gmail.com (CLUB_MANAGER)

-- =========================================================================
-- 1. STANDARD FEEDING PLANS (Baseline tiêu chuẩn)
-- =========================================================================

-- Standard Plan A: Khẩu phần Huấn luyện Tiêu chuẩn (STD_TRAINING)
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'C1010000-0000-0000-0000-000000000001')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'C1010000-0000-0000-0000-000000000001', 'STABLE_CARE', 'STANDARD_FEEDING_PLAN', 'api', NULL, @adminUserId, 'ACTIVE', '2026-10-01',
        N'{"planCode":"STD_TRAINING","planName":"Khẩu phần Huấn luyện Tiêu chuẩn","description":"Dành cho ngựa đua trong chu kỳ luyện tập tiêu chuẩn, đảm bảo năng lượng và thể lực bền bỉ.","targetGroup":"Ngựa tập luyện tiêu chuẩn","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải & khoáng vi lượng: 40g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Vitamin E & B-Complex: 25g"}]}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"planCode":"STD_TRAINING","planName":"Khẩu phần Huấn luyện Tiêu chuẩn","description":"Dành cho ngựa đua trong chu kỳ luyện tập tiêu chuẩn, đảm bảo năng lượng và thể lực bền bỉ.","targetGroup":"Ngựa tập luyện tiêu chuẩn","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải & khoáng vi lượng: 40g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Vitamin E & B-Complex: 25g"}]}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'C1010000-0000-0000-0000-000000000001';
END;

-- Standard Plan B: Khẩu phần Nghỉ ngơi & Phục hồi (STD_RECOVERY)
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'C1010000-0000-0000-0000-000000000002')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'C1010000-0000-0000-0000-000000000002', 'STABLE_CARE', 'STANDARD_FEEDING_PLAN', 'api', NULL, @adminUserId, 'ACTIVE', '2026-10-01',
        N'{"planCode":"STD_RECOVERY","planName":"Khẩu phần Nghỉ ngơi & Phục hồi","description":"Dành cho ngựa sau thi đấu hoặc đang trong giai đoạn nghỉ dưỡng phục hồi thể lực, giảm tinh bột và tăng xơ tiêu hóa.","targetGroup":"Ngựa nghỉ dưỡng / phục hồi","meals":[{"mealName":"Bữa sáng (07:00)","feedingTime":"07:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Men vi sinh tiêu hóa: 30g"},{"mealName":"Bữa trưa (12:00)","feedingTime":"12:00:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":4.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải bù nước: 25g"}]}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"planCode":"STD_RECOVERY","planName":"Khẩu phần Nghỉ ngơi & Phục hồi","description":"Dành cho ngựa sau thi đấu hoặc đang trong giai đoạn nghỉ dưỡng phục hồi thể lực, giảm tinh bột và tăng xơ tiêu hóa.","targetGroup":"Ngựa nghỉ dưỡng / phục hồi","meals":[{"mealName":"Bữa sáng (07:00)","feedingTime":"07:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Men vi sinh tiêu hóa: 30g"},{"mealName":"Bữa trưa (12:00)","feedingTime":"12:00:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":4.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải bù nước: 25g"}]}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'C1010000-0000-0000-0000-000000000002';
END;

-- Standard Plan C: Khẩu phần Huấn luyện Nhẹ (STD_LIGHT_TRAINING)
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'C1010000-0000-0000-0000-000000000003')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'C1010000-0000-0000-0000-000000000003', 'STABLE_CARE', 'STANDARD_FEEDING_PLAN', 'api', NULL, @adminUserId, 'ACTIVE', '2026-10-01',
        N'{"planCode":"STD_LIGHT_TRAINING","planName":"Khẩu phần Huấn luyện Nhẹ","description":"Dành cho ngựa mới bắt đầu chu kỳ tập nhẹ hoặc duy trì thể trạng ổn định.","targetGroup":"Ngựa tập nhẹ","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm khoáng vi lượng: 30g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null}]}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"planCode":"STD_LIGHT_TRAINING","planName":"Khẩu phần Huấn luyện Nhẹ","description":"Dành cho ngựa mới bắt đầu chu kỳ tập nhẹ hoặc duy trì thể trạng ổn định.","targetGroup":"Ngựa tập nhẹ","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm khoáng vi lượng: 30g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null}]}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'C1010000-0000-0000-0000-000000000003';
END;


-- =========================================================================
-- 2. HORSE ASSIGNMENTS & NUTRITION PLANS
-- =========================================================================

-- Horse 1: H01 - Thắng (2691A400-AD9C-479D-8DCD-13D685227399)
-- Plan v1: SUPERSEDED (hiệu lực 2026-09-15)
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'D1010000-0000-0000-0000-000000000001')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'D1010000-0000-0000-0000-000000000001', 'STABLE_CARE', 'NUTRITION_PLAN', 'api', '2691A400-AD9C-479D-8DCD-13D685227399', @adminUserId, 'SUPERSEDED', '2026-09-15',
        N'{"version":1,"effectiveDate":"2026-09-15","notes":"Khẩu phần Huấn luyện Tiêu chuẩn (v1 - giai đoạn khởi động)","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm khoáng vi lượng: 30g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Điện giải bù nước: 20g"}]}',
        '2026-09-15T08:00:00Z', '2026-09-30T17:00:00Z'
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"version":1,"effectiveDate":"2026-09-15","notes":"Khẩu phần Huấn luyện Tiêu chuẩn (v1 - giai đoạn khởi động)","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm khoáng vi lượng: 30g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Điện giải bù nước: 20g"}]}',
        status = 'SUPERSEDED',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'D1010000-0000-0000-0000-000000000001';
END;

-- Horse 1: H01 - Thắng (2691A400-AD9C-479D-8DCD-13D685227399)
-- Plan v2: ACTIVE (hiệu lực 2026-10-01)
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'D1010000-0000-0000-0000-000000000002')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'D1010000-0000-0000-0000-000000000002', 'STABLE_CARE', 'NUTRITION_PLAN', 'api', '2691A400-AD9C-479D-8DCD-13D685227399', @adminUserId, 'ACTIVE', '2026-10-01',
        N'{"version":2,"effectiveDate":"2026-10-01","notes":"Khẩu phần Huấn luyện Tiêu chuẩn (v2 - tăng cường năng lượng chuẩn bị thi đấu)","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải & khoáng vi lượng: 40g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Vitamin E & B-Complex: 25g"}]}',
        '2026-10-01T06:00:00Z', SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"version":2,"effectiveDate":"2026-10-01","notes":"Khẩu phần Huấn luyện Tiêu chuẩn (v2 - tăng cường năng lượng chuẩn bị thi đấu)","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải & khoáng vi lượng: 40g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Vitamin E & B-Complex: 25g"}]}',
        status = 'ACTIVE',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'D1010000-0000-0000-0000-000000000002';
END;

-- Horse 2: OWN-20260930-AC19 - Ex (065118D9-326D-4183-AB7D-1BABD133948B)
-- Plan v1: ACTIVE (hiệu lực 2026-10-01) - Training Standard
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'D1010000-0000-0000-0000-000000000003')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'D1010000-0000-0000-0000-000000000003', 'STABLE_CARE', 'NUTRITION_PLAN', 'api', '065118D9-326D-4183-AB7D-1BABD133948B', @adminUserId, 'ACTIVE', '2026-10-01',
        N'{"version":1,"effectiveDate":"2026-10-01","notes":"Khẩu phần Huấn luyện Tiêu chuẩn","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải & khoáng vi lượng: 40g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Vitamin E & B-Complex: 25g"}]}',
        '2026-10-01T06:00:00Z', SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"version":1,"effectiveDate":"2026-10-01","notes":"Khẩu phần Huấn luyện Tiêu chuẩn","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải & khoáng vi lượng: 40g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Vitamin E & B-Complex: 25g"}]}',
        status = 'ACTIVE',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'D1010000-0000-0000-0000-000000000003';
END;

-- Horse 3: 01345 - Demo (C955576F-638E-47D5-B731-DE2D116C68C0)
-- Plan v1: ACTIVE (hiệu lực 2026-10-01) - Recovery / Rest
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'D1010000-0000-0000-0000-000000000004')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'D1010000-0000-0000-0000-000000000004', 'STABLE_CARE', 'NUTRITION_PLAN', 'api', 'C955576F-638E-47D5-B731-DE2D116C68C0', @adminUserId, 'ACTIVE', '2026-10-01',
        N'{"version":1,"effectiveDate":"2026-10-01","notes":"Khẩu phần Nghỉ ngơi & Phục hồi","meals":[{"mealName":"Bữa sáng (07:00)","feedingTime":"07:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Men vi sinh tiêu hóa: 30g"},{"mealName":"Bữa trưa (12:00)","feedingTime":"12:00:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":4.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải bù nước: 25g"}]}',
        '2026-10-01T06:00:00Z', SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"version":1,"effectiveDate":"2026-10-01","notes":"Khẩu phần Nghỉ ngơi & Phục hồi","meals":[{"mealName":"Bữa sáng (07:00)","feedingTime":"07:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Men vi sinh tiêu hóa: 30g"},{"mealName":"Bữa trưa (12:00)","feedingTime":"12:00:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":4.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải bù nước: 25g"}]}',
        status = 'ACTIVE',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'D1010000-0000-0000-0000-000000000004';
END;

-- Horse 4: OWN-20261001-1761 - Horse99 (B6F4EF1F-ABF7-4C13-8150-A2476E3768E3)
-- Plan v1: ACTIVE (hiệu lực 2026-10-01) - Light Training
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'D1010000-0000-0000-0000-000000000005')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'D1010000-0000-0000-0000-000000000005', 'STABLE_CARE', 'NUTRITION_PLAN', 'api', 'B6F4EF1F-ABF7-4C13-8150-A2476E3768E3', @adminUserId, 'ACTIVE', '2026-10-01',
        N'{"version":1,"effectiveDate":"2026-10-01","notes":"Khẩu phần Huấn luyện Nhẹ","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm khoáng vi lượng: 30g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null}]}',
        '2026-10-01T06:00:00Z', SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"version":1,"effectiveDate":"2026-10-01","notes":"Khẩu phần Huấn luyện Nhẹ","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm khoáng vi lượng: 30g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null}]}',
        status = 'ACTIVE',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'D1010000-0000-0000-0000-000000000005';
END;

PRINT 'Seed nutrition plans completed successfully.';
