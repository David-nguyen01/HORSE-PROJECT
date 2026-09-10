package com.racehorse.racing.dto;

import java.util.List;
import java.util.UUID;

public record RaceHistoryResponse(
        UUID horseId,
        long totalRaces,
        long totalWins,
        List<RaceResultResponse> results
) {
}
