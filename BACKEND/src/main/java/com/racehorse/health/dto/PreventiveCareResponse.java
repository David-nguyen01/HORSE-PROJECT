package com.racehorse.health.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PreventiveCareResponse(UUID id, UUID horseId, String careType, LocalDate dueDate,
                                     Integer recurrenceDays, int reminderLeadDays, boolean completed,
                                     UUID nextScheduleId) {}
