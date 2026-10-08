package com.racehorse.wallet;

import tools.jackson.databind.JsonNode;
import java.time.Instant;

final class FakePayosGateway implements PayosGateway {
    @Override
    public Link create(long orderCode, long amountVnd, String description, Instant expiresAt) {
        return new Link(null, null, "fake-" + orderCode);
    }

    @Override
    public Status getStatus(long orderCode) { return new Status("PENDING", 0); }

    @Override
    public boolean verifyWebhook(JsonNode body) { return false; }

    @Override
    public boolean isFake() { return true; }
}
