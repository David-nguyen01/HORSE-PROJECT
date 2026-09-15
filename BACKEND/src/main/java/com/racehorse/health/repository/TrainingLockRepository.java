package com.racehorse.health.repository;

import com.racehorse.health.entity.TrainingLock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingLockRepository extends JpaRepository<TrainingLock, UUID> {

    @EntityGraph(attributePaths = {"horse", "lockedBy", "releasedBy"})
    Optional<TrainingLock> findByHorseIdAndReleasedAtIsNull(UUID horseId);

    @EntityGraph(attributePaths = {"horse", "lockedBy", "releasedBy"})
    Optional<TrainingLock> findDetailById(UUID id);

    @EntityGraph(attributePaths = {"horse", "lockedBy", "releasedBy"})
    List<TrainingLock> findByHorseIdOrderByLockedAtDesc(UUID horseId);
}
