package com.racehorse.training.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import com.racehorse.horse.repository.HorseRepository;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.repository.TrainingEvaluationRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import com.racehorse.training.repository.TrialRunBookingRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TrainingOwnerAccessTest {
    private final UUID horseId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID scheduleId = UUID.randomUUID();
    private final Horse horse = mock(Horse.class);
    private final HorseRepository horses = mock(HorseRepository.class);
    private final HorseOwnershipRepository ownerships = mock(HorseOwnershipRepository.class);
    private final HorseAccessService access = new HorseAccessService(horses, ownerships);
    private final TrainingScheduleRepository schedules = mock(TrainingScheduleRepository.class);
    private final TrialRunBookingRepository bookings = mock(TrialRunBookingRepository.class);
    private final TrainingEvaluationRepository evaluations = mock(TrainingEvaluationRepository.class);
    private final TrainingScheduleService service = new TrainingScheduleService(
            schedules, null, null, null, bookings, null, null, access, Clock.systemUTC(), evaluations, null);

    TrainingOwnerAccessTest() {
        when(horses.findDetailById(horseId)).thenReturn(Optional.of(horse));
        when(horse.getId()).thenReturn(horseId);
    }

    @Test
    void onlyTheCurrentTrainerCanUseTrainerAccess() {
        UserAccount currentTrainer = mock(UserAccount.class);
        UUID trainerId = UUID.randomUUID();
        when(currentTrainer.getId()).thenReturn(trainerId);
        when(horse.getCurrentTrainer()).thenReturn(currentTrainer);

        assertThat(access.requireTrainingAccess(horseId, trainerId, false, true, false)).isSameAs(horse);
        assertThatThrownBy(() -> access.requireTrainingAccess(horseId, UUID.randomUUID(), false, true, false))
                .isInstanceOf(ResourceNotFoundException.class);
        when(horse.getCurrentTrainer()).thenReturn(null);
        assertThatThrownBy(() -> access.requireTrainingAccess(horseId, trainerId, false, true, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nonOwnerCannotListOrGuessAnotherHorsesSchedule() {
        schedule();
        assertThatThrownBy(() -> service.ownerSchedules(horseId, ownerId, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.ownerSchedule(scheduleId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(schedules, never()).findOwnerVisibleByHorseId(any());
        verifyNoInteractions(bookings, evaluations);
    }

    @Test
    void ownershipDoesNotAllowReadingArchivedHorses() {
        schedule();
        when(ownerships.existsByHorseIdAndOwnerId(horseId, ownerId)).thenReturn(true);
        when(horse.getArchivedAt()).thenReturn(Instant.parse("2026-10-01T00:00:00Z"));

        assertThatThrownBy(() -> service.ownerSchedules(horseId, ownerId, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.ownerSchedule(scheduleId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(bookings, evaluations);
    }

    @Test
    void ownerCanReadPublishedHistoricalScheduleUsingClubDate() {
        TrainingSchedule schedule = schedule();
        when(ownerships.existsByHorseIdAndOwnerId(horseId, ownerId)).thenReturn(true);
        when(horse.getCurrentTrainer()).thenReturn(mock(UserAccount.class));
        when(schedule.getPublishedAt()).thenReturn(Instant.parse("2026-10-01T00:00:00Z"));
        when(schedule.getScheduledStart()).thenReturn(Instant.parse("2026-10-03T22:00:00Z"));
        when(schedules.findOwnerVisibleByHorseId(horseId)).thenReturn(List.of(schedule));
        when(bookings.findByTrainingScheduleId(scheduleId)).thenReturn(Optional.empty());
        when(evaluations.findByTrainingScheduleId(scheduleId)).thenReturn(Optional.empty());

        var response = service.ownerSchedule(scheduleId, ownerId);
        assertThat(response.trainingDate()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(response.racetrack()).isNull();
        assertThat(service.ownerSchedules(horseId, ownerId, LocalDate.of(2026, 10, 4),
                LocalDate.of(2026, 10, 4))).containsExactly(response);
        assertThat(service.ownerSchedules(horseId, ownerId, LocalDate.of(2026, 10, 3),
                LocalDate.of(2026, 10, 3))).isEmpty();
    }

    @Test
    void ownerCannotReadUnpublishedOrCancelledSchedules() {
        TrainingSchedule schedule = schedule();
        when(ownerships.existsByHorseIdAndOwnerId(horseId, ownerId)).thenReturn(true);
        when(horse.getCurrentTrainer()).thenReturn(mock(UserAccount.class));
        assertThatThrownBy(() -> service.ownerSchedule(scheduleId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
        when(schedule.getPublishedAt()).thenReturn(Instant.parse("2026-10-01T00:00:00Z"));
        when(schedule.getCancelledAt()).thenReturn(Instant.parse("2026-10-02T00:00:00Z"));
        assertThatThrownBy(() -> service.ownerSchedule(scheduleId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
        when(schedules.findOwnerVisibleByHorseId(horseId)).thenReturn(List.of(schedule));
        assertThat(service.ownerSchedules(horseId, ownerId, null, null)).isEmpty();
        verifyNoInteractions(bookings, evaluations);
    }

    @Test
    void ownerCannotViewScheduleBeforeHorseHasTrainer() {
        schedule();
        when(ownerships.existsByHorseIdAndOwnerId(horseId, ownerId)).thenReturn(true);

        assertThat(service.ownerSchedules(horseId, ownerId, null, null)).isEmpty();
        assertThatThrownBy(() -> service.ownerSchedule(scheduleId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(schedules, never()).findOwnerVisibleByHorseId(any());
    }

    private TrainingSchedule schedule() {
        TrainingSchedule schedule = mock(TrainingSchedule.class);
        when(schedule.getId()).thenReturn(scheduleId);
        when(schedule.getHorse()).thenReturn(horse);
        when(schedules.findDetailById(scheduleId)).thenReturn(Optional.of(schedule));
        return schedule;
    }
}
