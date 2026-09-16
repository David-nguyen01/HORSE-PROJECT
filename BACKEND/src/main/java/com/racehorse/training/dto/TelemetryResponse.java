package com.racehorse.training.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TelemetryResponse(UUID sessionId, UUID horseId, String entryMethod, String measurementContext,
                                OffsetDateTime startedAt, OffsetDateTime endedAt, int readingCount,
                                List<Alert> alerts) {
    public record Alert(UUID id, String metricType, String direction, String message) {}
}
