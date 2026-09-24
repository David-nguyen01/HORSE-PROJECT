package com.racehorse.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateIncidentReportRequest(
        @NotNull(message = "horseId không được để trống")
        UUID horseId,

        @NotBlank(message = "incidentType không được để trống")
        String incidentType,

        @NotBlank(message = "severity không được để trống")
        String severity,

        @NotBlank(message = "description không được để trống")
        String description,

        String photoUrl
) {
}
