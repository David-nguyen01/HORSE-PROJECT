-- =========================================================================
-- 19_revise_stable_nutrition_model.sql
-- Cập nhật dữ liệu phục vụ mô hình vận hành Stable-Based Nutrition Workload
-- =========================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 1. Cập nhật Stable 'b' -> 'Chuồng B'
UPDATE dbo.stables 
SET name = N'Chuồng B', location = N'Khu chuồng trại B'
WHERE id = 'E8D772BF-F73A-4C0C-9175-DD33FF4D4568';

-- Cập nhật stableName trong STALL_ASSIGNMENT của Chuồng B
UPDATE dbo.module_records
SET data_json = JSON_MODIFY(data_json, '$.stableName', N'Chuồng B')
WHERE module_code = 'STABLE_CARE' 
  AND record_type = 'STALL_ASSIGNMENT' 
  AND JSON_VALUE(data_json, '$.stableId') = 'e8d772bf-f73a-4c0c-9175-dd33ff4d4568';

-- Cập nhật stable_location cho các ngựa trong Chuồng B
UPDATE dbo.horses SET stable_location = N'Chuồng B / A-01' WHERE code = 'H01';
UPDATE dbo.horses SET stable_location = N'Chuồng B / A-02' WHERE code = 'OWN-20260930-AC19';
UPDATE dbo.horses SET stable_location = N'Chuồng B / A-04' WHERE code = '01345';

-- 2. Đưa ô A-03 về trạng thái TRỐNG (EMPTY STALL)
-- Kết thúc phân bổ ô A-03 của ngựa dđ (REQ-75C4ABC6)
UPDATE dbo.module_records
SET status = 'ENDED',
    data_json = JSON_MODIFY(data_json, '$.assignedUntil', CONVERT(VARCHAR(50), SYSDATETIMEOFFSET(), 127))
WHERE id = '1DB77A90-4FC2-4529-9FD6-30E99391EEE0' AND status = 'ACTIVE';

-- 3. Tạo Stable 'Chuồng A' (Khu phục hồi & cách ly) nếu chưa có
IF NOT EXISTS (SELECT 1 FROM dbo.stables WHERE id = 'E8D772BF-F73A-4C0C-9175-DD33FF4D4569')
BEGIN
    INSERT INTO dbo.stables (id, name, location, is_active)
    VALUES ('E8D772BF-F73A-4C0C-9175-DD33FF4D4569', N'Chuồng A', N'Khu chuồng trại A (Chăm sóc & Phục hồi)', 1);
END;

-- Thêm Stall B-01 cho Chuồng A nếu chưa có
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'DD9B5597-3724-44D1-B2A3-1565FBCC13E3')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, status, data_json)
    VALUES ('DD9B5597-3724-44D1-B2A3-1565FBCC13E3', 'STABLE_CARE', 'STALL', 'api', 'ACTIVE',
            '{"isActive":true,"stallCode":"B-01","stableId":"e8d772bf-f73a-4c0c-9175-dd33ff4d4569"}');
END;

-- Thêm Stall B-02 (ô trống) cho Chuồng A nếu chưa có
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'DD9B5597-3724-44D1-B2A3-1565FBCC13E4')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, status, data_json)
    VALUES ('DD9B5597-3724-44D1-B2A3-1565FBCC13E4', 'STABLE_CARE', 'STALL', 'api', 'ACTIVE',
            '{"isActive":true,"stallCode":"B-02","stableId":"e8d772bf-f73a-4c0c-9175-dd33ff4d4569"}');
END;

-- Gán ngựa dđ (REQ-75C4ABC6, id 6F93F395-57B9-4509-B6A6-6F1CBFA505C5) vào ô B-01 của Chuồng A
-- Lưu ý: Ngựa dđ CHƯA gán khẩu phần ăn để kiểm thử trạng thái "Chưa gán khẩu phần"
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = '1DB77A90-4FC2-4529-9FD6-30E99391EEE1')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json)
    VALUES ('1DB77A90-4FC2-4529-9FD6-30E99391EEE1', 'STABLE_CARE', 'STALL_ASSIGNMENT', 'api',
            '6F93F395-57B9-4509-B6A6-6F1CBFA505C5', '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', 'ACTIVE',
            CAST(SYSDATETIMEOFFSET() AS DATE),
            '{"stableId":"e8d772bf-f73a-4c0c-9175-dd33ff4d4569","stableName":"Chuồng A","stallId":"dd9b5597-3724-44d1-b2a3-1565fbcc13e3","stallCode":"B-01","assignedFrom":"2026-10-01T11:16:55.357854+07:00","assignedUntil":null}');
END;

UPDATE dbo.horses SET stable_location = N'Chuồng A / B-01' WHERE code = 'REQ-75C4ABC6';

SELECT s.name AS stable_name, 
       JSON_VALUE(m.data_json, '$.stallCode') AS stall_code,
       a.horse_id, h.code AS horse_code, h.name AS horse_name
FROM dbo.stables s
JOIN dbo.module_records m ON m.record_type = 'STALL' AND JSON_VALUE(m.data_json, '$.stableId') = CAST(s.id AS VARCHAR(36))
LEFT JOIN dbo.module_records a ON a.record_type = 'STALL_ASSIGNMENT' AND a.status = 'ACTIVE' AND JSON_VALUE(a.data_json, '$.stallId') = CAST(m.id AS VARCHAR(36))
LEFT JOIN dbo.horses h ON h.id = a.horse_id
ORDER BY s.name, JSON_VALUE(m.data_json, '$.stallCode');
