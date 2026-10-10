package com.racehorse.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateTrainingScheduleRequest(
        @NotNull UUID trainingPlanPhaseId,
        @NotNull UUID racetrackId,
        @NotNull UUID racetrackLaneId,
        @NotBlank @Size(max = 300) String taskName,
        @NotNull LocalDate trainingDate,
        @NotNull LocalTime slotStart
) {
}
