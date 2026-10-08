package com.racehorse.training.dto;

import com.racehorse.training.entity.WorkloadIntensity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record OwnerTrainingScheduleResponse(
        UUID id,
        LocalDate trainingDate,
        LocalTime operatingWindowStart,
        LocalTime operatingWindowEnd,
        String taskName,
        WorkloadIntensity workloadIntensity,
        String status,
        Racetrack racetrack,
        Lane lane,
        Evaluation evaluation
) {
    public record Racetrack(UUID id, String name) {}
    public record Lane(UUID id, int number) {}
    public record Evaluation(
            UUID id,
            LocalDate sessionDate,
            BigDecimal distanceMeters,
            BigDecimal averageSpeedKmh,
            BigDecimal maxSpeedKmh,
            BigDecimal intensityPercent,
            Integer stopBpm,
            Integer oneMinuteBpm,
            BigDecimal recoveryRate,
            int hours,
            Integer durationMinutes,
            String fitnessStatus,
            String trainerNotes,
            String videoUrl
    ) {}
}
