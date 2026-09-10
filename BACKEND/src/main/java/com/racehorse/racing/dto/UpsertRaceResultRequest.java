package com.racehorse.racing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpsertRaceResultRequest(
        @NotNull @PastOrPresent LocalDate raceDate,
        @NotBlank @Size(max = 200) String raceName,
        @NotBlank @Size(max = 200) String racecourse,
        @NotBlank @Size(max = 50) String distance,
        @Min(1) int finishPosition,
        @NotBlank @Size(max = 50) String finishTime,
        @NotBlank @Size(max = 150) String jockey,
        @Size(max = 100) String trackCondition,
        @NotBlank @Size(max = 100) String prizeMoney
) {
}
