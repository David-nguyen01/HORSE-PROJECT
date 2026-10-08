package com.racehorse.horse.mapper;

import com.racehorse.horse.dto.CreateHorseRequest;
import com.racehorse.horse.dto.HorseResponse;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.entity.HorseOwnership;
import com.racehorse.auth.entity.UserAccount;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HorseMapper {

    public Horse toEntity(CreateHorseRequest request) {
        HorseStatus status = request.status() == null ? HorseStatus.ACTIVE : request.status();
        return new Horse(
                normalize(request.code()),
                request.name().trim(),
                normalizeNullable(request.microchipPassportNumber()),
                request.birthDate(),
                request.gender(),
                request.colorMarkings().trim(),
                request.breed().trim(),
                request.stableLocation() == null ? "" : request.stableLocation().trim(),
                status);
    }

    public Horse toEntityForOwner(CreateHorseRequest request) {
        return new Horse(normalize(request.code()), request.name().trim(),
                normalizeNullable(request.microchipPassportNumber()), request.birthDate(), request.gender(),
                request.colorMarkings().trim(), request.breed().trim(), "", HorseStatus.PENDING_PAYMENT);
    }

    public HorseResponse toResponse(Horse horse, List<HorseOwnership> ownerships) {
        return new HorseResponse(
                horse.getId(),
                horse.getCode(),
                horse.getName(),
                horse.getMicrochipPassportNumber(),
                horse.getBirthDate(),
                horse.getGender(),
                horse.getColorMarkings(),
                horse.getBreed(),
                parentSummary(horse.getSire(), horse.getSireManualId(), horse.getSireManualName()),
                parentSummary(horse.getDam(), horse.getDamManualId(), horse.getDamManualName()),
                trainerSummary(horse),
                userSummary(horse.getCaretaker()),
                userSummary(horse.getDiagnosingVet()),
                ownerships.stream().map(this::ownerSummary).toList(),
                horse.getStableLocation(),
                horse.getStatus(),
                horse.getArchivedAt(),
                horse.getCreatedAt(),
                horse.getUpdatedAt(),
                horse.getPaymentStatus(),
                horse.getServiceOverride(),
                horse.getOverrideReason(),
                horse.getPaidUntil());
    }

    public HorseResponse toResponse(Horse horse) {
        return toResponse(horse, List.of());
    }

    private HorseResponse.ParentSummary parentSummary(Horse parent, String manualId, String manualName) {
        if (parent != null) return new HorseResponse.ParentSummary(parent.getId(), parent.getCode(), parent.getName());
        return manualName == null ? null : new HorseResponse.ParentSummary(null, manualId, manualName);
    }

    private HorseResponse.UserSummary trainerSummary(Horse horse) {
        return userSummary(horse.getCurrentTrainer());
    }

    private HorseResponse.UserSummary userSummary(UserAccount user) {
        return user == null ? null : new HorseResponse.UserSummary(
                user.getId(), user.getUsername(), user.getEmail());
    }

    private HorseResponse.OwnerSummary ownerSummary(HorseOwnership ownership) {
        return new HorseResponse.OwnerSummary(
                ownership.getOwner().getId(),
                ownership.getOwner().getUsername(),
                ownership.getOwner().getEmail(),
                ownership.getOwnershipPercentage());
    }

    private String normalize(String value) {
        return value.trim().toUpperCase();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : normalize(value);
    }
}
