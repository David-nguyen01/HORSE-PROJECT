package com.racehorse.wallet;

import java.math.BigDecimal;

public enum WalletPlan {
    P30(30, 5_000_000), P90(90, 15_000_000), P365(365, 60_000_000);

    private final int days;
    private final BigDecimal price;

    WalletPlan(int days, long price) {
        this.days = days;
        this.price = BigDecimal.valueOf(price);
    }

    public int days() { return days; }
    public BigDecimal price() { return price; }
}
