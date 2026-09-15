package com.racehorse.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CopyTrainingPlanRequest(
        @NotNull UUID targetHorseId,
        @NotBlank @Size(max = 200) String name,
        boolean active
) {
}
