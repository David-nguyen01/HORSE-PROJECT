package com.racehorse.training.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.TrainingPlanRequest;
import com.racehorse.training.dto.TrainingPlanResponse;
import com.racehorse.training.entity.SurfaceType;
import com.racehorse.training.entity.TrainingPlan;
import com.racehorse.training.entity.WorkloadIntensity;
import com.racehorse.training.repository.TrainingPlanRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingPlanServiceTest {

    @Mock TrainingPlanRepository repository;
    @Mock UserAccountRepository userRepository;
    @Mock HorseAccessService horseAccessService;
    @Mock TrainingScheduleRepository scheduleRepository;

    private TrainingPlanService service;

    @BeforeEach
    void setUp() {
        service = new TrainingPlanService(repository, userRepository, horseAccessService, scheduleRepository);
    }

    @Test
    void rejectsOverlappingPhasesBeforeWritingAnything() {
        UUID horseId = UUID.randomUUID();
        TrainingPlanRequest request = new TrainingPlanRequest(
                horseId,
                "Speed plan",
                true,
                List.of(
                        phase("Base", "2026-09-01", "2026-09-10"),
                        phase("Speed", "2026-09-10", "2026-09-20")));

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được trùng");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getEnforcesHorseReadAccess() {
        UUID planId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Horse horse = horse();
        org.springframework.test.util.ReflectionTestUtils.setField(horse, "id", horseId);
        UserAccount trainer = trainer();
        TrainingPlan plan = new TrainingPlan(horse, "Speed plan", null, trainer, true);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "id", planId);

        when(repository.findDetailById(planId)).thenReturn(java.util.Optional.of(plan));

        TrainingPlanResponse response = service.get(planId, userId, false);

        assertThat(response.id()).isEqualTo(planId);
        verify(horseAccessService).requireReadable(horseId, userId, false);
    }

    @Test
    void listForHorseEnforcesHorseReadAccess() {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.findByHorseIdOrderByCreatedAtDesc(horseId)).thenReturn(List.of());

        List<TrainingPlanResponse> result = service.listForHorse(horseId, userId, false);

        assertThat(result).isEmpty();
        verify(horseAccessService).requireReadable(horseId, userId, false);
    }

    private TrainingPlanRequest.Phase phase(String name, String start, String end) {
        return new TrainingPlanRequest.Phase(
                name, LocalDate.parse(start), LocalDate.parse(end), new BigDecimal("1200"),
                WorkloadIntensity.MEDIUM, SurfaceType.DIRT);
    }

    private Horse horse() {
        return new Horse("H-001", "Lightning", null, LocalDate.of(2022, 3, 10),
                HorseGender.STALLION, "White blaze", "Thoroughbred", "A-01", HorseStatus.ACTIVE);
    }

    private UserAccount trainer() {
        return new UserAccount("trainer", "trainer@test.local", "encoded", java.util.Set.of(Role.HEAD_TRAINER));
    }
}
