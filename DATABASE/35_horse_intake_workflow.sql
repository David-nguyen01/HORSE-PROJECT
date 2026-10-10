/* Separate payment entitlement from the staff -> vet -> stall intake workflow. */
USE [racehorse_local];
GO

IF COL_LENGTH(N'dbo.horses', N'intake_status') IS NULL
BEGIN
    ALTER TABLE dbo.horses ADD intake_status VARCHAR(20) NOT NULL
        CONSTRAINT df_horses_intake_status DEFAULT 'PENDING_WAITING_ROOM';
END;
GO

/* Preserve active historical placements and medical progress. Legacy workflow values
   in payment_status came from a horse that had already been PAID. */
UPDATE h
SET intake_status = CASE
    WHEN EXISTS (
        SELECT 1 FROM dbo.module_records m
        WHERE m.horse_id = h.id AND m.module_code = 'STABLE_CARE'
          AND m.record_type = 'STALL_ASSIGNMENT' AND m.status = 'ACTIVE'
    ) THEN 'STABLED'
    WHEN h.payment_status = 'PENDING_TRAINING' OR h.diagnosing_vet_user_id IS NOT NULL THEN 'PENDING_STALL'
    WHEN h.payment_status = 'IN_MEDICAL' THEN 'IN_MEDICAL'
    ELSE h.intake_status END,
    payment_status = CASE WHEN h.payment_status IN ('IN_MEDICAL', 'PENDING_TRAINING', 'TRAINING', 'ACTIVE')
                          THEN 'PAID' ELSE h.payment_status END
FROM dbo.horses h
WHERE h.intake_status = 'PENDING_WAITING_ROOM'
   OR h.payment_status IN ('IN_MEDICAL', 'PENDING_TRAINING', 'TRAINING', 'ACTIVE');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = N'ck_horses_intake_status' AND parent_object_id = OBJECT_ID(N'dbo.horses'))
BEGIN
    ALTER TABLE dbo.horses WITH CHECK ADD CONSTRAINT ck_horses_intake_status
        CHECK (intake_status IN ('PENDING_WAITING_ROOM', 'WAITING_FOR_VET', 'IN_MEDICAL', 'PENDING_STALL', 'STABLED'));
END;
GO
