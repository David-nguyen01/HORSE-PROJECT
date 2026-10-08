package com.racehorse.wallet;

import java.math.BigDecimal;

public enum WalletPlan {
    P30(30, 100_000), P90(90, 300_000), P365(365, 1_100_000);

    private final int days;
    private final BigDecimal price;

    WalletPlan(int days, long price) {
        this.days = days;
        this.price = BigDecimal.valueOf(price);
    }

    public int days() { return days; }
    public BigDecimal price() { return price; }
}
