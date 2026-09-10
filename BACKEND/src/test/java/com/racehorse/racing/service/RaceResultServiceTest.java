package com.racehorse.racing.service;

import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.racing.dto.RaceHistoryResponse;
import com.racehorse.racing.entity.RaceResult;
import com.racehorse.racing.repository.RaceResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceResultServiceTest {

    @Mock
    private RaceResultRepository repository;

    @Mock
    private HorseAccessService horseAccessService;

    @Test
    void historyRecalculatesTotalRacesAndWins() {
        UUID horseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Horse horse = horse();
        when(horseAccessService.requireReadable(horseId, ownerId, false)).thenReturn(horse);
        when(repository.findByHorseIdOrderByRaceDateDescCreatedAtDesc(horseId))
                .thenReturn(List.of(result(horse, 1), result(horse, 3), result(horse, 1)));

        RaceHistoryResponse history = new RaceResultService(repository, horseAccessService)
                .history(horseId, ownerId, false);

        assertThat(history.totalRaces()).isEqualTo(3);
        assertThat(history.totalWins()).isEqualTo(2);
        assertThat(history.results()).hasSize(3);
    }

    private RaceResult result(Horse horse, int position) {
        return new RaceResult(
                horse,
                LocalDate.of(2026, 8, position),
                "Race " + position,
                "Racecourse",
                "1600 m",
                position,
                "01:35.20",
                "Jockey",
                "Good",
                "10000000 VND");
    }

    private Horse horse() {
        return new Horse(
                "H-001",
                "Lightning",
                null,
                LocalDate.of(2022, 3, 10),
                HorseGender.STALLION,
                "White blaze",
                "Thoroughbred",
                "Stable A-01",
                HorseStatus.ACTIVE);
    }
}
