package com.racehorse.nutrition.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record DailyFeedingChecklistMealItem(
        String mealKey,
        String mealName,
        LocalTime feedingTime,
        BigDecimal concentrateKg,
        String concentrateType,
        BigDecimal forageKg,
        String forageType,
        String supplements,
        boolean confirmed,
        OffsetDateTime confirmedAt,
        String confirmedByName,
        String status
) {
    public DailyFeedingChecklistMealItem(
            String mealKey,
            String mealName,
            LocalTime feedingTime,
            BigDecimal concentrateKg,
            String concentrateType,
            BigDecimal forageKg,
            String forageType,
            String supplements,
            boolean confirmed,
            OffsetDateTime confirmedAt,
            String confirmedByName) {
        this(mealKey, mealName, feedingTime, concentrateKg, concentrateType, forageKg, forageType, supplements,
                confirmed, confirmedAt, confirmedByName, confirmed ? "CONFIRMED" : "PENDING");
    }
}
