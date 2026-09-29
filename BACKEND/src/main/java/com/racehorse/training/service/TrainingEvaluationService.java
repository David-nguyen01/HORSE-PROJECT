package com.racehorse.training.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.CreateTrainingEvaluationRequest;
import com.racehorse.training.dto.TrainingEvaluationResponse;
import com.racehorse.training.entity.TrainingEvaluation;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.repository.TrainingEvaluationRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
public class TrainingEvaluationService {

    private final TrainingEvaluationRepository repository;
    private final UserAccountRepository userRepository;
    private final HorseAccessService horseAccessService;
    private final TrainingScheduleRepository scheduleRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TrainingEvaluationService(
            TrainingEvaluationRepository repository,
            UserAccountRepository userRepository,
            HorseAccessService horseAccessService,
            TrainingScheduleRepository scheduleRepository,
            Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.horseAccessService = horseAccessService;
        this.scheduleRepository = scheduleRepository;
        this.clock = clock;
    }

    public TrainingEvaluationService(
            TrainingEvaluationRepository repository,
            UserAccountRepository userRepository,
            HorseAccessService horseAccessService,
            Clock clock) {
        this(repository, userRepository, horseAccessService, null, clock);
    }

    @Transactional
    public TrainingEvaluationResponse create(UUID actorId, CreateTrainingEvaluationRequest request) {
        Horse horse = horseAccessService.requireActive(request.horseId());
        UserAccount actor = userRepository.findById(actorId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người ghi nhận: " + actorId));
        TrainingSchedule schedule = null;
        if (request.trainingScheduleId() != null) {
            if (scheduleRepository == null) {
                throw new IllegalStateException("scheduleRepository is not configured");
            }
            schedule = scheduleRepository.findDetailById(request.trainingScheduleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch huấn luyện: " + request.trainingScheduleId()));
            if (!schedule.getHorse().getId().equals(horse.getId())) {
                throw new BusinessException("Lịch huấn luyện không thuộc về ngựa được đánh giá");
            }
            if (schedule.getCancelledAt() != null) {
                throw new BusinessException("Không thể ghi nhận đánh giá cho lịch huấn luyện đã bị hủy");
            }
            if (repository.existsByTrainingScheduleId(schedule.getId())) {
                throw new BusinessException("Lịch huấn luyện này đã có đánh giá");
            }
            schedule.complete(clock.instant());
        }
        String videoUrl = normalizeAndValidateVideoUrl(request.videoUrl());
        TrainingEvaluation evaluation = new TrainingEvaluation(
                horse,
                schedule,
                actor,
                request.sessionDate(),
                request.durationMinutes(),
                request.distanceMeters(),
                request.averageSpeedKmh(),
                request.trainingLoad(),
                request.heartRateRecoveryBpm(),
                request.perceivedExertion(),
                request.fitnessStatus(),
                request.trainerNotes().trim(),
                videoUrl,
                clock.instant());
        return toResponse(repository.save(evaluation));
    }

    @Transactional(readOnly = true)
    public TrainingEvaluationResponse getById(UUID evaluationId, UUID actorId, boolean privileged) {
        TrainingEvaluation evaluation = findDetail(evaluationId);
        horseAccessService.requireReadable(evaluation.getHorse().getId(), actorId, privileged);
        return toResponse(evaluation);
    }

    @Transactional(readOnly = true)
    public Page<TrainingEvaluationResponse> listForHorse(
            UUID horseId,
            UUID actorId,
            boolean privileged,
            Pageable pageable) {
        horseAccessService.requireReadable(horseId, actorId, privileged);
        return repository.findByHorseIdOrderBySessionDateDescCompletedAtDesc(horseId, pageable)
                .map(this::toResponse);
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
            throw new BusinessException("Liên kết video phải là URL HTTP hoặc HTTPS hợp lệ");
        }
        return normalized;
    }

    TrainingEvaluationResponse toResponse(TrainingEvaluation evaluation) {
        return new TrainingEvaluationResponse(
                evaluation.getId(),
                new TrainingEvaluationResponse.HorseSummary(
                        evaluation.getHorse().getId(),
                        evaluation.getHorse().getCode(),
                        evaluation.getHorse().getName()),
                evaluation.getTrainingSchedule() != null ? evaluation.getTrainingSchedule().getId() : null,
                new TrainingEvaluationResponse.UserSummary(
                        evaluation.getRecordedBy().getId(),
                        evaluation.getRecordedBy().getUsername()),
                evaluation.getSessionDate(),
                evaluation.getDurationMinutes(),
                evaluation.getDistanceMeters(),
                evaluation.getAverageSpeedKmh(),
                evaluation.getTrainingLoad(),
                evaluation.getHeartRateRecoveryBpm(),
                evaluation.getPerceivedExertion(),
                evaluation.getFitnessStatus(),
                evaluation.getTrainerNotes(),
                evaluation.getVideoUrl(),
                evaluation.getCompletedAt());
    }
}
