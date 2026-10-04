-- 15_daily_routine_care_tasks.sql
-- Daily Routine Tasks & Care Tasks table schema for STABLE_STAFF

IF OBJECT_ID(N'dbo.care_routine_definitions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.care_routine_definitions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_care_routine_definitions PRIMARY KEY,
        code VARCHAR(50) NOT NULL CONSTRAINT uq_care_routine_definitions_code UNIQUE,
        title NVARCHAR(255) NOT NULL,
        description NVARCHAR(MAX) NULL,
        task_type VARCHAR(50) NOT NULL,
        scheduled_time TIME NOT NULL,
        scope VARCHAR(20) NOT NULL CONSTRAINT df_care_routine_scope DEFAULT 'FACILITY',
        is_active BIT NOT NULL CONSTRAINT df_care_routine_is_active DEFAULT 1,
        sort_order INT NOT NULL CONSTRAINT df_care_routine_sort_order DEFAULT 0,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_care_routine_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_care_routine_updated_at DEFAULT SYSDATETIMEOFFSET()
    );
END;

IF OBJECT_ID(N'dbo.care_tasks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.care_tasks (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_care_tasks PRIMARY KEY,
        routine_definition_id UNIQUEIDENTIFIER NULL CONSTRAINT fk_care_tasks_routine REFERENCES dbo.care_routine_definitions(id),
        task_category VARCHAR(20) NOT NULL CONSTRAINT df_care_tasks_category DEFAULT 'ROUTINE',
        scope VARCHAR(20) NOT NULL CONSTRAINT df_care_tasks_scope DEFAULT 'FACILITY',
        horse_id UNIQUEIDENTIFIER NULL CONSTRAINT fk_care_tasks_horse REFERENCES dbo.horses(id),
        title NVARCHAR(255) NOT NULL,
        description NVARCHAR(MAX) NULL,
        task_type VARCHAR(50) NOT NULL,
        scheduled_date DATE NOT NULL,
        scheduled_time TIME NOT NULL,
        status VARCHAR(30) NOT NULL CONSTRAINT df_care_tasks_status DEFAULT 'PENDING',
        assigned_staff_id UNIQUEIDENTIFIER NULL CONSTRAINT fk_care_tasks_staff REFERENCES dbo.users(id),
        completed_at DATETIMEOFFSET NULL,
        completed_by_user_id UNIQUEIDENTIFIER NULL CONSTRAINT fk_care_tasks_completed_by REFERENCES dbo.users(id),
        completed_by_name NVARCHAR(100) NULL,
        completion_notes NVARCHAR(MAX) NULL,
        photo_url NVARCHAR(1000) NULL,
        photo_bytes VARBINARY(MAX) NULL,
        photo_content_type VARCHAR(100) NULL,
        created_by_user_id UNIQUEIDENTIFIER NULL CONSTRAINT fk_care_tasks_created_by REFERENCES dbo.users(id),
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_care_tasks_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_care_tasks_updated_at DEFAULT SYSDATETIMEOFFSET()
    );

    CREATE UNIQUE NONCLUSTERED INDEX uq_care_tasks_facility_routine_instance
    ON dbo.care_tasks (routine_definition_id, scheduled_date)
    WHERE routine_definition_id IS NOT NULL AND horse_id IS NULL;

    CREATE UNIQUE NONCLUSTERED INDEX uq_care_tasks_horse_routine_instance
    ON dbo.care_tasks (routine_definition_id, scheduled_date, horse_id)
    WHERE routine_definition_id IS NOT NULL AND horse_id IS NOT NULL;

    CREATE NONCLUSTERED INDEX idx_care_tasks_date_status
    ON dbo.care_tasks (scheduled_date, status);
END;

-- Seed exactly 5 baseline routine definitions if not already present
IF NOT EXISTS (SELECT 1 FROM dbo.care_routine_definitions WHERE code = 'ROUTINE_MORNING_STABLE_CHECK')
BEGIN
    INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order)
    VALUES (
        NEWID(),
        'ROUTINE_MORNING_STABLE_CHECK',
        N'Kiểm tra an toàn khu vực chuồng & thông gió',
        N'Kiểm tra tổng quan khu vực chuồng nuôi, chốt khóa an toàn, độ thông thoáng và đèn chiếu sáng đầu ca sáng.',
        'OTHER',
        '07:00:00',
        'FACILITY',
        1,
        10
    );
END;

IF NOT EXISTS (SELECT 1 FROM dbo.care_routine_definitions WHERE code = 'ROUTINE_WATER_TROUGH_CHECK')
BEGIN
    INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order)
    VALUES (
        NEWID(),
        'ROUTINE_WATER_TROUGH_CHECK',
        N'Kiểm tra & vệ sinh hệ thống máng nước tự động',
        N'Kiểm tra van phao tự động, áp lực nước và cọ rửa máng uống đảm bảo nguồn nước sạch cho toàn bộ chuồng trại.',
        'FEEDING',
        '08:00:00',
        'FACILITY',
        1,
        20
    );
END;

IF NOT EXISTS (SELECT 1 FROM dbo.care_routine_definitions WHERE code = 'ROUTINE_MORNING_MUCKING')
BEGIN
    INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order)
    VALUES (
        NEWID(),
        'ROUTINE_MORNING_MUCKING',
        N'Dọn phân chuồng & thay chất độn chuồng ca sáng',
        N'Thu gom phân, thay lớp rơm/mùn cưa ẩm ướt và rải vôi bột khử khuẩn lối đi hành lang chuồng.',
        'MUCKING_OUT',
        '09:30:00',
        'FACILITY',
        1,
        30
    );
END;

IF NOT EXISTS (SELECT 1 FROM dbo.care_routine_definitions WHERE code = 'ROUTINE_AFTERNOON_CORRIDOR_CLEAN')
BEGIN
    INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order)
    VALUES (
        NEWID(),
        'ROUTINE_AFTERNOON_CORRIDOR_CLEAN',
        N'Vệ sinh khử trùng lối đi & khu vực chuồng ca chiều',
        N'Quét dọn hành lang chuồng, rãnh thoát nước và thu gom rác thải đúng quy định.',
        'MUCKING_OUT',
        '14:30:00',
        'FACILITY',
        1,
        40
    );
END;

IF NOT EXISTS (SELECT 1 FROM dbo.care_routine_definitions WHERE code = 'ROUTINE_EVENING_LOCKUP_CHECK')
BEGIN
    INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order)
    VALUES (
        NEWID(),
        'ROUTINE_EVENING_LOCKUP_CHECK',
        N'Kiểm tra khóa chốt an toàn & ổn định chuồng cuối ngày',
        N'Kiểm tra khóa chốt tất cả các chuồng, lượng nước uống ban đêm và ổn định đàn ngựa cuối ngày.',
        'OTHER',
        '17:00:00',
        'FACILITY',
        1,
        50
    );
END;
