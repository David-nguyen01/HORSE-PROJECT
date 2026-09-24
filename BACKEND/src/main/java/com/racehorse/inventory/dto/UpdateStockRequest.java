package com.racehorse.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UpdateStockRequest(
        @NotNull(message = "newQuantity không được để trống")
        @DecimalMin(value = "0.0", message = "Số lượng tồn kho không được âm")
        Double newQuantity,

        String reason
) {
}
