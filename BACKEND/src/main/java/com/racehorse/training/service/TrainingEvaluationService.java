package com.racehorse.training.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.InvalidInputException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import com.racehorse.training.dto.CreateTrainingEvaluationRequest;
import com.racehorse.training.dto.TrainingEvaluationResponse;
import com.racehorse.training.entity.TrainingEvaluation;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.repository.TrainingEvaluationRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
public class TrainingEvaluationService {
    private static final int OPERATING_HOURS = 6;

    private final TrainingEvaluationRepository repository;
    private final UserAccountRepository userRepository;
    private final HorseAccessService horseAccessService;
    private final TrainingScheduleRepository scheduleRepository;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final HorseOwnershipRepository ownershipRepository;
    private final NotificationService notificationService;

    @Autowired
    public TrainingEvaluationService(TrainingEvaluationRepository repository, UserAccountRepository userRepository,
            HorseAccessService horseAccessService, TrainingScheduleRepository scheduleRepository, Clock clock,
            JdbcTemplate jdbc, HorseOwnershipRepository ownershipRepository, NotificationService notificationService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.horseAccessService = horseAccessService;
        this.scheduleRepository = scheduleRepository;
        this.clock = clock;
        this.jdbc = jdbc;
        this.ownershipRepository = ownershipRepository;
        this.notificationService = notificationService;
    }

    public TrainingEvaluationService(TrainingEvaluationRepository repository, UserAccountRepository userRepository,
            HorseAccessService horseAccessService, TrainingScheduleRepository scheduleRepository, Clock clock) {
        this(repository, userRepository, horseAccessService, scheduleRepository, clock, null, null, null);
    }

    @Transactional
    public TrainingEvaluationResponse create(UUID actorId, CreateTrainingEvaluationRequest request,
            boolean manager, boolean trainer) {
        if (manager || !trainer) throw new com.racehorse.common.exception.ForbiddenException(
                "Chỉ huấn luyện viên phụ trách mới được đánh giá buổi tập");
        Horse horse = horseAccessService.requireTrainingAccess(request.horseId(), actorId, manager, trainer, false);
        UserAccount actor = userRepository.findById(actorId).filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người ghi nhận: " + actorId));
        if (request.maxSpeedKmh() == null || request.maxSpeedKmh().signum() <= 0) {
            throw new InvalidInputException("Tốc độ tối đa phải lớn hơn 0");
        }
        if (request.stopBpm() == null || request.oneMinuteBpm() == null
                || request.stopBpm() < 0 || request.oneMinuteBpm() < 0
                || request.stopBpm() > 300 || request.oneMinuteBpm() > 300) {
            throw new InvalidInputException("Nhịp tim phải nằm trong khoảng 0 đến 300 BPM");
        }
        if (request.oneMinuteBpm() > request.stopBpm()) {
            throw new InvalidInputException("Nhịp tim sau 1 phút không được lớn hơn nhịp tim khi dừng");
        }
        TrainingSchedule schedule = null;
        if (request.trainingScheduleId() == null)
            throw new InvalidInputException("Đánh giá phải gắn với một buổi tập đã kết thúc");
        {
            schedule = scheduleRepository.findByIdForUpdate(request.trainingScheduleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch huấn luyện: " + request.trainingScheduleId()));
            horseAccessService.requireTrainingAccess(schedule.getHorse().getId(), actorId, manager, trainer, false);
            if (!schedule.getHorse().getId().equals(horse.getId())) {
                throw new InvalidInputException("Lịch huấn luyện không thuộc về ngựa được đánh giá");
            }
            if (schedule.getCancelledAt() != null) throw new BusinessException("Không thể đánh giá buổi tập đã bị hủy");
            if (!"COMPLETED".equals(schedule.getSessionStatus()))
                throw new BusinessException("Chỉ có thể đánh giá buổi tập đã hoàn thành");
            if (!request.sessionDate().equals(schedule.getScheduledStart().atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()))
                throw new InvalidInputException("Ngày đánh giá phải trùng với ngày buổi tập");
            if (repository.existsByTrainingScheduleId(schedule.getId())) {
                throw new BusinessException("Lịch huấn luyện này đã có đánh giá");
            }
        }

        int recoveryBpm = request.stopBpm() - request.oneMinuteBpm();
        BigDecimal averageSpeed = request.distanceMeters().multiply(BigDecimal.valueOf(3.6))
                .divide(request.trackCompletionSeconds(), 2, RoundingMode.HALF_UP);
        if (request.maxSpeedKmh().compareTo(averageSpeed) < 0)
            throw new InvalidInputException("Tốc độ tối đa không được nhỏ hơn tốc độ trung bình đã tính");
        BigDecimal intensity = averageSpeed.multiply(BigDecimal.valueOf(100))
                .divide(request.maxSpeedKmh(), 2, RoundingMode.HALF_UP);
        BigDecimal recoveryRate = BigDecimal.valueOf(recoveryBpm).setScale(2, RoundingMode.HALF_UP);
        String videoUrl = normalizeAndValidateVideoUrl(request.videoUrl());
        TrainingEvaluation evaluation = new TrainingEvaluation(horse, schedule, actor, request.sessionDate(),
                request.durationMinutes(), request.distanceMeters(), averageSpeed, request.trainingLoad(),
                recoveryBpm, request.perceivedExertion(), FitnessStatusPolicy.fromOneMinuteBpm(request.oneMinuteBpm()),
                request.trainerNotes().trim(), videoUrl, clock.instant());
        evaluation.setComputedMetrics(null, null, null, null, null, BigDecimal.ONE, intensity, recoveryRate, recoveryBpm);
        evaluation.setSessionMetrics(request.maxSpeedKmh(), request.stopBpm(), request.oneMinuteBpm(), intensity);
        evaluation.setApprovedV3Metrics(request.trackCompletionSeconds(), request.injured(),
                trimToNull(request.injuryLocation()), trimToNull(request.injuryNotes()));
        TrainingEvaluation saved = repository.save(evaluation);
        if (request.injured()) {
            if (jdbc == null) throw new IllegalStateException("Medical audit persistence is unavailable");
            jdbc.update("update horses set status='INJURED',health_status='INJURED',health_source_type='TRAINING_EVALUATION',"
                            + "health_source_id=?,health_changed_by_user_id=?,health_changed_at=SYSDATETIMEOFFSET(),"
                            + "updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",
                    saved.getId(), actorId, horse.getId());
        }
        notifyEvaluation(horse, saved, request.injured());
        return toResponse(saved);
    }

    private void notifyEvaluation(Horse horse, TrainingEvaluation evaluation, boolean injured) {
        if (notificationService == null || ownershipRepository == null || jdbc == null) return;
        var owners = ownershipRepository.findByHorseIdOrderByOwnerUsernameAsc(horse.getId());
        if (owners.size() == 1) {
            var owner = owners.getFirst().getOwner();
            notificationService.createDeduplicated(owner, "TRAINING_EVALUATION", NotificationPriority.NORMAL,
                    "Đã có đánh giá buổi tập mới", horse.getCode() + " - " + evaluation.getSessionDate(),
                    "TRAINING_EVALUATION", evaluation.getId(), "TRAINING_EVALUATION_OWNER_" + evaluation.getId());
        } else {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "Skipped evaluation owner notification: horse {0} has {1} ownership rows", horse.getId(), owners.size());
        }
        UUID caretakerId = firstUser("select caretaker_user_id from horses where id=?", horse.getId());
        UUID vetId = firstUser("select diagnosing_vet_user_id from horses where id=?", horse.getId());
        if (evaluation.getFitnessStatus() == com.racehorse.training.entity.FitnessStatus.YELLOW) {
            if (caretakerId == null) {
                System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                        "Skipped YELLOW caretaker notification: no caretaker assigned for horse {0}", horse.getId());
            } else notifyUser(caretakerId, "TRAINING_EVALUATION_YELLOW_CARETAKER", "Đánh giá huấn luyện mức YELLOW",
                    horse, evaluation, "CARETAKER");
            String previous = firstString("""
                    select top 1 fitness_status from training_evaluations
                    where horse_id=? and id<>? order by session_date desc,completed_at desc
                    """, horse.getId(), evaluation.getId());
            if ("YELLOW".equals(previous)) notifyVet(vetId, horse, evaluation, "YELLOW_STREAK",
                    "Ngựa có hai buổi đánh giá YELLOW liên tiếp");
        } else if (evaluation.getFitnessStatus() == com.racehorse.training.entity.FitnessStatus.RED) {
            notifyVet(vetId, horse, evaluation, "RED", "Đánh giá huấn luyện mức RED");
        }
        if (injured) notifyVet(vetId, horse, evaluation, "INJURY", "Đánh giá ghi nhận ngựa bị chấn thương");
    }

    private void notifyVet(UUID vetId, Horse horse, TrainingEvaluation evaluation, String kind, String text) {
        if (vetId == null) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR,
                    "Missing diagnosing veterinarian for horse {0} after evaluation {1}", horse.getId(), evaluation.getId());
            return;
        }
        notifyUser(vetId, "TRAINING_EVALUATION_" + kind, text, horse, evaluation, "VET_" + kind);
    }

    private void notifyUser(UUID userId, String type, String title, Horse horse, TrainingEvaluation evaluation, String keyPart) {
        userRepository.findById(userId).ifPresent(user -> notificationService.createDeduplicated(user, type,
                NotificationPriority.HIGH, title, horse.getCode() + " - " + evaluation.getSessionDate(),
                "TRAINING_EVALUATION", evaluation.getId(), "TRAINING_EVALUATION_" + keyPart + "_" + evaluation.getId()));
    }

    private UUID firstUser(String sql, UUID horseId) {
        return jdbc.query(sql, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, horseId);
    }

    private String firstString(String sql, UUID horseId, UUID evaluationId) {
        return jdbc.query(sql, rs -> rs.next() ? rs.getString(1) : null, horseId, evaluationId);
    }

    @Transactional(readOnly = true)
    public TrainingEvaluationResponse getById(UUID evaluationId, UUID actorId,
            boolean manager, boolean trainer, boolean owner) {
        TrainingEvaluation evaluation = findDetail(evaluationId);
        horseAccessService.requireTrainingAccess(evaluation.getHorse().getId(), actorId, manager, trainer, owner);
        TrainingEvaluationResponse response = toResponse(evaluation);
        return owner && !manager && !trainer ? withoutStaffIdentity(response) : response;
    }

    @Transactional(readOnly = true)
    public Page<TrainingEvaluationResponse> listForHorse(UUID horseId, UUID actorId,
            boolean manager, boolean trainer, boolean owner, Pageable pageable) {
        horseAccessService.requireTrainingAccess(horseId, actorId, manager, trainer, owner);
        return repository.findByHorseIdOrderBySessionDateDescCompletedAtDesc(horseId, pageable)
                .map(evaluation -> {
                    TrainingEvaluationResponse response = toResponse(evaluation);
                    return owner && !manager && !trainer ? withoutStaffIdentity(response) : response;
                });
    }

    private TrainingEvaluationResponse withoutStaffIdentity(TrainingEvaluationResponse response) {
        return new TrainingEvaluationResponse(response.id(), response.horse(), response.trainingScheduleId(), null,
                response.sessionDate(), response.hours(), response.durationMinutes(), response.distanceMeters(),
                response.averageSpeedKmh(), response.maxSpeedKmh(), response.intensityPercent(), response.stopBpm(),
                response.oneMinuteBpm(), response.recoveryRate(), response.trainingLoad(), response.perceivedExertion(),
                response.fitnessStatus(), response.trainerNotes(), response.videoUrl(), response.completedAt(),
                response.trackCompletionSeconds(), response.injured(), response.injuryLocation(), response.injuryNotes());
    }

    private TrainingEvaluation findDetail(UUID id) {
        return repository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá buổi tập: " + id));
    }

    private String normalizeAndValidateVideoUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidInputException("Liên kết video phải là URL HTTP hoặc HTTPS hợp lệ");
        }
        return normalized;
    }

    TrainingEvaluationResponse toResponse(TrainingEvaluation evaluation) {
        return new TrainingEvaluationResponse(evaluation.getId(),
                new TrainingEvaluationResponse.HorseSummary(evaluation.getHorse().getId(),
                        evaluation.getHorse().getCode(), evaluation.getHorse().getName()),
                evaluation.getTrainingSchedule() == null ? null : evaluation.getTrainingSchedule().getId(),
                new TrainingEvaluationResponse.UserSummary(evaluation.getRecordedBy().getId(),
                        evaluation.getRecordedBy().getUsername()),
                evaluation.getSessionDate(), OPERATING_HOURS, evaluation.getDurationMinutes(),
                evaluation.getDistanceMeters(),
                evaluation.getAverageSpeedKmh(), evaluation.getMaxSpeedKmh(), evaluation.getIntensityPercent(),
                evaluation.getHeartRateAtStopBpm(), evaluation.getHeartRateAfter1minBpm(),
                evaluation.getHeartRateRecoveryRate(), evaluation.getTrainingLoad(), evaluation.getPerceivedExertion(),
                evaluation.getFitnessStatus(), evaluation.getTrainerNotes(), evaluation.getVideoUrl(),
                evaluation.getCompletedAt(), evaluation.getTrackCompletionSeconds(), evaluation.getInjured(),
                evaluation.getInjuryLocation(), evaluation.getInjuryNotes());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
