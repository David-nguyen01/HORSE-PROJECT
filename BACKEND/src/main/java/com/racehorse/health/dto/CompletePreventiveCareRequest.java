package com.racehorse.health.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record CompletePreventiveCareRequest(@NotNull OffsetDateTime performedAt, String notes) {}
