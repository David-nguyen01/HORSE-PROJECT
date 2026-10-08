package com.racehorse.training.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.horse.entity.*;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.entity.*;
import com.racehorse.training.dto.TrainingPlanResponse;
import com.racehorse.training.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.lang.reflect.RecordComponent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TrainingPlanOwnerProjectionTest {
    @Test
    void sessionProjectionContainsNoStaffOrVetAssigneeFields() {
        var plans = mock(TrainingPlanRepository.class);
        var schedules = mock(TrainingScheduleRepository.class);
        var access = mock(HorseAccessService.class);
        var service = new TrainingPlanService(plans, mock(UserAccountRepository.class), access, schedules,
                mock(TrainingEvaluationRepository.class), Clock.systemUTC());
        UUID horseId = UUID.randomUUID(), planId = UUID.randomUUID(), actorId = UUID.randomUUID();
        var horse = new Horse("H1", "Horse", null, LocalDate.of(2020,1,1), HorseGender.MARE,
                "Bay", "Breed", "A1", HorseStatus.ACTIVE);
        ReflectionTestUtils.setField(horse, "id", horseId);
        var trainer = new UserAccount("trainer", "trainer@test.local", "encoded", Set.of(Role.HEAD_TRAINER));
        var plan = new TrainingPlan(horse, "Plan", null, trainer, true);
        ReflectionTestUtils.setField(plan, "id", planId);
        var phase = new TrainingPlanPhase(1, "Base", LocalDate.now().minusDays(1), LocalDate.now().plusDays(5),
                BigDecimal.valueOf(1000), WorkloadIntensity.LOW, SurfaceType.GRASS, List.of());
        ReflectionTestUtils.setField(phase, "id", UUID.randomUUID());
        plan.replacePhases(List.of(phase));
        var track = new Racetrack("Track", null);
        var session = new TrainingSchedule(phase, horse, track, "Walk", WorkloadIntensity.LOW,
                Instant.now(), Instant.now().plusSeconds(3600), trainer);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        when(plans.findDetailById(planId)).thenReturn(Optional.of(plan));
        when(plans.findByHorseIdOrderByCreatedAtDesc(horseId)).thenReturn(List.of(plan));
        when(schedules.findPlanSessions(List.of(planId))).thenReturn(List.of(session));

        var ownerDetail = service.get(planId, actorId, false, false, true);
        var ownerList = service.listForHorse(horseId, actorId, false, false, true).getFirst();
        for (var result : List.of(ownerDetail, ownerList)) {
            var row = result.phases().getFirst().sessions().getFirst();
            assertThat(row.taskName()).isEqualTo("Walk");
        }
        assertThat(Arrays.stream(TrainingPlanResponse.SessionSummary.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("assignedStaff", "veterinarian", "staffAssignedBy", "vetAssignedBy");
        verify(access, times(2)).requireTrainingAccess(horseId, actorId, false, false, true);
    }
}
