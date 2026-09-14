package com.racehorse.training.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.repository.HorseRepository;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.FitnessDashboardResponse;
import com.racehorse.training.entity.FitnessStatus;
import com.racehorse.training.entity.TrainingEvaluation;
import com.racehorse.training.repository.TrainingEvaluationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FitnessDashboardService {

    private final TrainingEvaluationRepository evaluationRepository;
    private final HorseRepository horseRepository;
    private final HorseAccessService horseAccessService;
    private final Clock clock;

    public FitnessDashboardService(
            TrainingEvaluationRepository evaluationRepository,
            HorseRepository horseRepository,
            HorseAccessService horseAccessService,
            Clock clock) {
        this.evaluationRepository = evaluationRepository;
        this.horseRepository = horseRepository;
        this.horseAccessService = horseAccessService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FitnessDashboardResponse get(
            UUID horseId,
            LocalDate from,
            LocalDate to,
            FitnessStatus fitnessStatus,
            String query) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate effectiveTo = to == null ? today : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(29) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException("Ngày bắt đầu không được sau ngày kết thúc");
        }
        if (effectiveTo.isAfter(today)) {
            throw new BusinessException("Khoảng thời gian dashboard không được nằm trong tương lai");
        }
        if (horseId != null) horseAccessService.requireActive(horseId);

        List<TrainingEvaluation> evaluations = evaluationRepository.findDashboardData(
                horseId, effectiveFrom, effectiveTo, null);
        String normalizedQuery = query == null || query.isBlank()
                ? null : query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery != null) {
            evaluations = evaluations.stream()
                    .filter(e -> matches(e.getHorse(), normalizedQuery))
                    .toList();
        }

        Map<UUID, TrainingEvaluation> latestByHorse = new LinkedHashMap<>();
        for (TrainingEvaluation evaluation : evaluations) {
            latestByHorse.put(evaluation.getHorse().getId(), evaluation);
        }

        List<Horse> horses = horseId == null
                ? horseRepository.findByArchivedAtIsNullOrderByNameAsc()
                : List.of(horseAccessService.requireActive(horseId));
        List<FitnessDashboardResponse.HorseFitnessSummary> horseSummaries = new ArrayList<>();
        for (Horse horse : horses) {
            if (normalizedQuery != null && !matches(horse, normalizedQuery)) continue;
            TrainingEvaluation latest = latestByHorse.get(horse.getId());
            if (fitnessStatus != null && (latest == null || latest.getFitnessStatus() != fitnessStatus)) continue;
            horseSummaries.add(new FitnessDashboardResponse.HorseFitnessSummary(
                    horse.getId(), horse.getCode(), horse.getName(), horse.getStableLocation(), horse.getStatus(),
                    latest == null ? null : latest.getFitnessStatus(),
                    latest == null ? null : latest.getId(),
                    latest == null ? null : latest.getSessionDate()));
        }

        java.util.Set<UUID> visibleHorseIds = horseSummaries.stream()
                .map(FitnessDashboardResponse.HorseFitnessSummary::horseId)
                .collect(java.util.stream.Collectors.toSet());
        List<FitnessDashboardResponse.FitnessDataPoint> points = evaluations.stream()
                .filter(e -> visibleHorseIds.contains(e.getHorse().getId()))
                .map(e -> new FitnessDashboardResponse.FitnessDataPoint(
                        e.getId(), e.getHorse().getId(), e.getHorse().getCode(), e.getHorse().getName(),
                        e.getSessionDate(), e.getTrainingLoad(), e.getAverageSpeedKmh(),
                        e.getHeartRateRecoveryBpm(), e.getFitnessStatus()))
                .toList();
        return new FitnessDashboardResponse(
                clock.instant(),
                new FitnessDashboardResponse.FilterSummary(horseId, effectiveFrom, effectiveTo, fitnessStatus),
                horseSummaries,
                points);
    }

    private boolean matches(Horse horse, String query) {
        return horse.getName().toLowerCase(Locale.ROOT).contains(query)
                || horse.getCode().toLowerCase(Locale.ROOT).contains(query);
    }
}
