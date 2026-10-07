package com.racehorse.inventory.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.UUID;

public record ReceiveReplenishmentRequest(
        UUID catalogItemId,
        @DecimalMin(value = "0.01") BigDecimal referencePriceVnd,
        @DecimalMin(value = "0.0") Double minThreshold,
        String location
) {}
