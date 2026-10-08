package com.racehorse.training.dto;

import com.racehorse.training.entity.SurfaceType;
import com.racehorse.training.entity.WorkloadIntensity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TrainingScheduleResponse(
        UUID id,
        HorseSummary horse,
        PhaseSummary phase,
        RacetrackSummary racetrack,
        String taskName,
        WorkloadIntensity workloadIntensity,
        String notes,
        String status,
        LocalDate trainingDate,
        LocalTime operatingWindowStart,
        LocalTime operatingWindowEnd,
        LaneSummary lane,
        EvaluationSummary evaluation,
        Instant scheduledStart,
        Instant scheduledEnd,
        Instant publishedAt,
        Instant cancelledAt,
        Instant completedAt
) {
    public record HorseSummary(UUID id, String code, String name, HorseContact caretaker, HorseContact diagnosingVet) {}
    public record PhaseSummary(UUID id, UUID planId, String planName, String name, LocalDate startDate,
                               LocalDate endDate, SurfaceType surfaceType) {}
    public record HorseContact(UUID id, String username, String email) {}
    public record RacetrackSummary(UUID id, String name) {}
    public record LaneSummary(UUID id, int number) {}
    public record EvaluationSummary(UUID id, LocalDate sessionDate, BigDecimal intensityPercent,
                                    BigDecimal recoveryRate, String fitnessStatus) {}
}
