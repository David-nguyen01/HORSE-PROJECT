USE [racehorse];
GO

-- 1. Xóa index cũ trên cột name nếu tồn tại
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_horses_name' AND object_id = OBJECT_ID('dbo.horses'))
BEGIN
    DROP INDEX idx_horses_name ON dbo.horses;
END;
GO

-- 2. Chuyển đổi các cột văn bản trong dbo.horses sang NVARCHAR để hỗ trợ tiếng Việt có dấu
ALTER TABLE dbo.horses ALTER COLUMN name NVARCHAR(150) NOT NULL;
ALTER TABLE dbo.horses ALTER COLUMN color_markings NVARCHAR(500) NOT NULL;
ALTER TABLE dbo.horses ALTER COLUMN breed NVARCHAR(100) NOT NULL;
ALTER TABLE dbo.horses ALTER COLUMN stable_location NVARCHAR(150) NOT NULL;
GO

-- 3. Tạo lại index idx_horses_name với kiểu NVARCHAR
CREATE INDEX idx_horses_name ON dbo.horses (name);
GO

-- 4. Chuyển đổi các cột văn bản trong dbo.race_results sang NVARCHAR
ALTER TABLE dbo.race_results ALTER COLUMN race_name NVARCHAR(200) NOT NULL;
ALTER TABLE dbo.race_results ALTER COLUMN racecourse NVARCHAR(200) NOT NULL;
ALTER TABLE dbo.race_results ALTER COLUMN jockey NVARCHAR(150) NOT NULL;
ALTER TABLE dbo.race_results ALTER COLUMN track_condition NVARCHAR(100) NULL;
GO

-- 5. Cập nhật dữ liệu ngựa hiện tại đang bị lỗi dấu hỏi ? do VARCHAR trước đó
UPDATE dbo.horses 
SET name = N'Thắng', 
    color_markings = N'Trắng', 
    breed = N'Ebula',
    updated_at = SYSDATETIMEOFFSET()
WHERE code = 'H01';
GO
