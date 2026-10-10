package com.racehorse.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record UpdateTrainingScheduleRequest(
        @NotBlank @Size(max = 300) String taskName,
        @NotNull LocalDate trainingDate,
        @NotNull UUID racetrackId,
        @NotNull UUID racetrackLaneId,
        @NotNull LocalTime slotStart
) {
}
