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
import com.racehorse.training.entity.Racetrack;
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

import com.racehorse.horse.repository.HorseOwnershipRepository;
import com.racehorse.notification.service.NotificationService;
import com.racehorse.training.dto.CreateVetAssessmentRequest;
import com.racehorse.training.dto.VetAssessmentResponse;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingEvaluationServiceTest {
    @Mock TrainingEvaluationRepository repository;
    @Mock UserAccountRepository userRepository;
    @Mock HorseAccessService horseAccess;
    @Mock TrainingScheduleRepository schedules;
    @Mock JdbcTemplate jdbc;
    @Mock HorseOwnershipRepository ownershipRepository;
    @Mock NotificationService notificationService;
    private final Instant now = Instant.parse("2026-09-10T02:00:00Z");
    private TrainingEvaluationService service;

    @BeforeEach void setUp() {
        service = new TrainingEvaluationService(repository, userRepository, horseAccess, schedules,
                Clock.fixed(now, ZoneOffset.UTC), jdbc, ownershipRepository, notificationService);
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
        org.mockito.ArgumentCaptor<TrainingEvaluation> saved = org.mockito.ArgumentCaptor.forClass(TrainingEvaluation.class);
        org.mockito.Mockito.verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPeakHeartRateBpm()).isEqualTo(160);
        assertThat(saved.getValue().getHeartRateAfterRestBpm()).isEqualTo(120);
        assertThat(saved.getValue().getRestMinutes()).isEqualByComparingTo("1");
        assertThat(saved.getValue().getHrr1Bpm()).isEqualTo(40);
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

    @Test void savesEvaluationWithoutOptionalExertion() {
        UUID horseId = UUID.randomUUID(); UUID actorId = UUID.randomUUID(); UUID scheduleId = UUID.randomUUID();
        Horse horse = horse(horseId); TrainingSchedule session = schedule(horse, true);
        session.finishTraining(now);
        org.springframework.test.util.ReflectionTestUtils.setField(session, "id", scheduleId);
        when(horseAccess.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(trainer()));
        when(schedules.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(session));
        when(repository.save(any(TrainingEvaluation.class))).thenAnswer(i -> i.getArgument(0));
        var input = new CreateTrainingEvaluationRequest(horseId, scheduleId, LocalDate.of(2026, 9, 9), 42,
                new BigDecimal("1800"), null, "Good session", null,
                new BigDecimal("60"), 160, 120, new BigDecimal("120"), null, false, null, null);

        TrainingEvaluationResponse result = service.create(actorId, input, false, true);

        assertThat(result.perceivedExertion()).isNull();
        assertThat(result.averageSpeedKmh()).isEqualByComparingTo("54.00");
        assertThat(result.intensityPercent()).isEqualByComparingTo("90.00");
    }

    @Test void refusesManagerAndNonTrainerEvaluationCreation() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.create(id, request(id, UUID.randomUUID(), null), true, false))
                .isInstanceOf(com.racehorse.common.exception.ForbiddenException.class);
        assertThatThrownBy(() -> service.create(id, request(id, UUID.randomUUID(), null), false, false))
                .isInstanceOf(com.racehorse.common.exception.ForbiddenException.class);
    }

    @Test void currentTrainerCanCorrectExistingEvaluationWithoutCreatingSecondRecord() {
        UUID horseId = UUID.randomUUID(), actorId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID(), evaluationId = UUID.randomUUID();
        Horse horse = horse(horseId); TrainingSchedule session = schedule(horse, true);
        session.finishTraining(now);
        org.springframework.test.util.ReflectionTestUtils.setField(session, "id", scheduleId);
        TrainingEvaluation existing = new TrainingEvaluation(horse, session, trainer(),
                LocalDate.of(2026, 9, 9), 30, new BigDecimal("1000"), new BigDecimal("30"),
                20, null, FitnessStatus.GREEN, "Before", null, now);
        org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", evaluationId);
        when(repository.findDetailById(evaluationId)).thenReturn(Optional.of(existing));
        when(horseAccess.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(repository.save(any(TrainingEvaluation.class))).thenAnswer(i -> i.getArgument(0));
        var input = new CreateTrainingEvaluationRequest(horseId, scheduleId, LocalDate.of(2026, 9, 9),
                42, new BigDecimal("1800"), null, "Corrected", null,
                new BigDecimal("60"), 160, 120, new BigDecimal("120"), null, false, null, null);

        var result = service.update(evaluationId, actorId, input, false, true);

        assertThat(result.trainerNotes()).isEqualTo("Corrected");
        assertThat(result.averageSpeedKmh()).isEqualByComparingTo("54.00");
        assertThat(result.id()).isEqualTo(evaluationId);
    }

    @Test void ownerEvaluationPayloadOmitsRecordedByIdentity() {
        UUID horseId = UUID.randomUUID(); UUID ownerId = UUID.randomUUID();
        Horse horse = horse(horseId); UserAccount trainer = trainer();
        TrainingEvaluation evaluation = new TrainingEvaluation(horse, trainer, LocalDate.of(2026, 9, 9), 42,
                new BigDecimal("1800"), new BigDecimal("54"), 40, 5,
                FitnessStatus.YELLOW, "Good session", null, now);
        when(repository.findByHorseIdOrderBySessionDateDescCompletedAtDesc(horseId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(evaluation)));

        var result = service.listForHorse(horseId, ownerId, false, false, true, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().recordedBy()).isNull();
    }

    private CreateTrainingEvaluationRequest request(UUID horseId, UUID scheduleId, String video) {
        return new CreateTrainingEvaluationRequest(horseId, scheduleId, LocalDate.of(2026, 9, 9), 42,
                new BigDecimal("1800"), new BigDecimal("54"), "Good session", video,
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
        TrainingSchedule schedule = new TrainingSchedule(phase, horse, new Racetrack("Track", null), "Run", WorkloadIntensity.MEDIUM,
                Instant.parse("2026-09-09T01:00:00Z"), Instant.parse("2026-09-09T02:00:00Z"), trainer());
        if (ready) { schedule.markReady(UUID.randomUUID(), now); schedule.startTraining(now); }
        return schedule;
    }

    @Test void saveVetAssessmentSuccessfullyPersistsAndNotifiesHeadTrainers() {
        UUID scheduleId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        Horse horse = horse(horseId);
        TrainingSchedule session = schedule(horse, true);
        org.springframework.test.util.ReflectionTestUtils.setField(session, "id", scheduleId);

        UserAccount vet = new UserAccount("dr_vet", "vet@test.local", "pw", Set.of(Role.VETERINARIAN));
        UserAccount headTrainer = trainer();

        when(schedules.findDetailById(scheduleId)).thenReturn(Optional.of(session));
        when(userRepository.findById(vetId)).thenReturn(Optional.of(vet));
        when(userRepository.findByRoles(List.of(Role.HEAD_TRAINER))).thenReturn(List.of(headTrainer));

        CreateVetAssessmentRequest req = new CreateVetAssessmentRequest(
                "GOOD", 55, "NORMAL", "NORMAL", "EXCELLENT",
                "Horse recovered well with no sign of muscle stiffness.",
                "Maintain current intensity", false);

        VetAssessmentResponse response = service.saveVetAssessment(scheduleId, vetId, req);

        assertThat(response).isNotNull();
        assertThat(response.scheduleId()).isEqualTo(scheduleId);
        assertThat(response.horseId()).isEqualTo(horseId);
        assertThat(response.horseCode()).isEqualTo("H-001");
        assertThat(response.horseName()).isEqualTo("Lightning");
        assertThat(response.veterinarianId()).isEqualTo(vetId);
        assertThat(response.veterinarianName()).isEqualTo("dr_vet");
        assertThat(response.overallCondition()).isEqualTo("GOOD");
        assertThat(response.heartRateRecoveryBpm()).isEqualTo(55);
        assertThat(response.clinicalConclusion()).isEqualTo("Horse recovered well with no sign of muscle stiffness.");
        assertThat(response.requiresMedicalIntervention()).isFalse();

        org.mockito.Mockito.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("insert into module_records"),
                any(UUID.class), eq(horseId), eq(vetId), any(LocalDate.class), anyString());
        org.mockito.Mockito.verify(notificationService).create(
                eq(headTrainer), eq("VET_ASSESSMENT_COMPLETED"), any(), anyString(), anyString(), eq("TRAINING_SCHEDULE"), eq(scheduleId));
    }

    @Test void saveVetAssessmentThrowsWhenScheduleNotFound() {
        UUID scheduleId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        when(schedules.findDetailById(scheduleId)).thenReturn(Optional.empty());

        CreateVetAssessmentRequest req = new CreateVetAssessmentRequest(
                "GOOD", 55, "NORMAL", "NORMAL", "EXCELLENT", "Conclusion", null, false);

        assertThatThrownBy(() -> service.saveVetAssessment(scheduleId, vetId, req))
                .isInstanceOf(com.racehorse.common.exception.ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy lịch huấn luyện");
    }

    @Test void getVetAssessmentReturnsAssessmentFromModuleRecords() {
        UUID scheduleId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID vetId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        String dataJson = """
                {
                  "scheduleId": "%s",
                  "overallCondition": "GOOD",
                  "heartRateRecoveryBpm": 52,
                  "respiratoryStatus": "NORMAL",
                  "gaitStatus": "NORMAL",
                  "muscleCondition": "EXCELLENT",
                  "clinicalConclusion": "Excellent condition",
                  "recommendation": "Ready for high speed drill",
                  "requiresMedicalIntervention": false,
                  "veterinarianId": "%s",
                  "veterinarianName": "dr_vet"
                }
                """.formatted(scheduleId, vetId);

        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", recordId);
        row.put("horse_id", horseId);
        row.put("horse_code", "H-001");
        row.put("horse_name", "Lightning");
        row.put("user_id", vetId);
        row.put("vet_name", "dr_vet");
        row.put("data_json", dataJson);
        row.put("created_at", java.time.OffsetDateTime.now(ZoneOffset.UTC));

        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("VET_TRAINING_ASSESSMENT"), eq(scheduleId.toString())))
                .thenReturn(List.of(row));

        VetAssessmentResponse response = service.getVetAssessment(scheduleId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(recordId);
        assertThat(response.scheduleId()).isEqualTo(scheduleId);
        assertThat(response.horseId()).isEqualTo(horseId);
        assertThat(response.heartRateRecoveryBpm()).isEqualTo(52);
        assertThat(response.clinicalConclusion()).isEqualTo("Excellent condition");
        assertThat(response.recommendation()).isEqualTo("Ready for high speed drill");
    }

    @Test void getVetAssessmentReturnsNullWhenNotFound() {
        UUID scheduleId = UUID.randomUUID();
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("VET_TRAINING_ASSESSMENT"), eq(scheduleId.toString())))
                .thenReturn(List.of());

        assertThat(service.getVetAssessment(scheduleId)).isNull();
    }
}
