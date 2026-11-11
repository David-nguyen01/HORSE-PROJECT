package com.racehorse.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalletServiceTest {
    @Test
    void paymentHistoryIsScopedToTheCurrentWalletOwner() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WalletService service = new WalletService(jdbc, mock(ObjectMapper.class),
                mock(PayosGateway.class), Clock.systemUTC(), "Asia/Ho_Chi_Minh");
        UUID ownerId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(ownerId), eq(20), eq(20)))
                .thenReturn(List.of());

        assertTrue(service.paymentHistory(ownerId, 20, 20).isEmpty());

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(ownerId), eq(20), eq(20));
        assertTrue(sql.getValue().contains("wallet_owner_user_id=?"));
        assertTrue(sql.getValue().contains("source_entity_type='WALLET'"));
    }

    @Test
    void overdueFeeStartsOnDayTwoAndRisesOnDayFour() {
        BigDecimal price = BigDecimal.valueOf(5_000_000);
        assertEquals(BigDecimal.ZERO, WalletService.lateFee(price, 0));
        assertEquals(BigDecimal.ZERO, WalletService.lateFee(price, 1));
        assertEquals(BigDecimal.valueOf(500_000).setScale(0), WalletService.lateFee(price, 2));
        assertEquals(BigDecimal.valueOf(500_000).setScale(0), WalletService.lateFee(price, 3));
        assertEquals(BigDecimal.valueOf(1_000_000).setScale(0), WalletService.lateFee(price, 4));
    }

    @Test
    void graceAndRestrictedStatesFollowClubDates() {
        LocalDate due = LocalDate.of(2026, 10, 10);
        assertEquals("ACTIVE", WalletService.state("P30", due, due));
        assertEquals("GRACE", WalletService.state("P30", due, due.plusDays(1)));
        assertEquals("LIMITED", WalletService.state("P30", due, due.plusDays(2)));
        assertEquals("LIMITED", WalletService.state("P30", due, due.plusDays(3)));
        assertEquals("SUSPENDED", WalletService.state("P30", due, due.plusDays(4)));
        assertEquals("NO_PLAN", WalletService.state(null, null, due.plusDays(4)));
    }

    @Test
    void nextCycleUsesScheduledPlanOnlyForThatHorse() {
        assertEquals(WalletPlan.P90, HorsePlanPolicy.renewalPlan(WalletPlan.P30, WalletPlan.P90));
        assertEquals(WalletPlan.P30, HorsePlanPolicy.renewalPlan(WalletPlan.P30, null));
        assertEquals(BigDecimal.valueOf(20_000_000), HorsePlanPolicy.totalDue(
                java.util.List.of(WalletPlan.P30, WalletPlan.P90)));
    }

    @Test
    void subscriptionPricesScaleByMonthlyRate() {
        assertEquals(BigDecimal.valueOf(5_000_000), WalletPlan.P30.price());
        assertEquals(BigDecimal.valueOf(15_000_000), WalletPlan.P90.price());
        assertEquals(BigDecimal.valueOf(60_000_000), WalletPlan.P365.price());
    }
}
