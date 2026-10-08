package com.racehorse.horse.dto;

import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HorseResponse(
        UUID id,
        String code,
        String name,
        String microchipPassportNumber,
        LocalDate birthDate,
        HorseGender gender,
        String colorMarkings,
        String breed,
        ParentSummary sire,
        ParentSummary dam,
        UserSummary currentTrainer,
        UserSummary caretaker,
        UserSummary diagnosingVet,
        List<OwnerSummary> owners,
        String stableLocation,
        HorseStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        String paymentStatus,
        Boolean serviceOverride,
        String overrideReason,
        LocalDate paidUntil
) {
    public record ParentSummary(UUID id, String code, String name) {
    }

    public record UserSummary(UUID id, String username, String email) {
    }

    public record OwnerSummary(UUID id, String username, String email, java.math.BigDecimal percentage) {
    }
}
