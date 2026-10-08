package com.racehorse.training.dto;

import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.training.entity.FitnessStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FitnessDashboardResponse(
        Instant generatedAt,
        FilterSummary filters,
        List<HorseFitnessSummary> horses,
        List<FitnessDataPoint> dataPoints
) {
    public record FilterSummary(
            UUID horseId,
            UUID phaseId,
            LocalDate from,
            LocalDate to,
            FitnessStatus fitnessStatus
    ) {
        public FilterSummary(UUID horseId, LocalDate from, LocalDate to, FitnessStatus fitnessStatus) {
            this(horseId, null, from, to, fitnessStatus);
        }
    }

    public record HorseFitnessSummary(
            UUID horseId,
            String horseCode,
            String horseName,
            String stableLocation,
            HorseStatus operationalStatus,
            FitnessStatus fitnessStatus,
            UUID latestEvaluationId,
            LocalDate latestEvaluationDate
    ) {}

    public record FitnessDataPoint(
            UUID evaluationId,
            UUID horseId,
            String horseCode,
            String horseName,
            LocalDate sessionDate,
            BigDecimal averageSpeedKmh,
            BigDecimal maxSpeedKmh,
            BigDecimal intensityPercent,
            Integer stopBpm,
            Integer oneMinuteBpm,
            BigDecimal recoveryRate,
            int hours,
            int heartRateRecoveryBpm,
            FitnessStatus fitnessStatus
    ) {}
}
