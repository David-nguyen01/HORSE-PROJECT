package com.racehorse.horse.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.horse.dto.CreateHorseRequest;
import com.racehorse.horse.dto.HorseResponse;
import com.racehorse.horse.dto.ReplaceOwnershipRequest;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.mapper.HorseMapper;
import com.racehorse.horse.repository.HorseRepository;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorseServiceTest {

    @Mock
    private HorseRepository horseRepository;

    @Mock
    private HorseOwnershipRepository ownershipRepository;

    @Mock
    private UserAccountRepository userRepository;

    private HorseService horseService;

    @BeforeEach
    void setUp() {
        horseService = new HorseService(
                horseRepository,
                ownershipRepository,
                userRepository,
                new HorseMapper(),
                Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createNormalizesIdentifiersAndUsesActiveAsDefaultStatus() {
        CreateHorseRequest request = request("  h-001  ", null);
        when(horseRepository.save(any(Horse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HorseResponse response = horseService.create(request);

        assertThat(response.code()).isEqualTo("H-001");
        assertThat(response.microchipPassportNumber()).isEqualTo("CHIP-001");
        assertThat(response.status()).isEqualTo(HorseStatus.ACTIVE);
        verify(horseRepository).save(any(Horse.class));
    }

    @Test
    void createRejectsDuplicateHorseCode() {
        CreateHorseRequest request = request("H-001", HorseStatus.RESTING);
        when(horseRepository.existsByCodeIgnoreCase("H-001")).thenReturn(true);

        assertThatThrownBy(() -> horseService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mã ngựa đã tồn tại");
        verify(horseRepository, never()).save(any(Horse.class));
    }

    @Test
    void ownershipChangeRequiresExplicitConfirmation() {
        ReplaceOwnershipRequest request = new ReplaceOwnershipRequest(
                false,
                List.of(new ReplaceOwnershipRequest.OwnerShare(
                        UUID.randomUUID(), new BigDecimal("100.00"))));

        assertThatThrownBy(() -> horseService.replaceOwnership(UUID.randomUUID(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("xác nhận");
        verify(ownershipRepository, never()).deleteByHorseId(any(UUID.class));
    }

    @Test
    void ownershipPercentagesMustTotalExactlyOneHundred() {
        UUID horseId = UUID.randomUUID();
        Horse horse = horseEntity("H-002");
        when(horseRepository.findDetailById(horseId)).thenReturn(Optional.of(horse));
        ReplaceOwnershipRequest request = new ReplaceOwnershipRequest(
                true,
                List.of(new ReplaceOwnershipRequest.OwnerShare(
                        UUID.randomUUID(), new BigDecimal("90.00"))));

        assertThatThrownBy(() -> horseService.replaceOwnership(horseId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("100.00%");
        verify(ownershipRepository, never()).deleteByHorseId(any(UUID.class));
    }

    @Test
    void ownershipCanOnlyBeAssignedToHorseOwnerAccounts() {
        UUID horseId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Horse horse = horseEntity("H-003");
        UserAccount staff = new UserAccount(
                "staff", "staff@example.com", "encoded", Set.of(Role.STABLE_STAFF));
        when(horseRepository.findDetailById(horseId)).thenReturn(Optional.of(horse));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        ReplaceOwnershipRequest request = new ReplaceOwnershipRequest(
                true,
                List.of(new ReplaceOwnershipRequest.OwnerShare(staffId, new BigDecimal("100.00"))));

        assertThatThrownBy(() -> horseService.replaceOwnership(horseId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HORSE_OWNER");
        verify(ownershipRepository, never()).deleteByHorseId(any(UUID.class));
    }

<<<<<<< Updated upstream
=======
    @Test
    void unpaidHorseIsHiddenFromVeterinarianAndStableStaffButVisibleToManager() {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Horse horse = horseEntity("H-UNPAID");
        ReflectionTestUtils.setField(horse, "id", horseId);
        horse.setPaymentStatus("UNPAID");
        when(horseRepository.findDetailById(horseId)).thenReturn(Optional.of(horse));

        assertThatThrownBy(() -> horseService.getByIdForUser(horseId, userId, Role.VETERINARIAN))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> horseService.getByIdForUser(horseId, userId, Role.STABLE_STAFF))
                .isInstanceOf(ResourceNotFoundException.class);
        when(ownershipRepository.findByHorseIdOrderByOwnerUsernameAsc(horseId)).thenReturn(List.of());
        assertThat(horseService.getByIdForUser(horseId, userId, Role.CLUB_MANAGER).id()).isEqualTo(horseId);
    }

    @Test
    void paidOrSponsoredHorseIsVisibleToCareRolesButExpiredPlanIsNot() {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Horse horse = horseEntity("H-PAID");
        ReflectionTestUtils.setField(horse, "id", horseId);
        horse.setPaymentStatus("PAID");
        horse.setIntakeStatus("WAITING_FOR_VET");
        horse.setPaidUntil(LocalDate.of(2026, 9, 12));
        when(horseRepository.findDetailById(horseId)).thenReturn(Optional.of(horse));
        when(ownershipRepository.findByHorseIdOrderByOwnerUsernameAsc(horseId)).thenReturn(List.of());

        assertThat(horseService.getByIdForUser(horseId, userId, Role.VETERINARIAN).id()).isEqualTo(horseId);
        horse.setPaidUntil(LocalDate.of(2026, 9, 9));
        assertThatThrownBy(() -> horseService.getByIdForUser(horseId, userId, Role.STABLE_STAFF))
                .isInstanceOf(ResourceNotFoundException.class);
        horse.setServiceOverride(true);
        assertThat(horseService.getByIdForUser(horseId, userId, Role.STABLE_STAFF).id()).isEqualTo(horseId);
    }

    @Test
    void trainerCannotOpenAnotherTrainersHorseByGuessingItsId() {
        UUID horseId = UUID.randomUUID();
        Horse horse = horseEntity("H-TRAINER");
        ReflectionTestUtils.setField(horse, "id", horseId);
        horse.setPaymentStatus("PAID");
        when(horseRepository.findDetailById(horseId)).thenReturn(Optional.of(horse));

        assertThatThrownBy(() -> horseService.getByIdForUser(horseId, UUID.randomUUID(), Role.HEAD_TRAINER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createRejectsMissingInitialOwner() {
        CreateHorseRequest request = new CreateHorseRequest(
                "H-NO-OWNER", "Horse Without Owner", null, LocalDate.of(2023, 1, 1),
                HorseGender.STALLION, "Bay", "Thoroughbred", null, null, null, "", HorseStatus.ACTIVE, null);

        assertThatThrownBy(() -> horseService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mỗi ngựa phải có đúng một chủ sở hữu");
        verify(horseRepository, never()).save(any(Horse.class));
    }

    @Test
    void oneOwnerCanOwnMultipleHorses() {
        UUID ownerId = UUID.randomUUID();
        UserAccount owner = new UserAccount("ownerMultiple", "encoded", "owner@example.com", Set.of(Role.HORSE_OWNER));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(horseRepository.save(any(Horse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateHorseRequest horse1 = new CreateHorseRequest(
                "H-O1", "Horse One", null, LocalDate.of(2022, 1, 1),
                HorseGender.STALLION, "Bay", "Thoroughbred", null, null, null, "", HorseStatus.ACTIVE, ownerId);
        CreateHorseRequest horse2 = new CreateHorseRequest(
                "H-O2", "Horse Two", null, LocalDate.of(2023, 2, 2),
                HorseGender.MARE, "Chestnut", "Arabian", null, null, null, "", HorseStatus.ACTIVE, ownerId);

        HorseResponse r1 = horseService.create(horse1);
        HorseResponse r2 = horseService.create(horse2);

        assertThat(r1.owners()).hasSize(1);
        assertThat(r1.owners().get(0).username()).isEqualTo("ownerMultiple");
        assertThat(r2.owners()).hasSize(1);
        assertThat(r2.owners().get(0).username()).isEqualTo("ownerMultiple");
    }

    @Test
    void replaceOwnershipRejectsMultipleOwners() {
        UUID horseId = UUID.randomUUID();
        ReplaceOwnershipRequest request = new ReplaceOwnershipRequest(
                true,
                List.of(
                        new ReplaceOwnershipRequest.OwnerShare(UUID.randomUUID(), new BigDecimal("50.00")),
                        new ReplaceOwnershipRequest.OwnerShare(UUID.randomUUID(), new BigDecimal("50.00"))));

        assertThatThrownBy(() -> horseService.replaceOwnership(horseId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mỗi ngựa phải có đúng một chủ sở hữu");
        verify(ownershipRepository, never()).deleteByHorseId(any(UUID.class));
    }

    @Test
    void replaceOwnershipReplacesSingleOwnerSuccessfully() {
        UUID horseId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        Horse horse = horseEntity("H-REPLACE");
        UserAccount newOwner = new UserAccount("newOwner", "encoded", "new@example.com", Set.of(Role.HORSE_OWNER));

        when(horseRepository.findDetailById(horseId)).thenReturn(Optional.of(horse));
        when(userRepository.findById(newOwnerId)).thenReturn(Optional.of(newOwner));

        ReplaceOwnershipRequest request = new ReplaceOwnershipRequest(
                true,
                List.of(new ReplaceOwnershipRequest.OwnerShare(newOwnerId, new BigDecimal("100.00"))));

        HorseResponse response = horseService.replaceOwnership(horseId, request);

        assertThat(response).isNotNull();
        verify(ownershipRepository).deleteByHorseId(horseId);
        verify(ownershipRepository).saveAll(any());
    }

>>>>>>> Stashed changes
    private CreateHorseRequest request(String code, HorseStatus status) {
        return new CreateHorseRequest(
                code,
                "Lightning",
                " chip-001 ",
                LocalDate.of(2022, 3, 10),
                HorseGender.STALLION,
                "White blaze",
                "Thoroughbred",
                null,
                null,
                null,
                "Stable A-01",
                status);
    }

    private Horse horseEntity(String code) {
        return new Horse(
                code,
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
