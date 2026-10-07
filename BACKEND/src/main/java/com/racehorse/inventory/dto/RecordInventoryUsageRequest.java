package com.racehorse.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordInventoryUsageRequest(
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.01") BigDecimal unitPriceVnd,
        UUID horseId,
        UUID stableId,
        String notes
) {}
