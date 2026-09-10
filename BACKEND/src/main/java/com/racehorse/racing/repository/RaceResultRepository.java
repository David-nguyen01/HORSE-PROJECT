package com.racehorse.racing.repository;

import com.racehorse.racing.entity.RaceResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceResultRepository extends JpaRepository<RaceResult, UUID> {

    List<RaceResult> findByHorseIdOrderByRaceDateDescCreatedAtDesc(UUID horseId);

    Optional<RaceResult> findByIdAndHorseId(UUID id, UUID horseId);
}
