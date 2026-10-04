-- 17_fix_inventory_unicode.sql
-- Fix UTF-8 encoding (mojibake) for sample inventory items and replenishment request
-- Ensure sqlcmd is executed with -f 65001

SET NOCOUNT ON;

-- 1. Cỏ khô Alfalfa (Mỹ vụ mới)
UPDATE dbo.module_records
SET data_json = N'{"itemName":"Cỏ khô Alfalfa (Mỹ vụ mới)","category":"FEED","unit":"kg","quantityOnHand":850.0,"minThreshold":300.0,"location":"Kho Thức Ăn A - Dãy 1","description":"Cỏ linh lăng sấy khô giàu đạm & canxi cho ngựa đua","referencePrice":45000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'A1010000-0000-0000-0000-000000000001';

-- 2. Yến mạch nguyên hạt cán dập (Oats)
UPDATE dbo.module_records
SET data_json = N'{"itemName":"Yến mạch nguyên hạt cán dập (Oats)","category":"FEED","unit":"kg","quantityOnHand":120.0,"minThreshold":200.0,"location":"Kho Thức Ăn A - Dãy 2","description":"Yến mạch giàu tinh bột tiêu hóa nhanh cho ngựa luyện tập cường độ cao","referencePrice":38000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'A1010000-0000-0000-0000-000000000002';

-- 3. Cám viên dinh dưỡng cao năng lượng
UPDATE dbo.module_records
SET data_json = N'{"itemName":"Cám viên dinh dưỡng cao năng lượng","category":"FEED","unit":"bao (25kg)","quantityOnHand":45.0,"minThreshold":20.0,"location":"Kho Thức Ăn B","description":"Hỗn hợp cám viên bổ sung khoáng và vitamin thiết yếu cho ngựa đua","referencePrice":480000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'A1010000-0000-0000-0000-000000000003';

-- 4. Gạc y tế vô trùng chuyên dụng
UPDATE dbo.module_records
SET data_json = N'{"itemName":"Gạc y tế vô trùng chuyên dụng","category":"MEDICATION","unit":"hộp (100 miếng)","quantityOnHand":8.0,"minThreshold":15.0,"location":"Tủ y tế khu chuồng trại","description":"Gạc tiệt trùng thấm hút xử lý vết thương ngoài da và vết cắt","referencePrice":150000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'A1010000-0000-0000-0000-000000000004';

-- 5. Băng cuốn co giãn thú y (Vet Wrap 10cm)
UPDATE dbo.module_records
SET data_json = N'{"itemName":"Băng cuốn co giãn thú y (Vet Wrap 10cm)","category":"MEDICATION","unit":"cuộn","quantityOnHand":42.0,"minThreshold":25.0,"location":"Tủ y tế khu chuồng trại","description":"Băng dính tự co giãn cố định khớp chân và gân ngựa sau vận động","referencePrice":65000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'A1010000-0000-0000-0000-000000000005';

-- 6. Dung dịch sát trùng Povidone Iodine 10% (1L)
UPDATE dbo.module_records
SET data_json = N'{"itemName":"Dung dịch sát trùng Povidone Iodine 10% (1L)","category":"MEDICATION","unit":"chai","quantityOnHand":5.0,"minThreshold":10.0,"location":"Tủ y tế khu chuồng trại","description":"Dung dịch diệt khuẩn rửa sạch vết thương hở và sát trùng móng","referencePrice":180000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'A1010000-0000-0000-0000-000000000006';

-- 7. Chế phẩm điện giải & khoáng vi lượng
UPDATE dbo.module_records
SET data_json = N'{"itemName":"Chế phẩm điện giải & khoáng vi lượng","category":"SUPPLEMENT","unit":"hũ (2kg)","quantityOnHand":18.0,"minThreshold":10.0,"location":"Kho dinh dưỡng bổ sung","description":"Bù nước và muối khoáng chống kiệt sức sau các bài tập nặng","referencePrice":620000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'A1010000-0000-0000-0000-000000000007';

-- 8. Chất độn chuồng mùn cưa thông ép khối
UPDATE dbo.module_records
SET data_json = N'{"itemName":"Chất độn chuồng mùn cưa thông ép khối","category":"EQUIPMENT","unit":"khối (20kg)","quantityOnHand":80.0,"minThreshold":50.0,"location":"Kho đệm lót chuồng","description":"Mùn cưa gỗ thông khử mùi, hút ẩm cao, chống trơn trượt chuồng nuôi","referencePrice":110000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'A1010000-0000-0000-0000-000000000008';

-- 9. Dung dịch khử trùng chuồng trại sinh học (5L)
UPDATE dbo.module_records
SET data_json = N'{"itemName":"Dung dịch khử trùng chuồng trại sinh học (5L)","category":"EQUIPMENT","unit":"can","quantityOnHand":6.0,"minThreshold":12.0,"location":"Kho hóa chất vệ sinh","description":"Chế phẩm vi sinh diệt khuẩn, khử mùi amoniac trong chuồng trại","referencePrice":320000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'A1010000-0000-0000-0000-000000000009';

-- 10. Găng tay cao su y tế dùng một lần
UPDATE dbo.module_records
SET data_json = N'{"itemName":"Găng tay cao su y tế dùng một lần","category":"EQUIPMENT","unit":"hộp (100 chiếc)","quantityOnHand":25.0,"minThreshold":10.0,"location":"Tủ bảo hộ lao động","description":"Găng tay nitrile chuyên dụng cho nhân viên dọn chuồng và sơ cứu","referencePrice":120000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'A1010000-0000-0000-0000-000000000010';

-- 11. Xô nhựa chăm sóc và cho ăn chống va đập (20L)
UPDATE dbo.module_records
SET data_json = N'{"itemName":"Xô nhựa chăm sóc và cho ăn chống va đập (20L)","category":"EQUIPMENT","unit":"cái","quantityOnHand":16.0,"minThreshold":10.0,"location":"Kho dụng cụ chuồng trại","description":"Xô nhựa dẻo siêu bền chịu va đập cho ăn và tiếp nước","referencePrice":95000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'A1010000-0000-0000-0000-000000000011';

-- 12. Replenishment Request B2020000-0000-0000-0000-000000000001
UPDATE dbo.module_records
SET data_json = N'{"itemId":"A1010000-0000-0000-0000-000000000002","itemName":"Yến mạch nguyên hạt cán dập (Oats)","category":"FEED","requestedQuantity":300.0,"unit":"kg","reason":"Tồn kho chỉ còn 120 kg (dưới ngưỡng an toàn 200 kg), đủ dùng trong 3 ngày tới cho lịch tập đua. Đề xuất nhập thêm 300 kg.","proposedByUserId":"48772A9D-9DA0-44A8-90F2-0459B3ADB768","proposedByName":"nguyen ba dung","proposedAt":"2026-10-01T16:00:00Z","reviewedByUserId":null,"reviewedByName":null,"reviewedAt":null,"reviewNotes":null,"referencePrice":38000.0,"estimatedCost":11400000.0}',
    updated_at = SYSDATETIMEOFFSET()
WHERE id = 'B2020000-0000-0000-0000-000000000001';

-- 13. Delete temporary test replenishment record
DELETE FROM dbo.module_records
WHERE id = '9DD01AF0-8DE0-42A2-BACB-4903E7961CAD';

PRINT 'Inventory Unicode fix applied successfully.';
