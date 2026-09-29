package com.racehorse.caretask.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CareTaskResponse(
        UUID id,
        UUID horseId,
        String horseCode,
        String horseName,
        String taskType,
        String title,
        LocalDate scheduledDate,
        LocalTime scheduledTime,
        UUID assignedStaffId,
        String status,
        boolean isOverdue,
        OffsetDateTime completedAt,
        UUID completedByUserId,
        String completedByName,
        String completionNotes,
        String photoUrl,
        OffsetDateTime createdAt
) {}
