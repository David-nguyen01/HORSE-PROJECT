package com.racehorse.nutrition.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

public record MealItem(
        @NotBlank @Size(max = 100) String mealName,
        @NotNull LocalTime feedingTime,
        @NotNull @DecimalMin("0.00") BigDecimal concentrateKg,
        @Size(max = 200) String concentrateType,
        @NotNull @DecimalMin("0.00") BigDecimal forageKg,
        @Size(max = 200) String forageType,
        @Size(max = 500) String supplements
) {}
