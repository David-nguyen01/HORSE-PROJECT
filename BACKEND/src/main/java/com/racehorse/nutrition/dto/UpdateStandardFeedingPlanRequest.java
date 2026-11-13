package com.racehorse.nutrition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateStandardFeedingPlanRequest(
        String planCode,

        @NotBlank(message = "Tên khẩu phần mẫu không được để trống")
        String planName,

        String description,
        String targetGroup,

        @NotEmpty(message = "Khẩu phần phải có ít nhất một bữa ăn")
        List<MealItem> meals
) {}
