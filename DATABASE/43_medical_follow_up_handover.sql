USE racehorse_local;
GO
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
GO

-- One open follow-up visit per horse. The existing module_records table stores the case.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.module_records')
               AND name=N'ux_medical_follow_up_open_horse')
BEGIN
    CREATE UNIQUE INDEX ux_medical_follow_up_open_horse
        ON dbo.module_records(horse_id)
        WHERE module_code='MEDICAL' AND record_type='MEDICAL_FOLLOW_UP'
          AND status IN ('ASSIGNED','HANDOVER_REQUESTED') AND horse_id IS NOT NULL;
END;
GO

-- Preserve upcoming/overdue follow-ups already recorded in the latest examination.
;WITH latest_exam AS (
    SELECT e.id, e.horse_id, e.user_id,
           TRY_CONVERT(date, JSON_VALUE(e.data_json,'$.followUpDate')) AS due_date,
           ROW_NUMBER() OVER (PARTITION BY e.horse_id ORDER BY e.created_at DESC, e.id DESC) AS rn
    FROM dbo.module_records e
    WHERE e.module_code='MEDICAL' AND e.record_type='MEDICAL_EXAMINATION'
)
INSERT INTO dbo.module_records
    (id,module_code,record_type,source_table,horse_id,user_id,status,event_date,data_json)
SELECT NEWID(),'MEDICAL','MEDICAL_FOLLOW_UP','medical_examinations',
       e.horse_id,e.user_id,'ASSIGNED',e.due_date,
       CONCAT(N'{"sourceExaminationId":"',CONVERT(varchar(36),e.id),
              N'","assignedVeterinarianId":"',CONVERT(varchar(36),e.user_id),N'"}')
FROM latest_exam e
JOIN dbo.horses h ON h.id=e.horse_id AND h.archived_at IS NULL
WHERE e.rn=1 AND e.due_date IS NOT NULL AND e.user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM dbo.module_records f WHERE f.horse_id=e.horse_id
                  AND f.module_code='MEDICAL' AND f.record_type='MEDICAL_FOLLOW_UP');
GO
