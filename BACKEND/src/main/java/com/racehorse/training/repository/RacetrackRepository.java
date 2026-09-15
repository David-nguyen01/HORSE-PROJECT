package com.racehorse.training.repository;

import com.racehorse.training.entity.Racetrack;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RacetrackRepository extends JpaRepository<Racetrack, UUID> {
    List<Racetrack> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
