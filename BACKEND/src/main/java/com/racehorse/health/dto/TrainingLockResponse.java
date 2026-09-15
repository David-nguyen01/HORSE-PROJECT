package com.racehorse.health.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TrainingLockResponse(
        UUID id,
        HorseSummary horse,
        String reason,
        LocalDate reviewDate,
        UserSummary lockedBy,
        Instant lockedAt,
        boolean active,
        UserSummary releasedBy,
        String releaseReason,
        Instant releasedAt
) {
    public record HorseSummary(UUID id, String code, String name) {}
    public record UserSummary(UUID id, String username) {}
}
