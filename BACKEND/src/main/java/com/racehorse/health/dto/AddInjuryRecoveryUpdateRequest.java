package com.racehorse.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AddInjuryRecoveryUpdateRequest(
        @NotBlank @Size(max = 30) String recoveryStatus,
        @Size(max = 2000) String notes,
        List<UUID> attachmentFileIds
) {
}
