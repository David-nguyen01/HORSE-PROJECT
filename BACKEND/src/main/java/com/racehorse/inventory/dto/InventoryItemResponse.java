package com.racehorse.inventory.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryItemResponse(
        UUID id,
        String itemName,
        String category,
        String unit,
        double quantityOnHand,
        double minThreshold,
        String location,
        String description,
        Double referencePrice,
        boolean isLowStock,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public InventoryItemResponse(
            UUID id,
            String itemName,
            String category,
            String unit,
            double quantityOnHand,
            double minThreshold,
            String location,
            String description,
            boolean isLowStock,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(id, itemName, category, unit, quantityOnHand, minThreshold, location, description, null, isLowStock, status, createdAt, updatedAt);
    }
}
