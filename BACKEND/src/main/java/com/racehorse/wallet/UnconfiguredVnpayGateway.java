package com.racehorse.wallet;

import com.racehorse.common.exception.ExternalServiceException;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

final class UnconfiguredVnpayGateway implements PayosGateway {
    private final String errorMessage;

    UnconfiguredVnpayGateway(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public Link create(long orderCode, long amountVnd, String description, Instant expiresAt) {
        throw new ExternalServiceException(errorMessage);
    }

    @Override
    public Link create(long orderCode, long amountVnd, String description, Instant expiresAt, String ipAddress) {
        throw new ExternalServiceException(errorMessage);
    }

    @Override
    public Status getStatus(long orderCode) {
        throw new ExternalServiceException(errorMessage);
    }

    @Override
    public boolean verifyWebhook(JsonNode body) {
        return false;
    }

    @Override
    public boolean verifyVnpay(Map<String, String> parameters) {
        return false;
    }

    @Override
    public boolean isFake() {
        return false;
    }

    @Override
    public boolean isVnpay() {
        return false;
    }
}
