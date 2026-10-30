USE racehorse_local;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- A previously deleted last examination may have left an open monthly visit.
-- Preserve its record for audit, but remove it from the actionable queue.
UPDATE f
SET status='CANCELLED',
    data_json=JSON_MODIFY(f.data_json,'$.cancelReason','NO_RECORDED_EXAMINATION'),
    updated_at=SYSDATETIMEOFFSET()
FROM dbo.module_records f
WHERE f.module_code='MEDICAL' AND f.record_type='MEDICAL_FOLLOW_UP'
  AND f.status IN ('ASSIGNED','HANDOVER_REQUESTED')
  AND NOT EXISTS (
      SELECT 1 FROM dbo.module_records e
      WHERE e.horse_id=f.horse_id AND e.module_code='MEDICAL'
        AND e.record_type='MEDICAL_EXAMINATION'
  );
GO
