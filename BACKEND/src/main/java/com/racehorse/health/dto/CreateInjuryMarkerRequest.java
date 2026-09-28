package com.racehorse.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateInjuryMarkerRequest(
        UUID examinationId,
        @NotBlank @Size(max = 200) String bodyLocation,
        @NotNull Double x,
        @NotNull Double y,
        @NotNull Double z,
        @NotBlank @Size(max = 30) String severity,
        @Size(max = 30) String initialStatus,
        @Size(max = 2000) String notes,
        List<UUID> attachmentFileIds
) {
}
