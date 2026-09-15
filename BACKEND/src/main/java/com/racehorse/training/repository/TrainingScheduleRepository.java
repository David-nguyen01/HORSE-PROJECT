package com.racehorse.training.repository;

import com.racehorse.training.entity.TrainingSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingScheduleRepository extends JpaRepository<TrainingSchedule, UUID> {

    boolean existsByAssignedStaffIdAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
            UUID staffId, Instant requestedEnd, Instant requestedStart);

    boolean existsByPhaseTrainingPlanId(UUID trainingPlanId);

    @EntityGraph(attributePaths = {"phase", "phase.trainingPlan", "horse", "assignedStaff", "createdBy"})
    Optional<TrainingSchedule> findDetailById(UUID id);

    @EntityGraph(attributePaths = {"phase", "horse", "assignedStaff", "createdBy"})
    @Query("""
            select s from TrainingSchedule s
            where s.scheduledStart < :toExclusive and s.scheduledEnd > :fromInclusive
              and (:horseId is null or s.horse.id = :horseId)
            order by s.scheduledStart asc
            """)
    List<TrainingSchedule> findCalendar(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            @Param("horseId") UUID horseId);

    @EntityGraph(attributePaths = {"phase", "horse", "assignedStaff", "createdBy"})
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
}
