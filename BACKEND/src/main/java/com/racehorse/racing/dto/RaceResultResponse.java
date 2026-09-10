package com.racehorse.racing.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RaceResultResponse(
        UUID id,
        UUID horseId,
        LocalDate raceDate,
        String raceName,
        String racecourse,
        String distance,
        int finishPosition,
        String finishTime,
        String jockey,
        String trackCondition,
        String prizeMoney,
        Instant createdAt,
        Instant updatedAt
) {
}
