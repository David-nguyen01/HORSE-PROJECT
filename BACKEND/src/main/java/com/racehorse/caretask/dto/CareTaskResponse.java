package com.racehorse.caretask.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CareTaskResponse(
        UUID id,
        UUID routineDefinitionId,
        String taskCategory,
        String scope,
        UUID horseId,
        String horseCode,
        String horseName,
        String taskType,
        String title,
        String description,
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
) {
    public CareTaskResponse(
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
    ) {
        this(
                id,
                null,
                "SPECIAL",
                horseId != null ? "HORSE" : "FACILITY",
                horseId,
                horseCode,
                horseName,
                taskType,
                title,
                null,
                scheduledDate,
                scheduledTime,
                assignedStaffId,
                status,
                isOverdue,
                completedAt,
                completedByUserId,
                completedByName,
                completionNotes,
                photoUrl,
                createdAt
        );
    }
}
