package com.racehorse.training.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.repository.HorseRepository;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.FitnessDashboardResponse;
import com.racehorse.training.entity.FitnessStatus;
import com.racehorse.training.entity.TrainingEvaluation;
import com.racehorse.training.repository.TrainingEvaluationRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FitnessDashboardServiceTest {

    @Mock TrainingEvaluationRepository evaluationRepository;
    @Mock HorseRepository horseRepository;
    @Mock HorseAccessService horseAccessService;
    private FitnessDashboardService service;

    @BeforeEach
    void setUp() {
        service = new FitnessDashboardService(
                evaluationRepository, horseRepository, horseAccessService,
                Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void dashboardReturnsOriginalEvaluationValuesAndTraceableId() {
        Horse horse = horse("H-001", "Lightning");
        UUID horseId = UUID.randomUUID();
        UUID evaluationId = UUID.randomUUID();
        ReflectionTestUtils.setField(horse, "id", horseId);
        TrainingEvaluation evaluation = evaluation(horse, FitnessStatus.GREEN, LocalDate.of(2026, 9, 8));
        ReflectionTestUtils.setField(evaluation, "id", evaluationId);
        when(evaluationRepository.findDashboardData(null, LocalDate.of(2026, 8, 12), LocalDate.of(2026, 9, 10), null))
                .thenReturn(List.of(evaluation));
        when(horseRepository.findByArchivedAtIsNullOrderByNameAsc()).thenReturn(List.of(horse));

        FitnessDashboardResponse response = service.get(null, null, null, null, null);

        assertThat(response.dataPoints()).singleElement().satisfies(point -> {
            assertThat(point.evaluationId()).isEqualTo(evaluationId);
            assertThat(point.trainingLoad()).isEqualByComparingTo("61.50");
            assertThat(point.averageSpeedKmh()).isEqualByComparingTo("52.40");
            assertThat(point.heartRateRecoveryBpm()).isEqualTo(35);
        });
        assertThat(response.horses()).singleElement()
                .extracting(FitnessDashboardResponse.HorseFitnessSummary::fitnessStatus)
                .isEqualTo(FitnessStatus.GREEN);
    }

    @Test
    void dashboardRejectsReversedDateRange() {
        assertThatThrownBy(() -> service.get(
                null, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1), null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngày bắt đầu");
    }

    private Horse horse(String code, String name) {
        return new Horse(code, name, null, LocalDate.of(2022, 3, 10), HorseGender.STALLION,
                "White blaze", "Thoroughbred", "A-01", HorseStatus.ACTIVE);
    }

    private TrainingEvaluation evaluation(Horse horse, FitnessStatus status, LocalDate date) {
        return new TrainingEvaluation(
                horse,
                new UserAccount("trainer", "trainer@test.local", "encoded", Set.of(Role.HEAD_TRAINER)),
                date, 40, new BigDecimal("1600"), new BigDecimal("52.40"),
                new BigDecimal("61.50"), 35, 6, status, "Ổn định", null,
                Instant.parse("2026-09-08T03:00:00Z"));
    }
}
