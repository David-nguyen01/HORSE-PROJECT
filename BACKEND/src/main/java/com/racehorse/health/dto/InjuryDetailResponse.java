package com.racehorse.health.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InjuryDetailResponse(
        UUID id,
        UUID horseId,
        String horseCode,
        String horseName,
        UUID examinationId,
        String bodyLocation,
        List<String> bodyRegions,
        String additionalNotes,
        Double x,
        Double y,
        Double z,
        String severity,
        String currentStatus,
        String initialNotes,
        List<UUID> attachmentFileIds,
        UUID recordedByUserId,
        String recordedByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<RecoveryUpdateView> timeline
) {
    public InjuryDetailResponse(
            UUID id,
            UUID horseId,
            String horseCode,
            String horseName,
            UUID examinationId,
            String bodyLocation,
            Double x,
            Double y,
            Double z,
            String severity,
            String currentStatus,
            String initialNotes,
            List<UUID> attachmentFileIds,
            UUID recordedByUserId,
            String recordedByName,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<RecoveryUpdateView> timeline
    ) {
        this(id, horseId, horseCode, horseName, examinationId, bodyLocation, List.of(), null, x, y, z, severity, currentStatus, initialNotes, attachmentFileIds, recordedByUserId, recordedByName, createdAt, updatedAt, timeline);
    }

    public record RecoveryUpdateView(
            UUID id,
            UUID injuryId,
            String recoveryStatus,
            String notes,
            List<UUID> attachmentFileIds,
            UUID recordedByUserId,
            String recordedByName,
            OffsetDateTime createdAt
    ) {}
}
