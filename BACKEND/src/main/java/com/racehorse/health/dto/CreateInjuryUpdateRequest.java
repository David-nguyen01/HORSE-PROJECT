package com.racehorse.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInjuryUpdateRequest(
        @NotBlank @Size(max = 100) String statusLabel,
        @Size(max = 100) String severityLabel,
        @NotBlank @Size(max = 4000) String notes
) {}
