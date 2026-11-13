package com.racehorse.servicepackage.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ServicePackageResponse(
        UUID id,
        String code,
        String name,
        String description,
        BigDecimal priceVnd,
        int durationDays,
        boolean isActive,
        int displayOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version
) {}
