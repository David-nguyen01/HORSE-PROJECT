package com.racehorse.caretask.dto;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CareRoutineDefinitionResponse(
        UUID id,
        String code,
        String title,
        String description,
        String taskType,
        LocalTime scheduledTime,
        String scope,
        boolean isActive,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
