-- =========================================================================
-- 10_seed_demo_data.sql
-- Seed Demo Business Data: 3 Horses, Stalls, Care Tasks, Telemetry, Training & Finance
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
    INSERT INTO dbo.horses (
        id, code, name, microchip_passport_number, birth_date, gender, color_markings, breed,
        sire_manual_name, sire_manual_id, dam_manual_name, dam_manual_id,
        current_trainer_id, caretaker_user_id, diagnosing_vet_user_id, stable_location,
        status, health_status, payment_status, service_override, paid_until,
        active_service_plan, service_plan_started_on, service_next_due_date,
        weight_kg, weight_record_id, weight_measured_at, weight_measured_by_user_id, weight_notes
    )
    VALUES (
        @h1Id, 'H01', N'Thắng', 'VN-MC-00101', '2021-03-15', 'STALLION', N'Trắng', N'Ebula',
        N'Bạch Mã Vương', 'VN-PED-001', N'Phi Yến', 'VN-PED-002',
        @trainerId, @staffId, @vetId, N'Chuồng B / A-01',
        'ACTIVE', 'FIT', 'PAID', 0, '2026-12-31',
        'P30', '2026-10-01', '2026-11-01',
        480.00, NEWID(), SYSDATETIMEOFFSET(), @vetId, N'Khám định kỳ tổng quát, thể trạng rất tốt.'
    );

    INSERT INTO dbo.horse_ownerships (id, horse_id, owner_id, ownership_percentage, is_primary_contact)
    VALUES (NEWID(), @h1Id, @ownerId, 100.0, 1);
END;

-- Horse 2: H02 - Ex
DECLARE @h2Id UNIQUEIDENTIFIER = 'B2222222-2222-2222-2222-222222222222';
IF NOT EXISTS (SELECT 1 FROM dbo.horses WHERE code = 'H02')
BEGIN
    INSERT INTO dbo.horses (
        id, code, name, microchip_passport_number, birth_date, gender, color_markings, breed,
        current_trainer_id, caretaker_user_id, diagnosing_vet_user_id, stable_location,
        status, health_status, payment_status, service_override, paid_until,
        active_service_plan, service_plan_started_on, service_next_due_date,
        weight_kg, weight_record_id, weight_measured_at, weight_measured_by_user_id, weight_notes
    )
    VALUES (
        @h2Id, 'H02', N'Ex', 'VN-MC-00102', '2020-05-20', 'GELDING', N'Hồng sắc', N'Arabian',
        @trainerId, @staffId, @vetId, N'Chuồng B / A-02',
        'ACTIVE', 'FIT', 'PAID', 0, '2026-12-31',
        'P30', '2026-10-01', '2026-11-01',
        465.00, NEWID(), SYSDATETIMEOFFSET(), @vetId, N'Thể trạng ổn định.'
    );

    INSERT INTO dbo.horse_ownerships (id, horse_id, owner_id, ownership_percentage, is_primary_contact)
    VALUES (NEWID(), @h2Id, @ownerId, 100.0, 1);
END;

-- Horse 3: H03 - Demo
DECLARE @h3Id UNIQUEIDENTIFIER = 'B3333333-3333-3333-3333-333333333333';
IF NOT EXISTS (SELECT 1 FROM dbo.horses WHERE code = 'H03')
BEGIN
    INSERT INTO dbo.horses (
        id, code, name, microchip_passport_number, birth_date, gender, color_markings, breed,
        current_trainer_id, caretaker_user_id, diagnosing_vet_user_id, stable_location,
        status, health_status, payment_status, service_override, paid_until,
        active_service_plan, service_plan_started_on, service_next_due_date,
        weight_kg, weight_record_id, weight_measured_at, weight_measured_by_user_id, weight_notes
    )
    VALUES (
        @h3Id, 'H03', N'Demo', 'VN-MC-00103', '2022-01-10', 'MARE', N'Hạt dẻ', N'Thoroughbred',
        @trainerId, @staffId, @vetId, N'Chuồng B / A-04',
        'ACTIVE', 'FIT', 'PAID', 0, '2026-12-31',
        'P30', '2026-10-01', '2026-11-01',
        450.00, NEWID(), SYSDATETIMEOFFSET(), @vetId, N'Ngựa non đang phát triển tốt.'
    );

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
-- 4. TODAY'S CARE TASKS (FLOW 4 FOR STABLE_STAFF)
-- =========================================================================
INSERT INTO dbo.care_tasks (
    id, routine_definition_id, task_category, scope, horse_id,
    title, description, task_type, scheduled_date, scheduled_time,
    status, assigned_staff_id, created_at, updated_at
)
SELECT
    NEWID(), d.id, 'ROUTINE', d.scope, NULL,
    d.title, d.description, d.task_type, @today, d.scheduled_time,
    'PENDING', @staffId, SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
FROM dbo.care_routine_definitions d
WHERE d.is_active = 1
  AND NOT EXISTS (
      SELECT 1 FROM dbo.care_tasks t
      WHERE t.routine_definition_id = d.id AND t.scheduled_date = @today AND t.horse_id IS NULL
  );

-- =========================================================================
-- 5. TELEMETRY & BASELINE READINESS FOR H01 (THẮNG)
-- =========================================================================

-- Telemetry Thresholds for H01
IF NOT EXISTS (SELECT 1 FROM dbo.horse_metric_thresholds WHERE horse_id = @h1Id AND metric_type = 'HEART_RATE')
    INSERT INTO dbo.horse_metric_thresholds (id, horse_id, metric_type, minimum_value, maximum_value, unit, configured_by_user_id, effective_from)
    VALUES (NEWID(), @h1Id, 'HEART_RATE', 30.0, 220.0, 'bpm', @vetId, '2026-01-01T00:00:00+07:00');

IF NOT EXISTS (SELECT 1 FROM dbo.horse_metric_thresholds WHERE horse_id = @h1Id AND metric_type = 'SPEED')
    INSERT INTO dbo.horse_metric_thresholds (id, horse_id, metric_type, minimum_value, maximum_value, unit, configured_by_user_id, effective_from)
    VALUES (NEWID(), @h1Id, 'SPEED', 5.0, 75.0, 'km/h', @trainerId, '2026-01-01T00:00:00+07:00');

-- Resting Telemetry Session (heart rate 36 bpm in resting range 30-42 bpm)
DECLARE @restingSessionId UNIQUEIDENTIFIER = 'F1111111-1111-1111-1111-111111111111';
IF NOT EXISTS (SELECT 1 FROM dbo.telemetry_sessions WHERE id = @restingSessionId)
BEGIN
    INSERT INTO dbo.telemetry_sessions (id, horse_id, started_at, ended_at, recorded_by_user_id, entry_method, measurement_context)
    VALUES (@restingSessionId, @h1Id, DATEADD(hour, -2, SYSDATETIMEOFFSET()), DATEADD(hour, -1, SYSDATETIMEOFFSET()), @vetId, 'MANUAL', 'RESTING');

    INSERT INTO dbo.telemetry_readings (telemetry_session_id, recorded_at, heart_rate_bpm, speed_kmh)
    VALUES (@restingSessionId, DATEADD(hour, -1, SYSDATETIMEOFFSET()), 36, 0.0);
END;

-- =========================================================================
-- 6. BASELINE TRAINING PLAN & PHASE FOR H01 (THẮNG)
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

-- Recent Training Evaluation for H01 (fitness_status = GREEN)
IF NOT EXISTS (SELECT 1 FROM dbo.training_evaluations WHERE horse_id = @h1Id)
BEGIN
    INSERT INTO dbo.training_evaluations (
        id, horse_id, recorded_by_user_id, session_date,
        duration_minutes, distance_meters, average_speed_kmh,
        heart_rate_recovery_bpm, perceived_exertion, fitness_status,
        trainer_notes, completed_at
    )
    VALUES (
        NEWID(), @h1Id, @trainerId, DATEADD(day, -1, @today),
        35, 1200.00, 48.50,
        65, 4, 'GREEN',
        N'Ngựa hoàn thành bài chạy nhẹ với phong độ xuất sắc, hồi phục nhanh.', SYSDATETIMEOFFSET()
    );
END;

-- =========================================================================
-- 7. FINANCIAL TRANSACTIONS FOR CURRENT MONTH
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

IF NOT EXISTS (SELECT 1 FROM dbo.financial_transactions WHERE category = 'WALLET_TOPUP' AND wallet_owner_user_id = @ownerId)
    INSERT INTO dbo.financial_transactions (id, horse_id, wallet_owner_user_id, transaction_date, transaction_type, category, amount, currency, description, recorded_by_user_id)
    VALUES (NEWID(), @h1Id, @ownerId, @currMonthStart, 'REVENUE', 'WALLET_TOPUP', 50000000.00, 'VND', N'Nạp tiền ví chủ ngựa ban đầu', @adminId);
GO
