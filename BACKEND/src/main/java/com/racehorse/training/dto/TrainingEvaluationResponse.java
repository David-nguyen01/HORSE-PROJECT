package com.racehorse.training.dto;

import com.racehorse.training.entity.FitnessStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TrainingEvaluationResponse(
        UUID id,
        HorseSummary horse,
        UUID trainingScheduleId,
        UserSummary recordedBy,
        LocalDate sessionDate,
        int hours,
        Integer durationMinutes,
        BigDecimal distanceMeters,
        BigDecimal averageSpeedKmh,
        BigDecimal maxSpeedKmh,
        BigDecimal intensityPercent,
        Integer stopBpm,
        Integer oneMinuteBpm,
        BigDecimal recoveryRate,
        Integer perceivedExertion,
        FitnessStatus fitnessStatus,
        String trainerNotes,
        String videoUrl,
        Instant completedAt,
        BigDecimal trackCompletionSeconds,
        Boolean injured,
        String injuryLocation,
        String injuryNotes
) {
    public record HorseSummary(UUID id, String code, String name) {}
    public record UserSummary(UUID id, String username) {}
}
