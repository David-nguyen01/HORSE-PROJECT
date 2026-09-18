package com.racehorse.racing.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RaceRegistrationResponse(
        UUID id,
        UUID raceEventId,
        UUID horseId,
        UUID registeredByUserId,
        String status,
        OffsetDateTime submittedAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime withdrawnAt,
        UUID withdrawnByUserId
) {}
