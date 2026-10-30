package com.racehorse.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record CreateTrainingEmergencyRequest(
        @NotNull UUID trainingScheduleId,
        @NotBlank String description,
        @NotNull Set<Recipient> recipients) {
    public enum Recipient { CARETAKER, DIAGNOSING_VET }
}
