package com.racehorse.health.dto;

import java.util.List;
import java.util.UUID;

/**
 * FR08 – response for the stable health map endpoint.
 * Each item represents one stall/stable slot with the horse currently assigned.
 */
public record StableMapResponse(
        List<StallEntry> stalls
) {
    public record StallEntry(
            String stableLocation,
            UUID horseId,
            String horseName,
            String registrationCode,
            String healthStatus   // FIT | MONITOR | INJURED | QUARANTINED
    ) {}
}
