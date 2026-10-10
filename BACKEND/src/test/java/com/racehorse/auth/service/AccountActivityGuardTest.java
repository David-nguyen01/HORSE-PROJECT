package com.racehorse.auth.service;

import com.racehorse.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountActivityGuardTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void freshAccountCanChangeRole() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(id))).thenReturn(0);

        assertThatCode(() -> new AccountActivityGuard(jdbc).requireEligibleForRoleChange(id))
                .doesNotThrowAnyException();
    }

    @Test
    void existingHorseOwnershipBlocksRoleChange() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(id)))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).contains("horse_ownerships") ? 1 : 0);

        assertThatThrownBy(() -> new AccountActivityGuard(jdbc).requireEligibleForRoleChange(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sở hữu ngựa");
    }

    @Test
    void pastAuditedBusinessActionBlocksRoleChangeEvenWhenSourceWasRemoved() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(id)))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).contains("dbo.audit_logs") ? 1 : 0);

        assertThatThrownBy(() -> new AccountActivityGuard(jdbc).requireEligibleForRoleChange(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("thao tác nghiệp vụ hoặc quản trị");
    }

    @Test
    void activeMedicalAssignmentBlocksAccountDisable() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(id)))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).contains("MEDICAL_ASSIGNMENT") ? 1 : 0);

        assertThatThrownBy(() -> new AccountActivityGuard(jdbc).requireNoActiveAssignments(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ca khám ban đầu");
    }

    @Test
    void completedHistoryDoesNotBlockDisable() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(id))).thenReturn(0);

        assertThatCode(() -> new AccountActivityGuard(jdbc).requireNoActiveAssignments(id))
                .doesNotThrowAnyException();
    }
}
