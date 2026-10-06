package com.racehorse.training.repository;

import com.racehorse.training.entity.RacetrackLane;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RacetrackLaneRepository extends JpaRepository<RacetrackLane, UUID> {
    List<RacetrackLane> findByRacetrackIdOrderByLaneNumberAsc(UUID racetrackId);
    @EntityGraph(attributePaths = "racetrack")
    Optional<RacetrackLane> findDetailById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lane from RacetrackLane lane join fetch lane.racetrack where lane.id = :id")
    Optional<RacetrackLane> findDetailByIdForUpdate(@Param("id") UUID id);
}
