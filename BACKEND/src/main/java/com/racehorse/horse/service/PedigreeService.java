package com.racehorse.horse.service;

import com.racehorse.horse.dto.PedigreeResponse;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PedigreeService {

    private static final int ANCESTOR_GENERATIONS = 3;

    private final HorseAccessService horseAccessService;

    public PedigreeService(HorseAccessService horseAccessService) {
        this.horseAccessService = horseAccessService;
    }

    @Transactional(readOnly = true)
    public PedigreeResponse get(UUID horseId, UUID userId, boolean privileged) {
        Horse horse = horseAccessService.requireReadable(horseId, userId, privileged);
        return new PedigreeResponse(
                ANCESTOR_GENERATIONS,
                buildNode(horse, ANCESTOR_GENERATIONS));
    }

    private PedigreeResponse.PedigreeNode buildNode(Horse horse, int remainingAncestorLevels) {
        if (horse == null) {
            return null;
        }
        return new PedigreeResponse.PedigreeNode(horse.getId(), horse.getCode(), horse.getName(), horse.getGender(),
                remainingAncestorLevels == 0 ? null : parentNode(horse.getSire(), horse.getSireManualId(),
                        horse.getSireManualName(), HorseGender.STALLION, remainingAncestorLevels - 1),
                remainingAncestorLevels == 0 ? null : parentNode(horse.getDam(), horse.getDamManualId(),
                        horse.getDamManualName(), HorseGender.MARE, remainingAncestorLevels - 1));
    }

    private PedigreeResponse.PedigreeNode parentNode(Horse parent, String manualId, String manualName,
            HorseGender gender, int remainingAncestorLevels) {
        if (parent != null) return buildNode(parent, remainingAncestorLevels);
        if (manualName == null) return null;
        return new PedigreeResponse.PedigreeNode(null, manualId, manualName, gender, null, null);
    }
}
