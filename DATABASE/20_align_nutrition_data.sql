-- =========================================================================
-- 20_align_nutrition_data.sql
-- Cập nhật dữ liệu khẩu phần cho Ex, Demo và dđ theo đúng nghiệp vụ
-- =========================================================================

SET NOCOUNT ON;
DECLARE @adminUserId UNIQUEIDENTIFIER = '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11';

-- 1. Cập nhật khẩu phần cho Ex (OWN-20260930-AC19): Khẩu phần Nghỉ ngơi & Phục hồi
-- 10.5kg cỏ · 1.5kg thức ăn tinh (3 bữa/ngày)
UPDATE dbo.module_records
SET data_json = N'{"version":1,"effectiveDate":"2026-10-01","notes":"Khẩu phần Nghỉ ngơi & Phục hồi","meals":[{"mealName":"Bữa sáng (07:00)","feedingTime":"07:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Men vi sinh tiêu hóa: 30g"},{"mealName":"Bữa trưa (12:00)","feedingTime":"12:00:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":4.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải bù nước: 25g"}]}',
    status = 'ACTIVE',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'D1010000-0000-0000-0000-000000000003';

-- 2. Cập nhật khẩu phần cho Demo (01345): Khẩu phần Huấn luyện Tiêu chuẩn
-- 9.0kg cỏ · 4.0kg thức ăn tinh (3 bữa/ngày)
UPDATE dbo.module_records
SET data_json = N'{"version":1,"effectiveDate":"2026-10-01","notes":"Khẩu phần Huấn luyện Tiêu chuẩn","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải & khoáng vi lượng: 40g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Vitamin E & B-Complex: 25g"}]}',
    status = 'ACTIVE',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'D1010000-0000-0000-0000-000000000004';

-- 3. Tạo/Cập nhật khẩu phần cho dđ (REQ-75C4ABC6): Khẩu phần Huấn luyện Nhẹ
-- 7.5kg cỏ · 2.5kg thức ăn tinh (3 bữa/ngày)
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'D1010000-0000-0000-0000-000000000010')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'D1010000-0000-0000-0000-000000000010', 'STABLE_CARE', 'NUTRITION_PLAN', 'api', '6F93F395-57B9-4509-B6A6-6F1CBFA505C5', @adminUserId, 'ACTIVE', '2026-10-01',
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
    WHERE id = 'D1010000-0000-0000-0000-000000000010';
END;
GO
