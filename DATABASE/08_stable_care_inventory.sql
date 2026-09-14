USE [racehorse];
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
GO

/* FR13 */
IF OBJECT_ID(N'dbo.stables', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.stables (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_stables PRIMARY KEY,
        name NVARCHAR(150) NOT NULL,
        location NVARCHAR(300) NULL,
        is_active BIT NOT NULL CONSTRAINT df_stables_is_active DEFAULT 1,
        CONSTRAINT uq_stables_name UNIQUE (name)
    );
END;
GO

IF OBJECT_ID(N'dbo.stalls', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.stalls (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_stalls PRIMARY KEY,
        stable_id UNIQUEIDENTIFIER NOT NULL,
        stall_code VARCHAR(50) NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_stalls_is_active DEFAULT 1,
        CONSTRAINT fk_stalls_stable FOREIGN KEY (stable_id) REFERENCES dbo.stables(id),
        CONSTRAINT uq_stalls_code UNIQUE (stable_id, stall_code)
    );
END;
GO

IF OBJECT_ID(N'dbo.horse_stall_assignments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_stall_assignments (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_stall_assignments PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        stall_id UNIQUEIDENTIFIER NOT NULL,
        assigned_from DATETIMEOFFSET NOT NULL,
        assigned_until DATETIMEOFFSET NULL,
        assigned_by_user_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT fk_stall_assignments_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_stall_assignments_stall FOREIGN KEY (stall_id) REFERENCES dbo.stalls(id),
        CONSTRAINT fk_stall_assignments_user FOREIGN KEY (assigned_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_stall_assignments_dates CHECK (assigned_until IS NULL OR assigned_from < assigned_until)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'uq_stall_assignments_current_horse' AND object_id=OBJECT_ID(N'dbo.horse_stall_assignments'))
    CREATE UNIQUE INDEX uq_stall_assignments_current_horse ON dbo.horse_stall_assignments(horse_id) WHERE assigned_until IS NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'uq_stall_assignments_current_stall' AND object_id=OBJECT_ID(N'dbo.horse_stall_assignments'))
    CREATE UNIQUE INDEX uq_stall_assignments_current_stall ON dbo.horse_stall_assignments(stall_id) WHERE assigned_until IS NULL;
GO

IF OBJECT_ID(N'dbo.horse_daily_routines', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_daily_routines (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_daily_routines PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        activity_name NVARCHAR(200) NOT NULL,
        start_time TIME NOT NULL,
        end_time TIME NOT NULL,
        effective_from DATE NOT NULL,
        effective_until DATE NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_daily_routines_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_daily_routines_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_daily_routines_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_daily_routines_time CHECK (start_time < end_time),
        CONSTRAINT ck_daily_routines_dates CHECK (effective_until IS NULL OR effective_from <= effective_until)
    );
    CREATE INDEX idx_daily_routines_horse_time ON dbo.horse_daily_routines(horse_id, effective_from, start_time);
END;
GO

/* FR17 master data is created before FR14 because meal items reference it. */
IF OBJECT_ID(N'dbo.inventory_categories', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.inventory_categories (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_inventory_categories PRIMARY KEY,
        code VARCHAR(50) NOT NULL,
        name NVARCHAR(150) NOT NULL,
        category_group VARCHAR(20) NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_inventory_categories_active DEFAULT 1,
        CONSTRAINT uq_inventory_categories_code UNIQUE (code),
        CONSTRAINT ck_inventory_categories_group CHECK (category_group IN ('FEED', 'MEDICINE', 'EQUIPMENT'))
    );
END;
GO

IF OBJECT_ID(N'dbo.inventory_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.inventory_items (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_inventory_items PRIMARY KEY,
        category_id UNIQUEIDENTIFIER NOT NULL,
        sku VARCHAR(80) NOT NULL,
        name NVARCHAR(200) NOT NULL,
        unit VARCHAR(50) NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_inventory_items_active DEFAULT 1,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_inventory_items_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_inventory_items_updated_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_inventory_items_category FOREIGN KEY (category_id) REFERENCES dbo.inventory_categories(id),
        CONSTRAINT uq_inventory_items_sku UNIQUE (sku)
    );
END;
GO

/* FR14 versioned nutrition plans. */
IF OBJECT_ID(N'dbo.nutrition_plans', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.nutrition_plans (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_nutrition_plans PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        version_number INT NOT NULL,
        valid_from DATE NOT NULL,
        valid_until DATE NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        approved_by_user_id UNIQUEIDENTIFIER NOT NULL,
        approved_at DATETIMEOFFSET NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_nutrition_plans_created_at DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_nutrition_plans_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_nutrition_plans_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_nutrition_plans_approver FOREIGN KEY (approved_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT uq_nutrition_plans_version UNIQUE (horse_id, version_number),
        CONSTRAINT ck_nutrition_plans_version CHECK (version_number > 0),
        CONSTRAINT ck_nutrition_plans_dates CHECK (valid_until IS NULL OR valid_from <= valid_until)
    );
    CREATE INDEX idx_nutrition_plans_current ON dbo.nutrition_plans(horse_id, valid_from DESC, valid_until);
END;
GO

IF OBJECT_ID(N'dbo.nutrition_plan_meals', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.nutrition_plan_meals (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_nutrition_plan_meals PRIMARY KEY,
        nutrition_plan_id UNIQUEIDENTIFIER NOT NULL,
        meal_time TIME NOT NULL,
        meal_name NVARCHAR(100) NULL,
        CONSTRAINT fk_nutrition_plan_meals_plan FOREIGN KEY (nutrition_plan_id) REFERENCES dbo.nutrition_plans(id),
        CONSTRAINT uq_nutrition_plan_meals_time UNIQUE (nutrition_plan_id, meal_time)
    );
END;
GO

IF OBJECT_ID(N'dbo.nutrition_meal_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.nutrition_meal_items (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_nutrition_meal_items PRIMARY KEY,
        meal_id UNIQUEIDENTIFIER NOT NULL,
        inventory_item_id UNIQUEIDENTIFIER NOT NULL,
        quantity DECIMAL(12,3) NOT NULL,
        supplement_notes NVARCHAR(1000) NULL,
        CONSTRAINT fk_nutrition_meal_items_meal FOREIGN KEY (meal_id) REFERENCES dbo.nutrition_plan_meals(id),
        CONSTRAINT fk_nutrition_meal_items_item FOREIGN KEY (inventory_item_id) REFERENCES dbo.inventory_items(id),
        CONSTRAINT uq_nutrition_meal_items_item UNIQUE (meal_id, inventory_item_id),
        CONSTRAINT ck_nutrition_meal_items_quantity CHECK (quantity > 0)
    );
END;
GO

/* FR15 */
IF OBJECT_ID(N'dbo.daily_tasks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.daily_tasks (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_daily_tasks PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        assigned_staff_id UNIQUEIDENTIFIER NOT NULL,
        training_schedule_id UNIQUEIDENTIFIER NULL,
        task_name NVARCHAR(300) NOT NULL,
        scheduled_start DATETIMEOFFSET NOT NULL,
        scheduled_end DATETIMEOFFSET NOT NULL,
        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
        completed_by_user_id UNIQUEIDENTIFIER NULL,
        completed_at DATETIMEOFFSET NULL,
        completion_notes NVARCHAR(2000) NULL,
        cancelled_at DATETIMEOFFSET NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_daily_tasks_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_daily_tasks_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_daily_tasks_version DEFAULT 0,
        CONSTRAINT fk_daily_tasks_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_daily_tasks_staff FOREIGN KEY (assigned_staff_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_daily_tasks_schedule FOREIGN KEY (training_schedule_id) REFERENCES dbo.training_schedules(id),
        CONSTRAINT fk_daily_tasks_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_daily_tasks_completer FOREIGN KEY (completed_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_daily_tasks_dates CHECK (scheduled_start < scheduled_end),
        CONSTRAINT ck_daily_tasks_completion CHECK (
            (completed_at IS NULL AND completed_by_user_id IS NULL)
            OR (completed_at IS NOT NULL AND completed_by_user_id IS NOT NULL))
    );
    CREATE INDEX idx_daily_tasks_staff_date ON dbo.daily_tasks(assigned_staff_id, scheduled_start, completed_at);
    CREATE INDEX idx_daily_tasks_horse_date ON dbo.daily_tasks(horse_id, scheduled_start);
END;
GO

IF OBJECT_ID(N'dbo.daily_task_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.daily_task_files (
        daily_task_id UNIQUEIDENTIFIER NOT NULL,
        stored_file_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT pk_daily_task_files PRIMARY KEY (daily_task_id, stored_file_id),
        CONSTRAINT fk_daily_task_files_task FOREIGN KEY (daily_task_id) REFERENCES dbo.daily_tasks(id),
        CONSTRAINT fk_daily_task_files_file FOREIGN KEY (stored_file_id) REFERENCES dbo.stored_files(id)
    );
END;
GO

/* FR16 */
IF OBJECT_ID(N'dbo.incident_reports', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.incident_reports (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_incident_reports PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        reported_by_user_id UNIQUEIDENTIFIER NOT NULL,
        incident_type NVARCHAR(150) NOT NULL,
        description NVARCHAR(4000) NOT NULL,
        submitted_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT fk_incident_reports_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
        CONSTRAINT fk_incident_reports_reporter FOREIGN KEY (reported_by_user_id) REFERENCES dbo.users(id)
    );
    CREATE INDEX idx_incident_reports_horse_time ON dbo.incident_reports(horse_id, submitted_at DESC);
END;
GO

IF OBJECT_ID(N'dbo.incident_report_files', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.incident_report_files (
        incident_report_id UNIQUEIDENTIFIER NOT NULL,
        stored_file_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT pk_incident_report_files PRIMARY KEY (incident_report_id, stored_file_id),
        CONSTRAINT fk_incident_report_files_report FOREIGN KEY (incident_report_id) REFERENCES dbo.incident_reports(id),
        CONSTRAINT fk_incident_report_files_file FOREIGN KEY (stored_file_id) REFERENCES dbo.stored_files(id)
    );
END;
GO

/* FR17 */
IF OBJECT_ID(N'dbo.storage_areas', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.storage_areas (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_storage_areas PRIMARY KEY,
        stable_id UNIQUEIDENTIFIER NULL,
        name NVARCHAR(150) NOT NULL,
        is_active BIT NOT NULL CONSTRAINT df_storage_areas_active DEFAULT 1,
        CONSTRAINT fk_storage_areas_stable FOREIGN KEY (stable_id) REFERENCES dbo.stables(id),
        CONSTRAINT uq_storage_areas_name UNIQUE (name)
    );
END;
GO

IF OBJECT_ID(N'dbo.storage_area_staff', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.storage_area_staff (
        storage_area_id UNIQUEIDENTIFIER NOT NULL,
        staff_user_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT pk_storage_area_staff PRIMARY KEY (storage_area_id, staff_user_id),
        CONSTRAINT fk_storage_area_staff_area FOREIGN KEY (storage_area_id) REFERENCES dbo.storage_areas(id),
        CONSTRAINT fk_storage_area_staff_user FOREIGN KEY (staff_user_id) REFERENCES dbo.users(id)
    );
END;
GO

IF OBJECT_ID(N'dbo.inventory_balances', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.inventory_balances (
        storage_area_id UNIQUEIDENTIFIER NOT NULL,
        inventory_item_id UNIQUEIDENTIFIER NOT NULL,
        current_quantity DECIMAL(14,3) NOT NULL,
        last_counted_at DATETIMEOFFSET NOT NULL,
        last_counted_by_user_id UNIQUEIDENTIFIER NOT NULL,
        version BIGINT NOT NULL CONSTRAINT df_inventory_balances_version DEFAULT 0,
        CONSTRAINT pk_inventory_balances PRIMARY KEY (storage_area_id, inventory_item_id),
        CONSTRAINT fk_inventory_balances_area FOREIGN KEY (storage_area_id) REFERENCES dbo.storage_areas(id),
        CONSTRAINT fk_inventory_balances_item FOREIGN KEY (inventory_item_id) REFERENCES dbo.inventory_items(id),
        CONSTRAINT fk_inventory_balances_user FOREIGN KEY (last_counted_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_inventory_balances_quantity CHECK (current_quantity >= 0)
    );
END;
GO

IF OBJECT_ID(N'dbo.inventory_transactions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.inventory_transactions (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_inventory_transactions PRIMARY KEY,
        storage_area_id UNIQUEIDENTIFIER NOT NULL,
        inventory_item_id UNIQUEIDENTIFIER NOT NULL,
        transaction_type VARCHAR(15) NOT NULL,
        quantity_change DECIMAL(14,3) NOT NULL,
        resulting_quantity DECIMAL(14,3) NOT NULL,
        recorded_by_user_id UNIQUEIDENTIFIER NOT NULL,
        description NVARCHAR(1000) NULL,
        recorded_at DATETIMEOFFSET NOT NULL,
        CONSTRAINT fk_inventory_transactions_area FOREIGN KEY (storage_area_id) REFERENCES dbo.storage_areas(id),
        CONSTRAINT fk_inventory_transactions_item FOREIGN KEY (inventory_item_id) REFERENCES dbo.inventory_items(id),
        CONSTRAINT fk_inventory_transactions_user FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_inventory_transactions_type CHECK (transaction_type IN ('COUNT', 'RECEIPT', 'USAGE', 'ADJUSTMENT')),
        CONSTRAINT ck_inventory_transactions_result CHECK (resulting_quantity >= 0)
    );
    CREATE INDEX idx_inventory_transactions_item_time ON dbo.inventory_transactions(storage_area_id, inventory_item_id, recorded_at DESC);
END;
GO

IF OBJECT_ID(N'dbo.replenishment_requests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.replenishment_requests (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_replenishment_requests PRIMARY KEY,
        storage_area_id UNIQUEIDENTIFIER NOT NULL,
        inventory_item_id UNIQUEIDENTIFIER NOT NULL,
        requested_quantity DECIMAL(14,3) NOT NULL,
        urgency VARCHAR(10) NOT NULL,
        requested_by_user_id UNIQUEIDENTIFIER NOT NULL,
        submitted_at DATETIMEOFFSET NOT NULL,
        reviewed_by_user_id UNIQUEIDENTIFIER NULL,
        reviewed_at DATETIMEOFFSET NULL,
        review_notes NVARCHAR(2000) NULL,
        CONSTRAINT fk_replenishment_requests_area FOREIGN KEY (storage_area_id) REFERENCES dbo.storage_areas(id),
        CONSTRAINT fk_replenishment_requests_item FOREIGN KEY (inventory_item_id) REFERENCES dbo.inventory_items(id),
        CONSTRAINT fk_replenishment_requests_requester FOREIGN KEY (requested_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_replenishment_requests_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_replenishment_requests_quantity CHECK (requested_quantity > 0),
        CONSTRAINT ck_replenishment_requests_urgency CHECK (urgency IN ('LOW', 'NORMAL', 'HIGH')),
        CONSTRAINT ck_replenishment_requests_review CHECK (
            (reviewed_at IS NULL AND reviewed_by_user_id IS NULL)
            OR (reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL))
    );
    CREATE INDEX idx_replenishment_requests_queue ON dbo.replenishment_requests(reviewed_at, urgency, submitted_at);
END;
GO
