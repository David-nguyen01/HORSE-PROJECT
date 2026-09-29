package com.racehorse.training.dto;

import com.racehorse.training.entity.FitnessStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTrainingEvaluationRequest(
        @NotNull UUID horseId,
        UUID trainingScheduleId,
        @NotNull @PastOrPresent LocalDate sessionDate,
        @Min(1) int durationMinutes,
        @NotNull @DecimalMin(value = "0.01") BigDecimal distanceMeters,
        @NotNull @DecimalMin(value = "0.01") BigDecimal averageSpeedKmh,
        @NotNull @DecimalMin(value = "0.00") BigDecimal trainingLoad,
        @Min(0) @Max(300) int heartRateRecoveryBpm,
        @Min(1) @Max(10) int perceivedExertion,
        @NotNull FitnessStatus fitnessStatus,
        @NotBlank @Size(max = 4000) String trainerNotes,
        @Size(max = 2000) String videoUrl
) {
    public CreateTrainingEvaluationRequest(
            UUID horseId,
            LocalDate sessionDate,
            int durationMinutes,
            BigDecimal distanceMeters,
            BigDecimal averageSpeedKmh,
            BigDecimal trainingLoad,
            int heartRateRecoveryBpm,
            int perceivedExertion,
            FitnessStatus fitnessStatus,
            String trainerNotes,
            String videoUrl
    ) {
        this(horseId, null, sessionDate, durationMinutes, distanceMeters, averageSpeedKmh,
                trainingLoad, heartRateRecoveryBpm, perceivedExertion, fitnessStatus, trainerNotes, videoUrl);
    }
}
