package com.racehorse.racing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CreateRaceEventRequest(
        @NotBlank String raceName,
        @NotBlank String racecourse,
        @NotNull LocalDate raceDate,
        @NotNull OffsetDateTime registrationDeadline,
        @NotNull @DecimalMin("1.0") BigDecimal distanceMeters,
        @NotBlank String raceClass,
        Integer minimumAgeYears,
        Integer maximumAgeYears,
        @DecimalMin("1.0") BigDecimal minimumWeightKg,
        @DecimalMin("1.0") BigDecimal maximumWeightKg
) {}
