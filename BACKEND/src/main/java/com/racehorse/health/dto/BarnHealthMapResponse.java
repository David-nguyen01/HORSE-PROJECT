package com.racehorse.health.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BarnHealthMapResponse(
        Summary summary,
        List<StableView> stables
) {
    public record Summary(
            int totalHorses,
            int fitCount,
            int monitorCount,
            int injuredCount,
            int quarantinedCount,
            int lockedCount
    ) {}

    public record StableView(
            UUID stableId,
            String name,
            String location,
            boolean active,
            List<StallView> stalls
    ) {}

    public record StallView(
            UUID stallId,
            String code,
            boolean active,
            HorseView horse
    ) {}

    public record HorseView(
            UUID horseId,
            String code,
            String name,
            String gender,
            String healthStatus,
            String stableLocation,
            TrainingLockView trainingLock
    ) {}

    public record TrainingLockView(
            boolean isLocked,
            UUID lockId,
            String reason,
            LocalDate reviewDate,
            Instant lockedAt
    ) {}
}
