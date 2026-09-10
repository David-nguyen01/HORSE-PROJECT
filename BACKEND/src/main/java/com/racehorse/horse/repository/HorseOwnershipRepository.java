package com.racehorse.horse.repository;

import com.racehorse.horse.entity.HorseOwnership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HorseOwnershipRepository extends JpaRepository<HorseOwnership, UUID> {

    @EntityGraph(attributePaths = "owner")
    List<HorseOwnership> findByHorseIdOrderByOwnerUsernameAsc(UUID horseId);

    boolean existsByHorseIdAndOwnerId(UUID horseId, UUID ownerId);

    void deleteByHorseId(UUID horseId);
}
