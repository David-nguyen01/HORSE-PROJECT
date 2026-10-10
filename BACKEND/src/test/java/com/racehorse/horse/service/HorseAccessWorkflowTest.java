package com.racehorse.horse.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import com.racehorse.horse.repository.HorseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HorseAccessWorkflowTest {
    private final UUID horseId = UUID.randomUUID();
    private HorseRepository horses;
    private JdbcTemplate jdbc;
    private HorseAccessService service;
    private Horse horse;

    @BeforeEach
    void setUp() {
        horses = mock(HorseRepository.class);
        jdbc = mock(JdbcTemplate.class);
        service = new HorseAccessService(horses, mock(HorseOwnershipRepository.class), jdbc);
        horse = new Horse("H-1", "Horse", null, LocalDate.of(2020, 1, 1),
                HorseGender.STALLION, "Brown", "Thoroughbred", "", HorseStatus.ACTIVE);
        horse.setPaymentStatus("PAID");
        when(horses.findDetailById(horseId)).thenReturn(Optional.of(horse));
    }

    @Test
    void paidHorseMustCompleteIntakeBeforeTraining() {
        ReflectionTestUtils.setField(horse, "healthStatus", "FIT");
        ReflectionTestUtils.setField(horse, "diagnosingVet", mock(UserAccount.class));
        horse.setIntakeStatus("PENDING_STALL");

        assertThatThrownBy(() -> service.requireTrainingReady(horseId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ô chuồng");
    }

    @Test
    void injuredStabledHorseCannotTrainEvenWithoutLock() {
        horse.setIntakeStatus("STABLED");
        ReflectionTestUtils.setField(horse, "diagnosingVet", mock(UserAccount.class));
        ReflectionTestUtils.setField(horse, "healthStatus", "INJURED");

        assertThatThrownBy(() -> service.requireTrainingReady(horseId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("FIT");
    }

    @Test
    void fitStabledHorseMustHaveAssignmentAndReleasedLock() {
        horse.setIntakeStatus("STABLED");
        ReflectionTestUtils.setField(horse, "diagnosingVet", mock(UserAccount.class));
        ReflectionTestUtils.setField(horse, "healthStatus", "FIT");
        when(jdbc.queryForObject(contains("STALL_ASSIGNMENT"), eq(Integer.class), eq(horseId))).thenReturn(1);
        when(jdbc.queryForObject(contains("training_locks"), eq(Integer.class), eq(horseId)))
                .thenReturn(1, 0);

        assertThatThrownBy(() -> service.requireTrainingReady(horseId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("khóa huấn luyện");
        assertThat(service.requireTrainingReady(horseId)).isSameAs(horse);
    }

    @Test
    void managerGuaranteeGrantsServiceAccessButNotTrainingBeforeIntake() {
        horse.setPaymentStatus("UNPAID");
        horse.setServiceOverride(true);
        assertThat(service.requireServiceEligible(horseId)).isSameAs(horse);
        assertThatThrownBy(() -> service.requireTrainingReady(horseId))
                .isInstanceOf(BusinessException.class);
    }
}
