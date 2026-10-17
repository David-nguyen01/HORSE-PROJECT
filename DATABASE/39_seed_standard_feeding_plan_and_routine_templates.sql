/* =========================================================================
   39_seed_standard_feeding_plan_and_routine_templates.sql
   Target Database: racehorse_local (or Azure SQL)
   Purpose:
     Idempotent seed data for:
     1. STD_STANDARD standard feeding plan (FR14) in dbo.module_records
     2. TEMPLATE_DEFAULT_HEALTHY & TEMPLATE_STALL_REST daily routine templates (FR13)
   ========================================================================= */

SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

USE [racehorse_local];
GO

DECLARE @adminId UNIQUEIDENTIFIER = '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11';
DECLARE @now DATETIMEOFFSET = SYSDATETIMEOFFSET();

-- 1. Standard Feeding Plan: STD_STANDARD
IF NOT EXISTS (
    SELECT 1 FROM dbo.module_records 
    WHERE id = 'C1010000-0000-0000-0000-000000000010'
       OR (module_code = 'STABLE_CARE' AND record_type = 'STANDARD_FEEDING_PLAN' AND JSON_VALUE(data_json, '$.planCode') = 'STD_STANDARD')
)
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'C1010000-0000-0000-0000-000000000010',
        'STABLE_CARE',
        'STANDARD_FEEDING_PLAN',
        'api',
        NULL,
        @adminId,
        'ACTIVE',
        '2026-10-01',
        N'{"planCode":"STD_STANDARD","planName":"Khẩu phần Tiêu chuẩn Thường nhật","description":"Dành cho ngựa có trạng thái bình thường, đáp ứng nhu cầu chăm sóc và duy trì thể lực thường nhật.","targetGroup":"Ngựa thể trạng bình thường / chăm sóc thường nhật","meals":[{"mealName":"Bữa sáng (07:00)","feedingTime":"07:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Khoáng vi lượng & muối khoáng: 20g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":0.8,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải bù nước: 20g"}]}',
        @now,
        @now
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"planCode":"STD_STANDARD","planName":"Khẩu phần Tiêu chuẩn Thường nhật","description":"Dành cho ngựa có trạng thái bình thường, đáp ứng nhu cầu chăm sóc và duy trì thể lực thường nhật.","targetGroup":"Ngựa thể trạng bình thường / chăm sóc thường nhật","meals":[{"mealName":"Bữa sáng (07:00)","feedingTime":"07:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Khoáng vi lượng & muối khoáng: 20g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":0.8,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải bù nước: 20g"}]}',
        updated_at = @now
    WHERE id = 'C1010000-0000-0000-0000-000000000010'
       OR (module_code = 'STABLE_CARE' AND record_type = 'STANDARD_FEEDING_PLAN' AND JSON_VALUE(data_json, '$.planCode') = 'STD_STANDARD');
END;

-- 2. Daily Routine Template: TEMPLATE_DEFAULT_HEALTHY
IF NOT EXISTS (
    SELECT 1 FROM dbo.module_records 
    WHERE id = 'D1010000-0000-0000-0000-000000000001'
       OR (module_code = 'STABLE_CARE' AND record_type = 'DAILY_ROUTINE_TEMPLATE' AND JSON_VALUE(data_json, '$.templateCode') = 'TEMPLATE_DEFAULT_HEALTHY')
)
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'D1010000-0000-0000-0000-000000000001',
        'STABLE_CARE',
        'DAILY_ROUTINE_TEMPLATE',
        'api',
        NULL,
        @adminId,
        'ACTIVE',
        CAST(@now AS DATE),
        N'{"templateCode":"TEMPLATE_DEFAULT_HEALTHY","templateName":"Lịch sinh hoạt tiêu chuẩn (Ngựa bình thường / Khỏe mạnh)","targetCondition":"FIT","description":"Lịch sinh hoạt thường nhật gồm vệ sinh, thả bãi cỏ tự do (Turnout), nghỉ trưa và dắt đi dạo nhẹ.","activities":[{"activityType":"GROOMING","startTime":"06:00","endTime":"07:00","notes":"Chải lông & vệ sinh chuẩn bị ca sáng"},{"activityType":"TURNOUT","startTime":"07:30","endTime":"08:30","notes":"Thả bãi dạo tự do tại bãi cỏ tiêu chuẩn"},{"activityType":"REST","startTime":"11:30","endTime":"13:30","notes":"Nghỉ trưa tĩnh tại ô chuồng"},{"activityType":"WALKING","startTime":"16:00","endTime":"17:00","notes":"Dắt đi dạo nhẹ thư giãn gân cốt"}]}',
        @now,
        @now
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"templateCode":"TEMPLATE_DEFAULT_HEALTHY","templateName":"Lịch sinh hoạt tiêu chuẩn (Ngựa bình thường / Khỏe mạnh)","targetCondition":"FIT","description":"Lịch sinh hoạt thường nhật gồm vệ sinh, thả bãi cỏ tự do (Turnout), nghỉ trưa và dắt đi dạo nhẹ.","activities":[{"activityType":"GROOMING","startTime":"06:00","endTime":"07:00","notes":"Chải lông & vệ sinh chuẩn bị ca sáng"},{"activityType":"TURNOUT","startTime":"07:30","endTime":"08:30","notes":"Thả bãi dạo tự do tại bãi cỏ tiêu chuẩn"},{"activityType":"REST","startTime":"11:30","endTime":"13:30","notes":"Nghỉ trưa tĩnh tại ô chuồng"},{"activityType":"WALKING","startTime":"16:00","endTime":"17:00","notes":"Dắt đi dạo nhẹ thư giãn gân cốt"}]}',
        updated_at = @now
    WHERE id = 'D1010000-0000-0000-0000-000000000001'
       OR (module_code = 'STABLE_CARE' AND record_type = 'DAILY_ROUTINE_TEMPLATE' AND JSON_VALUE(data_json, '$.templateCode') = 'TEMPLATE_DEFAULT_HEALTHY');
END;

-- 3. Daily Routine Template: TEMPLATE_STALL_REST
IF NOT EXISTS (
    SELECT 1 FROM dbo.module_records 
    WHERE id = 'D1010000-0000-0000-0000-000000000010'
       OR (module_code = 'STABLE_CARE' AND record_type = 'DAILY_ROUTINE_TEMPLATE' AND JSON_VALUE(data_json, '$.templateCode') = 'TEMPLATE_STALL_REST')
)
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (
        'D1010000-0000-0000-0000-000000000010',
        'STABLE_CARE',
        'DAILY_ROUTINE_TEMPLATE',
        'api',
        NULL,
        @adminId,
        'ACTIVE',
        CAST(@now AS DATE),
        N'{"templateCode":"TEMPLATE_STALL_REST","templateName":"Lịch sinh hoạt tĩnh tại chuồng (Dưỡng thương / Hạn chế y tế)","targetCondition":"RESTRICTED","description":"Lịch sinh hoạt tại chuồng cho ngựa chấn thương hoặc có khóa huấn luyện; tuyệt đối không thả bãi cỏ.","activities":[{"activityType":"STALL_REST","startTime":"07:00","endTime":"08:00","notes":"Nghỉ ngơi tĩnh tại ô chuồng theo chỉ định y tế - không thả bãi"},{"activityType":"ICE_BOOTS","startTime":"09:30","endTime":"10:30","notes":"Chườm đá lạnh & ngâm chân thư giãn cơ gân"},{"activityType":"REST","startTime":"11:30","endTime":"14:00","notes":"Nghỉ ngơi dưỡng thương tại chuồng"},{"activityType":"GROOMING","startTime":"16:00","endTime":"16:45","notes":"Chải lông nhẹ nhàng và kiểm tra khớp chân"}]}',
        @now,
        @now
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"templateCode":"TEMPLATE_STALL_REST","templateName":"Lịch sinh hoạt tĩnh tại chuồng (Dưỡng thương / Hạn chế y tế)","targetCondition":"RESTRICTED","description":"Lịch sinh hoạt tại chuồng cho ngựa chấn thương hoặc có khóa huấn luyện; tuyệt đối không thả bãi cỏ.","activities":[{"activityType":"STALL_REST","startTime":"07:00","endTime":"08:00","notes":"Nghỉ ngơi tĩnh tại ô chuồng theo chỉ định y tế - không thả bãi"},{"activityType":"ICE_BOOTS","startTime":"09:30","endTime":"10:30","notes":"Chườm đá lạnh & ngâm chân thư giãn cơ gân"},{"activityType":"REST","startTime":"11:30","endTime":"14:00","notes":"Nghỉ ngơi dưỡng thương tại chuồng"},{"activityType":"GROOMING","startTime":"16:00","endTime":"16:45","notes":"Chải lông nhẹ nhàng và kiểm tra khớp chân"}]}',
        updated_at = @now
    WHERE id = 'D1010000-0000-0000-0000-000000000010'
       OR (module_code = 'STABLE_CARE' AND record_type = 'DAILY_ROUTINE_TEMPLATE' AND JSON_VALUE(data_json, '$.templateCode') = 'TEMPLATE_STALL_REST');
END;
GO
