package com.racehorse.health.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RaceReadinessResponse(UUID horseId, UUID raceEventId, boolean ready,
                                    String healthStatus, boolean trainingUnlocked,
                                    String latestFitnessStatus, Integer restingHeartRateBpm,
                                    BigDecimal latestWeightKg, List<String> failedRules,
                                    UUID runningPhaseId, Integer evaluatedSessionCount,
                                    Integer sessionShortfall) {}
