package com.racehorse.health.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.CreateInjuryMarkerRequest;
import com.racehorse.health.dto.CreateInjuryUpdateRequest;
import com.racehorse.health.dto.InjuryMarkerResponse;
import com.racehorse.health.entity.InjuryMarker;
import com.racehorse.health.entity.InjuryRecoveryUpdate;
import com.racehorse.health.repository.InjuryMarkerRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class InjuryMarkerService {

    private final InjuryMarkerRepository repository;
    private final HorseAccessService horseAccessService;
    private final UserAccountRepository userRepository;
    private final Clock clock;

    public InjuryMarkerService(InjuryMarkerRepository repository,
                               HorseAccessService horseAccessService,
                               UserAccountRepository userRepository,
                               Clock clock) {
        this.repository = repository;
        this.horseAccessService = horseAccessService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** FR10: Create a new injury marker pin on the 3D model. */
    @Transactional
    public InjuryMarkerResponse create(UUID horseId, UUID actorId, CreateInjuryMarkerRequest req) {
        Horse horse = horseAccessService.requireActive(horseId);
        UserAccount actor = requireUser(actorId);
        InjuryMarker marker = new InjuryMarker(
                horse,
                req.modelName().trim(),
                req.bodyPartLabel().trim(),
                req.coordinateX(),
                req.coordinateY(),
                req.coordinateZ(),
                req.initialSeverity().trim(),
                actor,
                clock.instant());
        return toResponse(repository.save(marker));
    }

    /** FR10: List all injury markers for a horse (with full recovery timeline). */
    @Transactional(readOnly = true)
    public List<InjuryMarkerResponse> listByHorse(UUID horseId, UUID requesterId, boolean privileged) {
        horseAccessService.requireReadable(horseId, requesterId, privileged);
        return repository.findByHorseIdOrderByCreatedAtDesc(horseId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** FR10: Add a recovery progress update to an existing injury marker. */
    @Transactional
    public InjuryMarkerResponse addUpdate(UUID markerId, UUID actorId, CreateInjuryUpdateRequest req) {
        InjuryMarker marker = requireMarker(markerId);
        UserAccount actor = requireUser(actorId);
        InjuryRecoveryUpdate update = new InjuryRecoveryUpdate(
                marker,
                req.statusLabel().trim(),
                req.severityLabel() == null ? null : req.severityLabel().trim(),
                req.notes().trim(),
                actor,
                clock.instant());
        marker.getRecoveryUpdates().add(update);
        return toResponse(repository.save(marker));
    }

    /** FR10: Get a single injury marker with its full recovery timeline. */
    @Transactional(readOnly = true)
    public InjuryMarkerResponse getById(UUID markerId) {
        return toResponse(requireMarker(markerId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private InjuryMarker requireMarker(UUID id) {
        return repository.findWithUpdatesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm chấn thương: " + id));
    }

    private UserAccount requireUser(UUID id) {
        return userRepository.findById(id).filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + id));
    }

    private InjuryMarkerResponse toResponse(InjuryMarker m) {
        List<InjuryMarkerResponse.RecoveryUpdateResponse> updates = m.getRecoveryUpdates().stream()
                .map(u -> new InjuryMarkerResponse.RecoveryUpdateResponse(
                        u.getId(),
                        u.getStatusLabel(),
                        u.getSeverityLabel(),
                        u.getNotes(),
                        new InjuryMarkerResponse.UserSummary(
                                u.getRecordedBy().getId(), u.getRecordedBy().getUsername()),
                        u.getRecordedAt()))
                .toList();

        return new InjuryMarkerResponse(
                m.getId(),
                m.getHorse().getId(),
                m.getModelName(),
                m.getBodyPartLabel(),
                m.getCoordinateX(),
                m.getCoordinateY(),
                m.getCoordinateZ(),
                m.getInitialSeverity(),
                m.currentSeverity(),
                new InjuryMarkerResponse.UserSummary(
                        m.getCreatedBy().getId(), m.getCreatedBy().getUsername()),
                m.getCreatedAt(),
                updates);
    }
}
