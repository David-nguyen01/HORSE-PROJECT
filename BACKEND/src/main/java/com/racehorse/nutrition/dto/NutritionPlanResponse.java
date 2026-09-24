package com.racehorse.nutrition.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NutritionPlanResponse(
        UUID id,
        UUID horseId,
        int version,
        LocalDate effectiveDate,
        String status,
        String notes,
        List<MealItem> meals,
        UUID createdByUserId,
        OffsetDateTime createdAt
) {}
