package com.racehorse.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInventoryItemRequest(
        @NotBlank(message = "itemName không được để trống")
        String itemName,

        @NotBlank(message = "category không được để trống")
        String category,

        @NotBlank(message = "unit không được để trống")
        String unit,

        @NotNull(message = "initialQuantity không được để trống")
        @DecimalMin(value = "0.0", message = "Số lượng ban đầu không được âm")
        Double initialQuantity,

        @NotNull(message = "minThreshold không được để trống")
        @DecimalMin(value = "0.0", message = "Ngưỡng cảnh báo không được âm")
        Double minThreshold,

        String location,
        String description,
        Double referencePrice
) {
    public CreateInventoryItemRequest(
            String itemName,
            String category,
            String unit,
            Double initialQuantity,
            Double minThreshold,
            String location,
            String description
    ) {
        this(itemName, category, unit, initialQuantity, minThreshold, location, description, null);
    }
}
