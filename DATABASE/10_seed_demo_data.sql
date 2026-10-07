-- =========================================================================
-- 10_seed_demo_data.sql
-- Seed Demo Business Data: 3 Horses, Stalls, Nutrition, Training Plan & Financial Transactions
-- Target Database: racehorse_local
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
GO

USE [racehorse_local];
GO

DECLARE @adminId   UNIQUEIDENTIFIER = '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11';
DECLARE @trainerId UNIQUEIDENTIFIER = '22222222-2222-2222-2222-222222222222';
DECLARE @staffId   UNIQUEIDENTIFIER = '48772A9D-9DA0-44A8-90F2-0459B3ADB768';
DECLARE @ownerId   UNIQUEIDENTIFIER = '44444444-4444-4444-4444-444444444444';
DECLARE @vetId     UNIQUEIDENTIFIER = '55555555-5555-5555-5555-555555555555';
DECLARE @stableBId UNIQUEIDENTIFIER = 'E8D772BF-F73A-4C0C-9175-DD33FF4D4568';
DECLARE @today DATE = CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATE);

-- =========================================================================
-- 1. THREE DEMO HORSES & OWNERSHIPS
-- =========================================================================

-- Horse 1: H01 - Thắng
DECLARE @h1Id UNIQUEIDENTIFIER = 'B1111111-1111-1111-1111-111111111111';
IF NOT EXISTS (SELECT 1 FROM dbo.horses WHERE code = 'H01')
BEGIN
    INSERT INTO dbo.horses (id, code, name, microchip_passport_number, birth_date, gender, color_markings, breed,
                           current_trainer_id, caretaker_user_id, diagnosing_vet_user_id, stable_location,
                           status, health_status, payment_status, service_override, paid_until)
    VALUES (@h1Id, 'H01', N'Thắng', 'VN-MC-00101', '2021-03-15', 'STALLION', N'Trắng', N'Ebula',
            @trainerId, @staffId, @vetId, N'Chuồng B / A-01', 'ACTIVE', 'FIT', 'PAID', 0, '2026-12-31');

    INSERT INTO dbo.horse_ownerships (id, horse_id, owner_id, ownership_percentage, is_primary_contact)
    VALUES (NEWID(), @h1Id, @ownerId, 100.0, 1);
END;

-- Horse 2: H02 - Ex
DECLARE @h2Id UNIQUEIDENTIFIER = 'B2222222-2222-2222-2222-222222222222';
IF NOT EXISTS (SELECT 1 FROM dbo.horses WHERE code = 'H02')
BEGIN
    INSERT INTO dbo.horses (id, code, name, microchip_passport_number, birth_date, gender, color_markings, breed,
                           current_trainer_id, caretaker_user_id, diagnosing_vet_user_id, stable_location,
                           status, health_status, payment_status, service_override, paid_until)
    VALUES (@h2Id, 'H02', N'Ex', 'VN-MC-00102', '2020-05-20', 'GELDING', N'Hồng sắc', N'Arabian',
            @trainerId, @staffId, @vetId, N'Chuồng B / A-02', 'ACTIVE', 'FIT', 'PAID', 0, '2026-12-31');

    INSERT INTO dbo.horse_ownerships (id, horse_id, owner_id, ownership_percentage, is_primary_contact)
    VALUES (NEWID(), @h2Id, @ownerId, 100.0, 1);
END;

-- Horse 3: H03 - Demo
DECLARE @h3Id UNIQUEIDENTIFIER = 'B3333333-3333-3333-3333-333333333333';
IF NOT EXISTS (SELECT 1 FROM dbo.horses WHERE code = 'H03')
BEGIN
    INSERT INTO dbo.horses (id, code, name, microchip_passport_number, birth_date, gender, color_markings, breed,
                           current_trainer_id, caretaker_user_id, diagnosing_vet_user_id, stable_location,
                           status, health_status, payment_status, service_override, paid_until)
    VALUES (@h3Id, 'H03', N'Demo', 'VN-MC-00103', '2022-01-10', 'MARE', N'Hạt dẻ', N'Thoroughbred',
            @trainerId, @staffId, @vetId, N'Chuồng B / A-04', 'ACTIVE', 'FIT', 'PAID', 0, '2026-12-31');

    INSERT INTO dbo.horse_ownerships (id, horse_id, owner_id, ownership_percentage, is_primary_contact)
    VALUES (NEWID(), @h3Id, @ownerId, 100.0, 1);
END;

-- =========================================================================
-- 2. STALLS & STALL ASSIGNMENTS (CHUỒNG B)
-- =========================================================================

-- Stalls
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'C1010000-0000-0000-0000-000000000001')
    INSERT INTO dbo.module_records (id, module_code, record_type, status, data_json)
    VALUES ('C1010000-0000-0000-0000-000000000001', 'STABLE_CARE', 'STALL', 'ACTIVE',
            N'{"isActive":true,"stallCode":"A-01","stableId":"' + LOWER(CAST(@stableBId AS VARCHAR(36))) + N'","stableName":"Chuồng B"}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'C1010000-0000-0000-0000-000000000002')
    INSERT INTO dbo.module_records (id, module_code, record_type, status, data_json)
    VALUES ('C1010000-0000-0000-0000-000000000002', 'STABLE_CARE', 'STALL', 'ACTIVE',
            N'{"isActive":true,"stallCode":"A-02","stableId":"' + LOWER(CAST(@stableBId AS VARCHAR(36))) + N'","stableName":"Chuồng B"}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'C1010000-0000-0000-0000-000000000003')
    INSERT INTO dbo.module_records (id, module_code, record_type, status, data_json)
    VALUES ('C1010000-0000-0000-0000-000000000003', 'STABLE_CARE', 'STALL', 'ACTIVE',
            N'{"isActive":true,"stallCode":"A-03","stableId":"' + LOWER(CAST(@stableBId AS VARCHAR(36))) + N'","stableName":"Chuồng B"}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'C1010000-0000-0000-0000-000000000004')
    INSERT INTO dbo.module_records (id, module_code, record_type, status, data_json)
    VALUES ('C1010000-0000-0000-0000-000000000004', 'STABLE_CARE', 'STALL', 'ACTIVE',
            N'{"isActive":true,"stallCode":"A-04","stableId":"' + LOWER(CAST(@stableBId AS VARCHAR(36))) + N'","stableName":"Chuồng B"}');

-- Stall Assignments
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE record_type = 'STALL_ASSIGNMENT' AND horse_id = @h1Id)
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json)
    VALUES (NEWID(), 'STABLE_CARE', 'STALL_ASSIGNMENT', 'api', @h1Id, @adminId, 'ACTIVE', @today,
            N'{"stableId":"' + LOWER(CAST(@stableBId AS VARCHAR(36))) + N'","stableName":"Chuồng B","stallId":"c1010000-0000-0000-0000-000000000001","stallCode":"A-01","assignedFrom":"2026-10-01T00:00:00+07:00","assignedUntil":null}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE record_type = 'STALL_ASSIGNMENT' AND horse_id = @h2Id)
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json)
    VALUES (NEWID(), 'STABLE_CARE', 'STALL_ASSIGNMENT', 'api', @h2Id, @adminId, 'ACTIVE', @today,
            N'{"stableId":"' + LOWER(CAST(@stableBId AS VARCHAR(36))) + N'","stableName":"Chuồng B","stallId":"c1010000-0000-0000-0000-000000000002","stallCode":"A-02","assignedFrom":"2026-10-01T00:00:00+07:00","assignedUntil":null}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE record_type = 'STALL_ASSIGNMENT' AND horse_id = @h3Id)
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json)
    VALUES (NEWID(), 'STABLE_CARE', 'STALL_ASSIGNMENT', 'api', @h3Id, @adminId, 'ACTIVE', @today,
            N'{"stableId":"' + LOWER(CAST(@stableBId AS VARCHAR(36))) + N'","stableName":"Chuồng B","stallId":"c1010000-0000-0000-0000-000000000004","stallCode":"A-04","assignedFrom":"2026-10-01T00:00:00+07:00","assignedUntil":null}');

-- =========================================================================
-- 3. NUTRITION PLANS FOR HORSES
-- =========================================================================
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'D1010000-0000-0000-0000-000000000002')
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json)
    VALUES ('D1010000-0000-0000-0000-000000000002', 'STABLE_CARE', 'NUTRITION_PLAN', 'api', @h1Id, @adminId, 'ACTIVE', '2026-10-01',
            N'{"version":1,"effectiveDate":"2026-10-01","notes":"Khẩu phần Huấn luyện Tiêu chuẩn cho H01","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải & khoáng vi lượng: 40g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Vitamin E & B-Complex: 25g"}]}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'D1010000-0000-0000-0000-000000000003')
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json)
    VALUES ('D1010000-0000-0000-0000-000000000003', 'STABLE_CARE', 'NUTRITION_PLAN', 'api', @h2Id, @adminId, 'ACTIVE', '2026-10-01',
            N'{"version":1,"effectiveDate":"2026-10-01","notes":"Khẩu phần Nghỉ ngơi & Phục hồi cho H02","meals":[{"mealName":"Bữa sáng (07:00)","feedingTime":"07:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Men vi sinh tiêu hóa: 30g"},{"mealName":"Bữa trưa (12:00)","feedingTime":"12:00:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":4.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải bù nước: 25g"}]}');

IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'D1010000-0000-0000-0000-000000000004')
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json)
    VALUES ('D1010000-0000-0000-0000-000000000004', 'STABLE_CARE', 'NUTRITION_PLAN', 'api', @h3Id, @adminId, 'ACTIVE', '2026-10-01',
            N'{"version":1,"effectiveDate":"2026-10-01","notes":"Khẩu phần Huấn luyện Tiêu chuẩn cho H03","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải & khoáng vi lượng: 40g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Vitamin E & B-Complex: 25g"}]}');

-- =========================================================================
-- 4. BASELINE TRAINING PLAN & PHASE FOR H01 (THẮNG)
-- =========================================================================
DECLARE @planId UNIQUEIDENTIFIER = 'D1111111-1111-1111-1111-111111111111';
DECLARE @phaseId UNIQUEIDENTIFIER = 'E1111111-1111-1111-1111-111111111111';

IF NOT EXISTS (SELECT 1 FROM dbo.training_plans WHERE id = @planId)
BEGIN
    INSERT INTO dbo.training_plans (id, horse_id, created_by_user_id, name, target_goal, notes, is_active)
    VALUES (@planId, @h1Id, @trainerId, N'Kế hoạch huấn luyện mùa giải 2026', N'Tích lũy thể lực và cải thiện tốc độ', N'Kế hoạch huấn luyện tiêu chuẩn cho H01', 1);

    INSERT INTO dbo.training_plan_phases (id, training_plan_id, phase_order, name, start_date, end_date, target_distance_meters, workload_intensity, surface_type, rest_days_json)
    VALUES (@phaseId, @planId, 1, N'Giai đoạn tích lũy thể lực', '2026-10-01', '2026-12-31', 1200.0, 'LOW', 'DIRT', N'[]');
END;

-- =========================================================================
-- 5. SAMPLE FINANCIAL TRANSACTIONS FOR CURRENT MONTH (WITH CURRENCY='VND')
-- =========================================================================
DECLARE @currMonthStart DATE = DATEFROMPARTS(YEAR(@today), MONTH(@today), 1);

IF NOT EXISTS (SELECT 1 FROM dbo.financial_transactions WHERE category = 'FEED_COST' AND transaction_date = @currMonthStart)
    INSERT INTO dbo.financial_transactions (id, horse_id, stable_id, transaction_date, transaction_type, category, amount, currency, description, recorded_by_user_id)
    VALUES (NEWID(), @h1Id, @stableBId, @currMonthStart, 'EXPENSE', 'FEED_COST', 15000000.00, 'VND', N'Chi phí thức ăn tinh và cỏ khô nhập khẩu đợt 1', @adminId);

IF NOT EXISTS (SELECT 1 FROM dbo.financial_transactions WHERE category = 'MEDICAL_COST' AND transaction_date = @currMonthStart)
    INSERT INTO dbo.financial_transactions (id, horse_id, stable_id, transaction_date, transaction_type, category, amount, currency, description, recorded_by_user_id)
    VALUES (NEWID(), @h1Id, @stableBId, @currMonthStart, 'EXPENSE', 'MEDICAL_COST', 5500000.00, 'VND', N'Chi phí khám định kỳ và bổ sung vitamin vi lượng', @adminId);

IF NOT EXISTS (SELECT 1 FROM dbo.financial_transactions WHERE category = 'OPERATING_COST' AND transaction_date = @currMonthStart)
    INSERT INTO dbo.financial_transactions (id, horse_id, stable_id, transaction_date, transaction_type, category, amount, currency, description, recorded_by_user_id)
    VALUES (NEWID(), NULL, @stableBId, @currMonthStart, 'EXPENSE', 'OPERATING_COST', 8000000.00, 'VND', N'Chi phí vệ sinh, điện nước và đệm lót chuồng B', @adminId);

IF NOT EXISTS (SELECT 1 FROM dbo.financial_transactions WHERE category = 'PRIZE_REVENUE' AND transaction_date = @currMonthStart)
    INSERT INTO dbo.financial_transactions (id, horse_id, stable_id, transaction_date, transaction_type, category, amount, currency, description, recorded_by_user_id)
    VALUES (NEWID(), @h1Id, NULL, @currMonthStart, 'REVENUE', 'PRIZE_REVENUE', 50000000.00, 'VND', N'Tiền thưởng giải đua mùa thu H01 đạt thành tích xuất sắc', @adminId);
GO
