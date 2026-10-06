package com.racehorse.health.service;

import com.racehorse.auth.entity.*;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.*;
import com.racehorse.horse.repository.*;
import com.racehorse.horse.service.HorseAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TrainerMeasurementAccessTest {
    @Test
    void otherTrainerCannotReadWeightsHealthOrReadinessEvenWithPrivilegedFlag() {
        var horses = mock(HorseRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var access = new HorseAccessService(horses, mock(HorseOwnershipRepository.class));
        var service = new HorseMeasurementService(jdbc, access);
        UUID horseId = UUID.randomUUID(), stranger = UUID.randomUUID();
        var trainer = new UserAccount("trainer", "trainer@test.local", "encoded", Set.of(Role.HEAD_TRAINER));
        ReflectionTestUtils.setField(trainer, "id", UUID.randomUUID());
        var horse = new Horse("H1", "Horse", null, LocalDate.of(2020,1,1), HorseGender.MARE,
                "Bay", "Breed", "A1", HorseStatus.ACTIVE);
        horse.assignCurrentTrainer(trainer);
        when(horses.findDetailById(horseId)).thenReturn(Optional.of(horse));
        assertThatThrownBy(() -> service.weights(horseId, stranger, true, true)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.healthStatus(horseId, stranger, true, true)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.readiness(horseId, null, stranger, true, true)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(jdbc);
    }
}
