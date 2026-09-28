package com.racehorse.incident.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateIncidentStatusRequest(
        @NotBlank(message = "status không được để trống")
        String status,

        String resolutionNotes
) {
}
