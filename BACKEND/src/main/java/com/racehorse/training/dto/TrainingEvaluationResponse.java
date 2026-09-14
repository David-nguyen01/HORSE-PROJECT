package com.racehorse.training.dto;

import com.racehorse.training.entity.FitnessStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TrainingEvaluationResponse(
        UUID id,
        HorseSummary horse,
        UserSummary recordedBy,
        LocalDate sessionDate,
        int durationMinutes,
        BigDecimal distanceMeters,
        BigDecimal averageSpeedKmh,
        BigDecimal trainingLoad,
        int heartRateRecoveryBpm,
        int perceivedExertion,
        FitnessStatus fitnessStatus,
        String trainerNotes,
        String videoUrl,
        Instant completedAt
) {
    public record HorseSummary(UUID id, String code, String name) {}
    public record UserSummary(UUID id, String username) {}
}
