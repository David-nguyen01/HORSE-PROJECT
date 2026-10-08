package com.racehorse.wallet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WalletServiceTest {
    @Test
    void overdueFeeStartsOnDayTwoAndRisesOnDayFour() {
        BigDecimal price = BigDecimal.valueOf(100_000);
        assertEquals(BigDecimal.ZERO, WalletService.lateFee(price, 0));
        assertEquals(BigDecimal.ZERO, WalletService.lateFee(price, 1));
        assertEquals(BigDecimal.valueOf(10_000).setScale(0), WalletService.lateFee(price, 2));
        assertEquals(BigDecimal.valueOf(10_000).setScale(0), WalletService.lateFee(price, 3));
        assertEquals(BigDecimal.valueOf(20_000).setScale(0), WalletService.lateFee(price, 4));
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
        assertEquals(BigDecimal.valueOf(400_000), HorsePlanPolicy.totalDue(
                java.util.List.of(WalletPlan.P30, WalletPlan.P90)));
    }
}
