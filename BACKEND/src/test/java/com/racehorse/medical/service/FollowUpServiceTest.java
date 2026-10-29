package com.racehorse.medical.service;

import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FollowUpServiceTest {
    private JdbcTemplate jdbc;
    private UserAccountRepository users;
    private FollowUpService service;
    private UUID horseId;
    private UUID assignedVet;
    private UUID otherVet;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        users = mock(UserAccountRepository.class);
        service = new FollowUpService(jdbc, new ObjectMapper(), users, mock(NotificationService.class));
        horseId = UUID.randomUUID();
        assignedVet = UUID.randomUUID();
        otherVet = UUID.randomUUID();
    }

    @Test
    void onlyAssignedDoctorMayWriteFollowUpExamination() {
        UUID caseId = UUID.randomUUID();
        when(jdbc.queryForList(contains("record_type='MEDICAL_FOLLOW_UP'"), eq(horseId)))
                .thenReturn(List.of(Map.of("id", caseId.toString(), "user_id", assignedVet.toString(), "status", "ASSIGNED")));

        assertThat(service.requireAssignedForExamination(horseId, assignedVet)).isEqualTo(caseId);
        assertThatThrownBy(() -> service.requireAssignedForExamination(horseId, otherVet))
                .isInstanceOf(BusinessException.class).hasMessageContaining("bác sĩ khác");
    }

    @Test
    void nobodyMayWriteWhileHandoverIsPending() {
        UUID caseId = UUID.randomUUID();
        when(jdbc.queryForList(contains("record_type='MEDICAL_FOLLOW_UP'"), eq(horseId)))
                .thenReturn(List.of(Map.of("id", caseId, "user_id", assignedVet, "status", "HANDOVER_REQUESTED")));

        assertThatThrownBy(() -> service.requireAssignedForExamination(horseId, assignedVet))
                .isInstanceOf(BusinessException.class).hasMessageContaining("chờ bàn giao");
        assertThatThrownBy(() -> service.requireAssignedForExamination(horseId, otherVet))
                .isInstanceOf(BusinessException.class).hasMessageContaining("chờ bàn giao");
    }

    @Test
    void doctorWhoDoesNotOwnCaseCannotRequestHandover() {
        UUID caseId = UUID.randomUUID();
        when(jdbc.query(contains("select horse_id"), any(RowMapper.class), eq(caseId)))
                .thenReturn(List.of(horseId));
        when(jdbc.update(contains("status='HANDOVER_REQUESTED'"),
                eq("Không thể tham gia"), eq(FollowUpService.COVER_VISIT),
                eq(caseId), eq(horseId), eq(otherVet))).thenReturn(0);

        assertThatThrownBy(() -> service.requestHandover(caseId, otherVet, "Không thể tham gia"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("bác sĩ đang phụ trách");
    }

    @Test
    void secondDoctorCannotAcceptTheSameHandoverTwice() {
        UUID caseId = UUID.randomUUID();
        when(jdbc.query(contains("select horse_id"), any(RowMapper.class), eq(caseId)))
                .thenReturn(List.of(horseId));
        when(jdbc.query(contains("select user_id"), any(RowMapper.class), eq(caseId), eq(horseId)))
                .thenReturn(List.of(assignedVet));
        when(jdbc.update(contains("status='ASSIGNED'"),
                eq(otherVet), eq(otherVet), eq(caseId), eq(horseId), eq(otherVet)))
                .thenReturn(1, 0);
        FollowUpService.FollowUpView claimed = new FollowUpService.FollowUpView(
                caseId, horseId, "H03", "Horse", LocalDate.now(), otherVet,
                "vet2", "ASSIGNED", UUID.randomUUID().toString(), "Bàn giao",
                FollowUpService.COVER_VISIT, "MONTHLY");
        when(jdbc.query(contains("select f.id"), any(RowMapper.class))).thenReturn(List.of(claimed));
        when(users.findById(assignedVet)).thenReturn(Optional.empty());

        assertThat(service.acceptHandover(caseId, otherVet).veterinarianId()).isEqualTo(otherVet);
        verify(jdbc, never()).update(contains("set diagnosing_vet_user_id=?"), any(), any(), any());
        assertThatThrownBy(() -> service.acceptHandover(caseId, otherVet))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đã được bác sĩ khác tiếp nhận");
    }

    @Test
    void acceptingPermanentTransferChangesPrimaryVeterinarian() {
        UUID caseId = UUID.randomUUID();
        when(jdbc.query(contains("select horse_id"), any(RowMapper.class), eq(caseId)))
                .thenReturn(List.of(horseId));
        when(jdbc.query(contains("select user_id"), any(RowMapper.class), eq(caseId), eq(horseId)))
                .thenReturn(List.of(assignedVet));
        when(jdbc.update(contains("status='ASSIGNED'"),
                eq(otherVet), eq(otherVet), eq(caseId), eq(horseId), eq(otherVet))).thenReturn(1);
        when(jdbc.update(contains("set diagnosing_vet_user_id=?"),
                eq(otherVet), eq(horseId), eq(assignedVet))).thenReturn(1);
        FollowUpService.FollowUpView claimed = new FollowUpService.FollowUpView(
                caseId, horseId, "H03", "Horse", LocalDate.now(), otherVet,
                "vet2", "ASSIGNED", null, "Chuyển người phụ trách",
                FollowUpService.TRANSFER_CARE, "MONTHLY");
        when(jdbc.query(contains("select f.id"), any(RowMapper.class))).thenReturn(List.of(claimed));
        when(users.findById(assignedVet)).thenReturn(Optional.empty());

        assertThat(service.acceptHandover(caseId, otherVet).handoverMode())
                .isEqualTo(FollowUpService.TRANSFER_CARE);
        verify(jdbc).update(contains("set diagnosing_vet_user_id=?"),
                eq(otherVet), eq(horseId), eq(assignedVet));
    }

    @Test
    void monthlyVisitAfterOneTimeCoverReturnsToPrimaryDoctor() {
        UUID examId = UUID.randomUUID();
        LocalDate visitDate = LocalDate.of(2026, 10, 31);
        when(jdbc.query(contains("select diagnosing_vet_user_id"), any(RowMapper.class), eq(horseId)))
                .thenReturn(List.of(assignedVet));

        service.recordExamination(horseId, otherVet, examId, null, visitDate, null);

        verify(jdbc).update(contains("insert into module_records"),
                any(UUID.class), eq(horseId), eq(assignedVet), eq(LocalDate.of(2026, 11, 30)), anyString());
    }

    @Test
    void onlyRequestingDoctorCanWithdrawUnacceptedHandover() {
        UUID caseId = UUID.randomUUID();
        when(jdbc.query(contains("select horse_id"), any(RowMapper.class), eq(caseId)))
                .thenReturn(List.of(horseId));
        when(jdbc.update(contains("status='ASSIGNED'"), eq(caseId), eq(horseId), eq(otherVet)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.cancelHandover(caseId, otherVet))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Chỉ bác sĩ đã gửi yêu cầu");
    }
}
