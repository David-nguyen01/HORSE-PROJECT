package com.racehorse.nutrition.dto;

import java.util.List;

public record FeedingTemplateOptionView(
        String planCode,
        String planName,
        String description,
        String targetGroup,
        String suitability,
        String suitabilityReason,
        boolean canApply,
        boolean requiresConfirmation,
        List<MealItem> meals
) {}
