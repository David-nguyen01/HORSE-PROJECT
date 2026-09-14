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
import com.racehorse.training.entity.TrainingEvaluation;
import com.racehorse.training.repository.TrainingEvaluationRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingEvaluationServiceTest {

    @Mock TrainingEvaluationRepository repository;
    @Mock UserAccountRepository userRepository;
    @Mock HorseAccessService horseAccessService;

    private TrainingEvaluationService service;
    private final Instant now = Instant.parse("2026-09-10T02:00:00Z");

    @BeforeEach
    void setUp() {
        service = new TrainingEvaluationService(
                repository, userRepository, horseAccessService,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void createStoresTrainerEnteredMetricsWithoutRecalculation() {
        UUID horseId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Horse horse = horse();
        UserAccount trainer = trainer();
        when(horseAccessService.requireActive(horseId)).thenReturn(horse);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(trainer));
        when(repository.save(any(TrainingEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TrainingEvaluationResponse response = service.create(actorId, request(horseId, " https://video.test/run "));

        assertThat(response.durationMinutes()).isEqualTo(42);
        assertThat(response.distanceMeters()).isEqualByComparingTo("1800.50");
        assertThat(response.averageSpeedKmh()).isEqualByComparingTo("54.75");
        assertThat(response.trainingLoad()).isEqualByComparingTo("72.25");
        assertThat(response.heartRateRecoveryBpm()).isEqualTo(38);
        assertThat(response.perceivedExertion()).isEqualTo(7);
        assertThat(response.fitnessStatus()).isEqualTo(FitnessStatus.YELLOW);
        assertThat(response.videoUrl()).isEqualTo("https://video.test/run");
        assertThat(response.completedAt()).isEqualTo(now);
    }

    @Test
    void createRejectsUnsafeVideoProtocol() {
        UUID horseId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(horseAccessService.requireActive(horseId)).thenReturn(horse());
        when(userRepository.findById(actorId)).thenReturn(Optional.of(trainer()));

        assertThatThrownBy(() -> service.create(actorId, request(horseId, "javascript:alert(1)")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP hoặc HTTPS");
    }

    @Test
    void ownerHistoryRequiresHorseOwnershipCheck() {
        UUID horseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        when(repository.findByHorseIdOrderBySessionDateDescCompletedAtDesc(horseId, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        service.listForHorse(horseId, ownerId, false, pageable);

        verify(horseAccessService).requireReadable(horseId, ownerId, false);
    }

    private CreateTrainingEvaluationRequest request(UUID horseId, String videoUrl) {
        return new CreateTrainingEvaluationRequest(
                horseId, LocalDate.of(2026, 9, 9), 42,
                new BigDecimal("1800.50"), new BigDecimal("54.75"),
                new BigDecimal("72.25"), 38, 7, FitnessStatus.YELLOW,
                "Phản ứng tốt, cần theo dõi hồi phục.", videoUrl);
    }

    private Horse horse() {
        return new Horse("H-001", "Lightning", null, LocalDate.of(2022, 3, 10),
                HorseGender.STALLION, "White blaze", "Thoroughbred", "A-01", HorseStatus.ACTIVE);
    }

    private UserAccount trainer() {
        return new UserAccount("trainer", "trainer@test.local", "encoded", Set.of(Role.HEAD_TRAINER));
    }
}
