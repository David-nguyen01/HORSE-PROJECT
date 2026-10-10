package com.racehorse.stable.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HorseIntakeServiceTest {
    private JdbcTemplate jdbc;
    private HorseAccessService access;
    private HorseIntakeService service;
    private Horse horse;
    private UUID horseId;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        access = mock(HorseAccessService.class);
        service = new HorseIntakeService(jdbc, access);
        horse = new Horse("H-1", "Horse", null, LocalDate.of(2020, 1, 1),
                HorseGender.STALLION, "Brown", "Thoroughbred", "", HorseStatus.ACTIVE);
        horseId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        when(access.requireServiceEligible(horseId)).thenReturn(horse);
    }

    @Test
    void staffAdmitsGuaranteedHorseWithoutChangingPayment() {
        horse.setPaymentStatus("UNPAID");
        horse.setServiceOverride(true);
        when(jdbc.update(contains("set intake_status='WAITING_FOR_VET'"), eq(horseId))).thenReturn(1);

        var result = service.admitToWaitingRoom(horseId, staffId);

        assertThat(result.paymentStatus()).isEqualTo("UNPAID");
        assertThat(result.intakeStatus()).isEqualTo("WAITING_FOR_VET");
        verify(jdbc).update(contains("WAITING_ROOM_ADMISSION"), any(UUID.class), eq(horseId), eq(staffId));
    }

    @Test
    void cannotAdmitHorseAlreadyAtVet() {
        horse.setIntakeStatus("IN_MEDICAL");

        assertThatThrownBy(() -> service.admitToWaitingRoom(horseId, staffId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("phòng chờ");
    }

    @Test
    void enteringWaitingRoomNotifiesEveryActiveVeterinarian() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        UserAccount first = mock(UserAccount.class);
        UserAccount second = mock(UserAccount.class);
        when(users.findByRoles(List.of(Role.VETERINARIAN))).thenReturn(List.of(first, second));
        when(jdbc.update(contains("set intake_status='WAITING_FOR_VET'"), eq(horseId))).thenReturn(1);

        new HorseIntakeService(jdbc, access, users, notifications).admitToWaitingRoom(horseId, staffId);

        verify(notifications).create(eq(first), eq("HORSE_WAITING_FOR_VET"), any(), anyString(), anyString(),
                eq("HORSE"), eq(horseId));
        verify(notifications).create(eq(second), eq("HORSE_WAITING_FOR_VET"), any(), anyString(), anyString(),
                eq("HORSE"), eq(horseId));
    }
}
