package com.racehorse.reporting.service;

import com.racehorse.audit.service.AuditService;
import com.racehorse.finance.service.FinanceService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OwnerReportExpensesTest {
    @Test
    void expenseReportScopesHorsesFeesPrizesAndWinsToTheOwner() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        UUID ownerId = UUID.randomUUID();
        OwnerReportService service = new OwnerReportService(jdbc, mock(ObjectMapper.class),
                mock(FinanceService.class), mock(AuditService.class));

        OwnerReportService.ExpenseOverview result = service.myExpenses(ownerId,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(4)).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getAllValues()).anySatisfy(query -> assertThat(query)
                .contains("horse_ownerships ho", "ho.owner_id=?", "h.archived_at is null"));
        assertThat(sql.getAllValues()).anySatisfy(query -> assertThat(query)
                .contains("left join horses h", "ft.wallet_owner_user_id=?"));
        assertThat(sql.getAllValues()).anySatisfy(query -> assertThat(query)
                .contains("PRIZE_REVENUE", "ho.owner_id=?"));
        assertThat(sql.getAllValues()).anySatisfy(query -> assertThat(query)
                .contains("race_results rr", "rr.finish_position=1", "ho.owner_id=?"));
        assertThat(result.totalExpense()).isZero();
        assertThat(result.totalPrizeRevenue()).isZero();
    }
}
