package com.racehorse.caretask.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateCareTaskRequest(
        UUID horseId,
        @NotBlank @Size(max = 50) String taskType,
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDate scheduledDate,
        @NotNull LocalTime scheduledTime,
        UUID assignedStaffId
) {}
