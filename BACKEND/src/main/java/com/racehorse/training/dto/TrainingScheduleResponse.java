package com.racehorse.training.dto;

import com.racehorse.training.entity.SurfaceType;
import com.racehorse.training.entity.WorkloadIntensity;

import java.time.Instant;
import java.util.UUID;

public record TrainingScheduleResponse(
        UUID id,
        HorseSummary horse,
        PhaseSummary phase,
        UserSummary assignedStaff,
        String taskName,
        WorkloadIntensity workloadIntensity,
        Instant scheduledStart,
        Instant scheduledEnd,
        Instant publishedAt,
        Instant cancelledAt,
        Instant completedAt
) {
    public record HorseSummary(UUID id, String code, String name) {}
    public record PhaseSummary(UUID id, String name, SurfaceType surfaceType) {}
    public record UserSummary(UUID id, String username) {}
}
