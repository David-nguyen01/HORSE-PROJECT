-- 13_horse_service_payment.sql
-- Bổ sung quản lý thanh toán phí dịch vụ và quyền bảo lãnh dịch vụ của Club Manager

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'horses' AND COLUMN_NAME = 'payment_status'
)
BEGIN
    ALTER TABLE horses ADD payment_status VARCHAR(20) NOT NULL CONSTRAINT DF_horses_payment_status DEFAULT 'UNPAID';
END;

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'horses' AND COLUMN_NAME = 'service_override'
)
BEGIN
    ALTER TABLE horses ADD service_override BIT NOT NULL CONSTRAINT DF_horses_service_override DEFAULT 0;
END;

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'horses' AND COLUMN_NAME = 'override_reason'
)
BEGIN
    ALTER TABLE horses ADD override_reason NVARCHAR(300) NULL;
END;

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'horses' AND COLUMN_NAME = 'paid_until'
)
BEGIN
    ALTER TABLE horses ADD paid_until DATE NULL;
END;
