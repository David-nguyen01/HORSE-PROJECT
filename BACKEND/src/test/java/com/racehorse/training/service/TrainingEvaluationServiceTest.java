package com.racehorse.training.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.CreateTrainingEvaluationRequest;
import com.racehorse.training.dto.TrainingEvaluationResponse;
import com.racehorse.training.entity.FitnessStatus;
import com.racehorse.training.entity.SurfaceType;
import com.racehorse.training.entity.TrainingEvaluation;
import com.racehorse.training.entity.TrainingPlan;
import com.racehorse.training.entity.TrainingPlanPhase;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.entity.WorkloadIntensity;
import com.racehorse.training.repository.TrainingEvaluationRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingEvaluationServiceTest {
    @Mock TrainingEvaluationRepository repository;
    @Mock UserAccountRepository userRepository;
    @Mock HorseAccessService horseAccess;
    @Mock TrainingScheduleRepository schedules;
    private final Instant now = Instant.parse("2026-09-10T02:00:00Z");
    private TrainingEvaluationService service;

    @BeforeEach void setUp() {
        service = new TrainingEvaluationService(repository, userRepository, horseAccess, schedules,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void computesEvaluationAndLeavesCompletedSessionTimestampUnchanged() {
        UUID horseId = UUID.randomUUID(); UUID actorId = UUID.randomUUID(); UUID scheduleId = UUID.randomUUID();
        Horse horse = horse(horseId); UserAccount trainer = trainer();
        TrainingSchedule session = schedule(horse, true);
        Instant finishedAt = Instant.parse("2026-09-09T04:00:00Z"); session.finishTraining(finishedAt);
        org.springframework.test.util.ReflectionTestUtils.setField(session, "id", scheduleId);
        when(horseAccess.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(trainer));
        when(schedules.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(session));
        when(repository.existsByTrainingScheduleId(scheduleId)).thenReturn(false);
        when(repository.save(any(TrainingEvaluation.class))).thenAnswer(i -> i.getArgument(0));

        TrainingEvaluationResponse result = service.create(actorId, request(horseId, scheduleId,
                "https://video.test/run"), false, true);

        assertThat(result.averageSpeedKmh()).isEqualByComparingTo("54.00");
        assertThat(result.intensityPercent()).isEqualByComparingTo("90.00");
        assertThat(result.recoveryRate()).isEqualByComparingTo("40.00");
        assertThat(result.perceivedExertion()).isEqualTo(5);
        assertThat(result.fitnessStatus()).isEqualTo(FitnessStatus.YELLOW);
        assertThat(result.trackCompletionSeconds()).isEqualByComparingTo("120.000");
        assertThat(session.getCompletedAt()).isEqualTo(finishedAt);
    }

    @Test void rejectsEvaluationUntilSessionHasFinished() {
        UUID horseId = UUID.randomUUID(); UUID actorId = UUID.randomUUID(); UUID scheduleId = UUID.randomUUID();
        Horse horse = horse(horseId); TrainingSchedule session = schedule(horse, false);
        org.springframework.test.util.ReflectionTestUtils.setField(session, "id", scheduleId);
        when(horseAccess.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(trainer()));
        when(schedules.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.create(actorId, request(horseId, scheduleId, null), false, true))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đã hoàn thành");
    }

    @Test void refusesManagerAndNonTrainerEvaluationCreation() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.create(id, request(id, UUID.randomUUID(), null), true, false))
                .isInstanceOf(com.racehorse.common.exception.ForbiddenException.class);
        assertThatThrownBy(() -> service.create(id, request(id, UUID.randomUUID(), null), false, false))
                .isInstanceOf(com.racehorse.common.exception.ForbiddenException.class);
    }

    @Test void ownerEvaluationPayloadOmitsRecordedByIdentity() {
        UUID horseId = UUID.randomUUID(); UUID ownerId = UUID.randomUUID();
        Horse horse = horse(horseId); UserAccount trainer = trainer();
        TrainingEvaluation evaluation = new TrainingEvaluation(horse, trainer, LocalDate.of(2026, 9, 9), 42,
                new BigDecimal("1800"), new BigDecimal("54"), new BigDecimal("72"), 40, 5,
                FitnessStatus.YELLOW, "Good session", null, now);
        when(repository.findByHorseIdOrderBySessionDateDescCompletedAtDesc(horseId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(evaluation)));

        var result = service.listForHorse(horseId, ownerId, false, false, true, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().recordedBy()).isNull();
    }

    private CreateTrainingEvaluationRequest request(UUID horseId, UUID scheduleId, String video) {
        return new CreateTrainingEvaluationRequest(horseId, scheduleId, LocalDate.of(2026, 9, 9), 42,
                new BigDecimal("1800"), new BigDecimal("54"), new BigDecimal("72"), "Good session", video,
                new BigDecimal("60"), 160, 120, new BigDecimal("120"), 5, false, null, null);
    }

    private Horse horse(UUID id) {
        Horse horse = new Horse("H-001", "Lightning", null, LocalDate.of(2022, 3, 10),
                HorseGender.STALLION, "White blaze", "Thoroughbred", "A-01", HorseStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(horse, "id", id); return horse;
    }

    private UserAccount trainer() { return new UserAccount("trainer", "trainer@test.local", "encoded", Set.of(Role.HEAD_TRAINER)); }

    private TrainingSchedule schedule(Horse horse, boolean ready) {
        var plan = new TrainingPlan(horse, "Active plan", null, trainer(), true);
        var phase = new TrainingPlanPhase(1, "Phase", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("1500"), WorkloadIntensity.MEDIUM, SurfaceType.GRASS);
        plan.replacePhases(List.of(phase));
        TrainingSchedule schedule = new TrainingSchedule(phase, horse, trainer(), "Run", WorkloadIntensity.MEDIUM,
                Instant.parse("2026-09-09T01:00:00Z"), Instant.parse("2026-09-09T02:00:00Z"), trainer());
        if (ready) { schedule.markReady(UUID.randomUUID(), now); schedule.startTraining(now); }
        return schedule;
    }
}
