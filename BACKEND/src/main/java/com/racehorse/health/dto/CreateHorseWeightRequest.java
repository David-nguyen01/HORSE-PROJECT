package com.racehorse.health.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateHorseWeightRequest(
        @NotNull @DecimalMin("0.01") BigDecimal weightKg,
        @NotNull OffsetDateTime measuredAt,
        @Size(max=1000) String notes) {}
