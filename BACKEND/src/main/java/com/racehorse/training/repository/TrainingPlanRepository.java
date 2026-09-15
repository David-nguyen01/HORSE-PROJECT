package com.racehorse.training.repository;

import com.racehorse.training.entity.TrainingPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, UUID> {

    @EntityGraph(attributePaths = {"horse", "sourcePlan", "createdBy", "phases"})
    Optional<TrainingPlan> findDetailById(UUID id);

    @EntityGraph(attributePaths = {"horse", "sourcePlan", "createdBy", "phases"})
    List<TrainingPlan> findByHorseIdOrderByCreatedAtDesc(UUID horseId);
}
