package com.racehorse.horse.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.horse.dto.CreateHorseRequest;
import com.racehorse.horse.dto.HorseResponse;
import com.racehorse.horse.dto.ReplaceOwnershipRequest;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.mapper.HorseMapper;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import com.racehorse.horse.repository.HorseRepository;

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
