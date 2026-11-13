package com.racehorse.nutrition.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record QuickFeedMealItem(
        String mealId,
        String mealName,
        LocalTime feedingTime,
        BigDecimal concentrateKg,
        String concentrateType,
        BigDecimal forageKg,
        String forageType,
        String supplements,
        String status,
        boolean confirmed,
        OffsetDateTime confirmedAt,
        String confirmedByName
) {}
