package com.racehorse.nutrition.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record DailyFeedingLogResponse(
        UUID id,
        UUID horseId,
        LocalDate feedingDate,
        String mealKey,
        String mealName,
        LocalTime feedingTime,
        UUID planId,
        int planVersion,
        String status,
        UUID confirmedByUserId,
        String confirmedByName,
        OffsetDateTime confirmedAt,
        Map<String, Object> mealSnapshot,
        OffsetDateTime createdAt
) {}
