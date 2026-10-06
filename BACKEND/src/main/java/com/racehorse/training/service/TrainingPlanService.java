package com.racehorse.training.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.InvalidInputException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.CopyTrainingPlanRequest;
import com.racehorse.training.dto.CopyTrainingPlanResponse;
import com.racehorse.training.dto.TrainingPlanRequest;
import com.racehorse.training.dto.TrainingPlanResponse;
import com.racehorse.training.dto.TrainingScheduleResponse;
import com.racehorse.training.entity.TrainingEvaluation;
import com.racehorse.training.entity.TrainingPlan;
import com.racehorse.training.entity.TrainingPlanPhase;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.repository.TrainingEvaluationRepository;
import com.racehorse.training.repository.TrainingPlanRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TrainingPlanService {
    private static final ZoneId CLUB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TrainingPlanRepository repository;
    private final UserAccountRepository userRepository;
    private final HorseAccessService horseAccessService;
    private final TrainingScheduleRepository scheduleRepository;
    private final TrainingEvaluationRepository evaluationRepository;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    @Autowired
    public TrainingPlanService(TrainingPlanRepository repository, UserAccountRepository userRepository,
            HorseAccessService horseAccessService, TrainingScheduleRepository scheduleRepository,
            TrainingEvaluationRepository evaluationRepository, Clock clock, JdbcTemplate jdbc) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.horseAccessService = horseAccessService;
        this.scheduleRepository = scheduleRepository;
        this.evaluationRepository = evaluationRepository;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    public TrainingPlanService(TrainingPlanRepository repository, UserAccountRepository userRepository,
            HorseAccessService horseAccessService, TrainingScheduleRepository scheduleRepository, Clock clock) {
        this(repository, userRepository, horseAccessService, scheduleRepository, null, clock, null);
    }

    public TrainingPlanService(TrainingPlanRepository repository, UserAccountRepository userRepository,
            HorseAccessService horseAccessService, TrainingScheduleRepository scheduleRepository,
            TrainingEvaluationRepository evaluationRepository, Clock clock) {
        this(repository,userRepository,horseAccessService,scheduleRepository,evaluationRepository,clock,null);
    }

    @Transactional
    public TrainingPlanResponse create(UUID actorId, TrainingPlanRequest request, boolean manager, boolean trainer) {
        Horse horse = horseAccessService.requireTrainingAccess(request.horseId(), actorId, manager, trainer, false);
        requireFitForPlanning(horse);
        requireDiagnosedForTrainer(horse.getId(), manager, trainer);
        List<TrainingPlanPhase> phases = buildPhases(request.phases());
        UserAccount actor = requireUser(actorId);
        TrainingPlan plan = new TrainingPlan(horse, request.name().trim(), null, actor, request.active());
        plan.replacePhases(phases);
        return toResponse(repository.save(plan));
    }

    @Transactional
    public TrainingPlanResponse update(UUID planId, UUID actorId, TrainingPlanRequest request, boolean manager, boolean trainer) {
        TrainingPlan plan = requirePlan(planId);
        Horse horse = horseAccessService.requireTrainingAccess(plan.getHorse().getId(), actorId, manager, trainer, false);
        if ("EXPIRED".equals(planStatus(plan))) throw new BusinessException("Kế hoạch đã hết hạn, chỉ được xem");
        requireFitForPlanning(horse);
        requireDiagnosedForTrainer(horse.getId(), manager, trainer);
        if (!horse.getId().equals(request.horseId())) {
            throw new InvalidInputException("Không thể chuyển kế hoạch sang ngựa khác; hãy dùng chức năng sao chép");
        }
        if (scheduleRepository.existsByPhaseTrainingPlanId(planId)) {
            throw new BusinessException("Không thể thay các giai đoạn của kế hoạch đã phát sinh lịch huấn luyện");
        }
        List<TrainingPlanPhase> phases = buildPhases(request.phases());
        plan.update(request.name().trim(), request.active());
        plan.replacePhases(List.of());
        repository.flush();
        plan.replacePhases(phases);
        return toResponse(repository.save(plan));
    }

    @Transactional
    public CopyTrainingPlanResponse copy(UUID sourcePlanId, UUID actorId, CopyTrainingPlanRequest request,
            boolean manager, boolean trainer) {
        TrainingPlan source = requirePlan(sourcePlanId);
        horseAccessService.requireTrainingAccess(source.getHorse().getId(), actorId, manager, trainer, false);
        Horse targetHorse = horseAccessService.requireTrainingAccess(request.targetHorseId(), actorId, manager, trainer, false);
        requireFitForPlanning(targetHorse);
        requireDiagnosedForTrainer(targetHorse.getId(), manager, trainer);
        UserAccount actor = requireUser(actorId);
        List<TrainingPlanPhase> phases = new ArrayList<>();
        List<CopyTrainingPlanResponse.PhaseRestDays> restDayResults = new ArrayList<>();
        List<LocalDate> acceptedPlanRestDays = new ArrayList<>();
        for (TrainingPlanPhase phase : source.getPhases()) {
            List<LocalDate> kept = new ArrayList<>();
            List<CopyTrainingPlanResponse.DroppedRestDay> dropped = new ArrayList<>();
            Set<LocalDate> seen = new HashSet<>();
            for (LocalDate date : phase.getRestDays().stream().sorted().toList()) {
                String reason = null;
                if (!seen.add(date)) reason = "Ngày nghỉ bị lặp";
                else if (date.isBefore(phase.getStartDate()) || date.isAfter(phase.getEndDate()))
                    reason = "Ngày nghỉ nằm ngoài khoảng thời gian của giai đoạn";
                else reason = restDayViolation(acceptedPlanRestDays, date);
                if (reason == null) { kept.add(date); acceptedPlanRestDays.add(date); }
                else dropped.add(new CopyTrainingPlanResponse.DroppedRestDay(date, reason));
            }
            restDayResults.add(new CopyTrainingPlanResponse.PhaseRestDays(null, phase.getName(), kept, dropped));
            phases.add(new TrainingPlanPhase(phase.getPhaseOrder(), phase.getName(), phase.getStartDate(),
                    phase.getEndDate(), phase.getTargetDistanceMeters(), phase.getWorkloadIntensity(),
                    phase.getSurfaceType(), kept));
        }
        TrainingPlan copy = new TrainingPlan(targetHorse, request.name().trim(), source, actor, request.active());
        copy.replacePhases(phases);
        TrainingPlan saved = repository.save(copy);
        TrainingPlanResponse result = toResponse(saved);
        List<CopyTrainingPlanResponse.PhaseRestDays> completedResults = new ArrayList<>();
        for (int index = 0; index < restDayResults.size(); index++) {
            CopyTrainingPlanResponse.PhaseRestDays value = restDayResults.get(index);
            completedResults.add(new CopyTrainingPlanResponse.PhaseRestDays(result.phases().get(index).id(),
                    value.phaseName(), value.kept(), value.dropped()));
        }
        return new CopyTrainingPlanResponse(result, completedResults);
    }

    @Transactional(readOnly = true)
    public TrainingPlanResponse get(UUID planId, UUID userId, boolean manager, boolean trainer, boolean owner) {
        TrainingPlan plan = requirePlan(planId);
        horseAccessService.requireTrainingAccess(plan.getHorse().getId(), userId, manager, trainer, owner);
        return toResponses(List.of(plan), owner && !manager && !trainer).getFirst();
    }

    @Transactional(readOnly = true)
    public List<TrainingPlanResponse> listForHorse(UUID horseId, UUID userId,
            boolean manager, boolean trainer, boolean owner) {
        horseAccessService.requireTrainingAccess(horseId, userId, manager, trainer, owner);
        return toResponses(repository.findByHorseIdOrderByCreatedAtDesc(horseId), owner && !manager && !trainer);
    }

    private TrainingPlan requirePlan(UUID id) {
        return repository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch huấn luyện: " + id));
    }

    private UserAccount requireUser(UUID id) {
        return userRepository.findById(id).filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + id));
    }

    private List<TrainingPlanPhase> buildPhases(List<TrainingPlanRequest.Phase> requests) {
        List<TrainingPlanRequest.Phase> sorted = new ArrayList<>(requests);
        sorted.sort(Comparator.comparing(TrainingPlanRequest.Phase::startDate)
                .thenComparing(TrainingPlanRequest.Phase::endDate));
        for (int index = 0; index < sorted.size(); index++) {
            TrainingPlanRequest.Phase current = sorted.get(index);
            if (current.startDate().isAfter(current.endDate())) {
                throw new InvalidInputException("Ngày bắt đầu giai đoạn phải trước hoặc bằng ngày kết thúc");
            }
            if (index > 0 && !current.startDate().isAfter(sorted.get(index - 1).endDate())) {
                throw new InvalidInputException("Các giai đoạn trong cùng kế hoạch không được trùng thời gian");
            }
            List<LocalDate> restDays = current.restDays() == null ? List.of() : current.restDays().stream().sorted().toList();
            if (new HashSet<>(restDays).size() != restDays.size()
                    || restDays.stream().anyMatch(day -> day.isBefore(current.startDate()) || day.isAfter(current.endDate()))) {
                throw new InvalidInputException("Ngày nghỉ phải không trùng và nằm trong khoảng thời gian của giai đoạn");
            }
        }
        List<TrainingPlanPhase> phases = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            TrainingPlanRequest.Phase phase = sorted.get(index);
            phases.add(new TrainingPlanPhase(index + 1, phase.name().trim(), phase.startDate(), phase.endDate(),
                    phase.targetDistanceMeters(), phase.workloadIntensity(), phase.surfaceType(),
                    phase.restDays() == null ? List.of() : phase.restDays()));
        }
        List<LocalDate> allRestDays = phases.stream().flatMap(phase -> phase.getRestDays().stream()).sorted().toList();
        validateConsecutiveRestDays(allRestDays);
        List<LocalDate> accepted = new ArrayList<>();
        for (LocalDate date : allRestDays) {
            String violation = restDayViolation(accepted, date);
            if (violation != null) throw new InvalidInputException(violation);
            accepted.add(date);
        }
        return phases;
    }

    private void validateConsecutiveRestDays(List<LocalDate> restDays) {
        int consecutive = 1;
        for (int index = 1; index < restDays.size(); index++) {
            consecutive = restDays.get(index).equals(restDays.get(index - 1).plusDays(1))
                    ? consecutive + 1 : 1;
            if (consecutive > 2) {
                throw new InvalidInputException("Không được có quá 2 ngày nghỉ liên tiếp");
            }
        }
    }

    private String restDayViolation(List<LocalDate> accepted, LocalDate candidate) {
        List<LocalDate> all = new ArrayList<>(accepted);
        all.add(candidate);
        all.sort(LocalDate::compareTo);
        int consecutive = 1;
        for (int index = 1; index < all.size(); index++) {
            consecutive = all.get(index).equals(all.get(index - 1).plusDays(1)) ? consecutive + 1 : 1;
            if (consecutive > 2) return "Không được có quá 2 ngày nghỉ liên tiếp";
        }

        WeekFields iso = WeekFields.ISO;
        long sameWeek = accepted.stream().filter(date -> date.get(iso.weekBasedYear()) == candidate.get(iso.weekBasedYear())
                && date.get(iso.weekOfWeekBasedYear()) == candidate.get(iso.weekOfWeekBasedYear())).count();
        return sameWeek > 0 ? "Mỗi tuần từ thứ Hai đến Chủ nhật chỉ được có 1 ngày nghỉ" : null;
    }

    private boolean isInjured(Horse horse) {
        return horse.getStatus() == HorseStatus.INJURED || "INJURED".equalsIgnoreCase(horse.getHealthStatus());
    }

    private void requireFitForPlanning(Horse horse) {
        if (isInjured(horse)) throw new BusinessException("Ngựa đang bị chấn thương, không thể tạo hoặc cập nhật kế hoạch");
    }

    private void requireDiagnosedForTrainer(UUID horseId, boolean manager, boolean trainer) {
        if (!trainer || manager || jdbc == null) return;
        Integer count=jdbc.queryForObject("select count(*) from horses where id=? and diagnosing_vet_user_id is not null",Integer.class,horseId);
        if (count == null || count == 0) throw new BusinessException("Ngựa cần được bác sĩ thú y chẩn đoán trước khi lập kế hoạch");
    }

    private boolean activeOnDate(TrainingPlan plan) {
        LocalDate today = LocalDate.now(clock.withZone(CLUB_ZONE));
        return plan.isActive() && plan.getPhases().stream().map(TrainingPlanPhase::getEndDate)
                .max(LocalDate::compareTo).map(end -> !today.isAfter(end)).orElse(false);
    }

    private TrainingPlanResponse toResponse(TrainingPlan plan) {
        return toResponses(List.of(plan)).getFirst();
    }

    private List<TrainingPlanResponse> toResponses(List<TrainingPlan> plans) {
        return toResponses(plans, false);
    }

    private List<TrainingPlanResponse> toResponses(List<TrainingPlan> plans, boolean ownerView) {
        if (plans.isEmpty()) return List.of();
        List<UUID> planIds = plans.stream().map(TrainingPlan::getId).toList();
        List<TrainingSchedule> sessions = scheduleRepository.findPlanSessions(planIds);
        Map<UUID, TrainingEvaluation> evaluations = sessions.isEmpty() || evaluationRepository == null
                ? Map.of()
                : evaluationRepository.findByTrainingScheduleIdIn(sessions.stream().map(TrainingSchedule::getId).toList())
                        .stream().collect(Collectors.toMap(value -> value.getTrainingSchedule().getId(), value -> value));
        Map<UUID, List<TrainingSchedule>> sessionsByPhase = sessions.stream()
                .collect(Collectors.groupingBy(value -> value.getPhase().getId()));
        Map<UUID, List<TrainingPlanResponse.SessionSummary>> summaries = new HashMap<>();
        for (TrainingSchedule session : sessions) {
            TrainingEvaluation evaluation = evaluations.get(session.getId());
            TrainingPlanResponse.EvaluationSummary evaluationSummary = evaluation == null ? null
                    : new TrainingPlanResponse.EvaluationSummary(evaluation.getId(), evaluation.getSessionDate(),
                            evaluation.getIntensityPercent(), evaluation.getHeartRateRecoveryRate(),
                            evaluation.getFitnessStatus().name());
            String status = session.getCancelledAt() != null ? "CANCELLED"
                    : session.getSessionStatus() != null ? session.getSessionStatus()
                    : session.getCompletedAt() != null ? "COMPLETED"
                    : session.getPublishedAt() != null ? "PUBLISHED" : "PLANNED";
            summaries.computeIfAbsent(session.getPhase().getId(), ignored -> new ArrayList<>()).add(
                    new TrainingPlanResponse.SessionSummary(session.getId(),
                            session.getScheduledStart().atZone(CLUB_ZONE).toLocalDate(), status,
                            session.getWorkloadIntensity(), session.getTaskName(),
                            ownerView || session.getAssignedStaff() == null ? null
                                    : new TrainingScheduleResponse.UserSummary(session.getAssignedStaff().getId(),
                                            session.getAssignedStaff().getUsername()),
                            ownerView || session.getVeterinarian() == null ? null
                                    : new TrainingScheduleResponse.UserSummary(session.getVeterinarian().getId(),
                                            session.getVeterinarian().getUsername()),
                            ownerView ? null : session.getStaffAssignedBy(), ownerView ? null : session.getVetAssignedBy(),
                            session.getRacetrack() == null ? null
                                    : new TrainingScheduleResponse.RacetrackSummary(session.getRacetrack().getId(),
                                            session.getRacetrack().getName()),
                            evaluationSummary));
        }
        return plans.stream().map(plan -> new TrainingPlanResponse(plan.getId(),
                new TrainingPlanResponse.HorseSummary(plan.getHorse().getId(), plan.getHorse().getCode(), plan.getHorse().getName()),
                plan.getName(), plan.getSourcePlan() == null ? null : plan.getSourcePlan().getId(),
                new TrainingPlanResponse.UserSummary(plan.getCreatedBy().getId(), plan.getCreatedBy().getUsername()),
                activeOnDate(plan), planStatus(plan),
                plan.getPhases().stream().map(phase -> new TrainingPlanResponse.Phase(phase.getId(),
                        phase.getPhaseOrder(), phase.getName(), phase.getStartDate(), phase.getEndDate(),
                        phase.getTargetDistanceMeters(), phase.getWorkloadIntensity(), phase.getSurfaceType(),
                        phase.getRestDays(), summaries.getOrDefault(phase.getId(), List.of()))).toList(),
                plan.getCreatedAt(), plan.getUpdatedAt())).toList();
    }

    private String planStatus(TrainingPlan plan) {
        if (!plan.isActive()) return "INACTIVE";
        LocalDate today = LocalDate.now(clock.withZone(CLUB_ZONE));
        return plan.getPhases().stream().map(TrainingPlanPhase::getEndDate)
                .max(LocalDate::compareTo).filter(end -> !today.isAfter(end))
                .isPresent() ? "ACTIVE" : "EXPIRED";
    }
}
