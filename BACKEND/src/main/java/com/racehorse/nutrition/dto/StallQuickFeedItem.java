package com.racehorse.nutrition.dto;

import java.util.List;
import java.util.UUID;

public record StallQuickFeedItem(
        UUID stableId,
        String stableName,
        UUID stallId,
        String stallCode,
        String stallLocationDisplay,
        UUID horseId,
        String horseCode,
        String horseName,
        String horseDisplay,
        boolean hasPlan,
        UUID planId,
        int planVersion,
        List<QuickFeedMealItem> meals
) {}
