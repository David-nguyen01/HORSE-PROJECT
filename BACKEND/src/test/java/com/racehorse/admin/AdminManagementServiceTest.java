package com.racehorse.admin;

import com.racehorse.audit.service.AuditService;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.auth.service.AccountActivityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminManagementServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock PasswordEncoder encoder;
    @Mock AuditService audit;
    @Mock AccountActivityGuard activityGuard;
    private AdminManagementService service;

    @BeforeEach
    void setUp() {
        service = new AdminManagementService(jdbc, encoder, audit, new ObjectMapper(), activityGuard);
    }

    @Test
    void repeatedAccountStatusDoesNotWriteOrCreateFalseAudit() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        accountSnapshot(target, true);

        service.setEnabled(actor, target, true);

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Object[]>any());
        verifyNoInteractions(audit);
    }

    @Test
    void managerCannotLockOwnCurrentAccount() {
        UUID actor = UUID.randomUUID();
        accountSnapshot(actor, true);

        assertThatThrownBy(() -> service.setEnabled(actor, actor, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể tự khóa");

        verifyNoInteractions(audit);
    }

    @Test
    void accountWithActiveAssignmentCannotBeDisabled() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        accountSnapshot(target, true);
        org.mockito.Mockito.doThrow(new BusinessException("Còn ca khám"))
                .when(activityGuard).requireNoActiveAssignments(target);

        assertThatThrownBy(() -> service.setEnabled(actor, target, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Còn ca khám");

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Object[]>any());
        verifyNoInteractions(audit);
    }

    @Test
    void restoreRejectsHorseThatIsNotArchived() {
        UUID horseId = UUID.randomUUID();
        when(jdbc.update("update horses set archived_at=null,updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=? and archived_at is not null", horseId))
                .thenReturn(0);

        assertThatThrownBy(() -> service.restore(UUID.randomUUID(), horseId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang lưu trữ");

        verifyNoInteractions(audit);
    }

    @Test
    void rejectsUnboundedAccountPageSize() {
        assertThatThrownBy(() -> service.accounts(null, null, null, 0, 101))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phân trang");
        verifyNoInteractions(jdbc, audit);
    }

    @Test
    void managerCanGuaranteeHorseWithLegacyAdmissionValueInPaymentStatus() {
        UUID actor = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        when(jdbc.queryForObject("select count(*) from horses where id=?", Integer.class, horseId))
                .thenReturn(1);
        Map<String, Object> before = new HashMap<>();
        before.put("name", "Horse 01");
        before.put("payment_status", "PENDING_PAYMENT");
        before.put("service_override", false);
        before.put("override_reason", null);
        before.put("paid_until", null);
        when(jdbc.queryForMap(
                "select name, payment_status, service_override, override_reason, paid_until from horses where id=?",
                horseId)).thenReturn(before);

        service.updateServiceAccess(actor, horseId, null, true, "Club Manager bảo lãnh", null);

        verify(jdbc).update(
                "update horses set payment_status=?, service_override=?, override_reason=?, paid_until=?, updated_at=SYSDATETIMEOFFSET(), version=version+1 where id=?",
                "PENDING_PAYMENT", true, "Club Manager bảo lãnh", null, horseId);
        verify(audit).record(
                org.mockito.ArgumentMatchers.eq(actor),
                org.mockito.ArgumentMatchers.eq("PROFILE"),
                org.mockito.ArgumentMatchers.eq("SERVICE_ACCESS_OVERRIDE"),
                org.mockito.ArgumentMatchers.eq("HORSE"),
                org.mockito.ArgumentMatchers.eq(horseId),
                org.mockito.ArgumentMatchers.contains("duyệt bảo lãnh"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void explicitInvalidPaymentStatusIsStillRejected() {
        UUID horseId = UUID.randomUUID();
        when(jdbc.queryForObject("select count(*) from horses where id=?", Integer.class, horseId))
                .thenReturn(1);
        Map<String, Object> before = new HashMap<>();
        before.put("name", "Horse 01");
        before.put("payment_status", "UNPAID");
        before.put("service_override", false);
        before.put("override_reason", null);
        before.put("paid_until", null);
        when(jdbc.queryForMap(
                "select name, payment_status, service_override, override_reason, paid_until from horses where id=?",
                horseId)).thenReturn(before);

        assertThatThrownBy(() -> service.updateServiceAccess(
                UUID.randomUUID(), horseId, "IN_TRAINING", true, "Bảo lãnh", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Trạng thái thanh toán không hợp lệ");

        verify(audit, never()).record(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private void accountSnapshot(UUID id, boolean enabled) {
        when(jdbc.queryForObject("select count(*) from users where id=?", Integer.class, id)).thenReturn(1);
        when(jdbc.queryForMap("select username,email,enabled from users where id=?", id))
                .thenReturn(Map.of("username", "manager", "email", "manager@example.com", "enabled", enabled));
    }
}
