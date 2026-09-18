package com.racehorse.audit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateAuditSettingsRequest(
        @NotNull
        @Min(1)
        @Max(3650)
        Integer retentionDays
) {}
