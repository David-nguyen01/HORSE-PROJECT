package com.racehorse.training.service;

import com.racehorse.horse.entity.Horse;
import com.racehorse.training.dto.CreateTrialRunBookingRequest;
import com.racehorse.training.dto.TrackSlotResponse;
import com.racehorse.training.entity.Racetrack;
import com.racehorse.training.entity.RacetrackLane;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.entity.TrialRunBooking;
import com.racehorse.training.repository.RacetrackLaneRepository;
import com.racehorse.training.repository.RacetrackRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import com.racehorse.training.repository.TrialRunBookingRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RacetrackServiceTest {
    private final RacetrackRepository trackRepository = mock(RacetrackRepository.class);
    private final RacetrackLaneRepository laneRepository = mock(RacetrackLaneRepository.class);
    private final TrialRunBookingRepository bookingRepository = mock(TrialRunBookingRepository.class);
    private final TrainingScheduleRepository scheduleRepository = mock(TrainingScheduleRepository.class);
    private final RacetrackService service = new RacetrackService(trackRepository, laneRepository,
            bookingRepository, scheduleRepository, "Asia/Ho_Chi_Minh");

    @Test
    void generatesExactlyEighteenOneHourSlotsPerActiveLane() {
        UUID trackId = UUID.randomUUID();
        UUID laneId = UUID.randomUUID();
        Racetrack track = mock(Racetrack.class);
        RacetrackLane lane = mock(RacetrackLane.class);
        when(track.getOpensAt()).thenReturn(LocalTime.of(6, 0));
        when(track.getClosesAt()).thenReturn(LocalTime.MIDNIGHT);
        when(track.isClosesNextDay()).thenReturn(true);
        when(track.getSlotMinutes()).thenReturn(60);
        when(lane.getId()).thenReturn(laneId);
        when(lane.getLaneNumber()).thenReturn(1);
        when(lane.isActive()).thenReturn(true);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(laneRepository.findByRacetrackIdOrderByLaneNumberAsc(trackId)).thenReturn(List.of(lane));
        when(bookingRepository.findByLaneRacetrackIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
                eq(trackId), any(), any())).thenReturn(List.of());

        List<TrackSlotResponse> slots = service.slots(trackId, LocalDate.of(2026, 9, 16));

        assertThat(slots).hasSize(18);
        assertThat(slots.getFirst().startTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(slots.getFirst().endTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(slots.getLast().startTime()).isEqualTo(LocalTime.of(23, 0));
        assertThat(slots.getLast().endTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(slots.getLast().endsNextDay()).isTrue();
        assertThat(slots).allMatch(TrackSlotResponse::available);
    }

    @Test
    void bookingAtElevenPmEndsAtMidnightAndUsesConfiguredClubTimeZone() {
        UUID laneId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        Racetrack track = mock(Racetrack.class);
        RacetrackLane lane = mock(RacetrackLane.class);
        TrainingSchedule schedule = mock(TrainingSchedule.class);
        Horse horse = mock(Horse.class);
        when(track.isActive()).thenReturn(true);
        when(track.getOpensAt()).thenReturn(LocalTime.of(6, 0));
        when(track.getClosesAt()).thenReturn(LocalTime.MIDNIGHT);
        when(track.isClosesNextDay()).thenReturn(true);
        when(track.getSlotMinutes()).thenReturn(60);
        when(lane.getId()).thenReturn(laneId);
        when(lane.isActive()).thenReturn(true);
        when(lane.getRacetrack()).thenReturn(track);
        when(schedule.getId()).thenReturn(scheduleId);
        when(schedule.getHorse()).thenReturn(horse);
        when(laneRepository.findDetailById(laneId)).thenReturn(Optional.of(lane));
        when(scheduleRepository.findDetailById(scheduleId)).thenReturn(Optional.of(schedule));
        when(bookingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.book(new CreateTrialRunBookingRequest(scheduleId, laneId,
                LocalDate.of(2026, 9, 16), LocalTime.of(23, 0)));

        var captor = org.mockito.ArgumentCaptor.forClass(TrialRunBooking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(Duration.between(captor.getValue().getBookedStart(), captor.getValue().getBookedEnd()))
                .isEqualTo(Duration.ofHours(1));
        assertThat(captor.getValue().getBookedStart().toString()).isEqualTo("2026-09-16T16:00:00Z");
        assertThat(captor.getValue().getBookedEnd().toString()).isEqualTo("2026-09-16T17:00:00Z");
    }
}
