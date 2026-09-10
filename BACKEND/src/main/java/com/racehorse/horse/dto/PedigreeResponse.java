package com.racehorse.horse.dto;

import com.racehorse.horse.entity.HorseGender;

import java.util.UUID;

public record PedigreeResponse(
        int ancestorGenerations,
        PedigreeNode horse
) {
    public record PedigreeNode(
            UUID id,
            String code,
            String name,
            HorseGender gender,
            PedigreeNode sire,
            PedigreeNode dam
    ) {
    }
}
