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

    @EntityGraph(attributePaths = {"horse", "recordedBy"})
    Optional<TrainingEvaluation> findDetailById(UUID id);

    @EntityGraph(attributePaths = {"horse", "recordedBy"})
    Page<TrainingEvaluation> findByHorseIdOrderBySessionDateDescCompletedAtDesc(
            UUID horseId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"horse", "recordedBy"})
    @Query("""
            select e from TrainingEvaluation e
            where e.sessionDate between :from and :to
              and (:horseId is null or e.horse.id = :horseId)
              and (:fitnessStatus is null or e.fitnessStatus = :fitnessStatus)
            order by e.sessionDate asc, e.completedAt asc
            """)
    List<TrainingEvaluation> findDashboardData(
            @Param("horseId") UUID horseId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("fitnessStatus") FitnessStatus fitnessStatus);
}
