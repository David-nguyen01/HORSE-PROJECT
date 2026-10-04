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
        Instant completedAt,
        UUID racetrackId,
        String racetrackName,
        UUID veterinarianUserId,
        String veterinarianName,
        String vetAssignedBy,
        Instant vetAssignedAt,
        String staffAssignedBy,
        Instant staffAssignedAt,
        Boolean canClaim
) {
    public record HorseSummary(UUID id, String code, String name) {}
    public record PhaseSummary(UUID id, String name, SurfaceType surfaceType) {}
    public record UserSummary(UUID id, String username) {}

    public TrainingScheduleResponse withCanClaim(boolean canClaim) {
        return new TrainingScheduleResponse(
                id, horse, phase, assignedStaff, taskName, workloadIntensity,
                scheduledStart, scheduledEnd, publishedAt, cancelledAt, completedAt,
                racetrackId, racetrackName, veterinarianUserId, veterinarianName,
                vetAssignedBy, vetAssignedAt, staffAssignedBy, staffAssignedAt, canClaim
        );
    }

    public UUID getHorseId() { return horse != null ? horse.id() : null; }
    public String getHorseCode() { return horse != null ? horse.code() : null; }
    public String getHorseName() { return horse != null ? horse.name() : null; }
    public String getStaffName() { return assignedStaff != null ? assignedStaff.username() : null; }
}
