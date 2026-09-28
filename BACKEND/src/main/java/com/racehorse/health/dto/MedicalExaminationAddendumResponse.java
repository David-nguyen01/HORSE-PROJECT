package com.racehorse.health.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MedicalExaminationAddendumResponse(
        UUID id,
        UUID examinationId,
        UUID authoredByUserId,
        String authorName,
        String notes,
        String healthStatusAfter,
        OffsetDateTime createdAt
) {
}
