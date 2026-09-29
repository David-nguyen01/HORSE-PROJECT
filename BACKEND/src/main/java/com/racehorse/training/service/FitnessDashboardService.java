package com.racehorse.training.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.repository.HorseRepository;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.FitnessDashboardResponse;
import com.racehorse.training.entity.FitnessStatus;
import com.racehorse.training.entity.TrainingEvaluation;
import com.racehorse.training.entity.TrainingPlanPhase;
import com.racehorse.training.repository.TrainingEvaluationRepository;
import com.racehorse.training.repository.TrainingPlanPhaseRepository;
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
    private final TrainingPlanPhaseRepository phaseRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public FitnessDashboardService(
            TrainingEvaluationRepository evaluationRepository,
            HorseRepository horseRepository,
            HorseAccessService horseAccessService,
            TrainingPlanPhaseRepository phaseRepository,
            Clock clock) {
        this.evaluationRepository = evaluationRepository;
        this.horseRepository = horseRepository;
        this.horseAccessService = horseAccessService;
        this.phaseRepository = phaseRepository;
        this.clock = clock;
    }

    public FitnessDashboardService(
            TrainingEvaluationRepository evaluationRepository,
            HorseRepository horseRepository,
            HorseAccessService horseAccessService,
            Clock clock) {
        this(evaluationRepository, horseRepository, horseAccessService, null, clock);
    }

    @Transactional(readOnly = true)
    public FitnessDashboardResponse get(
            UUID horseId,
            UUID phaseId,
            LocalDate from,
            LocalDate to,
            FitnessStatus fitnessStatus,
            String query) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        UUID targetHorseId = horseId;
        TrainingPlanPhase phase = null;
        if (phaseId != null) {
            if (phaseRepository == null) {
                throw new IllegalStateException("phaseRepository is not configured");
            }
            phase = phaseRepository.findDetailById(phaseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giai đoạn huấn luyện: " + phaseId));
            UUID phaseHorseId = phase.getTrainingPlan().getHorse().getId();
            if (targetHorseId != null && !targetHorseId.equals(phaseHorseId)) {
                throw new BusinessException("Giai đoạn huấn luyện không thuộc về ngựa được chọn");
            }
            targetHorseId = phaseHorseId;
        }

        LocalDate effectiveTo = to == null
                ? (phase != null && !phase.getEndDate().isAfter(today) ? phase.getEndDate() : today)
                : to;
        LocalDate effectiveFrom = from == null
                ? (phase != null ? phase.getStartDate() : effectiveTo.minusDays(29))
                : from;
        if (phase != null && from == null && phase.getStartDate().isAfter(today)) {
            throw new BusinessException("Giai đoạn huấn luyện chưa bắt đầu");
        }
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException("Ngày bắt đầu không được sau ngày kết thúc");
        }
        if (effectiveTo.isAfter(today)) {
            throw new BusinessException("Khoảng thời gian dashboard không được nằm trong tương lai");
        }
        if (targetHorseId != null) horseAccessService.requireActive(targetHorseId);

        List<TrainingEvaluation> evaluations = evaluationRepository.findDashboardData(
                targetHorseId, phaseId, effectiveFrom, effectiveTo, null);
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

        List<Horse> horses = targetHorseId == null
                ? horseRepository.findByArchivedAtIsNullOrderByNameAsc()
                : List.of(horseAccessService.requireActive(targetHorseId));
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
                new FitnessDashboardResponse.FilterSummary(targetHorseId, phaseId, effectiveFrom, effectiveTo, fitnessStatus),
                horseSummaries,
                points);
    }

    @Transactional(readOnly = true)
    public FitnessDashboardResponse get(
            UUID horseId,
            LocalDate from,
            LocalDate to,
            FitnessStatus fitnessStatus,
            String query) {
        return get(horseId, null, from, to, fitnessStatus, query);
    }

    private boolean matches(Horse horse, String query) {
        return horse.getName().toLowerCase(Locale.ROOT).contains(query)
                || horse.getCode().toLowerCase(Locale.ROOT).contains(query);
    }
}
