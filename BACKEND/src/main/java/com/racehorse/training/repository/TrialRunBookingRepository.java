package com.racehorse.training.repository;

import com.racehorse.training.entity.TrialRunBooking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface TrialRunBookingRepository extends JpaRepository<TrialRunBooking, UUID> {
    boolean existsByTrainingScheduleId(UUID scheduleId);
    @EntityGraph(attributePaths = {"lane", "lane.racetrack", "trainingSchedule", "trainingSchedule.horse"})
    java.util.Optional<TrialRunBooking> findByTrainingScheduleId(UUID scheduleId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from TrialRunBooking b join fetch b.lane lane join fetch lane.racetrack where b.trainingSchedule.id = :scheduleId")
    java.util.Optional<TrialRunBooking> findByTrainingScheduleIdForUpdate(@Param("scheduleId") UUID scheduleId);
    boolean existsByLaneIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
            UUID laneId, Instant end, Instant start);
    boolean existsByLaneIdAndTrainingScheduleIdNotAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
            UUID laneId, UUID scheduleId, Instant end, Instant start);
    boolean existsByTrainingScheduleHorseIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
            UUID horseId, Instant end, Instant start);
    @EntityGraph(attributePaths = {"lane", "lane.racetrack", "trainingSchedule", "trainingSchedule.horse"})
    List<TrialRunBooking> findByLaneRacetrackIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
            UUID racetrackId, Instant end, Instant start);
    @EntityGraph(attributePaths = {"lane", "lane.racetrack", "trainingSchedule", "trainingSchedule.horse"})
    @Query("select b from TrialRunBooking b where (:racetrackId is null or b.lane.racetrack.id=:racetrackId) " +
            "and (:horseId is null or b.trainingSchedule.horse.id=:horseId) and (:includeCancelled=true or b.cancelledAt is null) " +
            "and (:trainerId is null or b.trainingSchedule.horse.currentTrainer is not null and b.trainingSchedule.horse.currentTrainer.id=:trainerId) " +
            "and (:fromTime is null or b.bookedStart>=:fromTime) and (:toTime is null or b.bookedStart<:toTime) order by b.bookedStart desc")
    List<TrialRunBooking> search(@Param("racetrackId") UUID racetrackId,@Param("horseId") UUID horseId,
            @Param("includeCancelled") boolean includeCancelled,@Param("trainerId") UUID trainerId,
            @Param("fromTime") Instant fromTime,@Param("toTime") Instant toTime);
    @EntityGraph(attributePaths = {"lane", "lane.racetrack", "trainingSchedule", "trainingSchedule.horse"})
    java.util.Optional<TrialRunBooking> findDetailById(UUID id);
}
