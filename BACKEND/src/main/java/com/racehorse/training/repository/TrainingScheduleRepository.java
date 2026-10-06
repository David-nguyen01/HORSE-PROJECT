package com.racehorse.training.repository;

import com.racehorse.training.entity.TrainingSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingScheduleRepository extends JpaRepository<TrainingSchedule, UUID> {

    boolean existsByAssignedStaffIdAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
            UUID staffId, Instant requestedEnd, Instant requestedStart);

    boolean existsByHorseIdAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
            UUID horseId, Instant requestedEnd, Instant requestedStart);
    boolean existsByHorseIdAndIdNotAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
            UUID horseId, UUID scheduleId, Instant requestedEnd, Instant requestedStart);

    boolean existsByRacetrackIdAndIdNotAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
            UUID racetrackId, UUID scheduleId, Instant requestedEnd, Instant requestedStart);

    boolean existsByAssignedStaffIdAndIdNotAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
            UUID staffId, UUID scheduleId, Instant requestedEnd, Instant requestedStart);

    boolean existsByVeterinarianIdAndIdNotAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
            UUID veterinarianId, UUID scheduleId, Instant requestedEnd, Instant requestedStart);

    boolean existsByPhaseTrainingPlanId(UUID trainingPlanId);

    @EntityGraph(attributePaths = {"phase", "phase.trainingPlan", "horse", "assignedStaff", "veterinarian", "racetrack", "createdBy"})
    @Query("select s from TrainingSchedule s where s.phase.trainingPlan.id = :planId order by s.scheduledStart")
    List<TrainingSchedule> findPlanSessions(@Param("planId") UUID planId);

    @EntityGraph(attributePaths = {"phase", "phase.trainingPlan", "horse", "assignedStaff", "veterinarian", "racetrack", "createdBy"})
    @Query("select s from TrainingSchedule s where s.phase.trainingPlan.id in :planIds order by s.scheduledStart")
    List<TrainingSchedule> findPlanSessions(@Param("planIds") List<UUID> planIds);

    @EntityGraph(attributePaths = {"phase", "phase.trainingPlan", "horse", "assignedStaff", "veterinarian", "racetrack", "createdBy"})
    Optional<TrainingSchedule> findDetailById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TrainingSchedule s where s.id = :id")
    Optional<TrainingSchedule> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"phase", "horse", "assignedStaff", "veterinarian", "racetrack", "createdBy"})
    @Query("""
            select s from TrainingSchedule s
            where s.scheduledStart < :toExclusive and s.scheduledEnd > :fromInclusive
              and (:horseId is null or s.horse.id = :horseId)
              and (:trainerId is null or s.horse.currentTrainer is not null and s.horse.currentTrainer.id = :trainerId)
            order by s.scheduledStart asc
            """)
    List<TrainingSchedule> findCalendar(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            @Param("horseId") UUID horseId,
            @Param("trainerId") UUID trainerId);

    @EntityGraph(attributePaths = {"phase", "phase.trainingPlan", "horse", "racetrack"})
    @Query("""
            select s from TrainingSchedule s
            where s.horse.id = :horseId and s.publishedAt is not null and s.cancelledAt is null
            order by s.scheduledStart desc
            """)
    List<TrainingSchedule> findOwnerVisibleByHorseId(@Param("horseId") UUID horseId);

    @EntityGraph(attributePaths = {"phase", "horse", "assignedStaff", "veterinarian", "racetrack", "createdBy"})
    @Query("""
            select s from TrainingSchedule s
            where s.assignedStaff.id = :staffId
              and s.publishedAt is not null
              and s.cancelledAt is null
              and s.scheduledStart < :toExclusive and s.scheduledEnd > :fromInclusive
            order by s.scheduledStart asc
            """)
    List<TrainingSchedule> findPublishedForStaff(
            @Param("staffId") UUID staffId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @EntityGraph(attributePaths = {"phase", "horse", "assignedStaff", "veterinarian", "racetrack", "createdBy"})
    @Query("select s from TrainingSchedule s where s.publishedAt is not null and s.cancelledAt is null and s.scheduledStart >= :from and s.scheduledStart < :to order by s.scheduledStart")
    List<TrainingSchedule> findPublishedBetween(@Param("from") Instant from, @Param("to") Instant to);

    @EntityGraph(attributePaths = {"phase", "horse", "assignedStaff", "veterinarian", "racetrack", "createdBy"})
    @Query("select s from TrainingSchedule s where (s.assignedStaff.id=:userId or s.veterinarian.id=:userId) and s.cancelledAt is null and s.scheduledStart < :toExclusive and s.scheduledEnd > :fromInclusive order by s.scheduledStart")
    List<TrainingSchedule> findAssignedToUser(@Param("userId") UUID userId, @Param("fromInclusive") Instant fromInclusive, @Param("toExclusive") Instant toExclusive);
}
