USE racehorse_local;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- A monthly follow-up requires a recorded examination. Preserve the rows for
-- audit/history, but remove automatically created appointments for horses that
-- have never been examined. Their first real exam will create the next visit.
UPDATE f
SET status='CANCELLED',
    data_json=JSON_MODIFY(f.data_json,'$.cancelReason','NO_PRIOR_EXAMINATION'),
    updated_at=SYSDATETIMEOFFSET()
FROM dbo.module_records f
WHERE f.module_code='MEDICAL' AND f.record_type='MEDICAL_FOLLOW_UP'
  AND f.status IN ('ASSIGNED','HANDOVER_REQUESTED')
  AND f.source_table='medical_examinations'
  AND JSON_VALUE(f.data_json,'$.sourceExaminationId') IS NULL
  AND JSON_VALUE(f.data_json,'$.sourceExaminationDeleted') IS NULL
  AND JSON_VALUE(f.data_json,'$.visitType')='MONTHLY'
  AND NOT EXISTS (
      SELECT 1 FROM dbo.module_records e
      WHERE e.horse_id=f.horse_id AND e.module_code='MEDICAL'
        AND e.record_type='MEDICAL_EXAMINATION'
  );
GO
