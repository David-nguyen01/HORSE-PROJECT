package com.racehorse.finance.service;

import com.racehorse.audit.service.AuditService;
import com.racehorse.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private AuditService audit;

    private FinanceService service;
    private final UUID actor = UUID.randomUUID();
    private final UUID horseId = UUID.randomUUID();
    private final UUID stableId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        service = new FinanceService(jdbc, audit);
    }

    @Test
    void create_expenseWithPrizeRevenue_throwsException() {
        assertThatThrownBy(() -> service.create(actor, horseId, null, today, "EXPENSE", "PRIZE_REVENUE",
                new BigDecimal("10000000"), "Tiền thưởng sai loại", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Danh mục PRIZE_REVENUE không phù hợp với khoản chi (EXPENSE)");

        verifyNoInteractions(audit);
    }

    @Test
    void create_revenueWithFeedCost_throwsException() {
        assertThatThrownBy(() -> service.create(actor, horseId, null, today, "REVENUE", "FEED_COST",
                new BigDecimal("5000000"), "Chi phí thức ăn sai loại", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Danh mục FEED_COST không phù hợp với khoản thu (REVENUE)");

        verifyNoInteractions(audit);
    }

    @Test
    void create_revenueWithMedicalCost_throwsException() {
        assertThatThrownBy(() -> service.create(actor, horseId, null, today, "REVENUE", "MEDICAL_COST",
                new BigDecimal("2000000"), "Chi phí y tế sai loại", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Danh mục MEDICAL_COST không phù hợp với khoản thu (REVENUE)");

        verifyNoInteractions(audit);
    }

    @Test
    void create_revenueWithOperatingCost_throwsException() {
        assertThatThrownBy(() -> service.create(actor, null, stableId, today, "REVENUE", "OPERATING_COST",
                new BigDecimal("3000000"), "Chi phí vận hành sai loại", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Danh mục OPERATING_COST không phù hợp với khoản thu (REVENUE)");

        verifyNoInteractions(audit);
    }

    @Test
    void create_invalidType_throwsException() {
        assertThatThrownBy(() -> service.create(actor, horseId, null, today, "OTHER", "FEED_COST",
                new BigDecimal("1000000"), "Loại không hợp lệ", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("transactionType chỉ nhận EXPENSE hoặc REVENUE");

        verifyNoInteractions(audit);
    }

    @Test
    void create_invalidCategory_throwsException() {
        assertThatThrownBy(() -> service.create(actor, horseId, null, today, "EXPENSE", "UNKNOWN_CATEGORY",
                new BigDecimal("1000000"), "Danh mục không hợp lệ", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("category không hợp lệ");

        verifyNoInteractions(audit);
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_validExpenseFeedCost_succeeds() {
        when(jdbc.queryForObject(contains("horses"), eq(Integer.class), eq(horseId))).thenReturn(1);
        when(jdbc.update(contains("insert into financial_transactions"), any(Object[].class))).thenReturn(1);

        FinanceService.TransactionView mockView = new FinanceService.TransactionView(
                UUID.randomUUID(), horseId, "Ngựa A", null, null, today,
                "EXPENSE", "FEED_COST", new BigDecimal("5000000"), "VND",
                "Mua cỏ", null, null, actor, OffsetDateTime.now());
        when(jdbc.query(contains("financial_transactions ft"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(mockView));

        FinanceService.TransactionView result = service.create(actor, horseId, null, today, "EXPENSE", "FEED_COST",
                new BigDecimal("5000000"), "Mua cỏ", null, null);

        assertThat(result).isNotNull();
        assertThat(result.transactionType()).isEqualTo("EXPENSE");
        assertThat(result.category()).isEqualTo("FEED_COST");
        verify(audit).record(eq(actor), eq("REPORTING"), eq("TRANSACTION_CREATE"), eq("FINANCIAL_TRANSACTION"),
                any(), contains("Chi phí"), isNull(), isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_validRevenuePrizeRevenue_succeeds() {
        when(jdbc.queryForObject(contains("horses"), eq(Integer.class), eq(horseId))).thenReturn(1);
        when(jdbc.update(contains("insert into financial_transactions"), any(Object[].class))).thenReturn(1);

        FinanceService.TransactionView mockView = new FinanceService.TransactionView(
                UUID.randomUUID(), horseId, "Ngựa A", null, null, today,
                "REVENUE", "PRIZE_REVENUE", new BigDecimal("50000000"), "VND",
                "Tiền thưởng vô địch", null, null, actor, OffsetDateTime.now());
        when(jdbc.query(contains("financial_transactions ft"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(mockView));

        FinanceService.TransactionView result = service.create(actor, horseId, null, today, "REVENUE", "PRIZE_REVENUE",
                new BigDecimal("50000000"), "Tiền thưởng vô địch", null, null);

        assertThat(result).isNotNull();
        assertThat(result.transactionType()).isEqualTo("REVENUE");
        assertThat(result.category()).isEqualTo("PRIZE_REVENUE");
        verify(audit).record(eq(actor), eq("REPORTING"), eq("TRANSACTION_CREATE"), eq("FINANCIAL_TRANSACTION"),
                any(), contains("Doanh thu"), isNull(), isNull());
    }
}
