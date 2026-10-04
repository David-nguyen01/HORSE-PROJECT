package com.racehorse.training.repository;

import com.racehorse.training.entity.TrainingSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingScheduleRepository extends JpaRepository<TrainingSchedule, UUID> {

    boolean existsByAssignedStaffIdAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
            UUID staffId, Instant requestedEnd, Instant requestedStart);

    boolean existsByPhaseTrainingPlanId(UUID trainingPlanId);

    @EntityGraph(attributePaths = {"phase", "phase.trainingPlan", "horse", "assignedStaff", "createdBy", "veterinarianUser", "racetrack"})
    Optional<TrainingSchedule> findDetailById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TrainingSchedule s WHERE s.id = :id")
    Optional<TrainingSchedule> findByIdWithLock(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"phase", "horse", "assignedStaff", "createdBy", "veterinarianUser", "racetrack"})
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

    @EntityGraph(attributePaths = {"phase", "horse", "assignedStaff", "createdBy", "veterinarianUser", "racetrack"})
    @Query("""
            select s from TrainingSchedule s
            where s.assignedStaff.id = :staffId
              and s.cancelledAt is null
              and s.scheduledStart < :toExclusive and s.scheduledEnd > :fromInclusive
            order by s.scheduledStart asc
            """)
    List<TrainingSchedule> findPublishedForStaff(
            @Param("staffId") UUID staffId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @EntityGraph(attributePaths = {"phase", "horse", "assignedStaff", "createdBy", "veterinarianUser", "racetrack"})
    @Query("""
            select s from TrainingSchedule s
            where s.veterinarianUser is null
              and s.cancelledAt is null
              and s.scheduledStart between :fromInclusive and :toInclusive
              and s.scheduledStart > :deadline
            order by s.scheduledStart asc
            """)
    List<TrainingSchedule> findOpenForVet(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toInclusive") Instant toInclusive,
            @Param("deadline") Instant deadline);

    @EntityGraph(attributePaths = {"phase", "horse", "assignedStaff", "createdBy", "veterinarianUser", "racetrack"})
    @Query("""
            select s from TrainingSchedule s
            where s.assignedStaff is null
              and s.cancelledAt is null
              and s.scheduledStart between :fromInclusive and :toInclusive
              and s.scheduledStart > :deadline
            order by s.scheduledStart asc
            """)
    List<TrainingSchedule> findOpenForStaff(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toInclusive") Instant toInclusive,
            @Param("deadline") Instant deadline);

    @EntityGraph(attributePaths = {"phase", "horse", "assignedStaff", "createdBy", "veterinarianUser", "racetrack"})
    @Query("""
            select s from TrainingSchedule s
            where s.veterinarianUser.id = :vetId
              and s.cancelledAt is null
              and s.scheduledStart between :fromInclusive and :toInclusive
            order by s.scheduledStart asc
            """)
    List<TrainingSchedule> findByVetId(
            @Param("vetId") UUID vetId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toInclusive") Instant toInclusive);

    @Query(value = """
            SELECT CASE WHEN COUNT(*) > 0 THEN CAST(1 AS BIT) ELSE CAST(0 AS BIT) END
            FROM dbo.training_schedules
            WHERE veterinarian_user_id = :vetId
              AND cancelled_at IS NULL
              AND CAST(scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS DATE) = :date
            """, nativeQuery = true)
    boolean existsByVetOnDate(
            @Param("vetId") UUID vetId,
            @Param("date") LocalDate date);

    @Query(value = """
            SELECT CASE WHEN COUNT(*) > 0 THEN CAST(1 AS BIT) ELSE CAST(0 AS BIT) END
            FROM dbo.training_schedules
            WHERE assigned_staff_id = :staffId
              AND cancelled_at IS NULL
              AND CAST(scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS DATE) = :date
            """, nativeQuery = true)
    boolean existsByStaffOnDate(
            @Param("staffId") UUID staffId,
            @Param("date") LocalDate date);

    @Query(value = """
            SELECT * FROM dbo.training_schedules
            WHERE cancelled_at IS NULL
              AND (assigned_staff_id IS NULL OR veterinarian_user_id IS NULL)
              AND CAST(scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS DATE) = :targetDate
            """, nativeQuery = true)
    List<TrainingSchedule> findNeedingAssignmentForDate(@Param("targetDate") LocalDate targetDate);
}
