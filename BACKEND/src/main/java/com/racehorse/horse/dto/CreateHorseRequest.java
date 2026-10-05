package com.racehorse.horse.dto;

import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateHorseRequest(
        @NotBlank @Size(max = 50) String code,
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
        HorseStatus status,
        UUID initialOwnerId
) {
    public CreateHorseRequest(
            String code,
            String name,
            String microchipPassportNumber,
            LocalDate birthDate,
            HorseGender gender,
            String colorMarkings,
            String breed,
            UUID sireId,
            UUID damId,
            UUID currentTrainerId,
            String stableLocation,
            HorseStatus status
    ) {
        this(code, name, microchipPassportNumber, birthDate, gender, colorMarkings, breed, sireId, damId, currentTrainerId, stableLocation, status, null);
    }
}
