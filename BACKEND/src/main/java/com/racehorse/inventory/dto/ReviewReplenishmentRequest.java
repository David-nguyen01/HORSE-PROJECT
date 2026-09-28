package com.racehorse.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewReplenishmentRequest(
        @NotBlank(message = "status không được để trống (APPROVED hoặc REJECTED)")
        String status,

        String reviewNotes
) {
}
