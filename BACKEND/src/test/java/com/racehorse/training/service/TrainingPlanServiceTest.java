package com.racehorse.training.service;

import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.TrainingPlanRequest;
import com.racehorse.training.entity.SurfaceType;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    private TrainingPlanRequest.Phase phase(String name, String start, String end) {
        return new TrainingPlanRequest.Phase(
                name, LocalDate.parse(start), LocalDate.parse(end), new BigDecimal("1200"),
                WorkloadIntensity.MEDIUM, SurfaceType.DIRT);
    }
}
