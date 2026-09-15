package com.racehorse.training.repository;

import com.racehorse.training.entity.RacetrackLane;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RacetrackLaneRepository extends JpaRepository<RacetrackLane, UUID> {
    List<RacetrackLane> findByRacetrackIdOrderByLaneNumberAsc(UUID racetrackId);
    @EntityGraph(attributePaths = "racetrack")
    Optional<RacetrackLane> findDetailById(UUID id);
}
