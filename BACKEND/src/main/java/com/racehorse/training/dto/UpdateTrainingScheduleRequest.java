package com.racehorse.training.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.racehorse.training.entity.WorkloadIntensity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateTrainingScheduleRequest(
        @NotBlank @Size(max = 300) String taskName,
        @NotNull LocalDate trainingDate,
        @NotNull UUID racetrackId,
        @Size(max = 2000) String notes,
        WorkloadIntensity workloadIntensity,
        UUID assignedStaffId,
        UUID veterinarianId
) {
}
