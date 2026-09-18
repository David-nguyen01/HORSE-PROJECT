package com.racehorse.training.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateTelemetryRequest(UUID trainingScheduleId, @NotBlank String measurementContext,
                                     @NotNull OffsetDateTime startedAt, OffsetDateTime endedAt,
                                     @NotEmpty List<@Valid Reading> readings) {
    public record Reading(@NotNull OffsetDateTime recordedAt,
                          @Min(0) @Max(300) Integer heartRateBpm,
                          @DecimalMin("0.0") BigDecimal speedKmh) {}
}
