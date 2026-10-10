package com.racehorse.medical.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.repository.HorseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MedicalQueueWorkflowTest {
    private JdbcTemplate jdbc;
    private HorseRepository horses;
    private MedicalQueueService service;
    private UUID horseId;
    private UUID vetId;
    private Horse horse;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        horses = mock(HorseRepository.class);
        service = new MedicalQueueService(jdbc, horses, new ObjectMapper());
        horseId = UUID.randomUUID();
        vetId = UUID.randomUUID();
        horse = new Horse("H-1", "Horse", null, LocalDate.of(2020, 1, 1),
                HorseGender.STALLION, "Brown", "Thoroughbred", "", HorseStatus.ACTIVE);
        horse.setPaymentStatus("PAID");
        when(horses.findDetailById(horseId)).thenReturn(Optional.of(horse));
    }

    @Test
    void veterinarianCannotReceiveBeforeStaffWaitingRoom() {
        assertThatThrownBy(() -> service.receiveHorse(horseId, vetId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("phòng chờ");
    }

    @Test
    void veterinarianCannotCompleteWithoutNewExamination() {
        horse.setIntakeStatus("IN_MEDICAL");
        UUID assignmentId = UUID.randomUUID();
        when(jdbc.query(contains("record_type='MEDICAL_ASSIGNMENT'"), any(RowMapper.class), eq(horseId), eq(vetId)))
                .thenReturn(List.of(assignmentId));
        when(jdbc.queryForObject(contains("record_type='MEDICAL_EXAMINATION'"), eq(Integer.class),
                eq(assignmentId), eq(horseId), eq(vetId))).thenReturn(0);

        assertThatThrownBy(() -> service.completeMedical(horseId, vetId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("hồ sơ khám");
    }
}
