package com.racehorse.health.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.health.dto.CreateTrainingLockRequest;
import com.racehorse.health.dto.ReleaseTrainingLockRequest;
import com.racehorse.health.entity.TrainingLock;
import com.racehorse.health.repository.TrainingLockRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingLockServiceTest {

    @Mock TrainingLockRepository repository;
    @Mock HorseAccessService horseAccessService;
    @Mock UserAccountRepository userRepository;

    private TrainingLockService service;
    private final Instant now = Instant.parse("2026-09-15T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new TrainingLockService(
                repository, horseAccessService, userRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void rejectsSecondActiveLockForSameHorse() {
        UUID horseId = UUID.randomUUID();
        when(horseAccessService.requireActive(horseId)).thenReturn(horse());
        when(repository.findByHorseIdAndReleasedAtIsNull(horseId))
                .thenReturn(Optional.of(new TrainingLock(horse(), "Existing", null, veterinarian(), now)));

        assertThatThrownBy(() -> service.lock(
                horseId, UUID.randomUUID(), new CreateTrainingLockRequest("New lock", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang có");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cannotReleaseBeforeRequiredReviewDate() {
        UUID lockId = UUID.randomUUID();
        TrainingLock value = new TrainingLock(
                horse(), "Injury", LocalDate.of(2026, 9, 20), veterinarian(), now);
        when(repository.findDetailById(lockId)).thenReturn(Optional.of(value));

        assertThatThrownBy(() -> service.release(
                lockId, UUID.randomUUID(), new ReleaseTrainingLockRequest("Recovered")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chưa đến ngày tái khám");
    }

    private Horse horse() {
        return new Horse("H-LOCK", "Lock Test", null, LocalDate.of(2022, 1, 1),
                HorseGender.MARE, "Bay", "Thoroughbred", "A-01", HorseStatus.INJURED);
    }

    private UserAccount veterinarian() {
        return new UserAccount("vet", "vet@test.local", "encoded", Set.of(Role.VETERINARIAN));
    }
}
