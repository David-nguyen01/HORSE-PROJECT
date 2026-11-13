package com.racehorse.nutrition.repository;

import com.racehorse.nutrition.entity.DailyFeedingLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyFeedingLogRepository extends JpaRepository<DailyFeedingLogEntity, UUID> {

    Optional<DailyFeedingLogEntity> findByHorseIdAndFeedingDateAndMealKey(UUID horseId, LocalDate feedingDate, String mealKey);

    List<DailyFeedingLogEntity> findByHorseIdAndFeedingDate(UUID horseId, LocalDate feedingDate);

    List<DailyFeedingLogEntity> findByHorseIdOrderByFeedingDateDescFeedingTimeDesc(UUID horseId);

    List<DailyFeedingLogEntity> findByHorseIdAndFeedingDateBetweenOrderByFeedingDateDescFeedingTimeDesc(
            UUID horseId, LocalDate startDate, LocalDate endDate);
}
