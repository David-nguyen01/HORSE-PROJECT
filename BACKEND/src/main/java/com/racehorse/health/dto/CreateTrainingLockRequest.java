package com.racehorse.health.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTrainingLockRequest(
        @NotBlank @Size(max = 2000) String reason,
        @FutureOrPresent LocalDate reviewDate
) {
}
