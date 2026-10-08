package com.racehorse.wallet;

import java.math.BigDecimal;
import java.util.List;

final class HorsePlanPolicy {
    private HorsePlanPolicy() { }

    static WalletPlan renewalPlan(WalletPlan current, WalletPlan scheduled) {
        return scheduled == null ? current : scheduled;
    }

    static BigDecimal totalDue(List<WalletPlan> plans) {
        return plans.stream().map(WalletPlan::price).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
