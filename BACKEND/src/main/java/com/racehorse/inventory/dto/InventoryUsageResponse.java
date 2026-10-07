package com.racehorse.inventory.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryUsageResponse(
        UUID id,
        UUID itemId,
        String itemName,
        BigDecimal quantity,
        String unit,
        BigDecimal remainingQuantity,
        BigDecimal unitPriceVnd,
        BigDecimal totalCostVnd,
        UUID horseId,
        UUID stableId,
        UUID financeTransactionId,
        String notes,
        OffsetDateTime recordedAt
) {}
