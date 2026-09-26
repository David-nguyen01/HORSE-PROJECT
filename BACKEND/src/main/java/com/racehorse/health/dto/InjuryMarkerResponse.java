package com.racehorse.health.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InjuryMarkerResponse(
        UUID id,
        UUID horseId,
        String modelName,
        String bodyPartLabel,
        BigDecimal coordinateX,
        BigDecimal coordinateY,
        BigDecimal coordinateZ,
        String initialSeverity,
        String currentSeverity,
        UserSummary createdBy,
        Instant createdAt,
        List<RecoveryUpdateResponse> recoveryUpdates
) {
    public record UserSummary(UUID id, String username) {}

    public record RecoveryUpdateResponse(
            UUID id,
            String statusLabel,
            String severityLabel,
            String notes,
            UserSummary recordedBy,
            Instant recordedAt
    ) {}
}
