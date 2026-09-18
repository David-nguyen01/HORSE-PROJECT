package com.racehorse.inventory.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryCatalogResponse(
        UUID id,
        String type,
        String name,
        String description,
        String unit,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
