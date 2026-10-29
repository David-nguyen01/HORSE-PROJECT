USE racehorse_local;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- Backfill one upcoming monthly visit for each horse with a primary veterinarian.
;WITH last_exam AS (
    SELECT e.horse_id, e.event_date,
           TRY_CONVERT(date, JSON_VALUE(e.data_json,'$.followUpDate')) AS requested_due,
           ROW_NUMBER() OVER (PARTITION BY e.horse_id ORDER BY e.event_date DESC,e.created_at DESC,e.id DESC) AS rn
    FROM dbo.module_records e
    WHERE e.module_code='MEDICAL' AND e.record_type='MEDICAL_EXAMINATION'
), due AS (
    SELECT h.id AS horse_id,h.diagnosing_vet_user_id AS vet_id,
           DATEADD(month,1,COALESCE(e.event_date,CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time'))) AS monthly_due,
           e.requested_due
    FROM dbo.horses h
    JOIN last_exam e ON e.horse_id=h.id AND e.rn=1
    WHERE h.archived_at IS NULL AND h.diagnosing_vet_user_id IS NOT NULL
      AND h.intake_status IN ('PENDING_STALL','STABLED')
)
INSERT INTO dbo.module_records
    (id,module_code,record_type,source_table,horse_id,user_id,status,event_date,data_json)
SELECT NEWID(),'MEDICAL','MEDICAL_FOLLOW_UP','medical_examinations',
       d.horse_id,d.vet_id,'ASSIGNED',
       CASE WHEN d.requested_due<d.monthly_due THEN d.requested_due ELSE d.monthly_due END,
       CONCAT(N'{"assignedVeterinarianId":"',CONVERT(varchar(36),d.vet_id),N'","visitType":"',
              CASE WHEN d.requested_due<d.monthly_due THEN 'CLINICAL' ELSE 'MONTHLY' END,N'"}')
FROM due d
WHERE NOT EXISTS (SELECT 1 FROM dbo.module_records f WHERE f.horse_id=d.horse_id
                  AND f.module_code='MEDICAL' AND f.record_type='MEDICAL_FOLLOW_UP'
                  AND f.status IN ('ASSIGNED','HANDOVER_REQUESTED'));
GO

-- Move later appointments forward if they would skip the monthly check.
;WITH last_exam AS (
    SELECT e.horse_id,e.event_date,
           ROW_NUMBER() OVER (PARTITION BY e.horse_id ORDER BY e.event_date DESC,e.created_at DESC,e.id DESC) AS rn
    FROM dbo.module_records e
    WHERE e.module_code='MEDICAL' AND e.record_type='MEDICAL_EXAMINATION'
)
UPDATE f SET event_date=DATEADD(month,1,e.event_date),
             data_json=JSON_MODIFY(f.data_json,'$.visitType','MONTHLY'),
             updated_at=SYSDATETIMEOFFSET()
FROM dbo.module_records f
JOIN last_exam e ON e.horse_id=f.horse_id AND e.rn=1
WHERE f.module_code='MEDICAL' AND f.record_type='MEDICAL_FOLLOW_UP'
  AND f.status='ASSIGNED' AND e.event_date IS NOT NULL
  AND f.event_date>DATEADD(month,1,e.event_date);
GO

-- Existing cases without a substitute acceptance belong to the primary veterinarian.
UPDATE f SET user_id=h.diagnosing_vet_user_id,
             data_json=JSON_MODIFY(f.data_json,'$.assignedVeterinarianId',
                        CONVERT(varchar(36),h.diagnosing_vet_user_id)),
             updated_at=SYSDATETIMEOFFSET()
FROM dbo.module_records f
JOIN dbo.horses h ON h.id=f.horse_id
WHERE f.module_code='MEDICAL' AND f.record_type='MEDICAL_FOLLOW_UP'
  AND f.status='ASSIGNED' AND h.diagnosing_vet_user_id IS NOT NULL
  AND f.user_id<>h.diagnosing_vet_user_id
  AND JSON_VALUE(f.data_json,'$.acceptedBy') IS NULL;
GO
