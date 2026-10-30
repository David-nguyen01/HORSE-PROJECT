package com.racehorse.incident.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record IncidentReportResponse(
        UUID id,
        UUID horseId,
        String horseCode,
        String horseName,
        String incidentType,
        String severity,
        String description,
        String photoUrl,
        String status,
        LocalDate reportDate,
        UUID reportedByUserId,
        String reportedByName,
        OffsetDateTime reportedAt,
        UUID resolvedByUserId,
        String resolvedByName,
        OffsetDateTime resolvedAt,
        String resolutionNotes,
        UUID trainingScheduleId,
        String trainingTaskName,
        String groundName,
        Integer laneNumber,
        java.util.List<String> recipients,
        UUID emergencyVetId,
        String emergencyVetName,
        UUID emergencyExaminationId
) {
}
