package com.racehorse.training.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.repository.TrainingLockRepository;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import com.racehorse.training.dto.CreateTrainingScheduleRequest;
import com.racehorse.training.dto.TrainingScheduleResponse;
import com.racehorse.training.entity.TrainingPlanPhase;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.entity.WorkloadIntensity;
import com.racehorse.training.repository.TrainingPlanPhaseRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class TrainingScheduleService {

    private final TrainingScheduleRepository repository;
    private final TrainingPlanPhaseRepository phaseRepository;
    private final TrainingLockRepository lockRepository;
    private final UserAccountRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public TrainingScheduleService(
            TrainingScheduleRepository repository,
            TrainingPlanPhaseRepository phaseRepository,
            TrainingLockRepository lockRepository,
            UserAccountRepository userRepository,
            NotificationService notificationService,
            Clock clock) {
        this.repository = repository;
        this.phaseRepository = phaseRepository;
        this.lockRepository = lockRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public TrainingScheduleResponse create(UUID actorId, CreateTrainingScheduleRequest request) {
        if (!request.scheduledStart().isBefore(request.scheduledEnd())) {
            throw new BusinessException("Thời gian bắt đầu phải trước thời gian kết thúc");
        }
        TrainingPlanPhase phase = phaseRepository.findDetailById(request.trainingPlanPhaseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy giai đoạn huấn luyện: " + request.trainingPlanPhaseId()));
        if (!phase.getTrainingPlan().isActive()) {
            throw new BusinessException("Chỉ có thể tạo lịch từ kế hoạch huấn luyện đang hoạt động");
        }
        LocalDate scheduleDate = LocalDate.ofInstant(request.scheduledStart(), ZoneOffset.UTC);
        if (scheduleDate.isBefore(phase.getStartDate()) || scheduleDate.isAfter(phase.getEndDate())) {
            throw new BusinessException("Ngày tập phải nằm trong khoảng thời gian của giai đoạn huấn luyện");
        }
        UserAccount staff = requireEnabledUser(request.assignedStaffId());
        if (!staff.getRoles().contains(Role.STABLE_STAFF)) {
            throw new BusinessException("Người được phân công phải có role STABLE_STAFF");
        }
        if (repository.existsByAssignedStaffIdAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
                staff.getId(), request.scheduledEnd(), request.scheduledStart())) {
            throw new BusinessException("Nhân viên đã có lịch trong khung giờ này");
        }
        if (phase.getWorkloadIntensity() == WorkloadIntensity.HIGH
                && lockRepository.findByHorseIdAndReleasedAtIsNull(phase.getTrainingPlan().getHorse().getId()).isPresent()) {
            throw new BusinessException("Không thể xếp lịch cường độ cao vì ngựa đang bị khóa huấn luyện");
        }
        TrainingSchedule schedule = new TrainingSchedule(
                phase,
                phase.getTrainingPlan().getHorse(),
                staff,
                request.taskName().trim(),
                phase.getWorkloadIntensity(),
                request.scheduledStart(),
                request.scheduledEnd(),
                requireEnabledUser(actorId));
        return toResponse(repository.save(schedule));
    }

    @Transactional
    public TrainingScheduleResponse publish(UUID scheduleId) {
        TrainingSchedule schedule = requireSchedule(scheduleId);
        if (schedule.getCancelledAt() != null) throw new BusinessException("Không thể công bố lịch đã hủy");
        if (schedule.getPublishedAt() == null) {
            schedule.publish(clock.instant());
            repository.save(schedule);
            notificationService.create(
                    schedule.getAssignedStaff(),
                    "TRAINING_SCHEDULE_PUBLISHED",
                    NotificationPriority.NORMAL,
                    "Bạn có lịch huấn luyện mới",
                    schedule.getHorse().getCode() + " - " + schedule.getTaskName(),
                    "TRAINING_SCHEDULE",
                    schedule.getId());
        }
        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public TrainingScheduleResponse get(UUID scheduleId, UUID actorId, boolean privileged) {
        TrainingSchedule schedule = requireSchedule(scheduleId);
        if (!privileged && !schedule.getAssignedStaff().getId().equals(actorId)) {
            throw new ResourceNotFoundException("Không tìm thấy lịch huấn luyện: " + scheduleId);
        }
        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public List<TrainingScheduleResponse> calendar(LocalDate from, LocalDate to, UUID horseId) {
        DateRange range = range(from, to);
        return repository.findCalendar(range.fromInclusive(), range.toExclusive(), horseId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TrainingScheduleResponse> mine(UUID staffId, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        return repository.findPublishedForStaff(staffId, range.fromInclusive(), range.toExclusive()).stream()
                .map(this::toResponse).toList();
    }

    private DateRange range(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate actualFrom = from == null ? today : from;
        LocalDate actualTo = to == null ? actualFrom : to;
        if (actualFrom.isAfter(actualTo)) throw new BusinessException("Khoảng ngày không hợp lệ");
        return new DateRange(
                actualFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),
                actualTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private TrainingSchedule requireSchedule(UUID id) {
        return repository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch huấn luyện: " + id));
    }

    private UserAccount requireEnabledUser(UUID id) {
        return userRepository.findById(id).filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + id));
    }

    TrainingScheduleResponse toResponse(TrainingSchedule value) {
        return new TrainingScheduleResponse(
                value.getId(),
                new TrainingScheduleResponse.HorseSummary(
                        value.getHorse().getId(), value.getHorse().getCode(), value.getHorse().getName()),
                new TrainingScheduleResponse.PhaseSummary(
                        value.getPhase().getId(), value.getPhase().getName(), value.getPhase().getSurfaceType()),
                new TrainingScheduleResponse.UserSummary(
                        value.getAssignedStaff().getId(), value.getAssignedStaff().getUsername()),
                value.getTaskName(), value.getWorkloadIntensity(), value.getScheduledStart(), value.getScheduledEnd(),
                value.getPublishedAt(), value.getCancelledAt(), value.getCompletedAt());
    }

    private record DateRange(Instant fromInclusive, Instant toExclusive) {}
}
