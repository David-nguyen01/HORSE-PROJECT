package com.racehorse.training.dto;

import com.racehorse.training.entity.SurfaceType;
import com.racehorse.training.entity.WorkloadIntensity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TrainingPlanResponse(
        UUID id,
        HorseSummary horse,
        String name,
        UUID sourcePlanId,
        UserSummary createdBy,
        boolean active,
        String status,
        List<Phase> phases,
        Instant createdAt,
        Instant updatedAt
) {
    public record HorseSummary(UUID id, String code, String name) {}
    public record UserSummary(UUID id, String username) {}
    public record Phase(
            UUID id,
            int order,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal targetDistanceMeters,
            WorkloadIntensity workloadIntensity,
            SurfaceType surfaceType,
            List<LocalDate> restDays,
            List<SessionSummary> sessions
    ) {}
    public record SessionSummary(
            UUID id,
            LocalDate date,
            String status,
            WorkloadIntensity workloadIntensity,
            String taskName,
            TrainingScheduleResponse.UserSummary assignedStaff,
            TrainingScheduleResponse.UserSummary veterinarian,
            String staffAssignedBy,
            String vetAssignedBy,
            TrainingScheduleResponse.RacetrackSummary racetrack,
            EvaluationSummary evaluation
    ) {}
    public record EvaluationSummary(UUID id, LocalDate sessionDate, BigDecimal intensityPercent,
                                    BigDecimal recoveryRate, String fitnessStatus) {}
}
