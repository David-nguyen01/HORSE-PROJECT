package com.racehorse.health.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InjuryRecoveryUpdateResponse(
        UUID id,
        UUID injuryId,
        String recoveryStatus,
        String notes,
        List<UUID> attachmentFileIds,
        UUID recordedByUserId,
        String recordedByName,
        OffsetDateTime createdAt
) {
}
