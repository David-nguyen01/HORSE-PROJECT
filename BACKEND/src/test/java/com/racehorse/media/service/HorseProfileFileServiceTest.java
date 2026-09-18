package com.racehorse.media.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.media.dto.HorseProfileFileResponse;
import com.racehorse.media.entity.HorseProfileFile;
import com.racehorse.media.entity.ProfileFileStatus;
import com.racehorse.media.repository.HorseProfileFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorseProfileFileServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T08:00:00Z");

    @Mock private HorseProfileFileRepository repository;
    @Mock private UserAccountRepository userRepository;
    @Mock private HorseAccessService horseAccessService;
    @Mock private CloudStorageClient cloudStorage;

    private HorseProfileFileService service;
    private final ProfileFileValidator validator = new ProfileFileValidator();

    @BeforeEach
    void setUp() {
        service = new HorseProfileFileService(
                repository,
                userRepository,
                horseAccessService,
                validator,
                cloudStorage,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void managerReplacementBecomesActiveImmediately() {
        UUID horseId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        Horse horse = horse();
        UserAccount manager = user("manager", Role.CLUB_MANAGER);
        when(horseAccessService.requireExisting(horseId)).thenReturn(horse);
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(cloudStorage.upload(any(byte[].class), any(String.class), any(UUID.class)))
                .thenReturn(imageAsset());
        when(repository.findFirstByHorseIdAndStatus(horseId, ProfileFileStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(HorseProfileFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HorseProfileFileResponse response = service.replaceDirectly(
                horseId, managerId, imageFile());

        assertThat(response.status()).isEqualTo(ProfileFileStatus.ACTIVE);
        assertThat(response.approvedAt()).isEqualTo(NOW);
        assertThat(response.approvedBy().username()).isEqualTo("manager");
    }

    @Test
    void ownerUploadStaysPendingUntilApproved() {
        UUID horseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Horse horse = horse();
        UserAccount owner = user("owner", Role.HORSE_OWNER);
        when(horseAccessService.requireOwned(horseId, ownerId)).thenReturn(horse);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(cloudStorage.upload(any(byte[].class), any(String.class), any(UUID.class)))
                .thenReturn(imageAsset());
        when(repository.save(any(HorseProfileFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HorseProfileFileResponse response = service.propose(horseId, ownerId, imageFile());

        assertThat(response.status()).isEqualTo(ProfileFileStatus.PENDING);
        assertThat(response.approvedAt()).isNull();
        assertThat(response.approvedBy()).isNull();
    }

    @Test
    void approvingProposalReplacesCurrentWithoutDeletingCloudAssets() {
        UUID horseId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        Horse horse = horse();
        UserAccount owner = user("owner", Role.HORSE_OWNER);
        UserAccount manager = user("manager", Role.CLUB_MANAGER);
        HorseProfileFile current = profileFile(horse, manager, ProfileFileStatus.ACTIVE);
        HorseProfileFile proposal = profileFile(horse, owner, ProfileFileStatus.PENDING);
        when(horseAccessService.requireExisting(horseId)).thenReturn(horse);
        when(userRepository.findById(approverId)).thenReturn(Optional.of(manager));
        when(repository.findByIdAndHorseIdAndStatus(
                proposalId, horseId, ProfileFileStatus.PENDING)).thenReturn(Optional.of(proposal));
        when(repository.findFirstByHorseIdAndStatus(horseId, ProfileFileStatus.ACTIVE))
                .thenReturn(Optional.of(current));

        HorseProfileFileResponse response = service.approve(horseId, proposalId, approverId);

        assertThat(current.getStatus()).isEqualTo(ProfileFileStatus.REPLACED);
        assertThat(response.status()).isEqualTo(ProfileFileStatus.ACTIVE);
        assertThat(response.approvedAt()).isEqualTo(NOW);
    }

    @Test
    void deletingMainFileDeletesCloudinaryAssetAndMarksMetadata() {
        UUID horseId = UUID.randomUUID();
        Horse horse = horse();
        HorseProfileFile current = profileFile(
                horse, user("manager", Role.CLUB_MANAGER), ProfileFileStatus.ACTIVE);
        when(horseAccessService.requireExisting(horseId)).thenReturn(horse);
        when(repository.findFirstByHorseIdAndStatus(horseId, ProfileFileStatus.ACTIVE))
                .thenReturn(Optional.of(current));

        service.deleteActive(horseId);

        verify(cloudStorage).delete("racehorse/horses/file", "image");
        assertThat(current.getStatus()).isEqualTo(ProfileFileStatus.DELETED);
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "horse.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1});
    }

    private CloudAsset imageAsset() {
        return new CloudAsset(
                "asset-id",
                "racehorse/horses/file",
                "image",
                "png",
                3,
                "https://res.cloudinary.com/example/image/upload/file.png");
    }

    private HorseProfileFile profileFile(
            Horse horse,
            UserAccount uploader,
            ProfileFileStatus status) {
        return new HorseProfileFile(
                horse,
                uploader,
                UUID.randomUUID().toString(),
                "racehorse/horses/file",
                "image",
                "png",
                "horse.png",
                "image/png",
                3,
                "https://res.cloudinary.com/example/image/upload/file.png",
                status);
    }

    private UserAccount user(String username, Role role) {
        return new UserAccount(
                username,
                username + "@example.com",
                "encoded-password",
                Set.of(role));
    }

    private Horse horse() {
        return new Horse(
                "H-001",
                "Lightning",
                null,
                LocalDate.of(2022, 3, 10),
                HorseGender.STALLION,
                "White blaze",
                "Thoroughbred",
                "Stable A-01",
                HorseStatus.ACTIVE);
    }
}
