package com.racehorse.audit.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditSettingsResponse(
        int retentionDays,
        UUID updatedByUserId,
        OffsetDateTime updatedAt
) {}
