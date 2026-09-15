package com.racehorse.training.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.CopyTrainingPlanRequest;
import com.racehorse.training.dto.TrainingPlanRequest;
import com.racehorse.training.dto.TrainingPlanResponse;
import com.racehorse.training.entity.TrainingPlan;
import com.racehorse.training.entity.TrainingPlanPhase;
import com.racehorse.training.repository.TrainingPlanRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class TrainingPlanService {

    private final TrainingPlanRepository repository;
    private final UserAccountRepository userRepository;
    private final HorseAccessService horseAccessService;
    private final TrainingScheduleRepository scheduleRepository;

    public TrainingPlanService(
            TrainingPlanRepository repository,
            UserAccountRepository userRepository,
            HorseAccessService horseAccessService,
            TrainingScheduleRepository scheduleRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.horseAccessService = horseAccessService;
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional
    public TrainingPlanResponse create(UUID actorId, TrainingPlanRequest request) {
        List<TrainingPlanPhase> phases = buildPhases(request.phases());
        Horse horse = horseAccessService.requireActive(request.horseId());
        UserAccount actor = requireUser(actorId);
        TrainingPlan plan = new TrainingPlan(horse, request.name().trim(), null, actor, request.active());
        plan.replacePhases(phases);
        return toResponse(repository.save(plan));
    }

    @Transactional
    public TrainingPlanResponse update(UUID planId, TrainingPlanRequest request) {
        TrainingPlan plan = requirePlan(planId);
        if (!plan.getHorse().getId().equals(request.horseId())) {
            throw new BusinessException("Không thể chuyển kế hoạch sang ngựa khác; hãy dùng chức năng sao chép");
        }
        if (scheduleRepository.existsByPhaseTrainingPlanId(planId)) {
            throw new BusinessException("Không thể thay các giai đoạn của kế hoạch đã phát sinh lịch huấn luyện");
        }
        horseAccessService.requireActive(request.horseId());
        List<TrainingPlanPhase> phases = buildPhases(request.phases());
        plan.update(request.name().trim(), request.active());
        plan.replacePhases(List.of());
        repository.flush();
        plan.replacePhases(phases);
        return toResponse(repository.save(plan));
    }

    @Transactional
    public TrainingPlanResponse copy(UUID sourcePlanId, UUID actorId, CopyTrainingPlanRequest request) {
        TrainingPlan source = requirePlan(sourcePlanId);
        Horse targetHorse = horseAccessService.requireActive(request.targetHorseId());
        UserAccount actor = requireUser(actorId);
        TrainingPlan copy = new TrainingPlan(targetHorse, request.name().trim(), source, actor, request.active());
        List<TrainingPlanPhase> phases = source.getPhases().stream()
                .map(phase -> new TrainingPlanPhase(
                        phase.getPhaseOrder(),
                        phase.getName(),
                        phase.getStartDate(),
                        phase.getEndDate(),
                        phase.getTargetDistanceMeters(),
                        phase.getWorkloadIntensity(),
                        phase.getSurfaceType()))
                .toList();
        copy.replacePhases(phases);
        return toResponse(repository.save(copy));
    }

    @Transactional(readOnly = true)
    public TrainingPlanResponse get(UUID planId) {
        return toResponse(requirePlan(planId));
    }

    @Transactional(readOnly = true)
    public List<TrainingPlanResponse> listForHorse(UUID horseId) {
        horseAccessService.requireActive(horseId);
        return repository.findByHorseIdOrderByCreatedAtDesc(horseId).stream()
                .map(this::toResponse)
                .toList();
    }

    private TrainingPlan requirePlan(UUID id) {
        return repository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch huấn luyện: " + id));
    }

    private UserAccount requireUser(UUID id) {
        return userRepository.findById(id)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + id));
    }

    private List<TrainingPlanPhase> buildPhases(List<TrainingPlanRequest.Phase> requests) {
        List<TrainingPlanRequest.Phase> sorted = new ArrayList<>(requests);
        sorted.sort(Comparator.comparing(TrainingPlanRequest.Phase::startDate)
                .thenComparing(TrainingPlanRequest.Phase::endDate));
        for (int index = 0; index < sorted.size(); index++) {
            TrainingPlanRequest.Phase current = sorted.get(index);
            if (current.startDate().isAfter(current.endDate())) {
                throw new BusinessException("Ngày bắt đầu giai đoạn phải trước hoặc bằng ngày kết thúc");
            }
            if (index > 0 && !current.startDate().isAfter(sorted.get(index - 1).endDate())) {
                throw new BusinessException("Các giai đoạn trong cùng kế hoạch không được trùng thời gian");
            }
        }
        List<TrainingPlanPhase> phases = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            TrainingPlanRequest.Phase phase = sorted.get(index);
            phases.add(new TrainingPlanPhase(
                    index + 1,
                    phase.name().trim(),
                    phase.startDate(),
                    phase.endDate(),
                    phase.targetDistanceMeters(),
                    phase.workloadIntensity(),
                    phase.surfaceType()));
        }
        return phases;
    }

    TrainingPlanResponse toResponse(TrainingPlan plan) {
        return new TrainingPlanResponse(
                plan.getId(),
                new TrainingPlanResponse.HorseSummary(
                        plan.getHorse().getId(), plan.getHorse().getCode(), plan.getHorse().getName()),
                plan.getName(),
                plan.getSourcePlan() == null ? null : plan.getSourcePlan().getId(),
                new TrainingPlanResponse.UserSummary(plan.getCreatedBy().getId(), plan.getCreatedBy().getUsername()),
                plan.isActive(),
                plan.getPhases().stream().map(phase -> new TrainingPlanResponse.Phase(
                        phase.getId(),
                        phase.getPhaseOrder(),
                        phase.getName(),
                        phase.getStartDate(),
                        phase.getEndDate(),
                        phase.getTargetDistanceMeters(),
                        phase.getWorkloadIntensity(),
                        phase.getSurfaceType())).toList(),
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }
}
