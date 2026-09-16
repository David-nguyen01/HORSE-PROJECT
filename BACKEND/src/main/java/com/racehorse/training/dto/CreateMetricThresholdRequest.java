package com.racehorse.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateMetricThresholdRequest(@NotBlank String metricType, BigDecimal minimumValue,
                                           BigDecimal maximumValue, @NotBlank String unit,
                                           @NotNull OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo) {}
