package com.racehorse.nutrition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateNutritionPlanRequest(
        @NotNull LocalDate effectiveDate,
        LocalDate validUntil,
        @Size(max = 2000) String notes,
        @NotEmpty List<@Valid MealItem> meals
) {}
