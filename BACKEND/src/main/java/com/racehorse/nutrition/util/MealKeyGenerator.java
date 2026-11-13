package com.racehorse.nutrition.util;

import java.text.Normalizer;
import java.time.LocalTime;
import java.util.Locale;

public final class MealKeyGenerator {

    private MealKeyGenerator() {}

    public static String generate(String mealName, LocalTime feedingTime) {
        String timePart = feedingTime != null
                ? String.format("%02d%02d", feedingTime.getHour(), feedingTime.getMinute())
                : "0000";
        String slug = slugify(mealName);
        return "meal_" + timePart + "_" + slug;
    }

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "meal";
        }
        String preprocessed = input.replace('đ', 'd').replace('Đ', 'd');
        String normalized = Normalizer.normalize(preprocessed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return slug.isBlank() ? "meal" : slug;
    }
}
