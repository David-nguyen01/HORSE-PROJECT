package com.racehorse.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateInventoryItemRequest(
        @NotBlank(message = "itemName không được để trống")
        String itemName,

        @NotBlank(message = "category không được để trống")
        String category,

        @NotBlank(message = "unit không được để trống")
        String unit,

        @NotNull(message = "minThreshold không được để trống")
        @DecimalMin(value = "0.0", message = "Ngưỡng cảnh báo không được âm")
        Double minThreshold,

        String location,
        String description,
        Double referencePrice,
        String status
) {
}
