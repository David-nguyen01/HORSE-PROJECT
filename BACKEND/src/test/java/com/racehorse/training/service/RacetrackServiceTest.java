package com.racehorse.training.service;

import com.racehorse.common.exception.InvalidInputException;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.CreateRacetrackRequest;
import com.racehorse.training.dto.TrackLaneAvailabilityResponse;
import com.racehorse.training.entity.Racetrack;
import com.racehorse.training.entity.RacetrackLane;
import com.racehorse.training.entity.TrialRunBooking;
import com.racehorse.training.repository.RacetrackLaneRepository;
import com.racehorse.training.repository.RacetrackRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import com.racehorse.training.repository.TrialRunBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RacetrackServiceTest {
    private final RacetrackRepository trackRepository = mock(RacetrackRepository.class);
    private final RacetrackLaneRepository laneRepository = mock(RacetrackLaneRepository.class);
    private final TrialRunBookingRepository bookingRepository = mock(TrialRunBookingRepository.class);
    private final TrainingScheduleRepository scheduleRepository = mock(TrainingScheduleRepository.class);
    private final HorseAccessService horseAccessService = mock(HorseAccessService.class);
    private RacetrackService service;

    @BeforeEach
    void setUp() {
        service = new RacetrackService(trackRepository, laneRepository, bookingRepository,
                scheduleRepository, horseAccessService, "Asia/Ho_Chi_Minh");
    }

    @Test
    void rejectsTrackCreationUnlessItHasExactlyFourLanes() {
        assertThatThrownBy(() -> service.create(new CreateRacetrackRequest("Track", null, 5)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("đúng 4 làn");
        verify(trackRepository, never()).save(any());
    }

    @Test
    void availabilityReturnsOnlyFreeLanesForTheRequestedDate() {
        UUID trackId = UUID.randomUUID();
        Racetrack track = new Racetrack("Track", null);
        ReflectionTestUtils.setField(track, "id", trackId);
        List<RacetrackLane> lanes = List.of(1, 2, 3, 4).stream().map(number -> {
            RacetrackLane lane = new RacetrackLane(track, number);
            ReflectionTestUtils.setField(lane, "id", UUID.randomUUID());
            return lane;
        }).toList();
        TrialRunBooking booking = mock(TrialRunBooking.class);
        when(booking.getLane()).thenReturn(lanes.get(1));
        when(trackRepository.findById(trackId)).thenReturn(java.util.Optional.of(track));
        when(laneRepository.findByRacetrackIdOrderByLaneNumberAsc(trackId)).thenReturn(lanes);
        when(bookingRepository.findByLaneRacetrackIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
                any(), any(), any())).thenReturn(List.of(booking));

        List<TrackLaneAvailabilityResponse> availability =
                service.availability(trackId, LocalDate.of(2026, 9, 16));

        assertThat(availability).extracting(TrackLaneAvailabilityResponse::laneNumber)
                .containsExactly(1, 3, 4);
        assertThat(availability).allMatch(lane -> lane.date().equals(LocalDate.of(2026, 9, 16)));
    }
}
