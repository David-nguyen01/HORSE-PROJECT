/* =========================================================================
   38_seed_horse_daily_routines.sql
   Target Database: racehorse_local
   Purpose:
     Seeds baseline non-training daily activity routines (FR13 / SS-006)
     for demo racehorses in dbo.module_records (record_type = 'HORSE_DAILY_ROUTINE').
     Provides realistic timetables for:
       - Healthy horse (H01 - Thắng): Turnout, rest, grooming, walking
       - Injured horse (H06 - Chiến Thắng): Medical stall rest, ice boots therapy,
         restricted rest, gentle grooming (strictly NO turnout / heavy exercise).
     Leaves other demo horses (H02, H05) without routines to support verifying
     empty-schedule handling and clinical restriction notices.
   ========================================================================= */

USE [racehorse_local];
GO
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

DECLARE @adminId UNIQUEIDENTIFIER = '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11';
DECLARE @h1Id    UNIQUEIDENTIFIER = 'B1111111-1111-1111-1111-111111111111'; -- H01 Thắng (Healthy / FIT)
DECLARE @h6Id    UNIQUEIDENTIFIER = 'F3C309AF-6F24-403D-BFB5-7FE3394A23A7'; -- H06 Chiến Thắng (INJURED / Training Locked)

-- 1. Daily routines for H01 - Thắng (Healthy racehorse with full daily schedule)
IF EXISTS (SELECT 1 FROM dbo.horses WHERE id = @h1Id)
BEGIN
    -- 06:00 - 07:00: Grooming & morning hygiene
    IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'E1010000-0000-0000-0000-000000000001')
    BEGIN
        INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, data_json)
        VALUES ('E1010000-0000-0000-0000-000000000001', 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'api', @h1Id, @adminId, 'ACTIVE',
                N'{"activityType":"GROOMING","startTime":"06:00","endTime":"07:00","notes":"Chải lông & vệ sinh chuẩn bị ca sáng","isActive":true}');
    END;

    -- 07:30 - 08:30: Morning grass turnout (Thả bãi dạo tự do tại bãi cỏ dãy A)
    IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'E1010000-0000-0000-0000-000000000002')
    BEGIN
        INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, data_json)
        VALUES ('E1010000-0000-0000-0000-000000000002', 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'api', @h1Id, @adminId, 'ACTIVE',
                N'{"activityType":"TURNOUT","startTime":"07:30","endTime":"08:30","notes":"Thả bãi dạo tự do tại bãi cỏ dãy A","isActive":true}');
    END;

    -- 11:30 - 13:30: Midday quiet stall rest
    IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'E1010000-0000-0000-0000-000000000003')
    BEGIN
        INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, data_json)
        VALUES ('E1010000-0000-0000-0000-000000000003', 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'api', @h1Id, @adminId, 'ACTIVE',
                N'{"activityType":"REST","startTime":"11:30","endTime":"13:30","notes":"Nghỉ trưa tĩnh tại ô chuồng A-01","isActive":true}');
    END;

    -- 16:00 - 17:00: Gentle walking exercise
    IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'E1010000-0000-0000-0000-000000000004')
    BEGIN
        INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, data_json)
        VALUES ('E1010000-0000-0000-0000-000000000004', 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'api', @h1Id, @adminId, 'ACTIVE',
                N'{"activityType":"WALKING","startTime":"16:00","endTime":"17:00","notes":"Dắt đi dạo nhẹ thư giãn gân cốt ca chiều","isActive":true}');
    END;
END;

-- 2. Daily routines for H06 - Chiến Thắng (Injured horse under medical restrictions: Stall rest, ice boots, gentle care)
IF EXISTS (SELECT 1 FROM dbo.horses WHERE id = @h6Id)
BEGIN
    -- 07:00 - 08:00: Stall rest (strictly no turnout)
    IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'E1010000-0000-0000-0000-000000000005')
    BEGIN
        INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, data_json)
        VALUES ('E1010000-0000-0000-0000-000000000005', 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'api', @h6Id, @adminId, 'ACTIVE',
                N'{"activityType":"STALL_REST","startTime":"07:00","endTime":"08:00","notes":"Nghỉ ngơi tĩnh tại ô chuồng theo chỉ định y tế - không thả bãi","isActive":true}');
    END;

    -- 09:30 - 10:30: Ice boots therapy
    IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'E1010000-0000-0000-0000-000000000006')
    BEGIN
        INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, data_json)
        VALUES ('E1010000-0000-0000-0000-000000000006', 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'api', @h6Id, @adminId, 'ACTIVE',
                N'{"activityType":"ICE_BOOTS","startTime":"09:30","endTime":"10:30","notes":"Chườm đá lạnh & ngâm chân chống viêm gân theo phác đồ thú y","isActive":true}');
    END;

    -- 11:30 - 14:00: Recovery rest
    IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'E1010000-0000-0000-0000-000000000007')
    BEGIN
        INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, data_json)
        VALUES ('E1010000-0000-0000-0000-000000000007', 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'api', @h6Id, @adminId, 'ACTIVE',
                N'{"activityType":"REST","startTime":"11:30","endTime":"14:00","notes":"Nghỉ ngơi dưỡng thương tại chuồng B-02","isActive":true}');
    END;

    -- 16:00 - 16:45: Gentle grooming & leg joint check
    IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'E1010000-0000-0000-0000-000000000008')
    BEGIN
        INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, data_json)
        VALUES ('E1010000-0000-0000-0000-000000000008', 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'api', @h6Id, @adminId, 'ACTIVE',
                N'{"activityType":"GROOMING","startTime":"16:00","endTime":"16:45","notes":"Chải lông nhẹ nhàng và kiểm tra nhiệt độ khớp chân","isActive":true}');
    END;
END;
GO
