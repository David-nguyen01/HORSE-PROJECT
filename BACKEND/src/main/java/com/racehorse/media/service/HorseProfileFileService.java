package com.racehorse.media.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.media.dto.HorseProfileFileResponse;
import com.racehorse.media.entity.HorseProfileFile;
import com.racehorse.media.entity.ProfileFileStatus;
import com.racehorse.media.repository.HorseProfileFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HorseProfileFileService {

    private final HorseProfileFileRepository repository;
    private final UserAccountRepository userRepository;
    private final HorseAccessService horseAccessService;
    private final ProfileFileValidator validator;
    private final CloudStorageClient cloudStorage;
    private final Clock clock;

    public HorseProfileFileService(
            HorseProfileFileRepository repository,
            UserAccountRepository userRepository,
            HorseAccessService horseAccessService,
            ProfileFileValidator validator,
            CloudStorageClient cloudStorage,
            Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.horseAccessService = horseAccessService;
        this.validator = validator;
        this.cloudStorage = cloudStorage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HorseProfileFileResponse getActive(UUID horseId) {
        horseAccessService.requireExisting(horseId);
        return repository.findFirstByHorseIdAndStatus(horseId, ProfileFileStatus.ACTIVE)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ngựa chưa có file hồ sơ chính: " + horseId));
    }

    @Transactional
    public HorseProfileFileResponse replaceDirectly(
            UUID horseId,
            UUID actorId,
            MultipartFile multipartFile) {
        Horse horse = horseAccessService.requireExisting(horseId);
        UserAccount actor = findUser(actorId);
        UploadedProfileFile uploaded = upload(horseId, multipartFile);
        try {
            repository.findFirstByHorseIdAndStatus(horseId, ProfileFileStatus.ACTIVE)
                    .ifPresent(HorseProfileFile::replace);
            repository.flush();

            HorseProfileFile profileFile = toEntity(
                    horse, actor, uploaded, ProfileFileStatus.ACTIVE);
            profileFile.approve(actor, clock.instant());
            return toResponse(repository.save(profileFile));
        } catch (RuntimeException exception) {
            safeDelete(uploaded.asset());
            throw exception;
        }
    }

    @Transactional
    public HorseProfileFileResponse propose(
            UUID horseId,
            UUID ownerId,
            MultipartFile multipartFile) {
        Horse horse = horseAccessService.requireOwned(horseId, ownerId);
        UserAccount owner = findUser(ownerId);
        UploadedProfileFile uploaded = upload(horseId, multipartFile);
        try {
            HorseProfileFile proposal = toEntity(
                    horse, owner, uploaded, ProfileFileStatus.PENDING);
            return toResponse(repository.save(proposal));
        } catch (RuntimeException exception) {
            safeDelete(uploaded.asset());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<HorseProfileFileResponse> pendingProposals(UUID horseId) {
        horseAccessService.requireExisting(horseId);
        return repository.findByHorseIdAndStatusOrderByCreatedAtAsc(
                        horseId, ProfileFileStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HorseProfileFileResponse approve(
            UUID horseId,
            UUID proposalId,
            UUID approverId) {
        horseAccessService.requireExisting(horseId);
        UserAccount approver = findUser(approverId);
        HorseProfileFile proposal = repository.findByIdAndHorseIdAndStatus(
                        proposalId, horseId, ProfileFileStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đề xuất file đang chờ: " + proposalId));

        repository.findFirstByHorseIdAndStatus(horseId, ProfileFileStatus.ACTIVE)
                .ifPresent(HorseProfileFile::replace);
        repository.flush();
        proposal.approve(approver, clock.instant());
        return toResponse(proposal);
    }

    @Transactional
    public void deleteActive(UUID horseId) {
        horseAccessService.requireExisting(horseId);
        HorseProfileFile active = repository.findFirstByHorseIdAndStatus(
                        horseId, ProfileFileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ngựa chưa có file hồ sơ chính: " + horseId));
        cloudStorage.delete(active.getCloudinaryPublicId(), active.getResourceType());
        active.delete();
    }

    private UploadedProfileFile upload(UUID horseId, MultipartFile multipartFile) {
        ProfileFileValidator.ValidatedProfileFile file = validator.validate(multipartFile);
        CloudAsset asset = cloudStorage.upload(file.content(), file.filename(), horseId);
        try {
            validator.validateCloudinaryResult(file.category(), asset);
        } catch (RuntimeException exception) {
            safeDelete(asset);
            throw exception;
        }
        return new UploadedProfileFile(file, asset);
    }

    private HorseProfileFile toEntity(
            Horse horse,
            UserAccount uploader,
            UploadedProfileFile uploaded,
            ProfileFileStatus status) {
        return new HorseProfileFile(
                horse,
                uploader,
                uploaded.asset().assetId(),
                uploaded.asset().publicId(),
                uploaded.asset().resourceType(),
                uploaded.asset().format(),
                uploaded.file().filename(),
                uploaded.file().contentType(),
                uploaded.asset().bytes(),
                uploaded.asset().secureUrl(),
                status);
    }

    private UserAccount findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + userId));
    }

    private HorseProfileFileResponse toResponse(HorseProfileFile file) {
        return new HorseProfileFileResponse(
                file.getId(),
                file.getHorse().getId(),
                userSummary(file.getUploadedBy()),
                userSummary(file.getApprovedBy()),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getFileFormat(),
                file.getFileSize(),
                file.getSecureUrl(),
                file.getStatus(),
                file.getApprovedAt(),
                file.getCreatedAt());
    }

    private HorseProfileFileResponse.UserSummary userSummary(UserAccount user) {
        return user == null ? null : new HorseProfileFileResponse.UserSummary(
                user.getId(), user.getUsername());
    }

    private void safeDelete(CloudAsset asset) {
        try {
            cloudStorage.delete(asset.publicId(), asset.resourceType());
        } catch (RuntimeException ignored) {
            // Preserve the original validation/database error. Cloudinary cleanup can be retried operationally.
        }
    }

    private record UploadedProfileFile(
            ProfileFileValidator.ValidatedProfileFile file,
            CloudAsset asset
    ) {
    }
}
