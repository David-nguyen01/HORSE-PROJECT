package com.racehorse.training.repository;

import com.racehorse.training.entity.TrialRunBooking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TrialRunBookingRepository extends JpaRepository<TrialRunBooking, UUID> {
    boolean existsByTrainingScheduleId(UUID scheduleId);
    boolean existsByLaneIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
            UUID laneId, Instant end, Instant start);
    @EntityGraph(attributePaths = {"lane", "lane.racetrack", "trainingSchedule", "trainingSchedule.horse"})
    List<TrialRunBooking> findByLaneRacetrackIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
            UUID racetrackId, Instant end, Instant start);
}
