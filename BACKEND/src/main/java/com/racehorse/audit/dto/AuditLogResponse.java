package com.racehorse.audit.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
        Long id,
        UUID actorId,
        String actorUsername,
        OffsetDateTime occurredAt,
        String moduleCode,
        String actionCode,
        String entityType,
        UUID entityId,
        String description,
        String oldValuesJson,
        String newValuesJson,
        String ipAddress,
        String userAgent
) {}
