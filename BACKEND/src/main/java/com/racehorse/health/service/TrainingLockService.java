package com.racehorse.health.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.CreateTrainingLockRequest;
import com.racehorse.health.dto.ReleaseTrainingLockRequest;
import com.racehorse.health.dto.TrainingLockResponse;
import com.racehorse.health.entity.TrainingLock;
import com.racehorse.health.repository.TrainingLockRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TrainingLockService {

    private final TrainingLockRepository repository;
    private final HorseAccessService horseAccessService;
    private final UserAccountRepository userRepository;
    private final Clock clock;

    public TrainingLockService(
            TrainingLockRepository repository,
            HorseAccessService horseAccessService,
            UserAccountRepository userRepository,
            Clock clock) {
        this.repository = repository;
        this.horseAccessService = horseAccessService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public TrainingLockResponse lock(UUID horseId, UUID actorId, CreateTrainingLockRequest request) {
        Horse horse = horseAccessService.requireActive(horseId);
        if (repository.findByHorseIdAndReleasedAtIsNull(horseId).isPresent()) {
            throw new BusinessException("Ngựa đang có một lệnh khóa huấn luyện hoạt động");
        }
        UserAccount actor = requireUser(actorId);
        TrainingLock trainingLock = new TrainingLock(
                horse, request.reason().trim(), request.reviewDate(), actor, clock.instant());
        return toResponse(repository.save(trainingLock));
    }

    @Transactional
    public TrainingLockResponse release(UUID lockId, UUID actorId, ReleaseTrainingLockRequest request) {
        TrainingLock trainingLock = requireLock(lockId);
        if (!trainingLock.isActive()) throw new BusinessException("Lệnh khóa huấn luyện đã được gỡ");
        LocalDate today = LocalDate.now(clock);
        if (trainingLock.getReviewDate() != null && today.isBefore(trainingLock.getReviewDate())) {
            throw new BusinessException("Chưa đến ngày tái khám nên chưa thể gỡ khóa huấn luyện");
        }
        trainingLock.release(requireUser(actorId), request.reason().trim(), clock.instant());
        return toResponse(repository.save(trainingLock));
    }

    @Transactional(readOnly = true)
    public TrainingLockResponse current(UUID horseId, UUID userId, boolean privileged) {
        horseAccessService.requireReadable(horseId, userId, privileged);
        return repository.findByHorseIdAndReleasedAtIsNull(horseId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Ngựa không có lệnh khóa huấn luyện hoạt động"));
    }

    @Transactional(readOnly = true)
    public List<TrainingLockResponse> history(UUID horseId, UUID userId, boolean privileged) {
        horseAccessService.requireReadable(horseId, userId, privileged);
        return repository.findByHorseIdOrderByLockedAtDesc(horseId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TrainingLockResponse current(UUID horseId) {
        return current(horseId, null, true);
    }

    @Transactional(readOnly = true)
    public List<TrainingLockResponse> history(UUID horseId) {
        return history(horseId, null, true);
    }

    private TrainingLock requireLock(UUID id) {
        return repository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lệnh khóa huấn luyện: " + id));
    }

    private UserAccount requireUser(UUID id) {
        return userRepository.findById(id).filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + id));
    }

    TrainingLockResponse toResponse(TrainingLock value) {
        return new TrainingLockResponse(
                value.getId(),
                new TrainingLockResponse.HorseSummary(
                        value.getHorse().getId(), value.getHorse().getCode(), value.getHorse().getName()),
                value.getReason(),
                value.getReviewDate(),
                new TrainingLockResponse.UserSummary(value.getLockedBy().getId(), value.getLockedBy().getUsername()),
                value.getLockedAt(),
                value.isActive(),
                value.getReleasedBy() == null ? null : new TrainingLockResponse.UserSummary(
                        value.getReleasedBy().getId(), value.getReleasedBy().getUsername()),
                value.getReleaseReason(),
                value.getReleasedAt());
    }
}
