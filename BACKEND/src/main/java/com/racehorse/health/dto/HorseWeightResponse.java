package com.racehorse.health.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HorseWeightResponse(UUID id, UUID horseId, UUID measuredByUserId, BigDecimal weightKg,
                                  OffsetDateTime measuredAt, String notes) {}
