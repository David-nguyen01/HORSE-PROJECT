package com.racehorse.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewReplenishmentRequest(
        @NotBlank(message = "status không được để trống (APPROVED hoặc REJECTED)")
        String status,

        String reviewNotes,
        Boolean receiveImmediately
) {
    public ReviewReplenishmentRequest(String status, String reviewNotes) {
        this(status, reviewNotes, false);
    }
}
