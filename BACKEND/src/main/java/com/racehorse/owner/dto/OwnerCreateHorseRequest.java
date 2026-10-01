package com.racehorse.owner.dto;

import com.racehorse.horse.entity.HorseGender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO dành cho HORSE_OWNER tạo hồ sơ ngựa mới.
 * Các trường code, status, stableLocation do hệ thống tự sinh,
 * Owner không được tự chọn.
 */
public record OwnerCreateHorseRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String microchipPassportNumber,
        @NotNull @PastOrPresent LocalDate birthDate,
        @NotNull HorseGender gender,
        @NotBlank @Size(max = 500) String colorMarkings,
        @NotBlank @Size(max = 100) String breed,
        UUID sireId,
        UUID damId,
        // Thông tin cha/mẹ nhập thủ công (khi ngựa cha/mẹ không có trong hệ thống)
        @Size(max = 300) String sireManualInfo,
        @Size(max = 300) String damManualInfo
) {
}
