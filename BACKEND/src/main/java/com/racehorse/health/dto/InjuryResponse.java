package com.racehorse.health.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InjuryResponse(
        UUID id,
        UUID horseId,
        UUID examinationId,
        String bodyLocation,
        List<String> bodyRegions,
        String additionalNotes,
        Double x,
        Double y,
        Double z,
        String severity,
        String currentStatus,
        String notes,
        List<UUID> attachmentFileIds,
        UUID recordedByUserId,
        String recordedByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int updateCount
) {
    public InjuryResponse(
            UUID id,
            UUID horseId,
            UUID examinationId,
            String bodyLocation,
            Double x,
            Double y,
            Double z,
            String severity,
            String currentStatus,
            String notes,
            List<UUID> attachmentFileIds,
            UUID recordedByUserId,
            String recordedByName,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            int updateCount
    ) {
        this(id, horseId, examinationId, bodyLocation, List.of(), null, x, y, z, severity, currentStatus, notes, attachmentFileIds, recordedByUserId, recordedByName, createdAt, updatedAt, updateCount);
    }
}
