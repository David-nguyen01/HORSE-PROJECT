package com.racehorse.nutrition.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyFeedingChecklistResponse(
        UUID horseId,
        String horseCode,
        String horseName,
        String stableLocation,
        LocalDate date,
        int planVersion,
        List<MealItem> meals
) {}
