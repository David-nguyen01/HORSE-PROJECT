package com.racehorse.owner.dto;

import com.racehorse.horse.entity.HorseGender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO dành cho HORSE_OWNER cập nhật hồ sơ ngựa (khi status còn DRAFT).
 * Owner KHÔNG thể đổi code, status, stableLocation.
 */
public record OwnerUpdateHorseRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String microchipPassportNumber,
        @NotNull @PastOrPresent LocalDate birthDate,
        @NotNull HorseGender gender,
        @NotBlank @Size(max = 500) String colorMarkings,
        @NotBlank @Size(max = 100) String breed,
        UUID sireId,
        UUID damId,
        @Size(max = 300) String sireManualInfo,
        @Size(max = 300) String damManualInfo
) {
}
