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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    private final UUID horseId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private TrainingPlanService service;
    private Horse horse;
    private UserAccount trainer;

    @BeforeEach
    void setUp() {
        service = new TrainingPlanService(repository, userRepository, horseAccessService, scheduleRepository,
                evaluationRepository, Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));
        horse = new Horse("H-001", "Lightning", null, LocalDate.of(2022, 3, 10),
                HorseGender.STALLION, "White blaze", "Thoroughbred", "A-01", HorseStatus.ACTIVE);
        ReflectionTestUtils.setField(horse, "id", horseId);
        trainer = new UserAccount("trainer-a", "trainer-a@test.local", "encoded", Set.of(Role.HEAD_TRAINER));
    }

    @Test
    void rejectsOverlappingPhases() {
        assertRejected(List.of(phase("Base", "2026-09-01", "2026-09-10", List.of()),
                phase("Speed", "2026-09-10", "2026-09-20", List.of())), "không được trùng");
    }

    @Test
    void rejectsRestDaysOutsidePhase() {
        assertRejected(List.of(phase("Base", "2026-09-01", "2026-09-10",
                List.of(LocalDate.parse("2026-08-31")))), "nằm trong khoảng");
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
    void createScopesHorseToCurrentTrainer() {
        when(horseAccessService.requireTrainingAccess(horseId, actorId, false, true, false)).thenReturn(horse);
        when(userRepository.findById(actorId)).thenReturn(java.util.Optional.of(trainer));
        when(scheduleRepository.findPlanSessions(anyList())).thenReturn(List.of());
        when(repository.save(any(TrainingPlan.class))).thenAnswer(invocation -> {
            TrainingPlan saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        service.create(actorId, request(List.of(phase("Base", "2026-09-01", "2026-09-30", List.of()))),
                false, true);

        verify(horseAccessService).requireTrainingAccess(horseId, actorId, false, true, false);
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
