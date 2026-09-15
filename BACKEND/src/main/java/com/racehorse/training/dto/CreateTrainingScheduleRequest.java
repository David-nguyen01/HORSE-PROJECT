package com.racehorse.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateTrainingScheduleRequest(
        @NotNull UUID trainingPlanPhaseId,
        @NotNull UUID assignedStaffId,
        @NotBlank @Size(max = 300) String taskName,
        @NotNull Instant scheduledStart,
        @NotNull Instant scheduledEnd
) {
}
