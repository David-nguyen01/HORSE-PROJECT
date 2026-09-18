package com.racehorse.racing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RaceEventResponse(
        UUID id,
        String raceName,
        String racecourse,
        LocalDate raceDate,
        OffsetDateTime registrationDeadline,
        BigDecimal distanceMeters,
        String raceClass,
        Integer minimumAgeYears,
        Integer maximumAgeYears,
        BigDecimal minimumWeightKg,
        BigDecimal maximumWeightKg,
        UUID createdByUserId,
        boolean active
) {}
