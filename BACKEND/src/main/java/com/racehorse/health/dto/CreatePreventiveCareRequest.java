package com.racehorse.health.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreatePreventiveCareRequest(@NotBlank String careType, @Size(max = 120) String careName,
                                          @Size(max = 2000) String details,
                                          @NotNull LocalDate dueDate,
                                          @Positive Integer recurrenceDays,
                                          @NotNull @PositiveOrZero Integer reminderLeadDays) {
    public CreatePreventiveCareRequest(String careType, String details, LocalDate dueDate,
                                       Integer recurrenceDays, Integer reminderLeadDays) {
        this(careType, null, details, dueDate, recurrenceDays, reminderLeadDays);
    }
}
