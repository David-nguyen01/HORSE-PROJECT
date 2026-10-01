package com.racehorse.training.repository;

import com.racehorse.training.entity.TrialRunBooking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TrialRunBookingRepository extends JpaRepository<TrialRunBooking, UUID> {
    boolean existsByTrainingScheduleId(UUID scheduleId);
    boolean existsByLaneIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
            UUID laneId, Instant end, Instant start);
    boolean existsByTrainingScheduleHorseIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
            UUID horseId, Instant end, Instant start);
    @EntityGraph(attributePaths = {"lane", "lane.racetrack", "trainingSchedule", "trainingSchedule.horse"})
    List<TrialRunBooking> findByLaneRacetrackIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
            UUID racetrackId, Instant end, Instant start);
    @EntityGraph(attributePaths = {"lane", "lane.racetrack", "trainingSchedule", "trainingSchedule.horse"})
    @Query("select b from TrialRunBooking b where (:racetrackId is null or b.lane.racetrack.id=:racetrackId) " +
            "and (:horseId is null or b.trainingSchedule.horse.id=:horseId) and (:includeCancelled=true or b.cancelledAt is null) " +
            "and (:fromTime is null or b.bookedStart>=:fromTime) and (:toTime is null or b.bookedStart<:toTime) order by b.bookedStart desc")
    List<TrialRunBooking> search(@Param("racetrackId") UUID racetrackId,@Param("horseId") UUID horseId,
            @Param("includeCancelled") boolean includeCancelled,@Param("fromTime") Instant fromTime,@Param("toTime") Instant toTime);
    @EntityGraph(attributePaths = {"lane", "lane.racetrack", "trainingSchedule", "trainingSchedule.horse"})
    java.util.Optional<TrialRunBooking> findDetailById(UUID id);
}
