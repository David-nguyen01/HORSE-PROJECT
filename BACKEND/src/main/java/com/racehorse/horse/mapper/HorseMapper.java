package com.racehorse.horse.mapper;

import com.racehorse.horse.dto.CreateHorseRequest;
import com.racehorse.horse.dto.HorseResponse;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.entity.HorseOwnership;
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
                request.stableLocation().trim(),
                status);
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
                parentSummary(horse.getSire()),
                parentSummary(horse.getDam()),
                trainerSummary(horse),
                ownerships.stream().map(this::ownerSummary).toList(),
                horse.getStableLocation(),
                horse.getStatus(),
                horse.getArchivedAt(),
                horse.getCreatedAt(),
                horse.getUpdatedAt(),
                horse.getPaymentStatus(),
                horse.getServiceOverride(),
                horse.getOverrideReason(),
                horse.getPaidUntil(),
                isServiceEligible(horse) || Boolean.TRUE.equals(horse.getServiceOverride()));
    }

    private boolean isServiceEligible(Horse horse) {
        if (horse == null || horse.getPaymentStatus() == null) return false;
        String status = horse.getPaymentStatus().toUpperCase();
        return status.equals("PAID") || 
               status.equals("IN_MEDICAL") || 
               status.equals("PENDING_TRAINING") || 
               status.equals("IN_TRAINING") || 
               status.equals("COMPLETED_ONBOARDING");
    }

    public HorseResponse toResponse(Horse horse) {
        return toResponse(horse, List.of());
    }

    private HorseResponse.ParentSummary parentSummary(Horse parent) {
        return parent == null
                ? null
                : new HorseResponse.ParentSummary(parent.getId(), parent.getCode(), parent.getName());
    }

    private HorseResponse.UserSummary trainerSummary(Horse horse) {
        if (horse.getCurrentTrainer() == null) {
            return null;
        }
        return new HorseResponse.UserSummary(
                horse.getCurrentTrainer().getId(),
                horse.getCurrentTrainer().getUsername(),
                horse.getCurrentTrainer().getEmail());
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
