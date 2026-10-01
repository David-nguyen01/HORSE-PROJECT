package com.racehorse.training.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.health.entity.TrainingLock;
import com.racehorse.health.repository.TrainingLockRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.notification.service.NotificationService;
import com.racehorse.training.dto.CreateTrainingScheduleRequest;
import com.racehorse.training.entity.SurfaceType;
import com.racehorse.training.entity.TrainingPlan;
import com.racehorse.training.entity.TrainingPlanPhase;
import com.racehorse.training.entity.WorkloadIntensity;
import com.racehorse.training.repository.TrainingPlanPhaseRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import com.racehorse.horse.service.HorseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingScheduleServiceTest {

    @Mock TrainingScheduleRepository repository;
    @Mock TrainingPlanPhaseRepository phaseRepository;
    @Mock TrainingLockRepository lockRepository;
    @Mock UserAccountRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock HorseAccessService horseAccessService;

    private TrainingScheduleService service;

    @BeforeEach
    void setUp() {
        service = new TrainingScheduleService(
                repository, phaseRepository, lockRepository, userRepository, notificationService,
                horseAccessService,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void rejectsHighIntensityScheduleWhileHorseIsLocked() {
        UUID phaseId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        TrainingPlanPhase phase = phase(WorkloadIntensity.HIGH);
        UserAccount staff = user("staff", Role.STABLE_STAFF);
        when(phaseRepository.findDetailById(phaseId)).thenReturn(Optional.of(phase));
        when(horseAccessService.requireReadable(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(horse());
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(lockRepository.findByHorseIdAndReleasedAtIsNull(org.mockito.ArgumentMatchers.nullable(UUID.class)))
                .thenReturn(Optional.of(new TrainingLock(
                        horse(), "Injury", null, user("vet", Role.VETERINARIAN), Instant.now())));

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request(phaseId, staffId), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang bị khóa");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private CreateTrainingScheduleRequest request(UUID phaseId, UUID staffId) {
        return new CreateTrainingScheduleRequest(
                phaseId, staffId, "Trial run",
                Instant.parse("2026-09-16T01:00:00Z"), Instant.parse("2026-09-16T02:00:00Z"));
    }

    private TrainingPlanPhase phase(WorkloadIntensity intensity) {
        TrainingPlan plan = new TrainingPlan(horse(), "Active", null, user("trainer", Role.HEAD_TRAINER), true);
        TrainingPlanPhase phase = new TrainingPlanPhase(
                1, "Phase", LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 20),
                new BigDecimal("1500"), intensity, SurfaceType.GRASS);
        plan.replacePhases(List.of(phase));
        return phase;
    }

    private Horse horse() {
        return new Horse("H-SCHEDULE", "Schedule Test", null, LocalDate.of(2022, 1, 1),
                HorseGender.STALLION, "Bay", "Thoroughbred", "A-01", HorseStatus.ACTIVE);
    }

    private UserAccount user(String name, Role role) {
        return new UserAccount(name, name + "@test.local", "encoded", Set.of(role));
    }
}
