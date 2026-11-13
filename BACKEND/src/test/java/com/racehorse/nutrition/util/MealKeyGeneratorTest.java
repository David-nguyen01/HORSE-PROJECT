package com.racehorse.nutrition.util;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class MealKeyGeneratorTest {

    @Test
    void generatesStandardKeyForVietnameseMeal() {
        String key = MealKeyGenerator.generate("Bữa sáng (06:30)", LocalTime.of(6, 30));
        assertThat(key).isEqualTo("meal_0630_bua_sang_06_30");
    }

    @Test
    void generatesStandardKeyForMiddayMeal() {
        String key = MealKeyGenerator.generate("Bữa trưa (11:30)", LocalTime.of(11, 30));
        assertThat(key).isEqualTo("meal_1130_bua_trua_11_30");
    }

    @Test
    void generatesStandardKeyForEveningMeal() {
        String key = MealKeyGenerator.generate("Bữa chiều (17:00)", LocalTime.of(17, 0));
        assertThat(key).isEqualTo("meal_1700_bua_chieu_17_00");
    }

    @Test
    void generatesKeyForEnglishMeal() {
        String key = MealKeyGenerator.generate("Breakfast", LocalTime.of(7, 0));
        assertThat(key).isEqualTo("meal_0700_breakfast");
    }

    @Test
    void handlesNullFeedingTimeGracefully() {
        String key = MealKeyGenerator.generate("Bữa phụ", null);
        assertThat(key).isEqualTo("meal_0000_bua_phu");
    }

    @Test
    void handlesNullMealNameGracefully() {
        String key = MealKeyGenerator.generate(null, LocalTime.of(8, 15));
        assertThat(key).isEqualTo("meal_0815_meal");
    }

    @Test
    void slugifyStripsDiacriticsAndSpecialCharacters() {
        String slug = MealKeyGenerator.slugify("Khẩu phần ăn đặc biệt #1 - Siêu năng lượng!");
        assertThat(slug).isEqualTo("khau_phan_an_dac_biet_1_sieu_nang_luong");
    }
}
