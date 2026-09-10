package com.racehorse.horse.dto;

import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateHorseRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String microchipPassportNumber,
        @NotNull @PastOrPresent LocalDate birthDate,
        @NotNull HorseGender gender,
        @NotBlank @Size(max = 500) String colorMarkings,
        @NotBlank @Size(max = 100) String breed,
        UUID sireId,
        UUID damId,
        UUID currentTrainerId,
        @NotBlank @Size(max = 150) String stableLocation,
        @NotNull HorseStatus status
) {
}
