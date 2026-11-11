USE [racehorse_local];
GO

;WITH admissions AS (
    SELECT horse_id, user_id,
           ROW_NUMBER() OVER (PARTITION BY horse_id ORDER BY created_at DESC, id DESC) AS sequence_number
    FROM dbo.module_records
    WHERE module_code = 'STABLE_CARE'
      AND record_type = 'WAITING_ROOM_ADMISSION'
      AND horse_id IS NOT NULL
      AND user_id IS NOT NULL
)
UPDATE h
SET caretaker_user_id = a.user_id,
    updated_at = SYSDATETIMEOFFSET(),
    version = version + 1
FROM dbo.horses h
JOIN admissions a ON a.horse_id = h.id AND a.sequence_number = 1
WHERE h.caretaker_user_id IS NULL;
GO
