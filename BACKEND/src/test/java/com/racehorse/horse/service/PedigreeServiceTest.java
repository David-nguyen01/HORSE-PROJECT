package com.racehorse.horse.service;

import com.racehorse.horse.dto.PedigreeResponse;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PedigreeServiceTest {

    @Test
    void returnsThreeAncestorLevels() {
        Horse greatGrandSire = horse("H-GGS", "Great Grand Sire");
        Horse grandSire = horse("H-GS", "Grand Sire");
        grandSire.assignParents(greatGrandSire, null);
        Horse sire = horse("H-S", "Sire");
        sire.assignParents(grandSire, null);
        Horse child = horse("H-C", "Child");
        child.assignParents(sire, null);
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        HorseAccessService accessService = mock(HorseAccessService.class);
        when(accessService.requireReadable(horseId, userId, true)).thenReturn(child);

        PedigreeResponse response = new PedigreeService(accessService).get(horseId, userId, true);

        assertThat(response.ancestorGenerations()).isEqualTo(3);
        assertThat(response.horse().sire().sire().sire().name()).isEqualTo("Great Grand Sire");
    }

    private Horse horse(String code, String name) {
        return new Horse(
                code,
                name,
                null,
                LocalDate.of(2020, 1, 1),
                HorseGender.STALLION,
                "Markings",
                "Thoroughbred",
                "Stable A",
                HorseStatus.ACTIVE);
    }
}
