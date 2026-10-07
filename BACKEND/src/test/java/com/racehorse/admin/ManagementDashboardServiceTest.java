package com.racehorse.admin;

import com.racehorse.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagementDashboardServiceTest {
    @Mock
    private JdbcTemplate jdbc;

    private ManagementDashboardService service;

    @BeforeEach
    void setUp() {
        service = new ManagementDashboardService(jdbc);
    }

    @Test
    void dashboardReturnsAggregatedMetrics() {
        when(jdbc.queryForObject(eq("select count(*) from horses where archived_at is null"), eq(Integer.class)))
                .thenReturn(3);
        when(jdbc.queryForObject(eq("select count(*) from users where enabled=1"), eq(Integer.class)))
                .thenReturn(5);

        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any()))
                .thenReturn(0);

        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("currency='VND'"), eq(BigDecimal.class), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("currency='VND'"), eq(BigDecimal.class), any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        Map<String, Object> result = service.dashboard("2026-10", "2026-09");

        assertThat(result).isNotNull();
        assertThat(result.get("month")).isEqualTo("2026-10");
        assertThat(result.get("compareTo")).isEqualTo("2026-09");
        assertThat(result.get("currency")).isEqualTo("VND");
        assertThat(result.get("activeHorses")).isEqualTo(3);
        assertThat(result.get("activeAccounts")).isEqualTo(5);
        assertThat(result.get("current")).isNotNull();
        assertThat(result.get("previous")).isNotNull();
    }

    @Test
    void invalidMonthFormatThrowsBusinessException() {
        assertThatThrownBy(() -> service.dashboard("invalid-date", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tháng phải có dạng YYYY-MM");
    }
}
