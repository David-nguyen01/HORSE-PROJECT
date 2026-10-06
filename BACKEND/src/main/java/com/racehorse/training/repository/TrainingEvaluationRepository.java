package com.racehorse.training.repository;

import com.racehorse.training.entity.FitnessStatus;
import com.racehorse.training.entity.TrainingEvaluation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingEvaluationRepository extends JpaRepository<TrainingEvaluation, UUID> {

    @EntityGraph(attributePaths = {"horse", "recordedBy", "trainingSchedule"})
    Optional<TrainingEvaluation> findDetailById(UUID id);

    boolean existsByTrainingScheduleId(UUID trainingScheduleId);

    Optional<TrainingEvaluation> findByTrainingScheduleId(UUID trainingScheduleId);

    @EntityGraph(attributePaths = {"horse", "recordedBy", "trainingSchedule"})
    List<TrainingEvaluation> findByTrainingScheduleIdIn(List<UUID> trainingScheduleIds);

    @EntityGraph(attributePaths = {"horse", "recordedBy", "trainingSchedule"})
    Page<TrainingEvaluation> findByHorseIdOrderBySessionDateDescCompletedAtDesc(
            UUID horseId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"horse", "recordedBy", "trainingSchedule"})
    @Query("""
            select e from TrainingEvaluation e
            where e.sessionDate between :from and :to
              and (:horseId is null or e.horse.id = :horseId)
              and (:fitnessStatus is null or e.fitnessStatus = :fitnessStatus)
              and (:phaseId is null or (e.trainingSchedule is not null and e.trainingSchedule.phase.id = :phaseId))
            order by e.sessionDate asc, e.completedAt asc
            """)
    List<TrainingEvaluation> findDashboardData(
            @Param("horseId") UUID horseId,
            @Param("phaseId") UUID phaseId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("fitnessStatus") FitnessStatus fitnessStatus);

    default List<TrainingEvaluation> findDashboardData(
            UUID horseId,
            LocalDate from,
            LocalDate to,
            FitnessStatus fitnessStatus) {
        return findDashboardData(horseId, null, from, to, fitnessStatus);
    }
}
