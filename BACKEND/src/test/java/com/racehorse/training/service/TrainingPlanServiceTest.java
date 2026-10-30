package com.racehorse.training.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.InvalidInputException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.TrainingPlanRequest;
import com.racehorse.training.dto.TrainingPlanResponse;
import com.racehorse.training.entity.SurfaceType;
import com.racehorse.training.entity.TrainingPlan;
import com.racehorse.training.entity.TrainingPlanPhase;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.entity.WorkloadIntensity;
import com.racehorse.training.repository.TrainingEvaluationRepository;
import com.racehorse.training.repository.TrainingPlanRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingPlanServiceTest {
    @Mock TrainingPlanRepository repository;
    @Mock UserAccountRepository userRepository;
    @Mock HorseAccessService horseAccessService;
    @Mock TrainingScheduleRepository scheduleRepository;
    @Mock TrainingEvaluationRepository evaluationRepository;
    @Mock JdbcTemplate jdbc;

    private final UUID horseId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private TrainingPlanService service;
    private Horse horse;
    private UserAccount trainer;

    @BeforeEach
    void setUp() {
        service = new TrainingPlanService(repository, userRepository, horseAccessService, scheduleRepository,
                evaluationRepository, Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC), jdbc);
        org.mockito.Mockito.lenient().when(jdbc.queryForObject(
                org.mockito.ArgumentMatchers.contains("diagnosing_vet_user_id is not null"),
                org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenReturn(1);
        horse = new Horse("H-001", "Lightning", null, LocalDate.of(2022, 3, 10),
                HorseGender.STALLION, "White blaze", "Thoroughbred", "A-01", HorseStatus.ACTIVE);
        ReflectionTestUtils.setField(horse, "id", horseId);
        trainer = new UserAccount("trainer-a", "trainer-a@test.local", "encoded", Set.of(Role.HEAD_TRAINER));
    }

    @Test
    void rejectsOverlappingPhases() {
        assertRejected(List.of(phase("Base", "2026-09-01", "2026-09-10", List.of(LocalDate.parse("2026-09-07"))),
                phase("Speed", "2026-09-10", "2026-09-20", List.of(LocalDate.parse("2026-09-14")))), "không được trùng");
    }

    @Test
    void rejectsPhaseWithoutRestDay() {
        assertRejected(List.of(phase("Base", "2026-09-01", "2026-09-30", List.of())),
                "ít nhất một ngày nghỉ");
    }

    @Test
    void rejectsRestDaysOutsidePhase() {
        assertRejected(List.of(phase("Base", "2026-09-01", "2026-09-10",
                List.of(LocalDate.parse("2026-08-31")))), "nằm trong khoảng");
    }

    @Test
    void rejectsNewPlanWithPastRestDay() {
        assertRejected(List.of(phase("Base", "2026-09-14", "2026-09-30",
                List.of(LocalDate.parse("2026-09-14")))), "quá khứ");
    }

    @Test
    void rejectsThreeConsecutiveRestDays() {
        assertRejected(List.of(phase("Base", "2026-09-01", "2026-09-30",
                List.of(LocalDate.parse("2026-09-07"), LocalDate.parse("2026-09-08"), LocalDate.parse("2026-09-09")))),
                "quá 2 ngày nghỉ liên tiếp");
    }

    @Test
    void rejectsTwoRestDaysInOneMondayToSundayWeek() {
        assertRejected(List.of(phase("Base", "2026-09-01", "2026-09-30",
                List.of(LocalDate.parse("2026-09-08"), LocalDate.parse("2026-09-10")))),
                "Mỗi tuần");
    }

    @Test
    void rejectsPlanningForAnInjuredHorse() {
        horse = new Horse("H-002", "Injured", null, LocalDate.of(2022, 3, 10),
                HorseGender.MARE, "Bay", "Thoroughbred", "A-01", HorseStatus.INJURED);
        ReflectionTestUtils.setField(horse, "id", horseId);
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        assertThatThrownBy(() -> service.create(actorId, request(List.of(phase("Recovery",
                "2026-09-01", "2026-09-30",
                List.of(LocalDate.parse("2026-09-07"), LocalDate.parse("2026-09-08"), LocalDate.parse("2026-09-09"))))),
                false, true))
                .isInstanceOf(com.racehorse.common.exception.BusinessException.class)
                .hasMessageContaining("không thể tạo hoặc cập nhật kế hoạch");
        verify(repository, never()).save(any(TrainingPlan.class));
    }

    @Test
    void rejectsPlanningForQuarantinedHorse() {
        ReflectionTestUtils.setField(horse, "healthStatus", "QUARANTINED");
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);

        assertThatThrownBy(() -> service.create(actorId,
                request(List.of(phase("Base", "2026-09-15", "2026-09-30",
                        List.of(LocalDate.parse("2026-09-21"))))), false, true))
                .isInstanceOf(com.racehorse.common.exception.BusinessException.class)
                .hasMessageContaining("đang cách ly");
        verify(repository, never()).save(any(TrainingPlan.class));
    }

    @Test
    void createScopesHorseToCurrentTrainer() {
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(userRepository.findById(actorId)).thenReturn(java.util.Optional.of(trainer));
        when(scheduleRepository.findPlanSessions(anyList())).thenReturn(List.of());
        when(repository.save(any(TrainingPlan.class))).thenAnswer(invocation -> {
            TrainingPlan saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        service.create(actorId, request(List.of(phase("Base", "2026-09-15", "2026-09-30", List.of(LocalDate.parse("2026-09-21"))))),
                false, true);

        verify(horseAccessService).requireTrainingAccess(horseId, actorId, false, true, false);
    }

    @Test
    void updateEditsExistingPhaseWithoutReplacingItsScheduledSession() {
        UUID planId = UUID.randomUUID();
        UUID phaseId = UUID.randomUUID();
        TrainingPlan plan = new TrainingPlan(horse, "Old plan", null, trainer, true);
        ReflectionTestUtils.setField(plan, "id", planId);
        TrainingPlanPhase existingPhase = new TrainingPlanPhase(1, "Base", LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-30"), BigDecimal.valueOf(1200), WorkloadIntensity.MEDIUM,
                SurfaceType.GRASS, List.of(LocalDate.parse("2026-09-07")));
        ReflectionTestUtils.setField(existingPhase, "id", phaseId);
        plan.replacePhases(List.of(existingPhase));
        TrainingSchedule session = org.mockito.Mockito.mock(TrainingSchedule.class);
        Instant scheduledStart = LocalDate.parse("2026-09-08").atTime(5, 0)
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        when(session.getPhase()).thenReturn(existingPhase);
        when(session.getScheduledStart()).thenReturn(scheduledStart);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getSessionStatus()).thenReturn("COMPLETED");
        when(session.getWorkloadIntensity()).thenReturn(WorkloadIntensity.MEDIUM);
        when(session.getTaskName()).thenReturn("Existing session");
        when(repository.findDetailById(planId)).thenReturn(Optional.of(plan));
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(scheduleRepository.findPlanSessions(planId)).thenReturn(List.of(session));
        when(scheduleRepository.findPlanSessions(anyList())).thenReturn(List.of(session));
        when(repository.save(any(TrainingPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrainingPlanRequest.Phase editedPhase = new TrainingPlanRequest.Phase(phaseId, "Strength", LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-30"), BigDecimal.valueOf(1800), WorkloadIntensity.HIGH,
                SurfaceType.DIRT, List.of(LocalDate.parse("2026-09-07")));
        TrainingPlanRequest.Phase addedPhase = new TrainingPlanRequest.Phase("Speed", LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-31"), BigDecimal.valueOf(2200), WorkloadIntensity.MEDIUM,
                SurfaceType.GRASS, List.of(LocalDate.parse("2026-10-05")));
        TrainingPlanResponse updated = service.update(planId, actorId,
                new TrainingPlanRequest(horseId, "Updated plan", true, List.of(editedPhase, addedPhase)), false, true);

        assertThat(updated.name()).isEqualTo("Updated plan");
        assertThat(updated.phases()).hasSize(2);
        assertThat(updated.phases().getFirst().id()).isEqualTo(phaseId);
        assertThat(updated.phases().getFirst().name()).isEqualTo("Strength");
        assertThat(updated.phases().getFirst().targetDistanceMeters()).isEqualByComparingTo("1800");
        assertThat(updated.phases().getFirst().sessions()).hasSize(1);
        assertThat(updated.phases().getFirst().sessions().getFirst().taskName()).isEqualTo("Existing session");
        assertThat(updated.phases().get(1).name()).isEqualTo("Speed");
    }

    @Test
    void updateRemovesPhaseAndItsExistingSessions() {
        UUID planId = UUID.randomUUID();
        UUID phaseId = UUID.randomUUID();
        TrainingPlan plan = new TrainingPlan(horse, "Plan", null, trainer, true);
        ReflectionTestUtils.setField(plan, "id", planId);
        TrainingPlanPhase existingPhase = new TrainingPlanPhase(1, "Base", LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-30"), BigDecimal.valueOf(1200), WorkloadIntensity.MEDIUM,
                SurfaceType.GRASS, List.of(LocalDate.parse("2026-09-07")));
        ReflectionTestUtils.setField(existingPhase, "id", phaseId);
        TrainingPlanPhase retainedPhase = new TrainingPlanPhase(2, "Speed", LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-31"), BigDecimal.valueOf(2200), WorkloadIntensity.MEDIUM,
                SurfaceType.GRASS, List.of(LocalDate.parse("2026-10-05")));
        UUID retainedId = UUID.randomUUID();
        ReflectionTestUtils.setField(retainedPhase, "id", retainedId);
        plan.replacePhases(List.of(existingPhase, retainedPhase));
        TrainingSchedule session = org.mockito.Mockito.mock(TrainingSchedule.class);
        when(session.getPhase()).thenReturn(existingPhase);
        UUID scheduleId = UUID.randomUUID();
        when(session.getId()).thenReturn(scheduleId);
        when(scheduleRepository.findPlanSessions(planId)).thenReturn(List.of(session));
        when(scheduleRepository.findPlanSessions(anyList())).thenReturn(List.of());
        when(repository.findDetailById(planId)).thenReturn(Optional.of(plan));
        when(repository.save(plan)).thenReturn(plan);
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);

        service.update(planId, actorId,
                new TrainingPlanRequest(horseId, "Plan", true, List.of(new TrainingPlanRequest.Phase(
                        retainedId, "Speed", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"),
                        BigDecimal.valueOf(2200), WorkloadIntensity.MEDIUM, SurfaceType.GRASS,
                        List.of(LocalDate.parse("2026-10-05"))))), false, true);
        verify(jdbc).update("delete from training_schedules where id=?", scheduleId);
        verify(jdbc).update("update training_plan_phases set phase_order=phase_order+100000 where id=?", phaseId);
        assertThat(plan.getPhases()).extracting(TrainingPlanPhase::getId).containsExactly(retainedId);
    }

    @Test
    void updateRejectsMakingExistingSessionDateARestDay() {
        service = new TrainingPlanService(repository, userRepository, horseAccessService, scheduleRepository,
                evaluationRepository, Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));
        UUID planId = UUID.randomUUID();
        UUID phaseId = UUID.randomUUID();
        TrainingPlan plan = new TrainingPlan(horse, "Plan", null, trainer, true);
        ReflectionTestUtils.setField(plan, "id", planId);
        TrainingPlanPhase existingPhase = new TrainingPlanPhase(1, "Base", LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-30"), BigDecimal.valueOf(1200), WorkloadIntensity.MEDIUM,
                SurfaceType.GRASS, List.of(LocalDate.parse("2026-09-07")));
        ReflectionTestUtils.setField(existingPhase, "id", phaseId);
        plan.replacePhases(List.of(existingPhase));
        TrainingSchedule session = org.mockito.Mockito.mock(TrainingSchedule.class);
        when(session.getPhase()).thenReturn(existingPhase);
        when(session.getScheduledStart()).thenReturn(LocalDate.parse("2026-09-08").atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
        when(session.getTaskName()).thenReturn("Existing session");
        when(scheduleRepository.findPlanSessions(planId)).thenReturn(List.of(session));
        when(repository.findDetailById(planId)).thenReturn(Optional.of(plan));
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);

        TrainingPlanRequest.Phase editedPhase = new TrainingPlanRequest.Phase(phaseId, "Base", LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-30"), BigDecimal.valueOf(1200), WorkloadIntensity.MEDIUM,
                SurfaceType.GRASS, List.of(LocalDate.parse("2026-09-08")));
        assertThatThrownBy(() -> service.update(planId, actorId,
                new TrainingPlanRequest(horseId, "Plan", true, List.of(editedPhase)), false, true))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Existing session");
        verify(repository, never()).save(any(TrainingPlan.class));
    }

    private void assertRejected(List<TrainingPlanRequest.Phase> phases, String message) {
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        assertThatThrownBy(() -> service.create(actorId, request(phases), false, true))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining(message);
        verify(repository, never()).save(any(TrainingPlan.class));
    }

    private TrainingPlanRequest request(List<TrainingPlanRequest.Phase> phases) {
        return new TrainingPlanRequest(horseId, "Training plan", true, phases);
    }

    private TrainingPlanRequest.Phase phase(String name, String start, String end, List<LocalDate> restDays) {
        return new TrainingPlanRequest.Phase(name, LocalDate.parse(start), LocalDate.parse(end),
                BigDecimal.valueOf(1200), WorkloadIntensity.MEDIUM, SurfaceType.GRASS, restDays);
    }
}
