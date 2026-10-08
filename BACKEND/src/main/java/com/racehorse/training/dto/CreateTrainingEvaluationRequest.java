package com.racehorse.training.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateTrainingEvaluationRequest(
        @NotNull UUID horseId,
        UUID trainingScheduleId,
        @NotNull LocalDate sessionDate,
        @NotNull @Min(1) Integer durationMinutes,
        @NotNull @DecimalMin("0.01") BigDecimal distanceMeters,
        @DecimalMin("0.00") BigDecimal averageSpeedKmh,
        @NotBlank @Size(max = 4000) String trainerNotes,
        @Size(max = 2000) String videoUrl,
        @NotNull @DecimalMin("0.01") BigDecimal maxSpeedKmh,
        @NotNull @Min(0) @Max(300) Integer stopBpm,
        @NotNull @Min(0) @Max(300) Integer oneMinuteBpm,
        @NotNull @DecimalMin("0.001") BigDecimal trackCompletionSeconds,
        @Min(1) @Max(10) Integer perceivedExertion,
        @NotNull Boolean injured,
        @Size(max = 200) String injuryLocation,
        @Size(max = 4000) String injuryNotes
) {
}
