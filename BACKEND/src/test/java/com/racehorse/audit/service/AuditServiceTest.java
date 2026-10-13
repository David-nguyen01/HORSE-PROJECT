package com.racehorse.audit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private AuditService service;

    private final UUID actorId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AuditService(jdbc);
    }

    @Test
    void recordPersistsAuditLogEntry() {
        service.record(actorId, "HORSE", "HORSE_CREATE", "HORSE", entityId,
                "Tạo mới hồ sơ ngựa", "{\"status\":\"DRAFT\"}", "{\"status\":\"ACTIVE\"}",
                "192.168.1.10", "Mozilla/5.0");

        verify(jdbc).update(contains("insert into audit_logs"),
                eq(actorId), any(), eq("HORSE"), eq("HORSE_CREATE"), eq("HORSE"), eq(entityId),
                eq("Tạo mới hồ sơ ngựa"), eq("{\"status\":\"DRAFT\"}"), eq("{\"status\":\"ACTIVE\"}"),
                eq("192.168.1.10"), eq("Mozilla/5.0"));
    }

    @Test
    void recordTrimsOverlengthIpAndUserAgent() {
        String longIp = "2001:0db8:85a3:0000:0000:8a2e:0370:7334:extra_long_ipv6_string_that_exceeds_45_chars";
        String longUserAgent = "A".repeat(600);

        service.record(actorId, "AUTH", "LOGIN", "USER", entityId,
                "Đăng nhập", null, null, longIp, longUserAgent);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(contains("insert into audit_logs"),
                eq(actorId), any(), eq("AUTH"), eq("LOGIN"), eq("USER"), eq(entityId),
                eq("Đăng nhập"), any(), any(), captor.capture(), captor.capture());

        assertThat(captor.getAllValues().get(0).toString()).hasSize(45);
        assertThat(captor.getAllValues().get(1).toString()).hasSize(500);
    }

    @Test
    void recordEightParamOverloadDelegatesSuccessfully() {
        service.record(actorId, "TRAINING", "SESSION_START", "TRAINING_SCHEDULE", entityId,
                "Bắt đầu buổi tập", "10.0.0.1", "Chrome");

        verify(jdbc).update(contains("insert into audit_logs"),
                eq(actorId), any(), eq("TRAINING"), eq("SESSION_START"), eq("TRAINING_SCHEDULE"), eq(entityId),
                eq("Bắt đầu buổi tập"), eq(null), eq(null), eq("10.0.0.1"), eq("Chrome"));
    }

    @Test
    void recordHandlesDatabaseExceptionWithoutCrashingCaller() {
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("DB down"));

        assertThatCode(() -> service.record(actorId, "STABLE", "STALL_ASSIGN", "STALL", entityId,
                "Phân chuồng", "127.0.0.1", "Agent"))
                .doesNotThrowAnyException();
    }
}
