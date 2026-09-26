package com.racehorse.health.repository;

import com.racehorse.health.entity.InjuryMarker;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InjuryMarkerRepository extends JpaRepository<InjuryMarker, UUID> {

    @EntityGraph(attributePaths = {"horse", "createdBy", "recoveryUpdates", "recoveryUpdates.recordedBy"})
    List<InjuryMarker> findByHorseIdOrderByCreatedAtDesc(UUID horseId);

    @EntityGraph(attributePaths = {"horse", "createdBy", "recoveryUpdates", "recoveryUpdates.recordedBy"})
    Optional<InjuryMarker> findWithUpdatesById(UUID id);
}
