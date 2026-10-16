/* =========================================================================
   40_backfill_stabled_horses_daily_routines.sql
   Target Database: racehorse_local
   Purpose:
     Idempotent backfill of default non-training daily activity routines
     (FR13 / SS-006) for all stabled racehorses currently lacking schedules.
     Applies:
       - TEMPLATE_DEFAULT_HEALTHY (Grooming, Turnout, Rest, Walking) for healthy horses
       - TEMPLATE_STALL_REST (Stall rest, Ice boots, Rest, Gentle grooming) for
         horses with injury, training locks, or medical restrictions (strictly NO turnout).
     Preserves existing schedules on already configured horses.
   ========================================================================= */

USE [racehorse_local];
GO
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

DECLARE @adminId UNIQUEIDENTIFIER = '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11';

DECLARE @horseId UNIQUEIDENTIFIER;
DECLARE @horseCode NVARCHAR(50);
DECLARE @healthStatus NVARCHAR(50);
DECLARE @isRestricted BIT;

DECLARE horse_cursor CURSOR LOCAL FAST_FORWARD FOR
SELECT
    h.id,
    h.code,
    h.health_status,
    CASE
        WHEN h.health_status IN ('INJURED', 'QUARANTINED', 'MONITOR') THEN 1
        WHEN EXISTS (
            SELECT 1 FROM dbo.training_locks tl
            WHERE tl.horse_id = h.id AND tl.released_at IS NULL
        ) THEN 1
        ELSE 0
    END AS is_restricted
FROM dbo.horses h
WHERE h.stable_location IS NOT NULL
  AND h.stable_location <> ''
  AND h.stable_location <> N'Khu chuồng trại'
  AND h.intake_status = 'STABLED'
  AND NOT EXISTS (
      SELECT 1 FROM dbo.module_records mr
      WHERE mr.module_code = 'STABLE_CARE'
        AND mr.record_type = 'HORSE_DAILY_ROUTINE'
        AND mr.horse_id = h.id
        AND mr.status = 'ACTIVE'
  );

OPEN horse_cursor;
FETCH NEXT FROM horse_cursor INTO @horseId, @horseCode, @healthStatus, @isRestricted;

WHILE @@FETCH_STATUS = 0
BEGIN
    IF @isRestricted = 1
    BEGIN
        -- Apply TEMPLATE_STALL_REST
        INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, data_json)
        VALUES
        (NEWID(), 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'migration', @horseId, @adminId, 'ACTIVE',
         N'{"activityType":"STALL_REST","startTime":"07:00","endTime":"08:30","notes":"Tĩnh dưỡng và theo dõi y tế tại ô chuồng","isActive":true}'),
        (NEWID(), 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'migration', @horseId, @adminId, 'ACTIVE',
         N'{"activityType":"ICE_BOOTS","startTime":"09:30","endTime":"10:30","notes":"Chườm đá lạnh & ngâm chân trị liệu gân khớp","isActive":true}'),
        (NEWID(), 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'migration', @horseId, @adminId, 'ACTIVE',
         N'{"activityType":"REST","startTime":"11:30","endTime":"13:30","notes":"Nghỉ ngơi dưỡng thương tại chuồng","isActive":true}'),
        (NEWID(), 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'migration', @horseId, @adminId, 'ACTIVE',
         N'{"activityType":"GROOMING","startTime":"16:00","endTime":"17:00","notes":"Chải lông nhẹ nhàng & kiểm tra phản xạ vận động","isActive":true}');

        PRINT N'Backfilled STALL_REST routines for restricted horse: ' + @horseCode;
    END
    ELSE
    BEGIN
        -- Apply TEMPLATE_DEFAULT_HEALTHY
        INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, data_json)
        VALUES
        (NEWID(), 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'migration', @horseId, @adminId, 'ACTIVE',
         N'{"activityType":"GROOMING","startTime":"06:00","endTime":"07:00","notes":"Chải lông & vệ sinh thân thể buổi sáng","isActive":true}'),
        (NEWID(), 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'migration', @horseId, @adminId, 'ACTIVE',
         N'{"activityType":"TURNOUT","startTime":"07:30","endTime":"09:00","notes":"Thả bãi cỏ dạo chơi tự do buổi sáng","isActive":true}'),
        (NEWID(), 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'migration', @horseId, @adminId, 'ACTIVE',
         N'{"activityType":"REST","startTime":"11:30","endTime":"13:30","notes":"Thời gian nghỉ trưa tĩnh tại ô chuồng","isActive":true}'),
        (NEWID(), 'STABLE_CARE', 'HORSE_DAILY_ROUTINE', 'migration', @horseId, @adminId, 'ACTIVE',
         N'{"activityType":"WALKING","startTime":"16:00","endTime":"17:00","notes":"Dắt đi dạo nhẹ nhàng thư giãn cơ bắp buổi chiều","isActive":true}');

        PRINT N'Backfilled DEFAULT_HEALTHY routines for healthy horse: ' + @horseCode;
    END;

    FETCH NEXT FROM horse_cursor INTO @horseId, @horseCode, @healthStatus, @isRestricted;
END;

CLOSE horse_cursor;
DEALLOCATE horse_cursor;
GO
