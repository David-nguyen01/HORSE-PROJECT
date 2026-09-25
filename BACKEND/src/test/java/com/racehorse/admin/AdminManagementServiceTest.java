package com.racehorse.admin;

import com.racehorse.audit.service.AuditService;
import com.racehorse.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

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
    private AdminManagementService service;

    @BeforeEach
    void setUp() {
        service = new AdminManagementService(jdbc, encoder, audit, new ObjectMapper());
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

    private void accountSnapshot(UUID id, boolean enabled) {
        when(jdbc.queryForObject("select count(*) from users where id=?", Integer.class, id)).thenReturn(1);
        when(jdbc.queryForMap("select username,email,enabled from users where id=?", id))
                .thenReturn(Map.of("username", "manager", "email", "manager@example.com", "enabled", enabled));
    }
}
