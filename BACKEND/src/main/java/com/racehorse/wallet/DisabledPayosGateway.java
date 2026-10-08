package com.racehorse.wallet;

import com.racehorse.common.exception.ExternalServiceException;
import tools.jackson.databind.JsonNode;
import java.time.Instant;

final class DisabledPayosGateway implements PayosGateway {
    @Override
    public Link create(long orderCode, long amountVnd, String description, Instant expiresAt) {
        throw new ExternalServiceException("Cổng PayOS chưa được cấu hình");
    }

    @Override
    public Status getStatus(long orderCode) {
        throw new ExternalServiceException("Cổng PayOS chưa được cấu hình");
    }

    @Override
    public boolean verifyWebhook(JsonNode body) { return false; }

    @Override
    public boolean isFake() { return false; }
}
