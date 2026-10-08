package com.racehorse.horse.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.horse.dto.CreateHorseRequest;
import com.racehorse.horse.dto.HorseResponse;
import com.racehorse.horse.dto.ReplaceOwnershipRequest;
import com.racehorse.horse.dto.UpdateOwnedHorseRequest;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseOwnership;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.mapper.HorseMapper;
import com.racehorse.horse.repository.HorseRepository;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
        when(userRepository.findById(request.initialOwnerId())).thenReturn(Optional.of(
                new UserAccount("owner", "encoded", "owner@example.com", Set.of(Role.HORSE_OWNER))));
        when(horseRepository.save(any(Horse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HorseResponse response = horseService.create(request);

        assertThat(response.code()).isEqualTo("H-001");
        assertThat(response.microchipPassportNumber()).isEqualTo("CHIP-001");
        assertThat(response.status()).isEqualTo(HorseStatus.ACTIVE);
        verify(horseRepository).save(any(Horse.class));
    }

    @Test
    void createWithInitialOwnerAssignsOwnershipSuccessfully() {
        UUID ownerId = UUID.randomUUID();
        UserAccount owner = new UserAccount("owner1", "encoded", "owner@gmail.com", Set.of(Role.HORSE_OWNER));
        CreateHorseRequest request = new CreateHorseRequest(
                "H-100",
                "Thunder",
                "CHIP-100",
                LocalDate.of(2022, 1, 1),
                HorseGender.STALLION,
                "Bay",
                "Thoroughbred",
                null,
                null,
                null,
                "Stable B-01",
                HorseStatus.ACTIVE,
                ownerId
        );

        when(horseRepository.save(any(Horse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        HorseResponse response = horseService.create(request);

        assertThat(response.code()).isEqualTo("H-100");
        assertThat(response.owners()).hasSize(1);
        assertThat(response.owners().get(0).username()).isEqualTo("owner1");
        assertThat(response.owners().get(0).percentage()).isEqualByComparingTo("100.00");
        verify(ownershipRepository).save(any(HorseOwnership.class));
    }

    @Test
    void ownerCannotChooseStallOrPaymentStatusWhenCreatingHorse() {
        UUID ownerId = UUID.randomUUID();
        UserAccount owner = new UserAccount("owner", "owner@example.com", "encoded", Set.of(Role.HORSE_OWNER));
        CreateHorseRequest request = new CreateHorseRequest("H-NEW", "New horse", null,
                LocalDate.of(2023, 1, 1), HorseGender.STALLION, "Bay", "Thoroughbred",
                null, null, null, "Other staff's stall", HorseStatus.INJURED, null);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(horseRepository.save(any(Horse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HorseResponse response = horseService.createForOwner(request, ownerId);

        assertThat(response.stableLocation()).isEmpty();
        assertThat(response.status()).isEqualTo(HorseStatus.PENDING_PAYMENT);
        assertThat(response.paymentStatus()).isEqualTo("UNPAID");
    }

    @Test
    void ownerCanChooseOwnedSireAndDamWhenCreatingHorse() {
        UUID ownerId = UUID.randomUUID();
        UUID sireId = UUID.randomUUID();
        UUID damId = UUID.randomUUID();
        Horse sire = horseEntity("SIRE");
        Horse dam = new Horse("DAM", "Dam", null, LocalDate.of(2021, 2, 1),
                HorseGender.MARE, "Bay", "Thoroughbred", "Stable A", HorseStatus.ACTIVE);
        CreateHorseRequest request = new CreateHorseRequest("FOAL", "Foal", null,
                LocalDate.of(2024, 1, 1), HorseGender.MARE, "Bay", "Thoroughbred",
                sireId, damId, null, null, null, null);
        when(ownershipRepository.existsByHorseIdAndOwnerId(sireId, ownerId)).thenReturn(true);
        when(ownershipRepository.existsByHorseIdAndOwnerId(damId, ownerId)).thenReturn(true);
        when(horseRepository.findById(sireId)).thenReturn(Optional.of(sire));
        when(horseRepository.findById(damId)).thenReturn(Optional.of(dam));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(
                new UserAccount("owner", "encoded", "owner@example.com", Set.of(Role.HORSE_OWNER))));
        when(horseRepository.save(any(Horse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HorseResponse response = horseService.createForOwner(request, ownerId);

        assertThat(response.sire().name()).isEqualTo("Lightning");
        assertThat(response.dam().name()).isEqualTo("Dam");
        assertThat(response.status()).isEqualTo(HorseStatus.PENDING_PAYMENT);
    }

    @Test
    void ownerCannotChooseAnotherOwnersHorseAsParent() {
        UUID ownerId = UUID.randomUUID();
        UUID sireId = UUID.randomUUID();
        CreateHorseRequest request = new CreateHorseRequest("FOAL", "Foal", null,
                LocalDate.of(2024, 1, 1), HorseGender.MARE, "Bay", "Thoroughbred",
                sireId, null, null, null, null, null);

        assertThatThrownBy(() -> horseService.createForOwner(request, ownerId))
                .isInstanceOf(com.racehorse.common.exception.ResourceNotFoundException.class);
        verify(horseRepository, never()).save(any(Horse.class));
    }

    @Test
    void ownerProfileEditPreservesAssignedStallAndOperationalStatus() {
        UUID horseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Horse horse = horseEntity("H-EDIT");
        when(horseRepository.findDetailById(horseId)).thenReturn(Optional.of(horse));
        when(ownershipRepository.existsByHorseIdAndOwnerId(horseId, ownerId)).thenReturn(true);
        UpdateOwnedHorseRequest request = new UpdateOwnedHorseRequest("Renamed", null,
                LocalDate.of(2022, 3, 10), HorseGender.STALLION, "White blaze", "Thoroughbred");

        HorseResponse updated = horseService.updateForOwner(horseId, ownerId, request);

        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.stableLocation()).isEqualTo("Stable A-01");
        assertThat(updated.status()).isEqualTo(HorseStatus.ACTIVE);
    }

    @Test
    void ownerCanEditPedigreeWithAnOwnedEligibleSire() {
        UUID horseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID sireId = UUID.randomUUID();
        Horse foal = horseEntity("H-EDIT");
        ReflectionTestUtils.setField(foal, "id", horseId);
        Horse sire = new Horse("SIRE", "Sire", null, LocalDate.of(2021, 1, 1),
                HorseGender.STALLION, "Bay", "Thoroughbred", "Stable A-01", HorseStatus.ACTIVE);
        when(horseRepository.findDetailById(horseId)).thenReturn(Optional.of(foal));
        when(ownershipRepository.existsByHorseIdAndOwnerId(horseId, ownerId)).thenReturn(true);
        when(ownershipRepository.existsByHorseIdAndOwnerId(sireId, ownerId)).thenReturn(true);
        when(horseRepository.findById(sireId)).thenReturn(Optional.of(sire));
        when(horseRepository.findDetailById(sireId)).thenReturn(Optional.of(sire));

        UpdateOwnedHorseRequest request = new UpdateOwnedHorseRequest("Lightning", null,
                LocalDate.of(2023, 1, 1), HorseGender.STALLION, "White blaze", "Thoroughbred",
                sireId, null, null, null, null, null);

        HorseResponse response = horseService.updateForOwner(horseId, ownerId, request);

        assertThat(response.sire()).isNotNull();
        assertThat(response.sire().name()).isEqualTo("Sire");
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
                status,
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
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
