package com.racehorse.training.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ForbiddenException;
import com.racehorse.common.exception.InvalidInputException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.repository.TrainingLockRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import com.racehorse.training.dto.CreateTrainingScheduleRequest;
import com.racehorse.training.dto.OwnerTrainingScheduleResponse;
import com.racehorse.training.dto.TrainingScheduleResponse;
import com.racehorse.training.dto.UpdateTrainingScheduleRequest;
import com.racehorse.training.entity.Racetrack;
import com.racehorse.training.entity.RacetrackLane;
import com.racehorse.training.entity.TrainingEvaluation;
import com.racehorse.training.entity.TrainingPlanPhase;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.entity.TrialRunBooking;
import com.racehorse.training.repository.RacetrackLaneRepository;
import com.racehorse.training.repository.RacetrackRepository;
import com.racehorse.training.repository.TrialRunBookingRepository;
import com.racehorse.training.repository.TrainingEvaluationRepository;
import com.racehorse.training.repository.TrainingPlanPhaseRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import com.racehorse.caretask.service.CareTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class TrainingScheduleService {
    private static final ZoneId CLUB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final LocalTime TRAINING_START = LocalTime.of(5, 0);
    private static final long TRAINING_WINDOW_SECONDS = 6 * 3600L;

    private static Instant trainingStart(LocalDate date) {
        return date.atTime(TRAINING_START).atZone(CLUB_ZONE).toInstant();
    }

    private final TrainingScheduleRepository repository;
    private final TrainingPlanPhaseRepository phaseRepository;
    private final RacetrackRepository racetrackRepository;
    private final RacetrackLaneRepository laneRepository;
    private final TrialRunBookingRepository bookingRepository;
    private final TrainingLockRepository lockRepository;
    private final UserAccountRepository userRepository;
    private final HorseAccessService horseAccessService;
    private final Clock clock;
    private final TrainingEvaluationRepository evaluationRepository;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;
    private final CareTaskService careTaskService;

    @Autowired
    public TrainingScheduleService(
            TrainingScheduleRepository repository,
            TrainingPlanPhaseRepository phaseRepository,
            RacetrackRepository racetrackRepository,
            RacetrackLaneRepository laneRepository,
            TrialRunBookingRepository bookingRepository,
            TrainingLockRepository lockRepository,
            UserAccountRepository userRepository,
            HorseAccessService horseAccessService,
            Clock clock,
            TrainingEvaluationRepository evaluationRepository,
            NotificationService notificationService,
            JdbcTemplate jdbc,
            @Autowired(required = false) CareTaskService careTaskService) {
        this.repository = repository;
        this.phaseRepository = phaseRepository;
        this.racetrackRepository = racetrackRepository;
        this.laneRepository = laneRepository;
        this.bookingRepository = bookingRepository;
        this.lockRepository = lockRepository;
        this.userRepository = userRepository;
        this.horseAccessService = horseAccessService;
        this.clock = clock;
        this.evaluationRepository = evaluationRepository;
        this.notificationService = notificationService;
        this.jdbc = jdbc;
        this.careTaskService = careTaskService;
    }

    public TrainingScheduleService(
            TrainingScheduleRepository repository,
            TrainingPlanPhaseRepository phaseRepository,
            RacetrackRepository racetrackRepository,
            RacetrackLaneRepository laneRepository,
            TrialRunBookingRepository bookingRepository,
            TrainingLockRepository lockRepository,
            UserAccountRepository userRepository,
            HorseAccessService horseAccessService,
            Clock clock,
            TrainingEvaluationRepository evaluationRepository,
            NotificationService notificationService,
            JdbcTemplate jdbc) {
        this(repository, phaseRepository, racetrackRepository, laneRepository, bookingRepository, lockRepository,
                userRepository, horseAccessService, clock, evaluationRepository, notificationService, jdbc, null);
    }

    public TrainingScheduleService(TrainingScheduleRepository repository, TrainingPlanPhaseRepository phaseRepository,
            RacetrackRepository racetrackRepository, TrainingLockRepository lockRepository,
            UserAccountRepository userRepository, HorseAccessService horseAccessService, Clock clock) {
        this(repository, phaseRepository, racetrackRepository, null, null, lockRepository, userRepository,
                horseAccessService, clock, null, null, null);
    }

    public TrainingScheduleService(TrainingScheduleRepository repository, TrainingPlanPhaseRepository phaseRepository,
            RacetrackRepository racetrackRepository, RacetrackLaneRepository laneRepository,
            TrialRunBookingRepository bookingRepository, TrainingLockRepository lockRepository,
            UserAccountRepository userRepository, HorseAccessService horseAccessService, Clock clock,
            TrainingEvaluationRepository evaluationRepository, NotificationService notificationService) {
        this(repository, phaseRepository, racetrackRepository, laneRepository, bookingRepository, lockRepository,
                userRepository, horseAccessService, clock, evaluationRepository, notificationService, null);
    }

    @Transactional
    public List<TrainingScheduleResponse> create(UUID actorId, CreateTrainingScheduleRequest request,
            boolean manager, boolean trainer) {
        TrainingPlanPhase phase = phaseRepository.findDetailById(request.trainingPlanPhaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giai đoạn huấn luyện"));
        Horse horse = horseAccessService.requireTrainingAccess(phase.getTrainingPlan().getHorse().getId(),
                actorId, manager, trainer, false);
        horseAccessService.requireTrainingReady(horse.getId());
        requireNotInjured(horse);
        if (trainer && jdbc != null && !hasDiagnosingVet(horse.getId()))
            throw new BusinessException("Ngựa cần được bác sĩ thú y chẩn đoán trước khi xếp lịch");
        LocalDate today = LocalDate.now(clock.withZone(CLUB_ZONE));
        LocalDate lastPhaseDate = phase.getTrainingPlan().getPhases().stream()
                .map(TrainingPlanPhase::getEndDate).max(LocalDate::compareTo).orElse(phase.getEndDate());
        if (!phase.getTrainingPlan().isActive() || today.isAfter(lastPhaseDate)) {
            throw new BusinessException("Kế hoạch đã hết hạn, không thể tạo buổi tập mới");
        }
        LocalDate date = request.trainingDate();
        if (date.isBefore(today))
            throw new InvalidInputException("Không thể tạo buổi tập trong quá khứ");
        if (date.isBefore(phase.getStartDate()) || date.isAfter(phase.getEndDate())) {
            throw new InvalidInputException("Ngày tập phải nằm trong giai đoạn huấn luyện");
        }
        if (phase.getRestDays().contains(date)) {
            throw new InvalidInputException("Không thể tạo buổi tập vào ngày nghỉ của giai đoạn");
        }
        if (laneRepository == null || bookingRepository == null) {
            throw new IllegalStateException("Track lane booking dependencies are not configured");
        }
        RacetrackLane lane = laneRepository.findDetailByIdForUpdate(request.racetrackLaneId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy làn chạy"));
        Racetrack track = lane.getRacetrack();
        if (!lane.isActive() || lane.getLaneNumber() < 1 || lane.getLaneNumber() > 4
                || !track.isActive() || !track.getId().equals(request.racetrackId())) {
            throw new InvalidInputException("Làn chạy phải thuộc sân đang hoạt động đã chọn");
        }
        Instant start = trainingStart(date);
        Instant end = start.plusSeconds(TRAINING_WINDOW_SECONDS);
        if (repository.existsByHorseIdAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
                horse.getId(), end, start)) {
            throw new BusinessException("Ngựa đã có buổi tập trùng khung giờ này");
        }
        if (bookingRepository.existsByLaneIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
                lane.getId(), end, start)) {
            throw new BusinessException("Làn chạy đã được đặt trong khung giờ này");
        }
        if (phase.getWorkloadIntensity().name().equals("HIGH")
                && lockRepository.findByHorseIdAndReleasedAtIsNull(horse.getId()).isPresent()) {
            throw new BusinessException("Không thể xếp lịch cường độ cao vì ngựa đang bị khóa huấn luyện");
        }

        UserAccount creator = user(actorId);
        TrainingSchedule schedule = new TrainingSchedule(phase, horse, track, request.taskName().trim(),
                phase.getWorkloadIntensity(), start, end, creator);
        schedule.publish(clock.instant());
        schedule = repository.saveAndFlush(schedule);
        bookingRepository.saveAndFlush(new TrialRunBooking(schedule, lane, start, end));
        notifyCaretaker(schedule, "CREATED");
        return List.of(toResponse(schedule));
    }

    public List<TrainingScheduleResponse> create(UUID actorId, CreateTrainingScheduleRequest request, boolean privileged) {
        return create(actorId, request, privileged, false);
    }

    @Transactional
    public TrainingScheduleResponse update(UUID scheduleId, UUID actorId, UpdateTrainingScheduleRequest request,
            boolean manager, boolean trainer) {
        TrainingSchedule schedule = lockedSchedule(scheduleId);
        horseAccessService.requireTrainingAccess(schedule.getHorse().getId(), actorId, manager, trainer, false);
        if (schedule.getCancelledAt() != null) throw new BusinessException("Buổi tập đã hủy");
        horseAccessService.requireTrainingReady(schedule.getHorse().getId());
        TrainingPlanPhase phase = schedule.getPhase();
        LocalDate date = request.trainingDate();
        if (!date.equals(schedule.getScheduledStart().atZone(CLUB_ZONE).toLocalDate())
                && date.isBefore(LocalDate.now(clock.withZone(CLUB_ZONE))))
            throw new InvalidInputException("Không thể chuyển buổi tập về ngày trong quá khứ");
        if (date.isBefore(phase.getStartDate()) || date.isAfter(phase.getEndDate())) {
            throw new InvalidInputException("Ngày tập phải nằm trong giai đoạn huấn luyện");
        }
        if (phase.getRestDays().contains(date)) {
            throw new InvalidInputException("Không thể tạo buổi tập vào ngày nghỉ của giai đoạn");
        }
        RacetrackLane lane = laneRepository.findDetailByIdForUpdate(request.racetrackLaneId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy làn chạy"));
        Racetrack track = lane.getRacetrack();
        if (!lane.isActive() || lane.getLaneNumber() < 1 || lane.getLaneNumber() > 4
                || !track.isActive() || !track.getId().equals(request.racetrackId())) {
            throw new InvalidInputException("Làn chạy phải thuộc sân đang hoạt động đã chọn");
        }
        Instant start = trainingStart(date);
        Instant end = start.plusSeconds(TRAINING_WINDOW_SECONDS);
        if (repository.existsByHorseIdAndIdNotAndCancelledAtIsNullAndScheduledStartLessThanAndScheduledEndGreaterThan(
                schedule.getHorse().getId(), scheduleId, end, start)) {
            throw new BusinessException("Ngựa đã có buổi tập trùng khung giờ này");
        }
        if (bookingRepository.existsByLaneIdAndTrainingScheduleIdNotAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
                lane.getId(), scheduleId, end, start)) {
            throw new BusinessException("Làn chạy đã được đặt trong khung giờ này");
        }
        TrialRunBooking booking = bookingRepository.findByTrainingScheduleIdForUpdate(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi tập chưa có lượt đặt làn"));
        schedule.updateDetails(track, request.taskName().trim(), start, end);
        booking.reschedule(lane, start, end);
        repository.saveAndFlush(schedule);
        bookingRepository.saveAndFlush(booking);
        notifyCaretaker(schedule, "UPDATED_" + clock.millis());
        return toResponse(schedule);
    }

    @Transactional
    public void cancel(UUID scheduleId, UUID actorId, boolean manager, boolean trainer) {
        TrainingSchedule schedule = lockedSchedule(scheduleId);
        horseAccessService.requireTrainingAccess(schedule.getHorse().getId(), actorId, manager, trainer, false);
        TrainingSessionDeletion.delete(jdbc, scheduleId);
        repository.flush();
    }

    @Transactional(readOnly = true)
    public List<TrainingScheduleResponse> calendar(LocalDate from, LocalDate to, UUID horseId) {
        DateRange range = range(from, to);
        return repository.findCalendar(range.from(), range.to(), horseId, null).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TrainingScheduleResponse> calendar(LocalDate from, LocalDate to, UUID horseId, UUID actorId,
            boolean manager, boolean trainer, boolean owner) {
        UUID trainerScope = null;
        if (trainer && !manager) trainerScope = actorId;
        if (horseId != null) {
            horseAccessService.requireTrainingAccess(horseId, actorId, manager, trainer, owner);
        } else if (!manager && !trainer) {
            throw new ForbiddenException("Vui lòng chọn ngựa để xem lịch huấn luyện");
        }
        DateRange range = range(from, to);
        return repository.findCalendar(range.from(), range.to(), horseId, trainerScope).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TrainingScheduleResponse get(UUID scheduleId, UUID actorId, boolean manager, boolean trainer, boolean owner) {
        TrainingSchedule schedule = schedule(scheduleId);
        if (manager || trainer || owner) {
            horseAccessService.requireTrainingAccess(schedule.getHorse().getId(), actorId, manager, trainer, owner);
        } else if (schedule.getHorse().getCaretaker() == null
                || !schedule.getHorse().getCaretaker().getId().equals(actorId)) {
            throw new ResourceNotFoundException("Không tìm thấy lịch huấn luyện: " + scheduleId);
        }
        return toResponse(schedule);
    }

    @Transactional
    public TrainingScheduleResponse publish(UUID scheduleId, UUID actorId, boolean manager, boolean trainer) {
        TrainingSchedule schedule = schedule(scheduleId);
        horseAccessService.requireTrainingAccess(schedule.getHorse().getId(), actorId, manager, trainer, false);
        if (schedule.getCancelledAt() != null) throw new BusinessException("Không thể công bố lịch đã hủy");
        if (schedule.getPublishedAt() == null) {
            schedule.publish(clock.instant());
            repository.save(schedule);
            notifyCaretaker(schedule, "PUBLISHED");
        }
        return toResponse(schedule);
    }

    @Transactional
    public TrainingScheduleResponse markReady(UUID scheduleId, UUID actorId, boolean manager) {
        TrainingSchedule value = lockedSchedule(scheduleId);
        if (manager == false) {
            UUID caretaker = jdbc == null ? null : jdbc.query(
                    "select caretaker_user_id from horses where id=?",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null, value.getHorse().getId());
            if (!actorId.equals(caretaker)) throw new ResourceNotFoundException("Không tìm thấy buổi tập được phép xác nhận");
        }
        requireTransitionable(value);
        if (!"PREPARING".equals(value.getSessionStatus()))
            throw new BusinessException("Chỉ buổi tập đang chuẩn bị mới có thể chuyển sang sẵn sàng");
        value.markReady(actorId, clock.instant());
        return toResponse(repository.save(value));
    }

    @Transactional
    public TrainingScheduleResponse startTraining(UUID scheduleId, UUID actorId) {
        TrainingSchedule value = lockedSchedule(scheduleId);
        horseAccessService.requireTrainingAccess(value.getHorse().getId(), actorId, false, true, false);
        requireTransitionable(value);
        if (!"READY".equals(value.getSessionStatus()))
            throw new BusinessException("Chỉ có thể bắt đầu buổi tập khi trạng thái là Sẵn sàng");
        horseAccessService.requireTrainingReady(value.getHorse().getId());
        requireNotInjured(value.getHorse());
        if (jdbc != null && !hasDiagnosingVet(value.getHorse().getId()))
            throw new BusinessException("Ngựa cần được bác sĩ thú y chẩn đoán trước khi huấn luyện");
        value.startTraining(clock.instant());
        return toResponse(repository.save(value));
    }

    @Transactional
    public TrainingScheduleResponse finishTraining(UUID scheduleId, UUID actorId) {
        TrainingSchedule value = lockedSchedule(scheduleId);
        horseAccessService.requireTrainingAccess(value.getHorse().getId(), actorId, false, true, false);
        requireTransitionable(value);
        if (!"IN_PROGRESS".equals(value.getSessionStatus()))
            throw new BusinessException("Chỉ buổi tập đang diễn ra mới có thể kết thúc");
        value.finishTraining(clock.instant());
        TrainingSchedule saved = repository.save(value);
        createRecoveryCareTask(saved);
        return toResponse(saved);
    }

    private void createRecoveryCareTask(TrainingSchedule schedule) {
        if (careTaskService == null || schedule == null || schedule.getHorse() == null) return;
        UUID caretakerId = null;
        if (schedule.getHorse().getCaretaker() != null) {
            caretakerId = schedule.getHorse().getCaretaker().getId();
        }
        if (caretakerId == null && jdbc != null) {
            caretakerId = jdbc.query(
                    "select caretaker_user_id from horses where id=?",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                    schedule.getHorse().getId()
            );
        }
        if (caretakerId != null) {
            try {
                careTaskService.createRecoveryTask(schedule, caretakerId);
            } catch (Exception ex) {
                System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                        "Failed to create recovery task for schedule {0}: {1}", schedule.getId(), ex.getMessage());
            }
        }
    }

    private TrainingSchedule lockedSchedule(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch huấn luyện: " + id));
    }

    private void requireTransitionable(TrainingSchedule schedule) {
        if (schedule.getCancelledAt() != null) throw new BusinessException("Buổi tập đã hủy");
    }

    private boolean hasDiagnosingVet(UUID horseId) {
        Integer count = jdbc.queryForObject("select count(*) from horses where id=? and diagnosing_vet_user_id is not null",
                Integer.class, horseId);
        return count != null && count > 0;
    }

    private void requireNotInjured(Horse horse) {
        if (horse.getStatus() == com.racehorse.horse.entity.HorseStatus.INJURED
                || "INJURED".equalsIgnoreCase(horse.getHealthStatus()))
            throw new BusinessException("Ngựa đang bị chấn thương, không thể xếp hoặc bắt đầu buổi tập");
    }

    @Transactional(readOnly = true)
    public List<OwnerTrainingScheduleResponse> ownerSchedules(UUID horseId, UUID ownerId) {
        return ownerSchedules(horseId, ownerId, null, null);
    }

    @Transactional(readOnly = true)
    public List<OwnerTrainingScheduleResponse> ownerSchedules(UUID horseId, UUID ownerId, LocalDate from, LocalDate to) {
        Horse ownedHorse = horseAccessService.requireTrainingAccess(horseId, ownerId, false, false, true);
        if (ownedHorse.getCurrentTrainer() == null) return List.of();
        List<TrainingSchedule> visible = repository.findOwnerVisibleByHorseId(horseId).stream()
                .filter(schedule -> schedule.getPublishedAt() != null && schedule.getCancelledAt() == null)
                .toList();
        if (from != null || to != null) {
            LocalDate start = from == null ? to : from;
            LocalDate end = to == null ? from : to;
            if (start.isAfter(end)) throw new InvalidInputException("Khoảng ngày không hợp lệ");
            visible = visible.stream().filter(schedule -> {
                LocalDate date = schedule.getScheduledStart().atZone(CLUB_ZONE).toLocalDate();
                return !date.isBefore(start) && !date.isAfter(end);
            }).toList();
        }
        return visible.stream().map(this::toOwnerResponse).toList();
    }

    @Transactional(readOnly = true)
    public OwnerTrainingScheduleResponse ownerSchedule(UUID scheduleId, UUID ownerId) {
        TrainingSchedule schedule = schedule(scheduleId);
        Horse ownedHorse = horseAccessService.requireTrainingAccess(schedule.getHorse().getId(), ownerId, false, false, true);
        if (ownedHorse.getCurrentTrainer() == null)
            throw new ResourceNotFoundException("Ngựa chưa có huấn luyện viên phụ trách");
        if (schedule.getPublishedAt() == null || schedule.getCancelledAt() != null) {
            throw new ResourceNotFoundException("Không tìm thấy buổi tập đã công bố");
        }
        return toOwnerResponse(schedule);
    }

    @Transactional(readOnly = true)
    public List<TrainingScheduleResponse> mine(UUID id, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        return repository.findForCaretaker(id, range.from(), range.to()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TrainingScheduleResponse> forDiagnosingVet(UUID id, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        return repository.findForDiagnosingVet(id, range.from(), range.to()).stream().map(this::toResponse).toList();
    }

    private void notifyCaretaker(TrainingSchedule schedule, String eventKey) {
        if (notificationService == null || schedule.getHorse().getCaretaker() == null || schedule.getId() == null) return;
        notificationService.createDeduplicated(schedule.getHorse().getCaretaker(), "TRAINING_SCHEDULE_PUBLISHED",
                NotificationPriority.NORMAL, "Bạn có lịch huấn luyện mới",
                schedule.getHorse().getCode() + " - " + schedule.getTaskName(), "TRAINING_SCHEDULE",
                schedule.getId(), "TRAINING_SCHEDULE_" + eventKey + "_" + schedule.getId());
    }

    private DateRange range(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(clock.withZone(CLUB_ZONE)) : from;
        LocalDate end = to == null ? start : to;
        if (start.isAfter(end)) throw new InvalidInputException("Khoảng ngày không hợp lệ");
        return new DateRange(start.atStartOfDay(CLUB_ZONE).toInstant(), end.plusDays(1).atStartOfDay(CLUB_ZONE).toInstant());
    }

    private TrainingSchedule schedule(UUID id) {
        return repository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch huấn luyện"));
    }

    private UserAccount user(UUID id) {
        return userRepository.findById(id).filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }

    TrainingScheduleResponse toResponse(TrainingSchedule schedule) {
        TrialRunBooking booking = bookingRepository == null ? null
                : bookingRepository.findByTrainingScheduleId(schedule.getId()).orElse(null);
        TrainingEvaluation evaluation = evaluationRepository == null ? null
                : evaluationRepository.findByTrainingScheduleId(schedule.getId()).orElse(null);
        String status = schedule.getCancelledAt() != null ? "CANCELLED"
                : schedule.getSessionStatus() != null ? schedule.getSessionStatus()
                : schedule.getCompletedAt() != null ? "COMPLETED"
                : schedule.getPublishedAt() != null ? "PUBLISHED" : "PLANNED";
        TrainingScheduleResponse.EvaluationSummary evaluationSummary = evaluation == null ? null
                : new TrainingScheduleResponse.EvaluationSummary(evaluation.getId(), evaluation.getSessionDate(),
                        evaluation.getIntensityPercent(), evaluation.getHeartRateRecoveryRate(),
                        evaluation.getFitnessStatus().name());
        return new TrainingScheduleResponse(schedule.getId(),
                new TrainingScheduleResponse.HorseSummary(schedule.getHorse().getId(), schedule.getHorse().getCode(),
                        schedule.getHorse().getName(), contact(schedule.getHorse().getCaretaker()),
                        contact(schedule.getHorse().getDiagnosingVet())),
                new TrainingScheduleResponse.PhaseSummary(schedule.getPhase().getId(),
                        schedule.getPhase().getTrainingPlan().getId(), schedule.getPhase().getTrainingPlan().getName(),
                        schedule.getPhase().getName(), schedule.getPhase().getStartDate(), schedule.getPhase().getEndDate(),
                        schedule.getPhase().getSurfaceType()),
                schedule.getRacetrack() == null ? null : new TrainingScheduleResponse.RacetrackSummary(
                        schedule.getRacetrack().getId(), schedule.getRacetrack().getName()),
                schedule.getTaskName(),
                schedule.getWorkloadIntensity(), schedule.getNotes(),
                status, schedule.getScheduledStart().atZone(CLUB_ZONE).toLocalDate(),
                schedule.getScheduledStart().atZone(CLUB_ZONE).toLocalTime(),
                schedule.getScheduledEnd().atZone(CLUB_ZONE).toLocalTime(),
                booking == null ? null : new TrainingScheduleResponse.LaneSummary(
                        booking.getLane().getId(), booking.getLane().getLaneNumber()),
                evaluationSummary, schedule.getScheduledStart(), schedule.getScheduledEnd(), schedule.getPublishedAt(),
                schedule.getCancelledAt(), schedule.getCompletedAt());
    }

    private TrainingScheduleResponse.HorseContact contact(UserAccount account) {
        return account == null ? null : new TrainingScheduleResponse.HorseContact(
                account.getId(), account.getUsername(), account.getEmail());
    }

    private OwnerTrainingScheduleResponse toOwnerResponse(TrainingSchedule schedule) {
        TrialRunBooking booking = bookingRepository.findByTrainingScheduleId(schedule.getId()).orElse(null);
        TrainingEvaluation evaluation = evaluationRepository.findByTrainingScheduleId(schedule.getId()).orElse(null);
        OwnerTrainingScheduleResponse.Evaluation summary = evaluation == null ? null
                : new OwnerTrainingScheduleResponse.Evaluation(evaluation.getId(), evaluation.getSessionDate(),
                        evaluation.getDistanceMeters(), evaluation.getAverageSpeedKmh(), evaluation.getMaxSpeedKmh(),
                        evaluation.getIntensityPercent(), evaluation.getHeartRateAtStopBpm(),
                        evaluation.getHeartRateAfter1minBpm(), evaluation.getHeartRateRecoveryRate(),
                        6, evaluation.getDurationMinutes(), evaluation.getFitnessStatus().name(),
                        evaluation.getTrainerNotes(), evaluation.getVideoUrl());
        return new OwnerTrainingScheduleResponse(schedule.getId(),
                schedule.getScheduledStart().atZone(CLUB_ZONE).toLocalDate(),
                schedule.getScheduledStart().atZone(CLUB_ZONE).toLocalTime(),
                schedule.getScheduledEnd().atZone(CLUB_ZONE).toLocalTime(), schedule.getTaskName(),
                schedule.getWorkloadIntensity(), "EMERGENCY_STOPPED".equals(schedule.getSessionStatus())
                        ? "EMERGENCY_STOPPED" : schedule.getCompletedAt() == null ? "PUBLISHED" : "COMPLETED",
                schedule.getRacetrack() == null ? null : new OwnerTrainingScheduleResponse.Racetrack(schedule.getRacetrack().getId(),
                        schedule.getRacetrack().getName()),
                booking == null ? null : new OwnerTrainingScheduleResponse.Lane(
                        booking.getLane().getId(), booking.getLane().getLaneNumber()), summary);
    }

    private record DateRange(Instant from, Instant to) {}
}
