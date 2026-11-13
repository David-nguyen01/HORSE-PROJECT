package com.racehorse.nutrition.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record ConfirmMealFeedingRequest(
        @NotNull(message = "Ngày cho ăn không được để trống")
        LocalDate date,
        String mealKey,
        String mealName,
        LocalTime feedingTime
) {}
