-- 16_seed_sample_inventory.sql
-- Seed / update 11 sample inventory items and 1 pending replenishment request into dbo.module_records
-- Note: On Windows, run sqlcmd with -f 65001 flag (e.g. sqlcmd -f 65001 -i DATABASE\16_seed_sample_inventory.sql)

SET NOCOUNT ON;

DECLARE @adminUserId UNIQUEIDENTIFIER = '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11'; -- admin@gmail.com (CLUB_MANAGER)
DECLARE @staffUserId UNIQUEIDENTIFIER = '48772A9D-9DA0-44A8-90F2-0459B3ADB768'; -- stable@gmail.com (STABLE_STAFF)
DECLARE @today DATE = CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATE);

-- 1. Cỏ khô Alfalfa (Mỹ vụ mới) - FEED - ĐỦ HÀNG
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000001')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'A1010000-0000-0000-0000-000000000001', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, @today, 'ACTIVE',
        N'{"itemName":"Cỏ khô Alfalfa (Mỹ vụ mới)","category":"FEED","unit":"kg","quantityOnHand":850.0,"minThreshold":300.0,"location":"Kho Thức Ăn A - Dãy 1","description":"Cỏ linh lăng sấy khô giàu đạm & canxi cho ngựa đua","referencePrice":45000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemName":"Cỏ khô Alfalfa (Mỹ vụ mới)","category":"FEED","unit":"kg","quantityOnHand":850.0,"minThreshold":300.0,"location":"Kho Thức Ăn A - Dãy 1","description":"Cỏ linh lăng sấy khô giàu đạm & canxi cho ngựa đua","referencePrice":45000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'A1010000-0000-0000-0000-000000000001';
END;

-- 2. Yến mạch nguyên hạt cán dập (Oats) - FEED - TỒN THẤP (120/200)
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000002')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'A1010000-0000-0000-0000-000000000002', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, @today, 'ACTIVE',
        N'{"itemName":"Yến mạch nguyên hạt cán dập (Oats)","category":"FEED","unit":"kg","quantityOnHand":120.0,"minThreshold":200.0,"location":"Kho Thức Ăn A - Dãy 2","description":"Yến mạch giàu tinh bột tiêu hóa nhanh cho ngựa luyện tập cường độ cao","referencePrice":38000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemName":"Yến mạch nguyên hạt cán dập (Oats)","category":"FEED","unit":"kg","quantityOnHand":120.0,"minThreshold":200.0,"location":"Kho Thức Ăn A - Dãy 2","description":"Yến mạch giàu tinh bột tiêu hóa nhanh cho ngựa luyện tập cường độ cao","referencePrice":38000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'A1010000-0000-0000-0000-000000000002';
END;

-- 3. Cám viên dinh dưỡng cao năng lượng - FEED - ĐỦ HÀNG
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000003')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'A1010000-0000-0000-0000-000000000003', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, @today, 'ACTIVE',
        N'{"itemName":"Cám viên dinh dưỡng cao năng lượng","category":"FEED","unit":"bao (25kg)","quantityOnHand":45.0,"minThreshold":20.0,"location":"Kho Thức Ăn B","description":"Hỗn hợp cám viên bổ sung khoáng và vitamin thiết yếu cho ngựa đua","referencePrice":480000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemName":"Cám viên dinh dưỡng cao năng lượng","category":"FEED","unit":"bao (25kg)","quantityOnHand":45.0,"minThreshold":20.0,"location":"Kho Thức Ăn B","description":"Hỗn hợp cám viên bổ sung khoáng và vitamin thiết yếu cho ngựa đua","referencePrice":480000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'A1010000-0000-0000-0000-000000000003';
END;

-- 4. Gạc y tế vô trùng chuyên dụng - MEDICATION - TỒN THẤP (8/15)
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000004')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'A1010000-0000-0000-0000-000000000004', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, @today, 'ACTIVE',
        N'{"itemName":"Gạc y tế vô trùng chuyên dụng","category":"MEDICATION","unit":"hộp (100 miếng)","quantityOnHand":8.0,"minThreshold":15.0,"location":"Tủ y tế khu chuồng trại","description":"Gạc tiệt trùng thấm hút xử lý vết thương ngoài da và vết cắt","referencePrice":150000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemName":"Gạc y tế vô trùng chuyên dụng","category":"MEDICATION","unit":"hộp (100 miếng)","quantityOnHand":8.0,"minThreshold":15.0,"location":"Tủ y tế khu chuồng trại","description":"Gạc tiệt trùng thấm hút xử lý vết thương ngoài da và vết cắt","referencePrice":150000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'A1010000-0000-0000-0000-000000000004';
END;

-- 5. Băng cuốn co giãn thú y (Vet Wrap 10cm) - MEDICATION - ĐỦ HÀNG
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000005')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'A1010000-0000-0000-0000-000000000005', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, @today, 'ACTIVE',
        N'{"itemName":"Băng cuốn co giãn thú y (Vet Wrap 10cm)","category":"MEDICATION","unit":"cuộn","quantityOnHand":42.0,"minThreshold":25.0,"location":"Tủ y tế khu chuồng trại","description":"Băng dính tự co giãn cố định khớp chân và gân ngựa sau vận động","referencePrice":65000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemName":"Băng cuốn co giãn thú y (Vet Wrap 10cm)","category":"MEDICATION","unit":"cuộn","quantityOnHand":42.0,"minThreshold":25.0,"location":"Tủ y tế khu chuồng trại","description":"Băng dính tự co giãn cố định khớp chân và gân ngựa sau vận động","referencePrice":65000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'A1010000-0000-0000-0000-000000000005';
END;

-- 6. Dung dịch sát trùng Povidone Iodine 10% (1L) - MEDICATION - TỒN THẤP (5/10)
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000006')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'A1010000-0000-0000-0000-000000000006', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, @today, 'ACTIVE',
        N'{"itemName":"Dung dịch sát trùng Povidone Iodine 10% (1L)","category":"MEDICATION","unit":"chai","quantityOnHand":5.0,"minThreshold":10.0,"location":"Tủ y tế khu chuồng trại","description":"Dung dịch diệt khuẩn rửa sạch vết thương hở và sát trùng móng","referencePrice":180000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemName":"Dung dịch sát trùng Povidone Iodine 10% (1L)","category":"MEDICATION","unit":"chai","quantityOnHand":5.0,"minThreshold":10.0,"location":"Tủ y tế khu chuồng trại","description":"Dung dịch diệt khuẩn rửa sạch vết thương hở và sát trùng móng","referencePrice":180000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'A1010000-0000-0000-0000-000000000006';
END;

-- 7. Chế phẩm điện giải & khoáng vi lượng - SUPPLEMENT - ĐỦ HÀNG
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000007')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'A1010000-0000-0000-0000-000000000007', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, @today, 'ACTIVE',
        N'{"itemName":"Chế phẩm điện giải & khoáng vi lượng","category":"SUPPLEMENT","unit":"hũ (2kg)","quantityOnHand":18.0,"minThreshold":10.0,"location":"Kho dinh dưỡng bổ sung","description":"Bù nước và muối khoáng chống kiệt sức sau các bài tập nặng","referencePrice":620000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemName":"Chế phẩm điện giải & khoáng vi lượng","category":"SUPPLEMENT","unit":"hũ (2kg)","quantityOnHand":18.0,"minThreshold":10.0,"location":"Kho dinh dưỡng bổ sung","description":"Bù nước và muối khoáng chống kiệt sức sau các bài tập nặng","referencePrice":620000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'A1010000-0000-0000-0000-000000000007';
END;

-- 8. Chất độn chuồng mùn cưa thông ép khối - EQUIPMENT - ĐỦ HÀNG
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000008')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'A1010000-0000-0000-0000-000000000008', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, @today, 'ACTIVE',
        N'{"itemName":"Chất độn chuồng mùn cưa thông ép khối","category":"EQUIPMENT","unit":"khối (20kg)","quantityOnHand":80.0,"minThreshold":50.0,"location":"Kho đệm lót chuồng","description":"Mùn cưa gỗ thông khử mùi, hút ẩm cao, chống trơn trượt chuồng nuôi","referencePrice":110000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemName":"Chất độn chuồng mùn cưa thông ép khối","category":"EQUIPMENT","unit":"khối (20kg)","quantityOnHand":80.0,"minThreshold":50.0,"location":"Kho đệm lót chuồng","description":"Mùn cưa gỗ thông khử mùi, hút ẩm cao, chống trơn trượt chuồng nuôi","referencePrice":110000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'A1010000-0000-0000-0000-000000000008';
END;

-- 9. Dung dịch khử trùng chuồng trại sinh học (5L) - EQUIPMENT - TỒN THẤP (6/12)
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000009')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'A1010000-0000-0000-0000-000000000009', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, @today, 'ACTIVE',
        N'{"itemName":"Dung dịch khử trùng chuồng trại sinh học (5L)","category":"EQUIPMENT","unit":"can","quantityOnHand":6.0,"minThreshold":12.0,"location":"Kho hóa chất vệ sinh","description":"Chế phẩm vi sinh diệt khuẩn, khử mùi amoniac trong chuồng trại","referencePrice":320000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemName":"Dung dịch khử trùng chuồng trại sinh học (5L)","category":"EQUIPMENT","unit":"can","quantityOnHand":6.0,"minThreshold":12.0,"location":"Kho hóa chất vệ sinh","description":"Chế phẩm vi sinh diệt khuẩn, khử mùi amoniac trong chuồng trại","referencePrice":320000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'A1010000-0000-0000-0000-000000000009';
END;

-- 10. Găng tay cao su y tế dùng một lần - EQUIPMENT - ĐỦ HÀNG
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000010')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'A1010000-0000-0000-0000-000000000010', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, @today, 'ACTIVE',
        N'{"itemName":"Găng tay cao su y tế dùng một lần","category":"EQUIPMENT","unit":"hộp (100 chiếc)","quantityOnHand":25.0,"minThreshold":10.0,"location":"Tủ bảo hộ lao động","description":"Găng tay nitrile chuyên dụng cho nhân viên dọn chuồng và sơ cứu","referencePrice":120000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemName":"Găng tay cao su y tế dùng một lần","category":"EQUIPMENT","unit":"hộp (100 chiếc)","quantityOnHand":25.0,"minThreshold":10.0,"location":"Tủ bảo hộ lao động","description":"Găng tay nitrile chuyên dụng cho nhân viên dọn chuồng và sơ cứu","referencePrice":120000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'A1010000-0000-0000-0000-000000000010';
END;

-- 11. Xô nhựa chăm sóc và cho ăn chống va đập (20L) - EQUIPMENT - ĐỦ HÀNG
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'A1010000-0000-0000-0000-000000000011')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'A1010000-0000-0000-0000-000000000011', 'INVENTORY', 'INVENTORY_ITEM', NULL, @adminUserId, @today, 'ACTIVE',
        N'{"itemName":"Xô nhựa chăm sóc và cho ăn chống va đập (20L)","category":"EQUIPMENT","unit":"cái","quantityOnHand":16.0,"minThreshold":10.0,"location":"Kho dụng cụ chuồng trại","description":"Xô nhựa dẻo siêu bền chịu va đập cho ăn và tiếp nước","referencePrice":95000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemName":"Xô nhựa chăm sóc và cho ăn chống va đập (20L)","category":"EQUIPMENT","unit":"cái","quantityOnHand":16.0,"minThreshold":10.0,"location":"Kho dụng cụ chuồng trại","description":"Xô nhựa dẻo siêu bền chịu va đập cho ăn và tiếp nước","referencePrice":95000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'A1010000-0000-0000-0000-000000000011';
END;

-- 12. Replenishment Request PENDING for Item #2 (Yến mạch)
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = 'B2020000-0000-0000-0000-000000000001')
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at)
    VALUES (
        'B2020000-0000-0000-0000-000000000001', 'INVENTORY', 'REPLENISHMENT_REQUEST', NULL, @staffUserId, @today, 'PENDING_APPROVAL',
        N'{"itemId":"A1010000-0000-0000-0000-000000000002","itemName":"Yến mạch nguyên hạt cán dập (Oats)","category":"FEED","requestedQuantity":300.0,"unit":"kg","reason":"Tồn kho chỉ còn 120 kg (dưới ngưỡng an toàn 200 kg), đủ dùng trong 3 ngày tới cho lịch tập đua. Đề xuất nhập thêm 300 kg.","proposedByUserId":"48772A9D-9DA0-44A8-90F2-0459B3ADB768","proposedByName":"nguyen ba dung","proposedAt":"2026-10-01T16:00:00Z","reviewedByUserId":null,"reviewedByName":null,"reviewedAt":null,"reviewNotes":null,"referencePrice":38000.0,"estimatedCost":11400000.0}',
        SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
    );
END
ELSE
BEGIN
    UPDATE dbo.module_records
    SET data_json = N'{"itemId":"A1010000-0000-0000-0000-000000000002","itemName":"Yến mạch nguyên hạt cán dập (Oats)","category":"FEED","requestedQuantity":300.0,"unit":"kg","reason":"Tồn kho chỉ còn 120 kg (dưới ngưỡng an toàn 200 kg), đủ dùng trong 3 ngày tới cho lịch tập đua. Đề xuất nhập thêm 300 kg.","proposedByUserId":"48772A9D-9DA0-44A8-90F2-0459B3ADB768","proposedByName":"nguyen ba dung","proposedAt":"2026-10-01T16:00:00Z","reviewedByUserId":null,"reviewedByName":null,"reviewedAt":null,"reviewNotes":null,"referencePrice":38000.0,"estimatedCost":11400000.0}',
        updated_at = SYSDATETIMEOFFSET()
    WHERE id = 'B2020000-0000-0000-0000-000000000001';
END;

PRINT 'Seed inventory completed/updated successfully.';
