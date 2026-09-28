package com.racehorse.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateReplenishmentRequest(
        UUID itemId,

        @NotBlank(message = "itemName không được để trống")
        String itemName,

        @NotBlank(message = "category không được để trống")
        String category,

        @NotNull(message = "requestedQuantity không được để trống")
        @DecimalMin(value = "0.01", message = "Số lượng yêu cầu phải lớn hơn 0")
        Double requestedQuantity,

        @NotBlank(message = "unit không được để trống")
        String unit,

        @NotBlank(message = "reason không được để trống")
        String reason
) {
}
