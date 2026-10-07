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
import com.racehorse.training.dto.TrainingScheduleResponse;
import com.racehorse.training.entity.SurfaceType;
import com.racehorse.training.entity.TrainingPlan;
import com.racehorse.training.entity.TrainingPlanPhase;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.entity.WorkloadIntensity;
import com.racehorse.training.repository.TrainingPlanPhaseRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
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

    private TrainingScheduleService service;

    @BeforeEach
    void setUp() {
        service = new TrainingScheduleService(
                repository, phaseRepository, lockRepository, userRepository, notificationService,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void rejectsHighIntensityScheduleWhileHorseIsLocked() {
        UUID phaseId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        TrainingPlanPhase phase = phase(WorkloadIntensity.HIGH);
        UserAccount staff = user("staff", Role.STABLE_STAFF);
        when(phaseRepository.findDetailById(phaseId)).thenReturn(Optional.of(phase));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(lockRepository.findByHorseIdAndReleasedAtIsNull(org.mockito.ArgumentMatchers.nullable(UUID.class)))
                .thenReturn(Optional.of(new TrainingLock(
                        horse(), "Injury", null, user("vet", Role.VETERINARIAN), Instant.now())));

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request(phaseId, staffId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang bị khóa");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsScheduleWhenHorseIsInMedical() {
        UUID phaseId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Horse inMedicalHorse = horse();
        inMedicalHorse.setPaymentStatus("IN_MEDICAL");

        TrainingPlan plan = new TrainingPlan(inMedicalHorse, "Active", null, user("trainer", Role.HEAD_TRAINER), true);
        TrainingPlanPhase phase = new TrainingPlanPhase(
                1, "Phase", LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 20),
                new BigDecimal("1500"), WorkloadIntensity.LOW, SurfaceType.GRASS);
        plan.replacePhases(List.of(phase));

        UserAccount staff = user("staff", Role.STABLE_STAFF);
        when(phaseRepository.findDetailById(phaseId)).thenReturn(Optional.of(phase));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request(phaseId, staffId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang trong ca khám y tế");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void vetCanClaimScheduleBeforeDeadline() {
        UUID scheduleId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        UserAccount vet = user("vet", Role.VETERINARIAN);

        TrainingPlanPhase phase = phase(WorkloadIntensity.LOW);
        UserAccount staff = user("staff", Role.STABLE_STAFF);
        TrainingSchedule schedule = new TrainingSchedule(
                phase, horse(), staff, "Supervised training", WorkloadIntensity.LOW,
                Instant.parse("2026-09-16T03:00:00Z"),
                Instant.parse("2026-09-16T04:00:00Z"),
                user("trainer", Role.HEAD_TRAINER)
        );

        when(repository.findByIdWithLock(scheduleId)).thenReturn(Optional.of(schedule));
        when(repository.existsByVetOnDate(org.mockito.ArgumentMatchers.eq(vetId), org.mockito.ArgumentMatchers.any(LocalDate.class))).thenReturn(false);
        when(userRepository.findById(vetId)).thenReturn(Optional.of(vet));
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        TrainingScheduleResponse response = service.claim(scheduleId, vetId, true);

        org.assertj.core.api.Assertions.assertThat(response.veterinarianUserId()).isEqualTo(vet.getId());
        org.assertj.core.api.Assertions.assertThat(response.vetAssignedBy()).isEqualTo("USER");
        verify(repository).save(schedule);
    }

    @Test
    void claimFailsWhenDeadlineExceeded() {
        UUID scheduleId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();

        TrainingPlanPhase phase = phase(WorkloadIntensity.LOW);
        UserAccount staff = user("staff", Role.STABLE_STAFF);
        TrainingSchedule schedule = new TrainingSchedule(
                phase, horse(), staff, "Same day training", WorkloadIntensity.LOW,
                Instant.parse("2026-09-15T05:00:00Z"),
                Instant.parse("2026-09-15T06:00:00Z"),
                user("trainer", Role.HEAD_TRAINER)
        );

        when(repository.findByIdWithLock(scheduleId)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.claim(scheduleId, vetId, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Thời hạn tự nhận phiên tập đã kết thúc");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void claimFailsWhenVetAlreadyAssigned() {
        UUID scheduleId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();

        TrainingPlanPhase phase = phase(WorkloadIntensity.LOW);
        UserAccount staff = user("staff", Role.STABLE_STAFF);
        TrainingSchedule schedule = new TrainingSchedule(
                phase, horse(), staff, "Already claimed", WorkloadIntensity.LOW,
                Instant.parse("2026-09-16T03:00:00Z"),
                Instant.parse("2026-09-16T04:00:00Z"),
                user("trainer", Role.HEAD_TRAINER)
        );
        schedule.setVeterinarianUser(user("otherVet", Role.VETERINARIAN));

        when(repository.findByIdWithLock(scheduleId)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.claim(scheduleId, vetId, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã có bác sĩ thú y đăng ký");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void claimFailsWhenVetAlreadyHasShiftOnDate() {
        UUID scheduleId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();

        TrainingPlanPhase phase = phase(WorkloadIntensity.LOW);
        UserAccount staff = user("staff", Role.STABLE_STAFF);
        TrainingSchedule schedule = new TrainingSchedule(
                phase, horse(), staff, "Second session", WorkloadIntensity.LOW,
                Instant.parse("2026-09-16T03:00:00Z"),
                Instant.parse("2026-09-16T04:00:00Z"),
                user("trainer", Role.HEAD_TRAINER)
        );

        when(repository.findByIdWithLock(scheduleId)).thenReturn(Optional.of(schedule));
        when(repository.existsByVetOnDate(org.mockito.ArgumentMatchers.eq(vetId), org.mockito.ArgumentMatchers.any(LocalDate.class))).thenReturn(true);

        assertThatThrownBy(() -> service.claim(scheduleId, vetId, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tối đa 1 phiên/ngày");
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
