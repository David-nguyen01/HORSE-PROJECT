package com.racehorse.horse.service;

import com.racehorse.horse.dto.PedigreeResponse;
import com.racehorse.horse.entity.Horse;
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
        return new PedigreeResponse.PedigreeNode(
                horse.getId(),
                horse.getCode(),
                horse.getName(),
                horse.getGender(),
                remainingAncestorLevels == 0 ? null : buildNode(horse.getSire(), remainingAncestorLevels - 1),
                remainingAncestorLevels == 0 ? null : buildNode(horse.getDam(), remainingAncestorLevels - 1));
    }
}
