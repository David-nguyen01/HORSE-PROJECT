package com.racehorse.health.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreatePreventiveCareRequest(@NotBlank String careType, String details,
                                          @NotNull LocalDate dueDate,
                                          @Positive Integer recurrenceDays,
                                          @NotNull @PositiveOrZero Integer reminderLeadDays) {}
