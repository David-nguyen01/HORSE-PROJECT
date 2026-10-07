package com.racehorse.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateInjuryMarkerRequest(
        UUID examinationId,
        @Size(max = 200) String bodyLocation,
        List<String> bodyRegions,
        @Size(max = 1000) String additionalNotes,
        Double x,
        Double y,
        Double z,
        @NotBlank @Size(max = 30) String severity,
        @Size(max = 30) String initialStatus,
        @Size(max = 2000) String notes,
        List<UUID> attachmentFileIds
) {
    public CreateInjuryMarkerRequest(
            UUID examinationId,
            String bodyLocation,
            Double x,
            Double y,
            Double z,
            String severity,
            String initialStatus,
            String notes,
            List<UUID> attachmentFileIds
    ) {
        this(examinationId, bodyLocation, null, null, x, y, z, severity, initialStatus, notes, attachmentFileIds);
    }
}
