package com.racehorse.health.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DuePreventiveCareResponse(
        UUID id,
        UUID horseId,
        String horseCode,
        String horseName,
        String careType,
        String careName,
        String details,
        LocalDate dueDate,
        Integer recurrenceDays,
        int reminderLeadDays,
        boolean isOverdue,
        long daysUntilDue
) {
}
