package com.racehorse.training.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.caretask.service.CareTaskService;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.repository.TrainingLockRepository;
import com.racehorse.horse.entity.Horse;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class TrainingScheduleService {

    private static final ZoneId CLUB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TrainingScheduleRepository repository;
    private final TrainingPlanPhaseRepository phaseRepository;
    private final TrainingLockRepository lockRepository;
    private final UserAccountRepository userRepository;
    private final NotificationService notificationService;
    private final CareTaskService careTaskService;
    private final Clock clock;

    public TrainingScheduleService(
            TrainingScheduleRepository repository,
            TrainingPlanPhaseRepository phaseRepository,
            TrainingLockRepository lockRepository,
            UserAccountRepository userRepository,
            NotificationService notificationService,
            CareTaskService careTaskService,
            Clock clock) {
        this.repository = repository;
        this.phaseRepository = phaseRepository;
        this.lockRepository = lockRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.careTaskService = careTaskService;
        this.clock = clock;
    }

    public TrainingScheduleService(
            TrainingScheduleRepository repository,
            TrainingPlanPhaseRepository phaseRepository,
            TrainingLockRepository lockRepository,
            UserAccountRepository userRepository,
            NotificationService notificationService,
            Clock clock) {
        this(repository, phaseRepository, lockRepository, userRepository, notificationService, null, clock);
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
        Horse horse = phase.getTrainingPlan().getHorse();
        if ("IN_MEDICAL".equalsIgnoreCase(horse.getPaymentStatus())) {
            throw new BusinessException("Ngựa đang trong ca khám y tế. Không thể xếp lịch tập cho đến khi hoàn thành khám.");
        }
        if (phase.getWorkloadIntensity() == WorkloadIntensity.HIGH
                && lockRepository.findByHorseIdAndReleasedAtIsNull(horse.getId()).isPresent()) {
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
            if (schedule.getAssignedStaff() != null) {
                notificationService.create(
                        schedule.getAssignedStaff(),
                        "TRAINING_SCHEDULE_PUBLISHED",
                        NotificationPriority.NORMAL,
                        "Bạn có lịch huấn luyện mới",
                        schedule.getHorse().getCode() + " - " + schedule.getTaskName(),
                        "TRAINING_SCHEDULE",
                        schedule.getId());
            }
        }
        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public TrainingScheduleResponse get(UUID scheduleId, UUID actorId, boolean privileged) {
        TrainingSchedule schedule = requireSchedule(scheduleId);
        boolean isAssignedStaff = schedule.getAssignedStaff() != null && schedule.getAssignedStaff().getId().equals(actorId);
        boolean isAssignedVet = schedule.getVeterinarianUser() != null && schedule.getVeterinarianUser().getId().equals(actorId);
        if (!privileged && !isAssignedStaff && !isAssignedVet) {
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
        return mine(staffId, false, from, to);
    }

    @Transactional(readOnly = true)
    public List<TrainingScheduleResponse> mine(UUID callerId, boolean isVet, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        List<TrainingSchedule> list = isVet
                ? repository.findByVetId(callerId, range.fromInclusive(), range.toExclusive())
                : repository.findPublishedForStaff(callerId, range.fromInclusive(), range.toExclusive());

        return list.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TrainingScheduleResponse claim(UUID scheduleId, UUID callerId, boolean isVet) {
        TrainingSchedule s = repository.findByIdWithLock(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch tập: " + scheduleId));

        if (s.getCancelledAt() != null) {
            throw new BusinessException("Phiên tập đã bị hủy");
        }

        LocalDate trainingDate = s.getScheduledStart().atZone(CLUB_ZONE).toLocalDate();
        Instant deadline = trainingDate.minusDays(1).atTime(23, 0).atZone(CLUB_ZONE).toInstant();
        if (clock.instant().isAfter(deadline)) {
            throw new BusinessException(
                    "Thời hạn tự nhận phiên tập đã kết thúc vào 23:00 ngày hôm trước (T-1). " +
                    "Phiên tập đã chuyển sang chế độ hệ thống điều phối tự động.");
        }

        if (isVet) {
            if (s.getVeterinarianUser() != null) {
                throw new BusinessException("Phiên tập đã có bác sĩ thú y đăng ký theo dõi");
            }
            if (repository.existsByVetOnDate(callerId, trainingDate)) {
                throw new BusinessException("Bạn đã có lịch theo dõi tại phiên tập khác trong ngày này (tối đa 1 phiên/ngày)");
            }
            UserAccount vetUser = requireEnabledUser(callerId);
            s.setVeterinarianUser(vetUser);
            s.setVetAssignedBy("USER");
            s.setVetAssignedAt(clock.instant());
        } else {
            if (s.getAssignedStaff() != null) {
                throw new BusinessException("Phiên tập đã được nhân viên chuồng khác tiếp nhận");
            }
            if (repository.existsByStaffOnDate(callerId, trainingDate)) {
                throw new BusinessException("Bạn đã có lịch làm việc tại phiên tập khác trong ngày này (tối đa 1 phiên/ngày)");
            }
            UserAccount staffUser = requireEnabledUser(callerId);
            s.setAssignedStaff(staffUser);
            s.setStaffAssignedBy("USER");
            s.setStaffAssignedAt(clock.instant());
            if (careTaskService != null) {
                careTaskService.createRecoveryTask(s, callerId);
            }
        }

        return toResponse(repository.save(s));
    }

    @Transactional(readOnly = true)
    public List<TrainingScheduleResponse> findOpenSessions(
            UUID callerId, boolean isVet, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate f = from != null ? from : today;
        LocalDate t = to != null ? to : f.plusDays(14);
        Instant fromI = f.atTime(5, 0).atZone(CLUB_ZONE).toInstant();
        Instant toI = t.atTime(23, 59, 59).atZone(CLUB_ZONE).toInstant();
        Instant deadline = today.atTime(23, 0).atZone(CLUB_ZONE).toInstant();

        List<TrainingSchedule> list = isVet
                ? repository.findOpenForVet(fromI, toI, deadline)
                : repository.findOpenForStaff(fromI, toI, deadline);

        return list.stream()
                .map(s -> toResponseWithCanClaim(s, callerId, isVet))
                .toList();
    }

    private TrainingScheduleResponse toResponseWithCanClaim(
            TrainingSchedule s, UUID callerId, boolean isVet) {
        LocalDate trainingDate = s.getScheduledStart().atZone(CLUB_ZONE).toLocalDate();
        boolean notBusy = isVet
                ? !repository.existsByVetOnDate(callerId, trainingDate)
                : !repository.existsByStaffOnDate(callerId, trainingDate);
        return toResponse(s, notBusy);
    }

    private DateRange range(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate actualFrom = from == null ? today.minusDays(30) : from;
        LocalDate actualTo = to == null ? today.plusDays(30) : to;
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
        return toResponse(value, null);
    }

    TrainingScheduleResponse toResponse(TrainingSchedule value, Boolean canClaim) {
        TrainingScheduleResponse.HorseSummary horse = value.getHorse() != null
                ? new TrainingScheduleResponse.HorseSummary(value.getHorse().getId(), value.getHorse().getCode(), value.getHorse().getName())
                : null;
        TrainingScheduleResponse.PhaseSummary phase = value.getPhase() != null
                ? new TrainingScheduleResponse.PhaseSummary(value.getPhase().getId(), value.getPhase().getName(), value.getPhase().getSurfaceType())
                : null;
        TrainingScheduleResponse.UserSummary assignedStaff = value.getAssignedStaff() != null
                ? new TrainingScheduleResponse.UserSummary(value.getAssignedStaff().getId(), value.getAssignedStaff().getUsername())
                : null;

        UUID racetrackId = value.getRacetrack() != null ? value.getRacetrack().getId() : null;
        String racetrackName = value.getRacetrack() != null ? value.getRacetrack().getName() : null;

        UUID vetId = value.getVeterinarianUser() != null ? value.getVeterinarianUser().getId() : null;
        String vetName = value.getVeterinarianUser() != null ? value.getVeterinarianUser().getUsername() : null;

        return new TrainingScheduleResponse(
                value.getId(),
                horse,
                phase,
                assignedStaff,
                value.getTaskName(),
                value.getWorkloadIntensity(),
                value.getScheduledStart(),
                value.getScheduledEnd(),
                value.getPublishedAt(),
                value.getCancelledAt(),
                value.getCompletedAt(),
                racetrackId,
                racetrackName,
                vetId,
                vetName,
                value.getVetAssignedBy(),
                value.getVetAssignedAt(),
                value.getStaffAssignedBy(),
                value.getStaffAssignedAt(),
                canClaim
        );
    }

    private record DateRange(Instant fromInclusive, Instant toExclusive) {}
}
