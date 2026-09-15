package com.racehorse.training.repository;

import com.racehorse.training.entity.TrainingPlanPhase;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TrainingPlanPhaseRepository extends JpaRepository<TrainingPlanPhase, UUID> {

    @EntityGraph(attributePaths = {"trainingPlan", "trainingPlan.horse"})
    Optional<TrainingPlanPhase> findDetailById(UUID id);
}
