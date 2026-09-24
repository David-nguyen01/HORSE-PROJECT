package com.racehorse.inventory.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReplenishmentResponse(
        UUID id,
        UUID itemId,
        String itemName,
        String category,
        double requestedQuantity,
        String unit,
        String reason,
        String status,
        UUID proposedByUserId,
        String proposedByName,
        OffsetDateTime proposedAt,
        UUID reviewedByUserId,
        String reviewedByName,
        OffsetDateTime reviewedAt,
        String reviewNotes
) {
}
